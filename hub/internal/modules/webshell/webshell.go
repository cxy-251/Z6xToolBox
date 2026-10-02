// Package webshell 提供网页终端：浏览器通过 WebSocket 连接到投影仪上的一个 shell。
//
// **这相当于把 shell 权限开放给网页**，因此：
//   - 默认关闭，必须在配置中显式启用；
//   - 除 token 鉴权外，只允许 allow_from 中列出的 IP 或网段访问，且该列表不能为空；
//   - 检查 WebSocket 请求的 Origin 必须与当前地址一致，防止其他网站借用浏览器中已登录的 Cookie 连入（跨站 WebSocket 劫持）。
//
// 原理：打开 /dev/ptmx 得到一对伪终端（主端和从端），shell 进程以从端作为标准输入输出，
// hub 在主端与 WebSocket 之间双向转发数据，于是 top、vi、Tab 补全等交互程序都能正常工作。
package webshell

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"strings"
	"sync/atomic"
	"syscall"

	"golang.org/x/net/websocket"
	"golang.org/x/sys/unix"

	"z6x/hub/internal/core"
)

//go:embed assets
var assets embed.FS

type Config struct {
	// AllowFrom 是允许访问的 IP 或网段，例如 ["192.168.0.21", "192.168.0.0/24"]。启用时必须填写。
	AllowFrom []string `yaml:"allow_from"`
	Shell     string   `yaml:"shell"`
	// MaxSessions 限制同时打开的终端数。
	MaxSessions int `yaml:"max_sessions"`
}

type Module struct {
	cfg      Config
	nets     []*net.IPNet
	env      *core.Env
	sessions atomic.Int32
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "webshell" }
func (m *Module) Title() string { return "网页终端" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{Shell: "/system/bin/sh", MaxSessions: 3}
	if err := env.Config.Decode("webshell", &m.cfg); err != nil {
		return err
	}
	if len(m.cfg.AllowFrom) == 0 {
		return fmt.Errorf("webshell 必须配置 allow_from（允许访问的 IP 或网段）")
	}
	for _, s := range m.cfg.AllowFrom {
		if !strings.Contains(s, "/") {
			if strings.Contains(s, ":") {
				s += "/128"
			} else {
				s += "/32"
			}
		}
		_, n, err := net.ParseCIDR(s)
		if err != nil {
			return fmt.Errorf("allow_from 中的地址格式不正确：%s", s)
		}
		m.nets = append(m.nets, n)
	}
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.Handle("GET /ui/webshell/{$}", m.allowed(http.HandlerFunc(m.page)))
	r.Handle("GET /ui/webshell/assets/", m.allowed(http.StripPrefix("/ui/webshell/", http.FileServerFS(assets))))
	r.Handle("GET /api/webshell/ws", m.allowed(websocket.Server{Handshake: checkOrigin, Handler: m.session}))
}

// allowed 只放行 allow_from 中的地址。
func (m *Module) allowed(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host, _, _ := net.SplitHostPort(r.RemoteAddr)
		ip := net.ParseIP(host)
		for _, n := range m.nets {
			if ip != nil && n.Contains(ip) {
				h.ServeHTTP(w, r)
				return
			}
		}
		m.env.Log.Warn("拒绝来自非允许地址的网页终端请求", "from", host)
		http.Error(w, "此地址不允许使用网页终端（见配置中的 webshell.allow_from）", http.StatusForbidden)
	})
}

// checkOrigin 要求 Origin 的主机与请求的 Host 一致。
// 浏览器在跨站请求时也会附带 Cookie，但会如实填写 Origin，因此可借此拒绝其他网站发起的连接。
func checkOrigin(cfg *websocket.Config, r *http.Request) error {
	o, err := url.Parse(r.Header.Get("Origin"))
	if err != nil || o.Host == "" || o.Host != r.Host {
		return fmt.Errorf("Origin 不匹配")
	}
	cfg.Origin = o
	return nil
}

// resize 是浏览器发来的窗口大小消息；其他消息均为键盘输入。
type resize struct {
	Cols uint16 `json:"cols"`
	Rows uint16 `json:"rows"`
}

func (m *Module) session(ws *websocket.Conn) {
	defer ws.Close()
	if m.sessions.Add(1) > int32(m.cfg.MaxSessions) {
		m.sessions.Add(-1)
		websocket.Message.Send(ws, "\r\n同时打开的终端已达上限，请先关闭其他终端。\r\n")
		return
	}
	defer m.sessions.Add(-1)

	ptmx, tty, err := openPTY()
	if err != nil {
		websocket.Message.Send(ws, "无法打开伪终端："+err.Error()+"\r\n")
		return
	}
	defer ptmx.Close()

	cmd := exec.Command(m.cfg.Shell, "-l")
	cmd.Env = append(os.Environ(), "TERM=xterm-256color", "HOME=/data/local/tmp")
	cmd.Dir = "/data/local/tmp"
	cmd.Stdin, cmd.Stdout, cmd.Stderr = tty, tty, tty
	// Setsid + Setctty：shell 成为新会话的首进程，并以伪终端为控制终端，Ctrl+C 等信号才能正常工作。
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true, Setctty: true}
	if err := cmd.Start(); err != nil {
		tty.Close()
		websocket.Message.Send(ws, "无法启动 shell："+err.Error()+"\r\n")
		return
	}
	tty.Close() // 子进程已持有从端，hub 自己不再需要
	from := ws.Request().RemoteAddr
	m.env.Log.Info("网页终端已打开", "from", from, "pid", cmd.Process.Pid)

	done := make(chan struct{})
	// 伪终端 → 浏览器
	go func() {
		defer close(done)
		buf := make([]byte, 32<<10)
		for {
			n, err := ptmx.Read(buf)
			if n > 0 {
				if websocket.Message.Send(ws, string(buf[:n])) != nil {
					return
				}
			}
			if err != nil {
				return
			}
		}
	}()
	// 浏览器 → 伪终端
	go func() {
		for {
			var msg string
			if err := websocket.Message.Receive(ws, &msg); err != nil {
				cmd.Process.Signal(syscall.SIGHUP) // 浏览器断开：通知 shell 挂断
				return
			}
			if strings.HasPrefix(msg, "\x00") { // 以 \x00 开头的是控制消息
				var rz resize
				if json.Unmarshal([]byte(msg[1:]), &rz) == nil && rz.Cols > 0 && rz.Rows > 0 {
					unix.IoctlSetWinsize(int(ptmx.Fd()), unix.TIOCSWINSZ, &unix.Winsize{Row: rz.Rows, Col: rz.Cols})
				}
				continue
			}
			if _, err := io.WriteString(ptmx, msg); err != nil {
				return
			}
		}
	}()
	<-done
	cmd.Process.Kill()
	cmd.Wait()
	m.env.Log.Info("网页终端已关闭", "from", from)
}

// openPTY 打开一对伪终端：主端由 hub 读写，从端交给 shell。
func openPTY() (ptmx *os.File, tty *os.File, err error) {
	ptmx, err = os.OpenFile("/dev/ptmx", os.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		return nil, nil, err
	}
	fd := int(ptmx.Fd())
	if err = unix.IoctlSetPointerInt(fd, unix.TIOCSPTLCK, 0); err != nil { // 解锁从端
		ptmx.Close()
		return nil, nil, err
	}
	n, err := unix.IoctlGetInt(fd, unix.TIOCGPTN) // 取得从端编号
	if err != nil {
		ptmx.Close()
		return nil, nil, err
	}
	tty, err = os.OpenFile(fmt.Sprintf("/dev/pts/%d", n), os.O_RDWR|syscall.O_NOCTTY, 0)
	if err != nil {
		ptmx.Close()
		return nil, nil, err
	}
	return ptmx, tty, nil
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprint(w, `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>网页终端</title>
<link rel="stylesheet" href="assets/xterm.css">
<style>html,body{margin:0;height:100%;background:#14171d;color:#e6e9ef;font:14px system-ui,sans-serif}
#bar{padding:6px 10px;background:#3a1d1d;color:#ffb4b4}#bar a{color:#9fc0ff}#t{position:absolute;top:34px;bottom:0;left:0;right:0;padding:4px}</style>
</head><body><div id="bar">⚠ 网页终端拥有投影仪的 shell 权限（uid 2000），可以停用、卸载应用。不使用时请在配置中关闭。 <a href="/">返回 hub</a></div>
<div id="t"></div>
<script src="assets/xterm.js"></script><script src="assets/addon-fit.js"></script>
<script>
const term=new Terminal({cursorBlink:true,fontFamily:'monospace',fontSize:14,scrollback:5000});
const fit=new FitAddon.FitAddon();term.loadAddon(fit);term.open(document.getElementById('t'));fit.fit();
const ws=new WebSocket((location.protocol==='https:'?'wss://':'ws://')+location.host+'/api/webshell/ws');
const sendSize=()=>{if(ws.readyState===1)ws.send('\x00'+JSON.stringify({cols:term.cols,rows:term.rows}))};
ws.onopen=()=>{sendSize();term.focus()};
ws.onmessage=e=>term.write(e.data);
ws.onclose=()=>term.write('\r\n\x1b[33m[连接已关闭]\x1b[0m\r\n');
term.onData(d=>{if(ws.readyState===1)ws.send(d)});
window.addEventListener('resize',()=>{fit.fit();sendSize()});
</script></body></html>`)
}
