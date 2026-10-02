// Package tasks 是任务管理：相当于手机的「最近任务」界面。极米去掉了投影仪的最近任务界面
// （按 APP_SWITCH 键无反应，2026-10-02 实测），这里用网页代替：列出最近使用和正在运行的应用，
// 可以逐个结束（am force-stop，并从最近任务中移除），也可以一键清理。
//
// 受保护的应用（桌面、输入法、代理等）只显示，不提供结束按钮，一键清理也会跳过它们。
package tasks

import (
	"context"
	"fmt"
	"net/http"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"z6x/hub/internal/android"
	"z6x/hub/internal/core"
)

type Config struct {
	// Protect 列出不允许结束的包名。桌面、当前输入法会自动加入。
	Protect []string `yaml:"protect"`
	// Names 为包名指定显示名称（shell 无法读取应用名称）。
	Names map[string]string `yaml:"names"`
}

type Module struct {
	cfg Config
	r   android.Runner
	env *core.Env
}

func New(r android.Runner) *Module {
	if r == nil {
		r = android.Exec{}
	}
	return &Module{r: r}
}

func (m *Module) Name() string  { return "tasks" }
func (m *Module) Title() string { return "任务管理" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{Protect: []string{"com.github.metacubex.clash.meta"}}
	return env.Config.Decode("tasks", &m.cfg)
}

func (m *Module) Stop(context.Context) error { return nil }

// App 是任务列表中的一项。
type App struct {
	Package   string `json:"package"`
	Name      string `json:"name"`
	Running   bool   `json:"running"`
	MemoryMB  int    `json:"memory_mb"`
	Recent    bool   `json:"recent"`
	TaskID    int    `json:"task_id,omitempty"`
	IdleSec   int    `json:"idle_sec"` // 最近任务中「已不活跃」的秒数
	Front     bool   `json:"front"`    // 当前在前台
	Protected bool   `json:"protected"`
	System    bool   `json:"system"` // 系统应用（非第三方）
}

var (
	reRecent = regexp.MustCompile(`Recent #\d+: Task\{\S+ #(\d+) type=(\w+) (?:A=\d+:|I=)([\w.]+)`)
	reIdle   = regexp.MustCompile(`inactive for (\d+)s`)
	reFront  = regexp.MustCompile(`mResumedActivity: ActivityRecord\{\S+ u0 ([\w.]+)/`)
)

type recent struct {
	task int
	idle int
	home bool
}

// parseRecents 解析 dumpsys activity recents：包名 → 任务号、闲置时长；桌面任务标记为 home。
func parseRecents(s string) map[string]recent {
	out := map[string]recent{}
	lines := strings.Split(s, "\n")
	for i, l := range lines {
		mm := reRecent.FindStringSubmatch(l)
		if mm == nil {
			continue
		}
		id, _ := strconv.Atoi(mm[1])
		r := recent{task: id, home: mm[2] == "home"}
		// 闲置时长在该任务的详细信息中，不一定紧跟在下一行：向后找到下一个任务为止
		for j := i + 1; j < len(lines) && !strings.Contains(lines[j], "Recent #"); j++ {
			if im := reIdle.FindStringSubmatch(lines[j]); im != nil {
				r.idle, _ = strconv.Atoi(im[1])
				break
			}
		}
		if _, ok := out[mm[3]]; !ok {
			out[mm[3]] = r
		}
	}
	return out
}

// parsePS 解析 ps -A -o USER,PID,RSS,NAME：应用进程（u0_aNN）按包名累计内存（KB）。
// 进程名为「包名:子进程」时计入该包。
func parsePS(s string) map[string]int {
	out := map[string]int{}
	for _, l := range strings.Split(s, "\n") {
		f := strings.Fields(l)
		if len(f) < 4 || !strings.HasPrefix(f[0], "u0_a") {
			continue
		}
		rss, _ := strconv.Atoi(f[2])
		pkg, _, _ := strings.Cut(f[3], ":")
		out[pkg] += rss
	}
	return out
}

func (m *Module) run(ctx context.Context, name string, args ...string) string {
	out, err := m.r.Run(ctx, name, args...)
	if err != nil {
		m.env.Log.Warn("命令失败", "cmd", name, "err", err)
	}
	return out
}

// protected 返回不允许结束的包名集合：配置的列表、桌面、当前输入法、hub 所需的系统组件。
func (m *Module) protected(ctx context.Context) map[string]bool {
	p := map[string]bool{"android": true, "com.android.systemui": true, "com.android.shell": true}
	for _, x := range m.cfg.Protect {
		p[x] = true
	}
	for _, h := range strings.Fields(m.run(ctx, "cmd", "role", "get-role-holders", "android.app.role.HOME")) {
		p[h] = true
	}
	if ime := m.run(ctx, "settings", "get", "secure", "default_input_method"); ime != "" {
		p[strings.SplitN(ime, "/", 2)[0]] = true
	}
	return p
}

func (m *Module) list(ctx context.Context) []App {
	recents := parseRecents(m.run(ctx, "dumpsys", "activity", "recents"))
	mem := parsePS(m.run(ctx, "ps", "-A", "-o", "USER,PID,RSS,NAME"))
	third := map[string]bool{}
	for _, l := range strings.Split(m.run(ctx, "pm", "list", "packages", "-3"), "\n") {
		if p, ok := strings.CutPrefix(strings.TrimSpace(l), "package:"); ok {
			third[p] = true
		}
	}
	front := ""
	if mm := reFront.FindStringSubmatch(m.run(ctx, "dumpsys", "activity", "activities")); mm != nil {
		front = mm[1]
	}
	prot := m.protected(ctx)
	// 显示：最近任务中的应用，以及正在运行的第三方应用（系统后台服务不显示，以免误结束）
	pkgs := map[string]bool{}
	for p := range recents {
		pkgs[p] = true
	}
	for p := range mem {
		if third[p] {
			pkgs[p] = true
		}
	}
	var out []App
	for p := range pkgs {
		r, isRecent := recents[p]
		if r.home {
			continue // 桌面本身不列出
		}
		name := m.cfg.Names[p]
		if name == "" {
			name = friendly[p]
		}
		out = append(out, App{Package: p, Name: name, Running: mem[p] > 0, MemoryMB: mem[p] / 1024, Recent: isRecent,
			TaskID: r.task, IdleSec: r.idle, Front: p == front, Protected: prot[p], System: !third[p]})
	}
	sort.Slice(out, func(i, j int) bool {
		a, b := out[i], out[j]
		if a.Front != b.Front {
			return a.Front
		}
		if a.Recent != b.Recent {
			return a.Recent
		}
		if a.Recent {
			return a.IdleSec < b.IdleSec
		}
		return a.MemoryMB > b.MemoryMB
	})
	return out
}

// friendly 是常见应用的显示名称（shell 读不到应用名，只能内置）。
var friendly = map[string]string{
	"org.smarttube.stable":               "SmartTube",
	"com.phlox.tvwebbrowser":             "TV Bro 浏览器",
	"com.cxinventor.file.explorer":       "CX 文件管理器",
	"org.galexander.sshd":                "SimpleSSHD",
	"com.github.metacubex.clash.meta":    "Clash Meta",
	"com.spocky.projengmenu":             "Projectivy 桌面",
	"de.szalkowski.activitylauncher.oss": "Activity Launcher",
	"com.sohu.inputmethod.sogou.tv":      "搜狗输入法",
	"com.xgimi.filemanager":              "极米文件管理",
	"com.xgimi.manager":                  "极米管家（清理与安全）",
	"com.xgimi.wirelessscreen":           "极米无线投屏",
	"com.android.newsettings":            "极米设置",
}

// stop 结束一个应用并从最近任务中移除。
func (m *Module) stop(ctx context.Context, a App) error {
	if a.Protected {
		return fmt.Errorf("%s 受保护，不能结束", a.Package)
	}
	if _, err := m.r.Run(ctx, "am", "force-stop", a.Package); err != nil {
		return err
	}
	if a.TaskID > 0 {
		m.run(ctx, "am", "stack", "remove", strconv.Itoa(a.TaskID)) // 从最近任务中移除，失败不影响结束
	}
	m.env.Log.Info("已结束应用", "package", a.Package)
	return nil
}

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/tasks/", func(w http.ResponseWriter, req *http.Request) {
		core.WriteJSON(w, m.list(req.Context()))
	})
	r.HandleFunc("POST /api/tasks/stop", func(w http.ResponseWriter, req *http.Request) {
		pkg := req.URL.Query().Get("package")
		for _, a := range m.list(req.Context()) {
			if a.Package == pkg {
				if err := m.stop(req.Context(), a); err != nil {
					core.WriteError(w, http.StatusForbidden, err.Error())
					return
				}
				core.WriteJSON(w, map[string]string{"status": "ok"})
				return
			}
		}
		core.WriteError(w, http.StatusNotFound, "任务列表中没有这个应用")
	})
	// 一键清理：结束除前台与受保护应用以外的全部列出应用；include_front=1 时连前台应用一起结束
	r.HandleFunc("POST /api/tasks/clean", func(w http.ResponseWriter, req *http.Request) {
		front := req.URL.Query().Get("include_front") == "1"
		var done []string
		for _, a := range m.list(req.Context()) {
			if a.Protected || (a.Front && !front) {
				continue
			}
			if m.stop(req.Context(), a) == nil {
				done = append(done, a.Package)
			}
		}
		core.WriteJSON(w, map[string]any{"stopped": done})
	})
	r.HandleFunc("GET /ui/tasks/{$}", m.page)
}
