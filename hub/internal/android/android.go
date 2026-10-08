// Package android 封装对安卓系统命令（input、am、cmd 等）的调用。
// 所有调用都以参数数组传给 exec，不经过 shell，避免命令注入；每次调用都有超时。
package android

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

// Runner 执行外部命令。测试时可以替换为假的实现。
type Runner interface {
	Run(ctx context.Context, name string, args ...string) (string, error)
}

// Exec 是真正调用系统命令的 Runner。
type Exec struct{ Timeout time.Duration }

func (e Exec) Run(ctx context.Context, name string, args ...string) (string, error) {
	t := e.Timeout
	if t == 0 {
		t = 10 * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, t)
	defer cancel()
	cmd := exec.CommandContext(ctx, Resolve(name), args...)
	var out bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &out
	err := cmd.Run()
	s := strings.TrimSpace(out.String())
	if ctx.Err() == context.DeadlineExceeded {
		return s, fmt.Errorf("%s 超时（%s）", name, t)
	}
	if err != nil {
		return s, fmt.Errorf("%s 执行失败：%v：%s", name, err, s)
	}
	return s, nil
}

// binDirs 是查找系统命令的目录，依次为：安卓系统命令、Termux 的命令（手机上 hub 在 Termux 中运行）。
var binDirs = []string{"/system/bin", "/system/xbin", "/vendor/bin", "/data/data/com.termux/files/usr/bin"}

// Resolve 把命令名换成完整路径（用 stat 逐个目录查找）。不能让 Go 的 exec.LookPath 去找：它会调用
// faccessat2，在手机 Termux（普通应用）的 seccomp 限制下触发 SIGSYS，整个 hub 被系统结束（2026-10-08 实测）。
// 找不到时原样返回（交给 exec 报错）；已含路径的命令不变。
func Resolve(name string) string {
	if strings.ContainsRune(name, '/') {
		return name
	}
	for _, d := range binDirs {
		p := filepath.Join(d, name)
		if st, err := os.Stat(p); err == nil && !st.IsDir() {
			return p
		}
	}
	for _, d := range filepath.SplitList(os.Getenv("PATH")) {
		p := filepath.Join(d, name)
		if st, err := os.Stat(p); err == nil && !st.IsDir() && d != "" {
			return p
		}
	}
	return name
}

// Keys 是常用按键名到安卓键值（KeyEvent）的对照。
var Keys = map[string]int{
	"home": 3, "back": 4, "up": 19, "down": 20, "left": 21, "right": 22, "ok": 23, "enter": 66,
	"menu": 82, "power": 26, "volup": 24, "voldown": 25, "mute": 164,
	"play": 85, "next": 87, "prev": 88, "stop": 86, "settings": 176,
}

// Device 提供对投影仪的常用操作。
type Device struct{ R Runner }

// Key 模拟按下一个键。name 可以是 Keys 中的名称，也可以直接是数字键值。
func (d Device) Key(ctx context.Context, name string) error {
	code, ok := Keys[strings.ToLower(name)]
	if !ok {
		if _, err := fmt.Sscanf(name, "%d", &code); err != nil || code <= 0 || code > 400 {
			return fmt.Errorf("未知按键：%s", name)
		}
	}
	_, err := d.R.Run(ctx, "/system/bin/input", "keyevent", fmt.Sprint(code))
	return err
}

// IsTypable 判断文本能否用 input text 输入：只支持可打印的 ASCII 字符。
func IsTypable(s string) bool {
	if s == "" {
		return false
	}
	for _, r := range s {
		if r < 0x20 || r > 0x7e {
			return false
		}
	}
	return true
}

// Text 向当前输入框输入文字。input text 中空格要写成 %s。
func (d Device) Text(ctx context.Context, s string) error {
	if !IsTypable(s) {
		return fmt.Errorf("只能输入英文、数字和半角符号（input text 的限制）")
	}
	_, err := d.R.Run(ctx, "/system/bin/input", "text", strings.ReplaceAll(s, " ", "%s"))
	return err
}

// OpenURL 让系统选择应用打开链接。
func (d Device) OpenURL(ctx context.Context, url string) error {
	if !strings.HasPrefix(url, "http://") && !strings.HasPrefix(url, "https://") {
		return fmt.Errorf("只支持 http 和 https 链接")
	}
	_, err := d.R.Run(ctx, "/system/bin/am", "start", "-a", "android.intent.action.VIEW", "-d", url)
	return err
}

// LaunchApp 启动某个应用。先查电视启动入口（LEANBACK_LAUNCHER），再查普通启动入口。
func (d Device) LaunchApp(ctx context.Context, pkg string) error {
	if !validPackage(pkg) {
		return fmt.Errorf("包名格式不正确：%s", pkg)
	}
	for _, cat := range []string{"android.intent.category.LEANBACK_LAUNCHER", "android.intent.category.LAUNCHER"} {
		out, err := d.R.Run(ctx, "/system/bin/cmd", "package", "resolve-activity", "--brief",
			"-a", "android.intent.action.MAIN", "-c", cat, pkg)
		if err != nil {
			continue
		}
		lines := strings.Split(out, "\n")
		comp := strings.TrimSpace(lines[len(lines)-1])
		if strings.Contains(comp, "/") {
			_, err = d.R.Run(ctx, "/system/bin/am", "start", "-n", comp)
			return err
		}
	}
	return fmt.Errorf("找不到 %s 的启动入口", pkg)
}

func validPackage(p string) bool {
	if p == "" || len(p) > 200 || !strings.Contains(p, ".") {
		return false
	}
	for _, r := range p {
		if !(r == '.' || r == '_' || r >= '0' && r <= '9' || r >= 'a' && r <= 'z' || r >= 'A' && r <= 'Z') {
			return false
		}
	}
	return true
}
