package notify

import (
	"context"
	"strings"
	"testing"
)

type fake struct{ args [][]string }

func (f *fake) Run(_ context.Context, name string, args ...string) (string, error) {
	f.args = append(f.args, append([]string{name}, args...))
	return "", nil
}

func TestSendUsesArgsAndUniqueTags(t *testing.T) {
	f := &fake{}
	m := New(f)
	m.Send(context.Background(), "标题", "内容; reboot")
	m.Send(context.Background(), "标题", "第二条")
	a, b := f.args[0], f.args[1]
	if a[0] != "/system/bin/cmd" || a[len(a)-1] != "内容; reboot" {
		t.Fatalf("参数不正确：%v", a)
	}
	if a[len(a)-2] == b[len(b)-2] {
		t.Error("两条通知的 tag 不应相同，否则后一条会覆盖前一条")
	}
}

func TestClip(t *testing.T) {
	s := strings.Repeat("字", 70)
	if got := clip(s, 60); len([]rune(got)) != 61 || !strings.HasSuffix(got, "…") {
		t.Errorf("截断结果不正确：%d 字", len([]rune(got)))
	}
}
