package jobs

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"gopkg.in/yaml.v3"

	"z6x/hub/internal/core"
)

func TestJobs(t *testing.T) {
	dir := t.TempDir()
	flag := filepath.Join(dir, "running")
	logf := filepath.Join(dir, "job.log")
	os.WriteFile(logf, []byte("第一行\n第二行\n"), 0o644)
	src := "items:\n  - id: demo\n    title: 测试任务\n    start: touch " + flag + "\n    stop: rm -f " + flag +
		"\n    status: test -e " + flag + " && echo 运行中\n    log: " + logf + "\n    autostart: false\n"
	var node yaml.Node
	yaml.Unmarshal([]byte(src), &node)
	data := t.TempDir()
	env := &core.Env{Config: &core.Config{DataDir: data, Modules: map[string]yaml.Node{"jobs": *node.Content[0]}},
		Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
	m := New()
	if err := m.Start(context.Background(), env); err != nil {
		t.Fatal(err)
	}
	mux := http.NewServeMux()
	m.Routes(mux)
	list := func() jobState {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest("GET", "/api/jobs/", nil))
		var out []jobState
		if err := json.Unmarshal(rec.Body.Bytes(), &out); err != nil || len(out) != 1 {
			t.Fatalf("列表不正确：%s", rec.Body)
		}
		return out[0]
	}
	post := func(path string) int {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest("POST", path, nil))
		return rec.Code
	}
	if s := list(); s.Running || s.Autostart || len(s.Log) != 2 {
		t.Fatalf("初始状态不正确：%+v", s)
	}
	if post("/api/jobs/demo/start") != 200 || !list().Running {
		t.Fatal("启动后应为运行中")
	}
	if post("/api/jobs/demo/stop") != 200 || list().Running {
		t.Fatal("停止后应为未运行")
	}
	if post("/api/jobs/demo/autostart?on=1") != 200 || !list().Autostart {
		t.Fatal("开机自动启动应可修改")
	}
	m2 := New() // 重启 hub 后仍保留网页上的设置
	m2.Start(context.Background(), env)
	if !m2.isAutostart(m2.cfg.Items[0]) {
		t.Error("开机自动启动的设置应在重启后保留")
	}
	if post("/api/jobs/none/start") != 404 {
		t.Error("不存在的任务应返回 404")
	}
}
