// Package clash 为投影仪上的 Clash 提供「加了白名单的订阅」：用户的 Clash 配置有意拒绝国内网站（屏蔽广告与统计），
// 开发时需要访问的少数网站（软件源镜像等）要直连。hub 用用户的订阅链接拉取原配置，在规则最前面插入白名单直连规则，
// 其余（节点、策略组、原有规则）原样保留，作为新的订阅地址交给 Clash。原配置可以是订阅链接，也可以是上传的配置文件
// （用户原来的配置在 Deck 上，曾由 Deck 的 8000 端口导入投影仪）：
//
//	Clash 中添加订阅 http://127.0.0.1:8090/clash/profile.yaml 并选中（本机访问不需要登录）。
//
// 白名单在「🔧 工具 → Clash 白名单」中编辑，Clash 中点「更新」或等自动更新后生效。
// 订阅链接与配置文件含节点凭据，只保存在设备的数据目录（权限 600），页面上不显示，从局域网访问配置需要登录。
package clash

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"

	"gopkg.in/yaml.v3"

	"z6x/hub/internal/core"
)

const (
	// fetchTimeout：拉取原订阅的超时
	fetchTimeout = 30 * time.Second
	// userAgent：订阅服务按它返回 Clash Meta（mihomo）格式的配置
	userAgent = "clash.meta"
	// maxProfile：原订阅的大小上限
	maxProfile = 16 << 20
	// controllerAddr：生成的配置中 Clash 控制接口的地址（只监听本机）
	controllerAddr = "127.0.0.1:9090"
	// logRetry：连不上控制接口时（Clash 未运行，或尚未切换到本配置）多久重试
	logRetry = 15 * time.Second
	// maxRejected：最多记录多少个被拒绝的域名
	maxRejected = 300
)

// rejectLine 匹配 Clash（mihomo）日志中的拒绝记录，如：
// [TCP] 127.0.0.1:41234 --> www.baidu.com:443 match GeoSite(cn) using REJECT
var rejectLine = regexp.MustCompile(`--> (\S+?):\d+ match (.+?) using REJECT`)

// watchLogs 持续读取 Clash 的日志（GET /logs，每行一条 JSON），记下被拒绝的域名。
func (m *Module) watchLogs(ctx context.Context) {
	for ctx.Err() == nil {
		m.mu.Lock()
		secret := m.st.Secret
		m.mu.Unlock()
		req, _ := http.NewRequestWithContext(ctx, "GET", "http://"+controllerAddr+"/logs?level=info", nil)
		req.Header.Set("Authorization", "Bearer "+secret)
		resp, err := http.DefaultClient.Do(req)
		if err == nil && resp.StatusCode != 200 {
			resp.Body.Close()
			err = fmt.Errorf("控制接口返回 %d（Clash 可能还没有更新到本配置）", resp.StatusCode)
		}
		if err != nil {
			m.mu.Lock()
			m.ctl = "未连接 Clash 控制接口：" + err.Error()
			m.mu.Unlock()
			select {
			case <-ctx.Done():
			case <-time.After(logRetry):
			}
			continue
		}
		m.mu.Lock()
		m.ctl = "已连接 Clash，正在记录被拒绝的域名"
		m.mu.Unlock()
		sc := bufio.NewScanner(resp.Body)
		for sc.Scan() {
			var e struct{ Payload string }
			if json.Unmarshal(sc.Bytes(), &e) != nil {
				continue
			}
			mm := rejectLine.FindStringSubmatch(e.Payload)
			if mm == nil {
				continue
			}
			m.recordReject(mm[1], mm[2])
		}
		resp.Body.Close()
	}
}

func (m *Module) recordReject(host, rule string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, w := range m.st.Whitelist { // 已在白名单中的不再列出
		if host == w || strings.HasSuffix(host, "."+w) {
			return
		}
	}
	r := m.rej[host]
	if r == nil {
		if len(m.rej) >= maxRejected { // 去掉最久没出现的
			var oldest *rejected
			for _, x := range m.rej {
				if oldest == nil || x.at.Before(oldest.at) {
					oldest = x
				}
			}
			delete(m.rej, oldest.Host)
		}
		r = &rejected{Host: host}
		m.rej[host] = r
	}
	r.Rule, r.Count, r.at = rule, r.Count+1, time.Now()
	r.Last = r.at.Format("15:04:05")
}

// defaultWhitelist 是首次使用时的白名单：开发常用的国内软件源镜像。
var defaultWhitelist = []string{"mirrors.tuna.tsinghua.edu.cn", "mirrors.ustc.edu.cn", "mirrors.sdu.edu.cn", "mirrors.aliyun.com", "goproxy.cn"}

type state struct {
	Subscription string   `json:"subscription"`
	Whitelist    []string `json:"whitelist"`
	LastFetch    string   `json:"last_fetch"`
	LastError    string   `json:"last_error"`
	RuleCount    int      `json:"rule_count"` // 原配置中的规则数
	Secret       string   `json:"secret"`     // Clash 控制接口的密码（只监听本机）
}

// rejected 是一个被原规则拒绝过的域名。
type rejected struct {
	Host  string `json:"host"`
	Rule  string `json:"rule"`
	Count int    `json:"count"`
	Last  string `json:"last"`
	at    time.Time
}

type Module struct {
	env   *core.Env
	mu    sync.Mutex
	st    state
	rej   map[string]*rejected // 最近被拒绝的域名（读取 Clash 日志得到）
	ctl   string               // Clash 控制接口的状态说明
	path  string               // 状态文件
	cache string               // 原配置：上传的配置文件，或最近一次拉取成功的订阅
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "clash" }
func (m *Module) Title() string { return "Clash 白名单" }

func (m *Module) Start(ctx context.Context, env *core.Env) error {
	m.env = env
	m.path = filepath.Join(env.Config.DataDir, "clash.json")
	m.cache = filepath.Join(env.Config.DataDir, "clash_source.yaml")
	m.st.Whitelist = defaultWhitelist
	if b, err := os.ReadFile(m.path); err == nil {
		json.Unmarshal(b, &m.st)
	}
	if m.st.Secret == "" {
		b := make([]byte, 16)
		rand.Read(b)
		m.st.Secret = hex.EncodeToString(b)
		m.save()
	}
	m.rej = map[string]*rejected{}
	go m.watchLogs(ctx)
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) save() error {
	b, _ := json.MarshalIndent(m.st, "", "  ")
	return os.WriteFile(m.path, b, 0o600)
}

// fetch 取得原配置：有订阅链接时拉取（失败时使用最近一次成功的副本），否则使用上传的配置文件。
func (m *Module) fetch(ctx context.Context) ([]byte, error) {
	m.mu.Lock()
	sub := m.st.Subscription
	m.mu.Unlock()
	if sub == "" {
		if b, err := os.ReadFile(m.cache); err == nil {
			return b, nil
		}
		return nil, fmt.Errorf("还没有原配置：请上传配置文件或填写订阅链接")
	}
	ctx, cancel := context.WithTimeout(ctx, fetchTimeout)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, "GET", sub, nil)
	if err != nil {
		return nil, fmt.Errorf("订阅链接格式错误")
	}
	req.Header.Set("User-Agent", userAgent)
	resp, err := http.DefaultClient.Do(req)
	var body []byte
	if err == nil {
		defer resp.Body.Close()
		body, err = io.ReadAll(io.LimitReader(resp.Body, maxProfile))
		if err == nil && resp.StatusCode != 200 {
			err = fmt.Errorf("订阅服务返回 %d", resp.StatusCode)
		}
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.st.LastFetch = time.Now().Format("01-02 15:04:05")
	if err != nil {
		// 错误信息中可能带有订阅地址，只保留错误类型
		msg := err.Error()
		if ue, ok := err.(interface{ Unwrap() error }); ok && ue.Unwrap() != nil {
			msg = ue.Unwrap().Error()
		}
		m.st.LastError = msg
		m.save()
		if old, e := os.ReadFile(m.cache); e == nil {
			return old, nil
		}
		return nil, fmt.Errorf("拉取订阅失败：%s", msg)
	}
	m.st.LastError = ""
	os.WriteFile(m.cache, body, 0o600)
	m.save()
	return body, nil
}

// withWhitelist 在原配置的 rules 最前面插入白名单直连规则；secret 不为空时同时打开只监听本机的控制接口
// （hub 由此读取被拒绝的域名）并把日志级别设为 info（拒绝记录是 info 级）。其余内容不变。
func withWhitelist(raw []byte, whitelist []string, secret string) ([]byte, int, error) {
	var doc yaml.Node
	if err := yaml.Unmarshal(raw, &doc); err != nil || len(doc.Content) == 0 || doc.Content[0].Kind != yaml.MappingNode {
		return nil, 0, fmt.Errorf("原订阅不是 Clash 配置（可能是节点列表格式）")
	}
	root := doc.Content[0]
	var rules *yaml.Node
	for i := 0; i+1 < len(root.Content); i += 2 {
		if root.Content[i].Value == "rules" {
			rules = root.Content[i+1]
		}
	}
	if rules == nil || rules.Kind != yaml.SequenceNode {
		return nil, 0, fmt.Errorf("原订阅中没有 rules")
	}
	if secret != "" {
		for _, kv := range [][2]string{{"external-controller", controllerAddr}, {"secret", secret}, {"log-level", "info"}} {
			set := false
			for i := 0; i+1 < len(root.Content); i += 2 {
				if root.Content[i].Value == kv[0] {
					root.Content[i+1] = &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: kv[1]}
					set = true
				}
			}
			if !set {
				root.Content = append([]*yaml.Node{{Kind: yaml.ScalarNode, Tag: "!!str", Value: kv[0]}, {Kind: yaml.ScalarNode, Tag: "!!str", Value: kv[1]}}, root.Content...)
			}
		}
	}
	count := len(rules.Content)
	var add []*yaml.Node
	for _, w := range whitelist {
		w = strings.TrimSpace(w)
		if w == "" || strings.HasPrefix(w, "#") {
			continue
		}
		rule := "DOMAIN-SUFFIX," + w + ",DIRECT"
		if _, _, err := net.ParseCIDR(w); err == nil {
			rule = "IP-CIDR," + w + ",DIRECT,no-resolve"
		} else if ip := net.ParseIP(w); ip != nil {
			rule = "IP-CIDR," + w + "/32,DIRECT,no-resolve"
		}
		add = append(add, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: rule})
	}
	rules.Content = append(add, rules.Content...)
	var buf bytes.Buffer
	enc := yaml.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(&doc); err != nil {
		return nil, 0, err
	}
	return buf.Bytes(), count, nil
}

func (m *Module) Routes(r core.Router) {
	// 给 Clash 的订阅地址：本机访问不需要登录（trust_local），从局域网访问需要登录
	r.HandleFunc("GET /clash/profile.yaml", func(w http.ResponseWriter, req *http.Request) {
		raw, err := m.fetch(req.Context())
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadGateway)
			return
		}
		m.mu.Lock()
		wl := append([]string{}, m.st.Whitelist...)
		m.mu.Unlock()
		m.mu.Lock()
		secret := m.st.Secret
		m.mu.Unlock()
		out, count, err := withWhitelist(raw, wl, secret)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadGateway)
			return
		}
		m.mu.Lock()
		m.st.RuleCount = count
		m.save()
		m.mu.Unlock()
		m.env.Log.Info("Clash 拉取了加白名单的订阅", "whitelist", len(wl), "rules", count)
		w.Header().Set("Content-Type", "text/yaml; charset=utf-8")
		w.Write(out)
	})
	r.HandleFunc("GET /api/clash/{$}", func(w http.ResponseWriter, _ *http.Request) {
		m.mu.Lock()
		defer m.mu.Unlock()
		_, err := os.Stat(m.cache)
		core.WriteJSON(w, map[string]any{"has_subscription": m.st.Subscription != "", "has_file": err == nil && m.st.Subscription == "", "whitelist": m.st.Whitelist,
			"last_fetch": m.st.LastFetch, "last_error": m.st.LastError, "rule_count": m.st.RuleCount})
	})
	// 修改白名单或订阅链接：{"whitelist": [...]} 或 {"subscription": "..."}；未给出的项不变
	r.HandleFunc("POST /api/clash/{$}", func(w http.ResponseWriter, req *http.Request) {
		var in struct {
			Whitelist    *[]string `json:"whitelist"`
			Subscription *string   `json:"subscription"`
		}
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, 64<<10)).Decode(&in); err != nil {
			core.WriteError(w, http.StatusBadRequest, "请求格式错误")
			return
		}
		m.mu.Lock()
		if in.Whitelist != nil {
			var wl []string
			for _, d := range *in.Whitelist {
				if d = strings.TrimSpace(d); d != "" {
					wl = append(wl, d)
				}
			}
			m.st.Whitelist = wl
		}
		if in.Subscription != nil {
			m.st.Subscription = strings.TrimSpace(*in.Subscription)
			os.Remove(m.cache)
		}
		err := m.save()
		m.mu.Unlock()
		if err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]bool{"ok": true})
	})
	// 上传原配置文件（请求体即 YAML）；上传后不再使用订阅链接
	r.HandleFunc("POST /api/clash/source", func(w http.ResponseWriter, req *http.Request) {
		raw, err := io.ReadAll(http.MaxBytesReader(w, req.Body, maxProfile))
		if err == nil {
			_, _, err = withWhitelist(raw, nil, "")
		}
		if err != nil {
			core.WriteError(w, http.StatusBadRequest, "不是有效的 Clash 配置："+err.Error())
			return
		}
		m.mu.Lock()
		defer m.mu.Unlock()
		if err := os.WriteFile(m.cache, raw, 0o600); err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		m.st.Subscription, m.st.LastError = "", ""
		m.save()
		core.WriteJSON(w, map[string]bool{"ok": true})
	})
	// 测试：拉取一次并生成配置，返回规则数（不返回配置内容）
	r.HandleFunc("POST /api/clash/test", func(w http.ResponseWriter, req *http.Request) {
		raw, err := m.fetch(req.Context())
		if err == nil {
			m.mu.Lock()
			wl := append([]string{}, m.st.Whitelist...)
			m.mu.Unlock()
			var count int
			if _, count, err = withWhitelist(raw, wl, ""); err == nil {
				core.WriteJSON(w, map[string]int{"rules": count, "whitelist": len(wl)})
				return
			}
		}
		core.WriteError(w, http.StatusBadGateway, err.Error())
	})
	// 最近被拒绝的域名（不在白名单中的），按最近一次时间排列
	r.HandleFunc("GET /api/clash/rejected", func(w http.ResponseWriter, _ *http.Request) {
		m.mu.Lock()
		defer m.mu.Unlock()
		out := []*rejected{}
		for _, r := range m.rej {
			out = append(out, r)
		}
		sort.Slice(out, func(i, j int) bool { return out[i].at.After(out[j].at) })
		core.WriteJSON(w, map[string]any{"controller": m.ctl, "items": out})
	})
	r.HandleFunc("GET /ui/clash/{$}", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "Clash 白名单", pageHTML) })
}

const pageHTML = `<div class="card"><b>使用方法</b><ol style="margin:6px 0 0;padding-left:20px;line-height:1.8"><small>
<li>在下面填写原来的订阅链接并保存（只保存在本设备上，页面上不显示）。</li>
<li>在投影仪的 Clash 中添加订阅：<code>http://127.0.0.1:8090/clash/profile.yaml</code>，并选中它。原来的订阅保留，随时可以切回。</li>
<li>修改白名单后，在 Clash 中点「更新」该订阅，或等它自动更新。</li></small></ol></div>
<div class="card"><b>白名单</b> <small>每行一个域名（含其所有子域名）或 IP / 网段，直连、不受原规则拒绝</small>
<textarea id="wl" rows="10" style="width:100%;font-family:monospace;margin-top:6px"></textarea>
<p><button id="save">保存白名单</button> <span id="wmsg" class="muted"></span></p></div>
<div class="card"><b>最近被拒绝的域名</b> <small id="ctl"></small>
<p><small>打开某个应用后，它访问被原规则拒绝的域名会出现在这里；需要的就加入白名单（之后在 Clash 中更新订阅）。</small></p>
<div id="rej"></div></div>
<div class="card"><b>原配置</b> <small id="sub"></small>
<p><small>上传配置文件：</small> <input type="file" id="file" accept=".yaml,.yml"></p>
<p><small>或填写订阅链接（填写后优先使用）：</small></p>
<p><input id="url" type="password" placeholder="粘贴原来的订阅链接" style="width:100%"></p>
<p><button id="setsub">保存订阅链接</button> <button id="test">测试拉取</button> <span id="tmsg" class="muted"></span></p>
<p><small id="last"></small></p></div>
<script>
const $=id=>document.getElementById(id);
const post=async(u,b)=>{const r=await fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:b?JSON.stringify(b):undefined});const d=await r.json().catch(()=>({}));if(!r.ok)throw new Error(d.error||r.status);return d};
async function load(){const s=await (await fetch('/api/clash/')).json();$('wl').value=(s.whitelist||[]).join('\n');
  $('sub').textContent=s.has_subscription?'使用订阅链接（已设置）':s.has_file?'使用上传的配置文件':'未设置';
  $('last').textContent=s.last_fetch?'最近拉取：'+s.last_fetch+(s.last_error?'，失败：'+s.last_error+'（使用上次成功的副本）':'，成功，原配置有 '+s.rule_count+' 条规则'):''}
$('save').onclick=async()=>{try{await post('/api/clash/',{whitelist:$('wl').value.split('\n')});$('wmsg').textContent='已保存，在 Clash 中更新订阅后生效';load()}catch(e){alert(e.message)}};
$('setsub').onclick=async()=>{if(!$('url').value.trim())return alert('请先粘贴订阅链接');try{await post('/api/clash/',{subscription:$('url').value});$('url').value='';load()}catch(e){alert(e.message)}};
$('file').onchange=async()=>{const f=$('file').files[0];if(!f)return;const r=await fetch('/api/clash/source',{method:'POST',body:await f.text()});
  const d=await r.json().catch(()=>({}));if(!r.ok)alert(d.error||r.status);$('file').value='';load()};
$('test').onclick=async()=>{$('tmsg').textContent='拉取中…';try{const d=await post('/api/clash/test');$('tmsg').textContent='成功：原配置 '+d.rules+' 条规则，前面加 '+d.whitelist+' 条白名单'}catch(e){$('tmsg').textContent='失败：'+e.message}load()};
async function loadRej(){const d=await (await fetch('/api/clash/rejected')).json();$('ctl').textContent=d.controller||'';const box=$('rej');box.replaceChildren();
  if(!d.items.length){box.textContent='（暂无）';return}
  for(const r of d.items){const row=document.createElement('div');row.style.cssText='display:flex;gap:8px;align-items:center;padding:4px 0;border-top:1px solid var(--line)';
    const n=document.createElement('span');n.style.flex='1';n.textContent=r.host;const i=document.createElement('small');i.textContent=r.count+' 次 · '+r.last+' · '+r.rule;
    const b=document.createElement('button');b.textContent='加入白名单';b.onclick=async()=>{const s=await (await fetch('/api/clash/')).json();
      if(!s.whitelist.includes(r.host)){await post('/api/clash/',{whitelist:[...s.whitelist,r.host]})}load();loadRej()};
    row.append(n,i,b);box.append(row)}}
load();loadRej();setInterval(loadRej,5000);
</script>`
