// Package jobs 管理设备上的后台任务（手机的短视频封面生成、投影仪的遥控器改键守护进程等）：
// 在网页上查看状态与日志、启动、停止，并设置是否随 hub 启动。各设备的开机脚本只需启动 hub，
// 后台任务由 hub 按「开机自动启动」的设置启动，新增任务只需在 hub.yaml 中登记，不必改开机脚本。
//
// 每个任务由命令描述：start、stop、status（退出码 0 表示运行中），在 hub 的工作目录中以 sh -c 执行。
// 命令以 shell 的完整路径执行：Go 查找可执行文件时会调用 faccessat2，在手机 Termux（普通应用）的
// seccomp 限制下会触发 SIGSYS 使整个 hub 被结束（2026-10-08 实测完整路径可正常执行）。
package jobs

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"z6x/hub/internal/core"
)

// Job 是 hub.yaml 中登记的一个后台任务。
type Job struct {
	ID        string `yaml:"id" json:"id"`
	Title     string `yaml:"title" json:"title"`
	Help      string `yaml:"help" json:"help,omitempty"`
	Start     string `yaml:"start" json:"-"`
	Stop      string `yaml:"stop" json:"-"`
	Status    string `yaml:"status" json:"-"` // 退出码 0 表示运行中；输出的前几行显示在页面上
	Log       string `yaml:"log" json:"-"`    // 日志文件（相对于 hub 的工作目录），页面显示末尾几行
	Autostart bool   `yaml:"autostart" json:"-"`
}

type Config struct {
	Items []Job `yaml:"items"`
}

const (
	// commandTimeout：start / stop / status 命令最长运行时间（start 应把任务放到后台后立即返回）
	commandTimeout = 30 * time.Second
	// autostartDelay：hub 启动后等多久再启动后台任务（让网络与资源库扫描先完成）
	autostartDelay = 15 * time.Second
	// logTailLines / logTailBytes：页面显示的日志行数与最多读取的字节数
	logTailLines = 15
	logTailBytes = 32 << 10
)

// shellPaths 依次尝试：Termux 的 sh（手机），系统的 sh（投影仪以 shell 身份运行）。
var shellPaths = []string{"/data/data/com.termux/files/usr/bin/sh", "/system/bin/sh", "/bin/sh"}

type Module struct {
	cfg   Config
	env   *core.Env
	shell string
	mu    sync.Mutex
	// autostart 为网页上修改过的「开机自动启动」（覆盖 hub.yaml 中的 autostart），保存在数据目录
	autostart     map[string]bool
	autostartPath string
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "jobs" }
func (m *Module) Title() string { return "后台任务" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	if err := env.Config.Decode("jobs", &m.cfg); err != nil {
		return err
	}
	for _, p := range shellPaths {
		if _, err := os.Stat(p); err == nil {
			m.shell = p
			break
		}
	}
	m.autostartPath = filepath.Join(env.Config.DataDir, "jobs_autostart.json")
	m.autostart = map[string]bool{}
	if b, err := os.ReadFile(m.autostartPath); err == nil {
		json.Unmarshal(b, &m.autostart)
	}
	go func() {
		time.Sleep(autostartDelay)
		for _, j := range m.cfg.Items {
			if m.isAutostart(j) && !m.running(j) {
				out, err := m.run(j.Start)
				env.Log.Info("开机自动启动后台任务", "job", j.ID, "output", strings.TrimSpace(out), "err", err)
			}
		}
	}()
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) isAutostart(j Job) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if v, ok := m.autostart[j.ID]; ok {
		return v
	}
	return j.Autostart
}

// run 在 hub 的工作目录中以 sh -c 执行命令，返回输出。
func (m *Module) run(command string) (string, error) {
	if command == "" {
		return "", nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), commandTimeout)
	defer cancel()
	out, err := exec.CommandContext(ctx, m.shell, "-c", command).CombinedOutput()
	return string(out), err
}

func (m *Module) running(j Job) bool {
	_, err := m.run(j.Status)
	return err == nil
}

func tail(path string) []string {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer f.Close()
	if st, err := f.Stat(); err == nil && st.Size() > logTailBytes {
		f.Seek(-logTailBytes, io.SeekEnd)
	}
	b, _ := io.ReadAll(f)
	lines := strings.Split(strings.TrimRight(string(b), "\n"), "\n")
	if len(lines) > logTailLines {
		lines = lines[len(lines)-logTailLines:]
	}
	return lines
}

type jobState struct {
	Job
	Running   bool     `json:"running"`
	Autostart bool     `json:"autostart"`
	Status    string   `json:"status"`
	Log       []string `json:"log"`
}

func (m *Module) find(id string) (Job, bool) {
	for _, j := range m.cfg.Items {
		if j.ID == id {
			return j, true
		}
	}
	return Job{}, false
}

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/jobs/", func(w http.ResponseWriter, _ *http.Request) {
		out := []jobState{}
		for _, j := range m.cfg.Items {
			st, err := m.run(j.Status)
			s := jobState{Job: j, Running: err == nil, Autostart: m.isAutostart(j), Status: strings.TrimSpace(st)}
			if j.Log != "" {
				s.Log = tail(j.Log)
			}
			out = append(out, s)
		}
		core.WriteJSON(w, out)
	})
	// /api/jobs/{id}/start、stop；/api/jobs/{id}/autostart?on=1
	r.HandleFunc("POST /api/jobs/{id}/{action}", func(w http.ResponseWriter, req *http.Request) {
		j, ok := m.find(req.PathValue("id"))
		if !ok {
			core.WriteError(w, http.StatusNotFound, "没有这个后台任务")
			return
		}
		var out string
		var err error
		switch req.PathValue("action") {
		case "start":
			out, err = m.run(j.Start)
		case "stop":
			out, err = m.run(j.Stop)
		case "autostart":
			m.mu.Lock()
			m.autostart[j.ID] = req.URL.Query().Get("on") == "1"
			b, _ := json.MarshalIndent(m.autostart, "", "  ")
			m.mu.Unlock()
			err = os.WriteFile(m.autostartPath, b, 0o600)
		default:
			core.WriteError(w, http.StatusNotFound, "未知操作")
			return
		}
		if err != nil {
			core.WriteError(w, http.StatusInternalServerError, strings.TrimSpace(out+" "+err.Error()))
			return
		}
		m.env.Log.Info("后台任务", "job", j.ID, "action", req.PathValue("action"))
		core.WriteJSON(w, map[string]string{"output": strings.TrimSpace(out)})
	})
	r.HandleFunc("GET /ui/jobs/{$}", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "后台任务", pageHTML) })
}

const pageHTML = `<style>
.job{border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin-bottom:12px}
.job h3{margin:0 0 4px;font-size:15px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.dot{display:inline-block;width:9px;height:9px;border-radius:50%}
.job pre{font-size:12px;background:var(--bg);border:1px solid var(--line);border-radius:6px;padding:6px 8px;max-height:220px;overflow:auto;white-space:pre-wrap;word-break:break-all;margin:8px 0 0}
.job .row{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-top:6px}
</style>
<p><small>设备上的后台脚本。开机时 hub 启动后约 15 秒，自动启动勾选了「开机自动启动」的任务；新增任务在 hub.yaml 的 jobs.items 中登记。</small></p>
<div id="list">读取中…</div>
<script>
const el=(t,x,c)=>{const e=document.createElement(t);if(x!=null)e.textContent=x;if(c)e.className=c;return e};
async function act(id,a,q){const r=await fetch('/api/jobs/'+encodeURIComponent(id)+'/'+a+(q||''),{method:'POST'});
  const d=await r.json().catch(()=>({}));if(!r.ok)alert('失败：'+(d.error||r.status));setTimeout(load,a==='autostart'?0:1500)}
async function load(){const jobs=await (await fetch('/api/jobs/')).json();const box=document.getElementById('list');box.replaceChildren();
  if(!jobs.length)box.append(el('p','这台设备没有登记后台任务。'));
  for(const j of jobs){const d=el('div',null,'job'),h=el('h3'),dot=el('span',null,'dot');dot.style.background=j.running?'#3fb950':'#8b949e';
    h.append(dot,j.title,el('small',j.running?'运行中':'未运行'));d.append(h);if(j.help)d.append(el('small',j.help));
    const row=el('div',null,'row'),b=el('button',j.running?'停止':'启动');b.onclick=()=>{if(j.running&&!confirm('停止「'+j.title+'」？'))return;act(j.id,j.running?'stop':'start')};
    const lab=el('label'),cb=el('input');cb.type='checkbox';cb.checked=j.autostart;cb.onchange=()=>act(j.id,'autostart','?on='+(cb.checked?1:0));lab.append(cb,' 开机自动启动');
    row.append(b,lab);d.append(row);
    const txt=[j.status,...(j.log||[])].filter(Boolean).join('\n');if(txt)d.append(el('pre',txt));box.append(d)}}
load();setInterval(load,10000);
</script>`
