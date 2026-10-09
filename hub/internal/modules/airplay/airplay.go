// Package airplay 是 AirPlay 1（RAOP）音频接收端：苹果设备（iPhone、iPad、Mac）在「音频输出」中选择
// 本设备即可把声音推送过来，由本设备的喇叭播放。在「🔧 工具 → AirPlay 音箱」中开关。
//
// 只支持 AirPlay 1 的音频（不支持视频、屏幕镜像、AirPlay 2 多房间），延迟约 2 秒，适合听音乐。
// 声音经 Termux 的 PulseAudio 播放（pacat）：hub 在 Termux 中运行时（手机）直接调用；
// hub 以 shell 身份运行时（投影仪）设 run_as_termux，借 run-as com.termux 以 Termux 的身份启动
// （投影仪允许 shell 使用 run-as，2026-10-09 实测；需在 Termux 中安装 pulseaudio）。
// 设备息屏后 CPU 可能休眠，期间收不到苹果设备的查找请求，可能暂时搜不到这个音箱；亮屏或正在播放时正常。
//
// 协议细节参照开源项目 shairport-sync：身份校验见 rtsp.go，音频解密与解码见 audio.go，局域网广播见 mdns.go。
package airplay

import (
	"bufio"
	"context"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"net/http"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"time"

	"z6x/hub/internal/android"
	"z6x/hub/internal/core"
)

type Config struct {
	On        *bool  `yaml:"on"`         // 是否开启（默认开启）；在设置页或工具页中切换
	Name      string `yaml:"name"`       // 在苹果设备上显示的音箱名称，默认为 hub 的设备名
	Port      int    `yaml:"port"`       // RTSP 端口
	LatencyMs int    `yaml:"latency_ms"` // 播放器缓冲（毫秒）
	// RunAsTermux：以 Termux 的身份启动 PulseAudio 与 pacat（hub 不在 Termux 中运行时，如投影仪）
	RunAsTermux bool `yaml:"run_as_termux"`
}

// termuxPrefix 借 run-as 以 Termux 的身份运行 Termux 的程序（设置 Termux 的环境变量）。
var termuxPrefix = []string{"/system/bin/run-as", "com.termux", termuxBin + "/env", "-i",
	"HOME=/data/data/com.termux/files/home", "PREFIX=/data/data/com.termux/files/usr",
	"TMPDIR=/data/data/com.termux/files/usr/tmp", "PATH=" + termuxBin, "LANG=en_US.UTF-8"}

const termuxBin = "/data/data/com.termux/files/usr/bin"

// termuxCmd 返回执行 Termux 中某个程序的完整命令。
func (m *Module) termuxCmd(name string, args ...string) []string {
	if m.cfg.RunAsTermux {
		return append(append(append([]string{}, termuxPrefix...), termuxBin+"/"+name), args...)
	}
	return append([]string{android.Resolve(name)}, args...)
}

const (
	defaultPort      = 5050
	defaultLatencyMs = 500
	// minLatencyMs / maxLatencyMs：播放缓冲的范围（太小在网络抖动时断音）
	minLatencyMs = 100
	maxLatencyMs = 3000
	// maxRTSPBody：RTSP 请求体的上限（封面图片等元数据可能较大）
	maxRTSPBody = 4 << 20
)

type Module struct {
	cfg  Config
	env  *core.Env
	key  *rsa.PrivateKey
	hwid []byte
	resp *responder
	ln   net.Listener

	mu      sync.Mutex
	on      bool
	errMsg  string
	client  string // 当前连接的设备
	agent   string
	since   time.Time
	cur     *stream
	curConn net.Conn
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "airplay" }
func (m *Module) Title() string { return "AirPlay 音箱" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{Name: env.Config.Name, Port: defaultPort, LatencyMs: defaultLatencyMs}
	if err := env.Config.Decode("airplay", &m.cfg); err != nil {
		return err
	}
	if m.cfg.Name == "" {
		m.cfg.Name = "z6x-hub"
	}
	der, err := base64.StdEncoding.DecodeString(airportKeyDER)
	if err != nil {
		return err
	}
	if m.key, err = x509.ParsePKCS1PrivateKey(der); err != nil {
		return err
	}
	// 设备标识：由名称算出的固定 6 字节（不使用真实的硬件地址）
	sum := sha1.Sum([]byte("z6x-airplay:" + m.cfg.Name))
	m.hwid = sum[:6]
	m.hwid[0] = m.hwid[0]&0xfc | 0x02 // 本地管理的单播地址
	if m.cfg.LatencyMs < minLatencyMs || m.cfg.LatencyMs > maxLatencyMs {
		m.cfg.LatencyMs = defaultLatencyMs
	}
	if m.cfg.On == nil || *m.cfg.On {
		if err := m.turnOn(); err != nil {
			env.Log.Warn("AirPlay 音箱启动失败", "err", err)
		}
	} else {
		// 关闭状态下启动（例如在设置中关闭后 hub 重启）：发一次告别通告。重启时旧进程来不及发，
		// 而记录的有效期很长（见 mdnsTTL），不发的话苹果设备会继续显示这个已关闭的音箱。
		go m.newResponder().goodbye()
	}
	return nil
}

func (m *Module) Stop(context.Context) error {
	m.turnOff()
	return nil
}

// player 返回播放命令（完整路径）；没有 pacat 时返回原因。
func (m *Module) player() ([]string, error) {
	if !m.cfg.RunAsTermux && !strings.HasPrefix(android.Resolve("pacat"), "/") {
		return nil, errors.New("没有 pacat：需要在 Termux 中运行 hub 并安装 PulseAudio（pkg install pulseaudio）；hub 以 shell 身份运行时设 run_as_termux")
	}
	run := func(c []string) ([]byte, error) { return exec.Command(c[0], c[1:]...).CombinedOutput() }
	// PulseAudio 未运行时启动它（-exit-idle-time=-1 不因空闲退出）
	if _, err := run(m.termuxCmd("pulseaudio", "--check")); err != nil {
		if out, err := run(m.termuxCmd("pulseaudio", "--start", "--exit-idle-time=-1")); err != nil {
			return nil, fmt.Errorf("启动 PulseAudio 失败：%v %s", err, strings.TrimSpace(string(out)))
		}
	}
	return m.termuxCmd("pacat", "--format=s16le", "--rate=44100", "--channels=2", "--stream-name=AirPlay",
		"--client-name=z6x-hub", "--latency-msec="+strconv.Itoa(m.cfg.LatencyMs)), nil
}

func (m *Module) turnOn() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.on {
		return nil
	}
	if _, err := m.player(); err != nil {
		m.errMsg = err.Error()
		return err
	}
	ln, err := net.Listen("tcp4", ":"+strconv.Itoa(m.cfg.Port))
	if err != nil {
		m.errMsg = "端口 " + strconv.Itoa(m.cfg.Port) + " 监听失败：" + err.Error()
		return err
	}
	m.resp = m.newResponder()
	if err := m.resp.start(); err != nil {
		ln.Close()
		m.errMsg = "局域网广播启动失败：" + err.Error()
		return err
	}
	m.ln, m.on, m.errMsg = ln, true, ""
	go m.accept(ln)
	m.env.Log.Info("AirPlay 音箱已开启", "name", m.cfg.Name, "port", m.cfg.Port)
	return nil
}

func (m *Module) newResponder() *responder {
	return &responder{instance: strings.ToUpper(hex.EncodeToString(m.hwid)) + "@" + m.cfg.Name,
		host: "z6x-" + hex.EncodeToString(m.hwid[3:]) + ".local.", port: m.cfg.Port, txt: txtRecords(),
		log: m.env.Log.Warn}
}

func (m *Module) turnOff() {
	m.mu.Lock()
	defer m.mu.Unlock()
	if !m.on {
		return
	}
	m.on = false
	m.resp.close()
	m.ln.Close()
	if m.curConn != nil {
		m.curConn.Close()
	}
	if m.cur != nil {
		m.cur.close()
	}
	m.cur, m.curConn, m.client = nil, nil, ""
	m.env.Log.Info("AirPlay 音箱已关闭")
}

func (m *Module) accept(ln net.Listener) {
	for {
		c, err := ln.Accept()
		if err != nil {
			return
		}
		go m.session(c)
	}
}

// session 处理一台设备的 RTSP 连接；新设备连接时接管（关闭之前的连接与播放）。
func (m *Module) session(c net.Conn) {
	defer c.Close()
	br := bufio.NewReader(c)
	local := c.LocalAddr().(*net.TCPAddr).IP
	var st *stream
	defer func() {
		if st != nil {
			st.close()
		}
		m.mu.Lock()
		if m.curConn == c {
			m.cur, m.curConn, m.client = nil, nil, ""
		}
		m.mu.Unlock()
	}()
	for {
		req, err := readRequest(br)
		if err != nil {
			return
		}
		cseq := req.headers["cseq"]
		var hdrs [][2]string
		switch req.method {
		case "OPTIONS":
			hdrs = append(hdrs, [2]string{"Public", "ANNOUNCE, SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER"})
		case "ANNOUNCE":
			ann, err := parseAnnounce(m.key, string(req.body))
			if err != nil {
				m.env.Log.Warn("AirPlay：无法接受的音频流", "err", err)
				fmt.Fprintf(c, "RTSP/1.0 415 Unsupported Media Type\r\nCSeq: %s\r\n\r\n", cseq)
				continue
			}
			if st != nil {
				st.close()
			}
			if st, err = newStream(ann, m.env.Log.Warn); err != nil {
				fmt.Fprintf(c, "RTSP/1.0 500 Internal Server Error\r\nCSeq: %s\r\n\r\n", cseq)
				continue
			}
			m.takeOver(c, st, req.headers["user-agent"])
		case "SETUP":
			if st == nil {
				fmt.Fprintf(c, "RTSP/1.0 455 Method Not Valid In This State\r\nCSeq: %s\r\n\r\n", cseq)
				continue
			}
			hdrs = append(hdrs, [2]string{"Transport", fmt.Sprintf("RTP/AVP/UDP;unicast;mode=record;server_port=%d;control_port=%d;timing_port=%d",
				port(st.audio), port(st.control), port(st.timing))}, [2]string{"Session", "1"})
		case "RECORD":
			if st != nil {
				cmd, err := m.player()
				if err == nil {
					err = st.startPlayer(cmd)
				}
				if err != nil {
					m.setErr("无法播放：" + err.Error())
				}
			}
			hdrs = append(hdrs, [2]string{"Audio-Latency", "11025"})
		case "SET_PARAMETER":
			if db, ok := parseVolume(string(req.body)); ok && st != nil {
				st.setVolume(db)
			}
		case "FLUSH":
			if st != nil {
				st.flush()
			}
		case "TEARDOWN":
			writeResponse(c, cseq, nil)
			return
		}
		if ch := req.headers["apple-challenge"]; ch != "" {
			if r, err := challengeResponse(m.key, ch, local, m.hwid); err == nil {
				hdrs = append(hdrs, [2]string{"Apple-Response", r})
			}
		}
		if err := writeResponse(c, cseq, hdrs); err != nil {
			return
		}
	}
}

func (m *Module) takeOver(c net.Conn, st *stream, agent string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.curConn != nil && m.curConn != c {
		m.curConn.Close() // 另一台设备接管
	}
	if m.cur != nil && m.cur != st {
		m.cur.close()
	}
	host, _, _ := net.SplitHostPort(c.RemoteAddr().String())
	m.cur, m.curConn, m.client, m.agent, m.since, m.errMsg = st, c, host, agent, time.Now(), ""
	m.env.Log.Info("AirPlay：开始接收", "from", host, "agent", agent)
}

func (m *Module) setErr(s string) {
	m.mu.Lock()
	m.errMsg = s
	m.mu.Unlock()
	m.env.Log.Warn("AirPlay：" + s)
}

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/airplay/{$}", func(w http.ResponseWriter, _ *http.Request) {
		m.mu.Lock()
		out := map[string]any{"on": m.on, "name": m.cfg.Name, "port": m.cfg.Port, "error": m.errMsg, "client": m.client, "agent": m.agent}
		if m.cur != nil {
			out["since"] = m.since.Format("15:04:05")
			out["packets"], out["lost"] = m.cur.packets.Load(), m.cur.lost.Load()
		}
		m.mu.Unlock()
		core.WriteJSON(w, out)
	})
	// 设置页「AirPlay 音箱」一组开头显示的状态
	r.HandleFunc("GET /api/airplay/status", func(w http.ResponseWriter, _ *http.Request) {
		m.mu.Lock()
		defer m.mu.Unlock()
		lines := []string{"状态：已关闭"}
		if m.on {
			lines = []string{"状态：已开启，在苹果设备的音频输出中选择「" + m.cfg.Name + "」"}
		}
		if m.client != "" {
			lines = append(lines, "正在接收："+m.client+"（"+m.agent+"），自 "+m.since.Format("15:04:05")+" 起")
		}
		if m.errMsg != "" {
			lines = append(lines, "⚠ "+m.errMsg)
		}
		core.WriteJSON(w, map[string]any{"lines": lines})
	})
	r.HandleFunc("GET /ui/airplay/{$}", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "AirPlay 音箱", pageHTML) })
}

const pageHTML = `<div class="card"><p id="st">读取中…</p><p><button id="tg"></button></p></div>
<div class="card"><small>名称、播放缓冲（延迟）等在「⚙️ 设置 → AirPlay 音箱」中修改。<br>在 iPhone / iPad 的控制中心或 Mac 的「声音」设置中，把音频输出选为这里显示的名称即可。
只支持 AirPlay 1 的音频，延迟约 2 秒，适合听音乐；不支持视频与屏幕镜像。
设备息屏时可能暂时搜不到这个音箱（CPU 休眠，收不到查找请求），亮屏或正在播放时正常。</small></div>
<script>
async function load(){const s=await (await fetch('/api/airplay/')).json();const st=document.getElementById('st'),tg=document.getElementById('tg');
  let t=s.on?'✅ 已开启：在苹果设备的音频输出中选择「'+s.name+'」':'⏸ 已关闭';
  if(s.client)t+='\n正在接收：'+s.client+(s.agent?'（'+s.agent+'）':'')+'，自 '+s.since+' 起，'+s.packets+' 个音频包'+(s.lost?'，丢失 '+s.lost+' 个':'');
  if(s.error)t+='\n⚠ '+s.error;
  st.textContent=t;st.style.whiteSpace='pre-line';tg.textContent=s.on?'关闭':'开启';
  // 开关是设置中的 modules.airplay.on（写入 hub.yaml，hub 重启约 2 秒后生效）
  tg.onclick=async()=>{const r=await fetch('/api/settings',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({values:{'modules.airplay.on':String(!s.on)}})});
    if(!r.ok)return alert((await r.json()).error);st.textContent='hub 正在重启…';setTimeout(function w(){fetch('/api/health').then(r=>r.ok?location.reload():setTimeout(w,1000)).catch(()=>setTimeout(w,1000))},1500)}}
load();setInterval(load,3000);
</script>`

// Settings 声明设置页中的参数（实现 core.SettingsProvider）。
func (m *Module) Settings() []core.SettingsGroup {
	const p = "modules.airplay."
	return []core.SettingsGroup{{Title: "🔊 AirPlay 音箱", StatusURL: "/api/airplay/status", Items: []core.Setting{
		{Key: p + "on", Label: "开启", Type: "bool", Default: true, Help: "关闭后苹果设备中不再显示本音箱"},
		{Key: p + "name", Label: "音箱名称", Type: "text", Default: m.env.Config.Name, Help: "在苹果设备的音频输出中显示的名称"},
		{Key: p + "latency_ms", Label: "播放缓冲", Unit: "毫秒", Type: "number", Min: minLatencyMs, Max: maxLatencyMs, Step: 50, Default: defaultLatencyMs,
			Help: "越小反应越快，网络不好时越容易断音。AirPlay 1 协议本身另有约 2 秒的延迟（发送端缓冲），这里改不了"},
		{Key: p + "port", Label: "端口", Type: "number", Min: 1024, Max: 65535, Step: 1, Default: defaultPort},
	}}}
}
