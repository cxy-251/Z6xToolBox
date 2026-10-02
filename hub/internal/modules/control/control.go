// Package control 提供遥控接口和手机网页遥控器：按键、输入文字、启动应用、按键宏。
// 也是智能家居（如 Home Assistant）控制投影仪的入口。
package control

import (
	"context"
	"encoding/json"
	"fmt"
	"html"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"

	"z6x/hub/internal/android"
	"z6x/hub/internal/core"
)

// Macro 是一组预设按键，按顺序发出，相邻两键之间间隔 IntervalMs 毫秒。
type Macro struct {
	Keys       []string `yaml:"keys"`
	IntervalMs int      `yaml:"interval_ms"`
}

type Config struct {
	Macros map[string]Macro `yaml:"macros"`
	// Apps 是遥控页面上的快捷应用：显示名称 → 包名。
	Apps map[string]string `yaml:"apps"`
}

type Module struct {
	cfg Config
	dev android.Device
	// mu 保证同一时间只执行一个按键序列，避免两个宏交错。
	mu sync.Mutex
}

func New(r android.Runner) *Module {
	if r == nil {
		r = android.Exec{}
	}
	return &Module{dev: android.Device{R: r}}
}

func (m *Module) Name() string  { return "control" }
func (m *Module) Title() string { return "网页遥控器" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	if err := env.Config.Decode("control", &m.cfg); err != nil {
		return err
	}
	for name, mac := range m.cfg.Macros {
		if len(mac.Keys) == 0 || len(mac.Keys) > 50 {
			return fmt.Errorf("宏 %s 的按键数量应在 1~50 之间", name)
		}
		for _, k := range mac.Keys {
			if _, ok := android.Keys[strings.ToLower(k)]; !ok {
				return fmt.Errorf("宏 %s 中有未知按键：%s", name, k)
			}
		}
	}
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("POST /api/control/key", m.key)
	r.HandleFunc("POST /api/control/text", m.text)
	r.HandleFunc("POST /api/control/app", m.app)
	r.HandleFunc("POST /api/control/macro", m.macro)
	r.HandleFunc("GET /api/control/macros", func(w http.ResponseWriter, _ *http.Request) { core.WriteJSON(w, m.cfg.Macros) })
	r.HandleFunc("GET /ui/control/{$}", m.page)
}

type request struct {
	Key     string `json:"key"`
	Repeat  int    `json:"repeat"`
	Text    string `json:"text"`
	Package string `json:"package"`
	Name    string `json:"name"`
}

func decode(w http.ResponseWriter, r *http.Request) (request, bool) {
	var req request
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16<<10)).Decode(&req); err != nil {
		core.WriteError(w, http.StatusBadRequest, "请求格式错误，应为 JSON")
		return req, false
	}
	return req, true
}

func reply(w http.ResponseWriter, err error) {
	if err != nil {
		core.WriteError(w, http.StatusBadRequest, err.Error())
		return
	}
	core.WriteJSON(w, map[string]bool{"ok": true})
}

func (m *Module) key(w http.ResponseWriter, r *http.Request) {
	req, ok := decode(w, r)
	if !ok {
		return
	}
	n := req.Repeat
	if n < 1 {
		n = 1
	}
	if n > 20 {
		n = 20
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for i := 0; i < n; i++ {
		if err := m.dev.Key(r.Context(), req.Key); err != nil {
			reply(w, err)
			return
		}
	}
	reply(w, nil)
}

func (m *Module) text(w http.ResponseWriter, r *http.Request) {
	if req, ok := decode(w, r); ok {
		reply(w, m.dev.Text(r.Context(), req.Text))
	}
}

func (m *Module) app(w http.ResponseWriter, r *http.Request) {
	if req, ok := decode(w, r); ok {
		reply(w, m.dev.LaunchApp(r.Context(), req.Package))
	}
}

func (m *Module) macro(w http.ResponseWriter, r *http.Request) {
	req, ok := decode(w, r)
	if !ok {
		return
	}
	mac, found := m.cfg.Macros[req.Name]
	if !found {
		core.WriteError(w, http.StatusNotFound, "没有这个宏："+req.Name)
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for i, k := range mac.Keys {
		if i > 0 && mac.IntervalMs > 0 {
			time.Sleep(time.Duration(mac.IntervalMs) * time.Millisecond)
		}
		if err := m.dev.Key(r.Context(), k); err != nil {
			reply(w, fmt.Errorf("第 %d 个按键失败：%w", i+1, err))
			return
		}
	}
	reply(w, nil)
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	var extra strings.Builder
	if len(m.cfg.Apps) > 0 {
		names := make([]string, 0, len(m.cfg.Apps))
		for n := range m.cfg.Apps {
			names = append(names, n)
		}
		sort.Strings(names)
		extra.WriteString(`<div class="card"><p>应用</p><div class="row">`)
		for _, n := range names {
			fmt.Fprintf(&extra, `<button class="ghost" data-v="%s" onclick="call('app',{package:this.dataset.v})">%s</button>`,
				html.EscapeString(m.cfg.Apps[n]), html.EscapeString(n))
		}
		extra.WriteString(`</div></div>`)
	}
	if len(m.cfg.Macros) > 0 {
		names := make([]string, 0, len(m.cfg.Macros))
		for n := range m.cfg.Macros {
			names = append(names, n)
		}
		sort.Strings(names)
		extra.WriteString(`<div class="card"><p>按键宏</p><div class="row">`)
		for _, n := range names {
			fmt.Fprintf(&extra, `<button class="ghost" data-v="%s" onclick="call('macro',{name:this.dataset.v})">%s</button>`,
				html.EscapeString(n), html.EscapeString(n))
		}
		extra.WriteString(`</div></div>`)
	}
	core.Page(w, "网页遥控器", `<div class="card"><div class="grid">
<button class="ghost" onclick="k('back')">返回</button><button onclick="k('up')">▲</button><button class="ghost" onclick="k('home')">主页</button>
<button onclick="k('left')">◀</button><button onclick="k('ok')">确认</button><button onclick="k('right')">▶</button>
<button class="ghost" onclick="k('menu')">菜单</button><button onclick="k('down')">▼</button><button class="ghost" onclick="k('settings')">设置</button>
<button class="ghost" onclick="k('voldown')">音量 −</button><button class="ghost" onclick="k('mute')">静音</button><button class="ghost" onclick="k('volup')">音量 +</button>
</div><div class="msg" id="msg"></div></div>
<div class="card"><p>输入文字到电视（仅英文、数字和半角符号）</p><input id="t"><p><button onclick="call('text',{text:document.getElementById('t').value})">发送</button></p></div>
`+extra.String()+`
<script>
const msg=t=>document.getElementById('msg').textContent=t;
async function call(a,b){const t=Date.now();try{const r=await fetch('/api/control/'+a,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)});
  const j=await r.json();msg(r.ok?'完成（'+(Date.now()-t)+' ms）':'失败：'+j.error)}catch(e){msg('失败：'+e.message)}}
const k=key=>call('key',{key});
</script>`)
}
