// Package wol 发送网络唤醒魔术包，并定时 ping 配置中的设备，显示在线状态。
package wol

import (
	"context"
	"encoding/hex"
	"fmt"
	"html"
	"net"
	"net/http"
	"os"
	"sort"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/icmp"
	"golang.org/x/net/ipv4"

	"z6x/hub/internal/core"
)

// Device 是一台可唤醒的设备。
type Device struct {
	MAC string `yaml:"mac"`
	// IP 用于定时 ping 判断是否在线，可留空。
	IP string `yaml:"ip"`
	// Broadcast 是发送魔术包的广播地址，留空时使用投影仪所在网段的广播地址；
	// 跨网段唤醒时填写目标网段的广播地址（如 192.168.1.255）。
	Broadcast string `yaml:"broadcast"`
}

type Config struct {
	Devices     map[string]Device `yaml:"devices"`
	Iface       string            `yaml:"iface"`
	IntervalSec int               `yaml:"interval_sec"`
}

type Status struct {
	Name    string `json:"name"`
	IP      string `json:"ip,omitempty"`
	Online  *bool  `json:"online,omitempty"`
	RTTMs   int64  `json:"rtt_ms,omitempty"`
	Checked string `json:"checked,omitempty"`
}

type Module struct {
	cfg    Config
	mu     sync.Mutex
	status map[string]Status
	env    *core.Env
}

func New() *Module { return &Module{status: map[string]Status{}} }

func (m *Module) Name() string  { return "wol" }
func (m *Module) Title() string { return "网络唤醒" }

func (m *Module) Start(ctx context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{Iface: "wlan0", IntervalSec: 30}
	if err := env.Config.Decode("wol", &m.cfg); err != nil {
		return err
	}
	for name, d := range m.cfg.Devices {
		if _, err := parseMAC(d.MAC); err != nil {
			return fmt.Errorf("设备 %s：%w", name, err)
		}
		if d.IP != "" && net.ParseIP(d.IP) == nil {
			return fmt.Errorf("设备 %s 的 IP 格式不正确：%s", name, d.IP)
		}
	}
	if m.cfg.IntervalSec < 10 {
		m.cfg.IntervalSec = 10
	}
	go m.pingLoop(ctx)
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("POST /api/wol/wake", m.wake)
	r.HandleFunc("GET /api/wol/devices", func(w http.ResponseWriter, _ *http.Request) { core.WriteJSON(w, m.list()) })
	r.HandleFunc("GET /ui/wol/{$}", m.page)
}

// parseMAC 接受 AA:BB:CC:DD:EE:FF、AA-BB-… 或 AABBCCDDEEFF。
func parseMAC(s string) ([]byte, error) {
	clean := strings.NewReplacer(":", "", "-", "", ".", "").Replace(strings.TrimSpace(s))
	b, err := hex.DecodeString(clean)
	if err != nil || len(b) != 6 {
		return nil, fmt.Errorf("MAC 地址格式不正确：%s", s)
	}
	return b, nil
}

// MagicPacket 生成魔术包：6 个 0xFF，后跟目标 MAC 重复 16 次，共 102 字节。
func MagicPacket(mac []byte) []byte {
	p := make([]byte, 0, 102)
	for i := 0; i < 6; i++ {
		p = append(p, 0xff)
	}
	for i := 0; i < 16; i++ {
		p = append(p, mac...)
	}
	return p
}

// broadcastOf 计算网卡所在网段的广播地址。
func broadcastOf(iface string) (string, error) {
	ifi, err := net.InterfaceByName(iface)
	if err != nil {
		return "", err
	}
	addrs, err := ifi.Addrs()
	if err != nil {
		return "", err
	}
	for _, a := range addrs {
		n, ok := a.(*net.IPNet)
		if !ok || n.IP.To4() == nil {
			continue
		}
		ip, mask := n.IP.To4(), n.Mask
		b := make(net.IP, 4)
		for i := range b {
			b[i] = ip[i] | ^mask[len(mask)-4+i]
		}
		return b.String(), nil
	}
	return "", fmt.Errorf("网卡 %s 没有 IPv4 地址", iface)
}

func (m *Module) wake(w http.ResponseWriter, r *http.Request) {
	name := r.URL.Query().Get("name")
	d, ok := m.cfg.Devices[name]
	if !ok {
		core.WriteError(w, http.StatusNotFound, "没有这台设备："+name)
		return
	}
	mac, _ := parseMAC(d.MAC)
	bc := d.Broadcast
	if bc == "" {
		var err error
		if bc, err = broadcastOf(m.cfg.Iface); err != nil {
			core.WriteError(w, http.StatusInternalServerError, "无法确定广播地址："+err.Error())
			return
		}
	}
	if err := send(MagicPacket(mac), bc); err != nil {
		core.WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	m.env.Log.Info("已发送唤醒包", "device", name, "broadcast", bc)
	core.WriteJSON(w, map[string]string{"sent_to": bc + ":9"})
}

// send 用 UDP 广播发送魔术包（端口 9，即 discard）。连发 3 次以降低丢包影响。
func send(pkt []byte, broadcast string) error {
	addr, err := net.ResolveUDPAddr("udp4", net.JoinHostPort(broadcast, "9"))
	if err != nil {
		return err
	}
	conn, err := net.ListenUDP("udp4", nil)
	if err != nil {
		return err
	}
	defer conn.Close()
	if err := setBroadcast(conn); err != nil {
		return fmt.Errorf("无法开启广播：%w", err)
	}
	for i := 0; i < 3; i++ {
		if _, err := conn.WriteToUDP(pkt, addr); err != nil {
			return fmt.Errorf("发送失败：%w", err)
		}
	}
	return nil
}

func (m *Module) pingLoop(ctx context.Context) {
	t := time.NewTicker(time.Duration(m.cfg.IntervalSec) * time.Second)
	defer t.Stop()
	for {
		for name, d := range m.cfg.Devices {
			if d.IP == "" {
				continue
			}
			rtt, err := Ping(d.IP, 2*time.Second)
			online := err == nil
			m.mu.Lock()
			m.status[name] = Status{Name: name, IP: d.IP, Online: &online, RTTMs: rtt.Milliseconds(), Checked: time.Now().Format("15:04:05")}
			m.mu.Unlock()
		}
		select {
		case <-ctx.Done():
			return
		case <-t.C:
		}
	}
}

// Ping 用普通身份的 ICMP 套接字（SOCK_DGRAM）发一次 ping。
// 安卓通过 net.ipv4.ping_group_range 允许普通用户这样做，无需 root。
func Ping(ip string, timeout time.Duration) (time.Duration, error) {
	c, err := icmp.ListenPacket("udp4", "0.0.0.0")
	if err != nil {
		return 0, err
	}
	defer c.Close()
	msg := icmp.Message{Type: ipv4.ICMPTypeEcho, Body: &icmp.Echo{ID: os.Getpid() & 0xffff, Seq: 1, Data: []byte("z6x-hub")}}
	b, err := msg.Marshal(nil)
	if err != nil {
		return 0, err
	}
	start := time.Now()
	if _, err := c.WriteTo(b, &net.UDPAddr{IP: net.ParseIP(ip)}); err != nil {
		return 0, err
	}
	c.SetReadDeadline(start.Add(timeout))
	buf := make([]byte, 1500)
	for {
		n, _, err := c.ReadFrom(buf)
		if err != nil {
			return 0, err
		}
		if rm, err := icmp.ParseMessage(1, buf[:n]); err == nil && rm.Type == ipv4.ICMPTypeEchoReply {
			return time.Since(start), nil
		}
	}
}

func (m *Module) list() []Status {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := []Status{}
	for name, d := range m.cfg.Devices {
		s, ok := m.status[name]
		if !ok {
			s = Status{Name: name, IP: d.IP}
		}
		out = append(out, s)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	var b strings.Builder
	for _, s := range m.list() {
		fmt.Fprintf(&b, `<li><div class="row"><b>%s</b><small data-n="%s" class="st"></small></div>
<p class="row"><button data-v="%s" onclick="wake(this.dataset.v)">唤醒</button></p></li>`,
			html.EscapeString(s.Name), html.EscapeString(s.Name), html.EscapeString(s.Name))
	}
	if b.Len() == 0 {
		b.WriteString(`<li>hub.yaml 的 wol.devices 中尚未配置设备。</li>`)
	}
	core.Page(w, "网络唤醒", `<ul class="list">`+b.String()+`</ul><div class="msg" id="msg"></div>
<p><small>电脑需在 BIOS 中开启网络唤醒；Windows 还需关闭「快速启动」，否则关机后网卡会完全断电。</small></p>
<script>
async function wake(n){const r=await fetch('/api/wol/wake?name='+encodeURIComponent(n),{method:'POST'}),j=await r.json();
  document.getElementById('msg').textContent=r.ok?'已向 '+j.sent_to+' 发送唤醒包':'失败：'+j.error}
async function load(){for(const s of await (await fetch('/api/wol/devices')).json()){
  const el=document.querySelector('.st[data-n="'+CSS.escape(s.name)+'"]');if(!el)continue;
  el.textContent=s.online===undefined?(s.ip?'检测中':'未配置 IP'):(s.online?'在线 '+s.rtt_ms+' ms':'离线')+'（'+s.checked+'）';
  el.className='st '+(s.online?'running':'stopped')}}
load();setInterval(load,10000);
</script>`)
}
