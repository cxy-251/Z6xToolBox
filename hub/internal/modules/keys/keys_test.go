package keys

import (
	"strings"
	"testing"
)

func TestRenderParseRoundTrip(t *testing.T) {
	m := &Module{cfg: Config{Z6x: "/data/local/tmp/z6x-tools/z6x"}}
	slots := []Slot{
		{Button: "youku", Action: "app:org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"},
		{Button: "youku", Long: true, Action: "tasks"},
		{Button: "jiguang", Action: "key:home"},
		{Button: "mango", Long: true, Action: "sh:input keyevent 3"},
		{Button: "side", Action: "clean"},
	}
	text, err := m.render(slots, "192.168.0.109:8090")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(text, "press:youku = sh: am start -n org.smarttube.stable/") || !strings.Contains(text, "long:youku = sh: am start -a android.intent.action.VIEW -d http://192.168.0.109:8090/ui/tasks/") {
		t.Fatalf("生成的配置不正确：\n%s", text)
	}
	got := m.parse(text)
	want := map[string]string{"youku/false": slots[0].Action, "youku/true": "tasks", "jiguang/false": "key:home", "mango/true": "sh:input keyevent 3", "qiyiguo/false": "", "side/false": "clean"}
	for _, s := range got {
		key := s.Button + "/" + map[bool]string{true: "true", false: "false"}[s.Long]
		if w, ok := want[key]; ok && s.Action != w {
			t.Errorf("%s 还原为 %q，应为 %q", key, s.Action, w)
		}
	}
	for _, bad := range []Slot{{Button: "x", Action: "key:home"}, {Button: "youku", Action: "app:bad name"}, {Button: "youku", Action: "sh:a\nlong:youku = sh: x"}} {
		if _, err := m.render([]Slot{bad}, "h:1"); err == nil {
			t.Errorf("应拒绝：%+v", bad)
		}
	}
}
