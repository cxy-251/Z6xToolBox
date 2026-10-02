// Package keys 是遥控器按键设置：在网页上为遥控器的四个影视快捷键（酷喵、云视听极光、奇异果、芒果）
// 以及调焦键两侧的两个键，分别设置短按和长按的功能，共 12 项。
//
// 本模块只负责读写配置文件，实际识别按键、执行动作的是 z6x-tools 的 keymap 守护进程
// （tools/src/keymap.rs）：它在配置文件被修改后 2 秒内自动重新加载。配置文件格式见 keymap 的说明。
package keys

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"regexp"
	"sort"
	"strings"

	"z6x/hub/internal/android"
	"z6x/hub/internal/core"
)

type Config struct {
	// File 是 keymap 守护进程读取的配置文件。
	File string `yaml:"file"`
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

func (m *Module) Name() string  { return "keys" }
func (m *Module) Title() string { return "遥控器按键" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{File: "/data/local/tmp/z6x-tools/keymap.conf"}
	return env.Config.Decode("keys", &m.cfg)
}

func (m *Module) Stop(context.Context) error { return nil }

// Buttons 是可设置的四个快捷键（keymap 中的名称与显示名称）。
var Buttons = []struct{ ID, Label string }{
	{"youku", "酷喵"}, {"jiguang", "云视听极光"}, {"qiyiguo", "奇异果"}, {"mango", "芒果"},
	{"wallpaper", "壁纸键"}, {"side", "调焦键另一侧的键"},
}

// Slot 是一项设置：某个键的短按或长按对应的动作。Action 为空表示不设置。
// 动作的写法：app:<包名/Activity>、tasks、key:<键名>、sh:<命令>。
type Slot struct {
	Button string `json:"button"`
	Long   bool   `json:"long"`
	Action string `json:"action"`
}

var lineRe = regexp.MustCompile(`^(press|long):(\w+)\s*=\s*(\w+):\s*(.*)$`)

// parse 从配置文件中读出 8 项设置。本页面写入的行带有标记，能还原为 app:、tasks 等写法。
func (m *Module) parse(text string) []Slot {
	var out []Slot
	for _, b := range Buttons {
		for _, long := range []bool{false, true} {
			out = append(out, Slot{Button: b.ID, Long: long})
		}
	}
	sc := bufio.NewScanner(strings.NewReader(text))
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		mm := lineRe.FindStringSubmatch(line)
		if mm == nil {
			continue
		}
		action := mm[3] + ":" + strings.TrimSpace(mm[4])
		if mm[3] == "sh" {
			cmd := strings.TrimSpace(mm[4])
			if a, ok := strings.CutPrefix(cmd, "am start -n "); ok && !strings.Contains(a, " ") {
				action = "app:" + a
			} else if strings.Contains(cmd, "/ui/tasks/") {
				action = "tasks"
			}
		}
		for i := range out {
			if out[i].Button == mm[2] && out[i].Long == (mm[1] == "long") {
				out[i].Action = action
			}
		}
	}
	return out
}

var (
	appRe  = regexp.MustCompile(`^[\w.]+/[\w.$]+$`)
	keyRe  = regexp.MustCompile(`^[a-z0-9]+(\s+[a-z0-9]+)*$`)
	unsafe = regexp.MustCompile(`[\r\n]`)
)

// render 把 8 项设置写成 keymap 的配置文本。
func (m *Module) render(slots []Slot, lanIP string) (string, error) {
	var b strings.Builder
	b.WriteString("# 遥控器按键设置：由 z6x-hub「遥控器按键」页面生成，手工修改会在下次保存时被覆盖。\n")
	b.WriteString("# 格式见 z6x keymap --help；keymap 守护进程在本文件修改后 2 秒内自动重新加载。\n\n")
	for _, s := range slots {
		if s.Action == "" {
			continue
		}
		ok := false
		for _, bt := range Buttons {
			ok = ok || bt.ID == s.Button
		}
		if !ok || unsafe.MatchString(s.Action) {
			return "", fmt.Errorf("设置不正确：%s", s.Button)
		}
		trig := "press:" + s.Button
		if s.Long {
			trig = "long:" + s.Button
		}
		kind, val, _ := strings.Cut(s.Action, ":")
		var act string
		switch kind {
		case "app":
			if !appRe.MatchString(val) {
				return "", fmt.Errorf("应用入口不正确：%s", val)
			}
			act = "sh: am start -n " + val
		case "tasks":
			// 在电视浏览器中打开任务管理页（浏览器需登录过一次 hub）
			act = fmt.Sprintf("sh: am start -a android.intent.action.VIEW -d http://%s/ui/tasks/ -n com.phlox.tvwebbrowser/.activity.main.MainActivity", lanIP)
		case "key":
			if !keyRe.MatchString(val) {
				return "", fmt.Errorf("键名不正确：%s", val)
			}
			act = "key: " + val
		case "sh":
			if strings.TrimSpace(val) == "" {
				return "", fmt.Errorf("命令为空")
			}
			act = "sh: " + strings.TrimSpace(val)
		default:
			return "", fmt.Errorf("未知动作：%s", s.Action)
		}
		b.WriteString(trig + " = " + act + "\n")
	}
	return b.String(), nil
}

// App 是可以从桌面启动的应用。
type App struct {
	Activity string `json:"activity"`
	Name     string `json:"name"`
}

func (m *Module) apps(ctx context.Context) []App {
	seen := map[string]bool{}
	var out []App
	for _, cat := range []string{"android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER"} {
		o, _ := m.r.Run(ctx, "cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN", "-c", cat)
		for _, l := range strings.Split(o, "\n") {
			a := strings.TrimSpace(l)
			// 跳过占位入口：极米设置的桌面入口为 com.xgimi.newsettings.mock.MockActivity，打开无反应（2026-10-02 用户确认）
			if !appRe.MatchString(a) || strings.Contains(a, ".mock.") || strings.HasSuffix(a, "MockActivity") {
				continue
			}
			pkg := strings.SplitN(a, "/", 2)[0]
			if seen[pkg] {
				continue
			}
			seen[pkg] = true
			name := names[pkg]
			if name == "" {
				name = pkg
			}
			out = append(out, App{Activity: a, Name: name})
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name < out[j].Name })
	return out
}

var names = map[string]string{
	"org.smarttube.stable": "SmartTube", "com.phlox.tvwebbrowser": "TV Bro 浏览器", "com.cxinventor.file.explorer": "CX 文件管理器",
	"org.galexander.sshd": "SimpleSSHD", "com.github.metacubex.clash.meta": "Clash Meta", "com.spocky.projengmenu": "Projectivy 桌面",
	"de.szalkowski.activitylauncher.oss": "Activity Launcher",
	"com.xgimi.filemanager":              "极米文件管理", "com.xgimi.manager": "极米管家（清理与安全）", "com.xgimi.wirelessscreen": "极米无线投屏",
}

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/keys/", func(w http.ResponseWriter, req *http.Request) {
		raw, err := os.ReadFile(m.cfg.File)
		if err != nil && !os.IsNotExist(err) {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		btns := make([]map[string]string, len(Buttons))
		for i, b := range Buttons {
			btns[i] = map[string]string{"id": b.ID, "label": b.Label}
		}
		core.WriteJSON(w, map[string]any{"buttons": btns, "slots": m.parse(string(raw)), "apps": m.apps(req.Context()), "file": m.cfg.File})
	})
	r.HandleFunc("POST /api/keys/", func(w http.ResponseWriter, req *http.Request) {
		var slots []Slot
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, 64<<10)).Decode(&slots); err != nil {
			core.WriteError(w, http.StatusBadRequest, "格式错误")
			return
		}
		host := req.Host
		if h, _, err := net.SplitHostPort(req.Host); err == nil {
			host = h
		}
		text, err := m.render(slots, net.JoinHostPort(host, "8090"))
		if err != nil {
			core.WriteError(w, http.StatusBadRequest, err.Error())
			return
		}
		tmp := m.cfg.File + ".tmp"
		if err := os.WriteFile(tmp, []byte(text), 0o644); err != nil || os.Rename(tmp, m.cfg.File) != nil {
			core.WriteError(w, http.StatusInternalServerError, "写入配置失败")
			return
		}
		m.env.Log.Info("遥控器按键设置已保存", "file", m.cfg.File)
		core.WriteJSON(w, map[string]string{"status": "ok"})
	})
	r.HandleFunc("GET /ui/keys/{$}", m.page)
}
