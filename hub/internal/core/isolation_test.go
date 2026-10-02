package core

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"gopkg.in/yaml.v3"
)

// testModule 是用于测试的模块：/api/<name>/boom 会 panic，/api/<name>/ok 正常返回。
type testModule struct{ name string }

func (m testModule) Name() string                      { return m.name }
func (m testModule) Start(context.Context, *Env) error { return nil }
func (m testModule) Stop(context.Context) error        { return nil }
func (m testModule) Routes(r Router) {
	r.HandleFunc("GET /api/"+m.name+"/boom", func(http.ResponseWriter, *http.Request) { panic("测试用 panic") })
	r.HandleFunc("GET /api/"+m.name+"/ok", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte("ok")) })
}

// TestPanicIsolation 验证规格要求：一个模块 panic 只让它自己进入 failed，其他模块照常工作。
func TestPanicIsolation(t *testing.T) {
	enabled := func() yaml.Node {
		var n yaml.Node
		n.Encode(map[string]bool{"enable": true})
		return n
	}
	cfg := &Config{Token: "0123456789abcdef0123", Modules: map[string]yaml.Node{"a": enabled(), "b": enabled()}}
	h := New(cfg, slog.New(slog.NewTextHandler(io.Discard, nil)))
	h.Add(testModule{"a"})
	h.Add(testModule{"b"})

	mux := http.NewServeMux()
	h.started = time.Now()
	h.coreRoutes(mux)
	for _, e := range h.entries {
		e.set(StateRunning, nil)
		e.mod.Routes(&moduleMux{inner: mux, e: e, log: h.log, token: cfg.Token})
	}
	get := func(path string) int {
		r := httptest.NewRequest("GET", path, nil)
		r.Header.Set("Authorization", "Bearer "+cfg.Token)
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, r)
		return w.Code
	}

	if c := get("/api/a/boom"); c != 500 {
		t.Fatalf("panic 应返回 500，实际 %d", c)
	}
	if c := get("/api/b/ok"); c != 200 {
		t.Fatalf("模块 a panic 后，模块 b 应仍正常，实际 %d", c)
	}
	states := map[string]State{}
	for _, s := range h.health().Modules {
		states[s.Name] = s.State
	}
	if states["a"] != StateFailed || states["b"] != StateRunning {
		t.Fatalf("状态不正确：%v", states)
	}
}
