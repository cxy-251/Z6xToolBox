package android

import (
	"context"
	"strings"
	"testing"
)

// fake 记录被调用的命令，不真正执行。
type fake struct {
	calls  []string
	output map[string]string
}

func (f *fake) Run(_ context.Context, name string, args ...string) (string, error) {
	cmd := name + " " + strings.Join(args, " ")
	f.calls = append(f.calls, cmd)
	for k, v := range f.output {
		if strings.Contains(cmd, k) {
			return v, nil
		}
	}
	return "", nil
}

func TestKeyByNameAndCode(t *testing.T) {
	f := &fake{}
	d := Device{R: f}
	d.Key(context.Background(), "home")
	d.Key(context.Background(), "24")
	if f.calls[0] != "/system/bin/input keyevent 3" || f.calls[1] != "/system/bin/input keyevent 24" {
		t.Errorf("命令不正确：%v", f.calls)
	}
	if err := d.Key(context.Background(), "rm -rf /"); err == nil {
		t.Error("非法按键名应被拒绝")
	}
}

func TestTextEscapesSpacesAndRejectsChinese(t *testing.T) {
	f := &fake{}
	d := Device{R: f}
	if err := d.Text(context.Background(), "a b;c"); err != nil {
		t.Fatal(err)
	}
	// 参数以数组传递，分号等字符不会被 shell 解释
	if f.calls[0] != "/system/bin/input text a%sb;c" {
		t.Errorf("命令不正确：%q", f.calls[0])
	}
	if err := d.Text(context.Background(), "中文"); err == nil {
		t.Error("中文应被拒绝")
	}
}

func TestOpenURLOnlyHTTP(t *testing.T) {
	d := Device{R: &fake{}}
	if err := d.OpenURL(context.Background(), "file:///data/x"); err == nil {
		t.Error("非 http 链接应被拒绝")
	}
}

func TestLaunchAppResolvesComponent(t *testing.T) {
	f := &fake{output: map[string]string{"LEANBACK_LAUNCHER": "priority=0\norg.smarttube.stable/com.liskovsoft.Main"}}
	d := Device{R: f}
	if err := d.LaunchApp(context.Background(), "org.smarttube.stable"); err != nil {
		t.Fatal(err)
	}
	if last := f.calls[len(f.calls)-1]; last != "/system/bin/am start -n org.smarttube.stable/com.liskovsoft.Main" {
		t.Errorf("启动命令不正确：%s", last)
	}
	if err := d.LaunchApp(context.Background(), "a;reboot"); err == nil {
		t.Error("非法包名应被拒绝")
	}
}
