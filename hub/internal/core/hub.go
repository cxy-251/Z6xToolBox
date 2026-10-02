package core

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"html"
	"log/slog"
	"net"
	"net/http"
	"runtime/debug"
	"sort"
	"strings"
	"time"
)

// Version 在编译时通过 -ldflags "-X z6x/hub/internal/core.Version=..." 写入。
var Version = "dev"

// Titled 是可选接口：模块提供网页时实现它，首页会列出入口。
type Titled interface {
	Title() string
}

// Hub 管理全部模块和主 HTTP 服务。
type Hub struct {
	cfg     *Config
	log     *slog.Logger
	entries []*entry
	started time.Time
	cfgPath string
	gate    *Gate
	stop    context.CancelFunc
}

func New(cfg *Config, log *slog.Logger) *Hub {
	return &Hub{cfg: cfg, log: log}
}

// Add 注册一个模块；未在配置中启用的模块直接忽略，既不启动也不注册路由。
func (h *Hub) Add(m Module) {
	if _, on := h.cfg.Enabled(m.Name()); !on {
		return
	}
	h.entries = append(h.entries, &entry{mod: m, state: StateStopped, since: time.Now()})
}

// Run 启动所有模块和主 HTTP 服务，直到 ctx 被取消。
func (h *Hub) Run(ctx context.Context) error {
	h.started = time.Now()
	ctx, h.stop = context.WithCancel(ctx)
	defer h.stop()
	h.gate = NewGate(h.cfg.Network, h.cfg.Token, h.log)
	mux := http.NewServeMux()
	h.coreRoutes(mux)
	h.stopRoute(mux)
	if h.cfgPath != "" {
		h.configRoutes(mux)
	}

	for _, e := range h.entries {
		e := e
		env := &Env{Config: h.cfg, Log: h.log.With("module", e.mod.Name()), Fail: func(err error) { e.set(StateFailed, err) },
			Serve: func(port int, handler http.Handler) { h.gate.Add(e.mod.Name(), port, handler) }}
		if err := h.startModule(ctx, e, env); err != nil {
			h.log.Error("模块启动失败", "module", e.mod.Name(), "err", err)
			e.set(StateFailed, err)
			continue
		}
		e.set(StateRunning, nil)
		e.mod.Routes(&moduleMux{inner: mux, e: e, log: h.log, token: h.cfg.Token})
		h.log.Info("模块已启动", "module", e.mod.Name())
	}

	// 主端口与模块的独立端口都交给 gate：按网络状态统一开启或关闭，每 15 秒检查一次。
	h.gate.Add("listen", portOf(h.cfg.Listen), logRequests(h.log, mux))
	errc := make(chan error, 1)
	go func() { errc <- h.gate.Run(ctx, 15*time.Second) }()
	h.log.Info("z6x-hub 已启动", "version", Version, "listen", h.cfg.Listen, "iface", h.cfg.Network.Iface, "modules", len(h.entries))

	var runErr error
	select {
	case <-ctx.Done():
	case runErr = <-errc:
	}
	h.stop()
	shut, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	for _, e := range h.entries {
		if err := e.mod.Stop(shut); err != nil {
			h.log.Warn("模块停止时出错", "module", e.mod.Name(), "err", err)
		}
	}
	if runErr != nil {
		return runErr
	}
	h.log.Info("z6x-hub 已退出")
	return nil
}

// startModule 调用模块的 Start，并拦截其中的 panic。
func (h *Hub) startModule(ctx context.Context, e *entry, env *Env) (err error) {
	defer func() {
		if v := recover(); v != nil {
			err = fmt.Errorf("启动时 panic：%v\n%s", v, debug.Stack())
		}
	}()
	return e.mod.Start(ctx, env)
}

// Health 是 /api/health 的返回内容。
type Health struct {
	Version   string         `json:"version"`
	UptimeSec int64          `json:"uptime_sec"`
	Modules   []ModuleStatus `json:"modules"`
}

func (h *Hub) health() Health {
	out := Health{Version: Version, UptimeSec: int64(time.Since(h.started).Seconds())}
	for _, e := range h.entries {
		out.Modules = append(out.Modules, e.status())
	}
	sort.Slice(out.Modules, func(i, j int) bool { return out.Modules[i].Name < out.Modules[j].Name })
	return out
}

func (h *Hub) coreRoutes(mux *http.ServeMux) {
	// 健康检查不要求 token：只包含版本和模块状态，不含任何敏感信息，便于工具箱判断 hub 是否在运行。
	mux.HandleFunc("GET /api/health", func(w http.ResponseWriter, r *http.Request) { WriteJSON(w, h.health()) })
	mux.Handle("GET /api/modules", RequireToken(h.cfg.Token, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		WriteJSON(w, h.health().Modules)
	})))
	mux.HandleFunc("GET /login", func(w http.ResponseWriter, r *http.Request) {
		Page(w, "登录", `<form method="post"><p>请输入 hub.yaml 中配置的 token：</p>
<input type="password" name="token" autofocus style="width:100%">
<input type="hidden" name="next" value="`+html.EscapeString(r.URL.Query().Get("next"))+`">
<p><button>登录</button></p></form>`)
	})
	mux.HandleFunc("POST /login", func(w http.ResponseWriter, r *http.Request) {
		if !ValidToken(r.FormValue("token"), h.cfg.Token) {
			time.Sleep(time.Second) // 减缓暴力尝试
			Page(w, "登录失败", `<p>token 不正确。<a href="/login">重试</a></p>`)
			return
		}
		setTokenCookie(w, h.cfg.Token)
		next := r.FormValue("next")
		if !strings.HasPrefix(next, "/") || strings.HasPrefix(next, "//") {
			next = "/"
		}
		http.Redirect(w, r, next, http.StatusSeeOther)
	})
	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		if !ValidToken(TokenFrom(r), h.cfg.Token) {
			http.Redirect(w, r, "/login?next=/", http.StatusFound)
			return
		}
		var b strings.Builder
		b.WriteString("<ul class=list>")
		for _, st := range h.health().Modules {
			e := h.find(st.Name)
			label := st.Name
			if t, ok := e.mod.(Titled); ok {
				label = fmt.Sprintf(`<a href="/ui/%s/">%s</a>`, st.Name, html.EscapeString(t.Title()))
			}
			fmt.Fprintf(&b, `<li>%s <span class="%s">%s</span>`, label, st.State, st.State)
			if st.Error != "" {
				fmt.Fprintf(&b, ` <small>%s</small>`, html.EscapeString(st.Error))
			}
			b.WriteString("</li>")
		}
		b.WriteString("</ul>")
		if bound, _ := h.gate.Status(); bound != "" && bound != "*" {
			fmt.Fprintf(&b, "<p><small>只在 %s 的 %s 上对外服务；离开可信 Wi-Fi 后自动停止，回来后自动恢复。</small></p>", html.EscapeString(h.cfg.Network.Iface), html.EscapeString(bound))
		}
		if h.cfgPath != "" {
			b.WriteString(`<p><a href="/ui/config/">编辑配置</a></p>`)
		}
		b.WriteString(`<form method="post" action="/api/stop" onsubmit="return confirm('停止 hub？停止后需要在 Deck 上执行 ./hub/ctl.sh 启动。')"><button class="ghost">停止 hub</button></form>`)
		fmt.Fprintf(&b, "<p><small>版本 %s · 已运行 %s</small></p>", Version, time.Since(h.started).Round(time.Second))
		Page(w, "z6x-hub · "+h.cfg.Name, b.String())
	})
}

func (h *Hub) find(name string) *entry {
	for _, e := range h.entries {
		if e.mod.Name() == name {
			return e
		}
	}
	return nil
}

// WriteJSON 以 JSON 格式返回 v。
func WriteJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	enc.Encode(v)
}

// WriteError 以 JSON 格式返回错误。
func WriteError(w http.ResponseWriter, code int, msg string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

type statusRecorder struct {
	http.ResponseWriter
	code int
}

func (s *statusRecorder) WriteHeader(c int) { s.code = c; s.ResponseWriter.WriteHeader(c) }

// Hijack 让 WebSocket 等需要接管底层连接的处理函数在包装后仍能工作。
func (s *statusRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	h, ok := s.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, fmt.Errorf("底层连接不支持接管")
	}
	s.code = http.StatusSwitchingProtocols
	return h.Hijack()
}

// Unwrap 供 http.ResponseController 取得原始的 ResponseWriter。
func (s *statusRecorder) Unwrap() http.ResponseWriter { return s.ResponseWriter }

// Flush 让测速等流式响应在包装后仍能及时发送。
func (s *statusRecorder) Flush() {
	if f, ok := s.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

// logRequests 记录每个请求的方法、路径、状态码和耗时。不记录查询参数和请求头，避免 token 进入日志。
func logRequests(log *slog.Logger, h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, code: 200}
		h.ServeHTTP(rec, r)
		if r.URL.Path == "/api/health" {
			return // 健康检查频繁，不记录
		}
		log.Info("请求", "method", r.Method, "path", r.URL.Path, "status", rec.code, "ms", time.Since(start).Milliseconds(), "from", r.RemoteAddr)
	})
}

// stopRoute 让用户在网页上停止 hub（例如在手机上用完后关闭）。Cookie 为 SameSite=Strict，其他网站无法代为提交。
func (h *Hub) stopRoute(mux *http.ServeMux) {
	mux.Handle("POST /api/stop", RequireToken(h.cfg.Token, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h.log.Warn("收到停止请求", "remote", r.RemoteAddr)
		Page(w, "已停止", `<p>hub 已停止。重新启动：在 Deck 上执行 <code>./hub/ctl.sh 设备名 start</code>。</p>`)
		go func() { time.Sleep(500 * time.Millisecond); h.stop() }()
	})))
}
