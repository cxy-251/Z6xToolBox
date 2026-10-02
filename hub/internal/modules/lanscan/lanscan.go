// Package lanscan 用 SSDP 扫描局域网中的 UPnP 设备（路由器、电视、音箱、NAS 等），
// 列出名称、厂商、型号和地址。
//
// 原理：向组播地址 239.255.255.250:1900 发送 M-SEARCH 请求，设备回复自身描述文件的地址（LOCATION），
// 再读取该 XML 文件得到详细信息。UDP 1900 已被系统组件占用，因此本模块只从随机端口发送、接收回复，不绑定 1900。
package lanscan

import (
	"bufio"
	"bytes"
	"context"
	"encoding/xml"
	"fmt"
	"html"
	"io"
	"net"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"

	"z6x/hub/internal/core"
)

type Config struct {
	// TimeoutSec 是等待设备回复的时间。
	TimeoutSec int `yaml:"timeout_sec"`
}

// Device 是扫描到的一台设备。同一台设备可能回复多条（不同服务），按 IP 合并。
type Device struct {
	IP           string   `json:"ip"`
	Name         string   `json:"name,omitempty"`
	Manufacturer string   `json:"manufacturer,omitempty"`
	Model        string   `json:"model,omitempty"`
	Type         string   `json:"type,omitempty"`
	Server       string   `json:"server,omitempty"`
	Services     []string `json:"services,omitempty"`
}

type Module struct {
	cfg    Config
	mu     sync.Mutex
	last   []Device
	lastAt time.Time
	busy   sync.Mutex
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "lanscan" }
func (m *Module) Title() string { return "局域网设备扫描" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.cfg = Config{TimeoutSec: 3}
	if err := env.Config.Decode("lanscan", &m.cfg); err != nil {
		return err
	}
	if m.cfg.TimeoutSec < 1 || m.cfg.TimeoutSec > 10 {
		m.cfg.TimeoutSec = 3
	}
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("POST /api/lanscan/scan", m.scanHandler)
	r.HandleFunc("GET /api/lanscan/{$}", func(w http.ResponseWriter, _ *http.Request) {
		m.mu.Lock()
		defer m.mu.Unlock()
		core.WriteJSON(w, map[string]any{"time": m.lastAt.Format(time.RFC3339), "devices": m.last})
	})
	r.HandleFunc("GET /ui/lanscan/{$}", m.page)
}

func (m *Module) scanHandler(w http.ResponseWriter, r *http.Request) {
	if !m.busy.TryLock() {
		core.WriteError(w, http.StatusConflict, "正在扫描，请稍候")
		return
	}
	defer m.busy.Unlock()
	devs, err := Scan(r.Context(), time.Duration(m.cfg.TimeoutSec)*time.Second)
	if err != nil {
		core.WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	m.mu.Lock()
	m.last, m.lastAt = devs, time.Now()
	m.mu.Unlock()
	core.WriteJSON(w, map[string]any{"time": m.lastAt.Format(time.RFC3339), "devices": devs})
}

const mSearch = "M-SEARCH * HTTP/1.1\r\n" +
	"HOST: 239.255.255.250:1900\r\n" +
	"MAN: \"ssdp:discover\"\r\n" +
	"MX: 2\r\n" +
	"ST: ssdp:all\r\n\r\n"

// reply 是一条 SSDP 回复中有用的部分。
type reply struct {
	ip, location, server, st string
}

// Scan 发送搜索请求并收集回复，然后读取各设备的描述文件。
func Scan(ctx context.Context, wait time.Duration) ([]Device, error) {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{})
	if err != nil {
		return nil, fmt.Errorf("无法创建 UDP 套接字：%w", err)
	}
	defer conn.Close()
	dst := &net.UDPAddr{IP: net.IPv4(239, 255, 255, 250), Port: 1900}
	for i := 0; i < 2; i++ { // UDP 可能丢包，发送两次
		if _, err := conn.WriteToUDP([]byte(mSearch), dst); err != nil {
			return nil, fmt.Errorf("发送搜索请求失败：%w", err)
		}
		time.Sleep(100 * time.Millisecond)
	}

	conn.SetReadDeadline(time.Now().Add(wait))
	var replies []reply
	buf := make([]byte, 4096)
	for {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			break // 超时即结束
		}
		if rp, ok := parseReply(buf[:n], from.IP.String()); ok {
			replies = append(replies, rp)
		}
	}
	return describe(ctx, replies), nil
}

// parseReply 解析 SSDP 回复，它的格式与 HTTP 响应头相同。
func parseReply(b []byte, ip string) (reply, bool) {
	sc := bufio.NewScanner(bytes.NewReader(b))
	if !sc.Scan() || !strings.HasPrefix(sc.Text(), "HTTP/1.1 200") {
		return reply{}, false
	}
	rp := reply{ip: ip}
	for sc.Scan() {
		k, v, ok := strings.Cut(sc.Text(), ":")
		if !ok {
			continue
		}
		v = strings.TrimSpace(v)
		switch strings.ToUpper(strings.TrimSpace(k)) {
		case "LOCATION":
			rp.location = v
		case "SERVER":
			rp.server = v
		case "ST":
			rp.st = v
		}
	}
	return rp, true
}

// describe 按 IP 合并回复，并读取每台设备的描述文件（最多并发 8 个，每个 3 秒超时）。
func describe(ctx context.Context, replies []reply) []Device {
	byIP := map[string]*Device{}
	locs := map[string]string{}
	for _, rp := range replies {
		d := byIP[rp.ip]
		if d == nil {
			d = &Device{IP: rp.ip}
			byIP[rp.ip] = d
		}
		if d.Server == "" {
			d.Server = rp.server
		}
		if rp.st != "" && !contains(d.Services, rp.st) {
			d.Services = append(d.Services, rp.st)
		}
		if _, ok := locs[rp.ip]; !ok && sameHost(rp.location, rp.ip) {
			locs[rp.ip] = rp.location
		}
	}
	client := &http.Client{Timeout: 3 * time.Second}
	sem := make(chan struct{}, 8)
	var wg sync.WaitGroup
	var mu sync.Mutex
	for ip, loc := range locs {
		wg.Add(1)
		go func() {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			info, err := fetchDescription(ctx, client, loc)
			if err != nil {
				return
			}
			mu.Lock()
			d := byIP[ip]
			d.Name, d.Manufacturer, d.Model, d.Type = info.Device.FriendlyName, info.Device.Manufacturer, info.Device.ModelName, shortType(info.Device.DeviceType)
			mu.Unlock()
		}()
	}
	wg.Wait()
	out := make([]Device, 0, len(byIP))
	for _, d := range byIP {
		sort.Strings(d.Services)
		out = append(out, *d)
	}
	sort.Slice(out, func(i, j int) bool { return ipLess(out[i].IP, out[j].IP) })
	return out
}

// sameHost 只读取与回复来源 IP 相同的描述文件地址，避免被引导去访问其他主机。
func sameHost(loc, ip string) bool {
	u, err := url.Parse(loc)
	return err == nil && (u.Scheme == "http" || u.Scheme == "https") && u.Hostname() == ip
}

type description struct {
	Device struct {
		DeviceType   string `xml:"deviceType"`
		FriendlyName string `xml:"friendlyName"`
		Manufacturer string `xml:"manufacturer"`
		ModelName    string `xml:"modelName"`
	} `xml:"device"`
}

func fetchDescription(ctx context.Context, c *http.Client, loc string) (description, error) {
	var d description
	req, err := http.NewRequestWithContext(ctx, "GET", loc, nil)
	if err != nil {
		return d, err
	}
	res, err := c.Do(req)
	if err != nil {
		return d, err
	}
	defer res.Body.Close()
	// encoding/xml 不解析外部实体，不存在 XXE 风险；同时限制读取大小。
	err = xml.NewDecoder(io.LimitReader(res.Body, 256<<10)).Decode(&d)
	return d, err
}

// shortType 把 urn:schemas-upnp-org:device:MediaRenderer:1 简化为 MediaRenderer。
func shortType(t string) string {
	parts := strings.Split(t, ":")
	if len(parts) >= 2 {
		return parts[len(parts)-2]
	}
	return t
}

func contains(s []string, v string) bool {
	for _, x := range s {
		if x == v {
			return true
		}
	}
	return false
}

func ipLess(a, b string) bool {
	pa, pb := net.ParseIP(a).To4(), net.ParseIP(b).To4()
	if pa == nil || pb == nil {
		return a < b
	}
	return bytes.Compare(pa, pb) < 0
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "局域网设备扫描", `<div class="card"><p class="row"><button id="go" onclick="scan()">开始扫描</button><span class="msg" id="msg"></span></p>
<small>通过 SSDP 发现支持 UPnP 的设备（路由器、电视、音箱、NAS 等），约需 `+html.EscapeString(fmt.Sprint(m.cfg.TimeoutSec))+` 秒。不支持 UPnP 的设备不会出现。</small></div>
<ul class="list" id="ls"></ul>
<script>
function render(r){const ul=document.getElementById('ls');ul.innerHTML='';
  if(!r.devices||!r.devices.length){ul.innerHTML='<li>没有发现设备</li>';return}
  for(const d of r.devices){const li=document.createElement('li');
    const b=document.createElement('b');b.textContent=(d.name||'（未提供名称）')+'  ';li.append(b);
    const s=document.createElement('small');s.textContent=d.ip+(d.manufacturer?' · '+d.manufacturer:'')+(d.model?' · '+d.model:'')+(d.type?' · '+d.type:'');li.append(s);
    if(d.server){const p=document.createElement('div');p.innerHTML='<small></small>';p.firstChild.textContent=d.server;li.append(p)}
    ul.append(li)}}
async function scan(){const b=document.getElementById('go'),msg=document.getElementById('msg');b.disabled=true;msg.textContent='扫描中…';
  const r=await fetch('/api/lanscan/scan',{method:'POST'}),j=await r.json();
  msg.textContent=r.ok?'发现 '+j.devices.length+' 台设备':'失败：'+j.error;if(r.ok)render(j);b.disabled=false}
fetch('/api/lanscan/').then(r=>r.json()).then(j=>{if(j.devices)render(j)});
</script>`)
}
