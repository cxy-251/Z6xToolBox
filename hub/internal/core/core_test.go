package core

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeCfg(t *testing.T, s string) string {
	t.Helper()
	p := filepath.Join(t.TempDir(), "hub.yaml")
	if err := os.WriteFile(p, []byte(s), 0o600); err != nil {
		t.Fatal(err)
	}
	return p
}

func TestConfigRejectsPortConflict(t *testing.T) {
	p := writeCfg(t, `
listen: ":8090"
token: "0123456789abcdef0123"
modules:
  files: { enable: true, port: 8090 }
`)
	_, err := LoadConfig(p)
	if err == nil || !strings.Contains(err.Error(), "端口 8090 冲突") {
		t.Fatalf("应报端口冲突，实际：%v", err)
	}
}

func TestConfigRejectsLowPortAndWeakToken(t *testing.T) {
	p := writeCfg(t, `
listen: ":80"
token: "short"
`)
	_, err := LoadConfig(p)
	if err == nil || !strings.Contains(err.Error(), "低于 1024") || !strings.Contains(err.Error(), "至少 16") {
		t.Fatalf("应同时报端口和 token 问题，实际：%v", err)
	}
}

func TestConfigIgnoresDisabledModulePort(t *testing.T) {
	p := writeCfg(t, `
token: "0123456789abcdef0123"
modules:
  files: { enable: false, port: 8090 }
`)
	if _, err := LoadConfig(p); err != nil {
		t.Fatalf("未启用的模块不应参与端口检查：%v", err)
	}
}

func TestRequireToken(t *testing.T) {
	ok := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte("ok")) })
	h := RequireToken("secret-token-1234567", ok)
	cases := []struct {
		name string
		req  func() *http.Request
		code int
	}{
		{"无 token 访问接口", func() *http.Request { return httptest.NewRequest("GET", "/api/x", nil) }, 401},
		{"Bearer 正确", func() *http.Request {
			r := httptest.NewRequest("GET", "/api/x", nil)
			r.Header.Set("Authorization", "Bearer secret-token-1234567")
			return r
		}, 200},
		{"Bearer 错误", func() *http.Request {
			r := httptest.NewRequest("GET", "/api/x", nil)
			r.Header.Set("Authorization", "Bearer wrong")
			return r
		}, 401},
		{"Basic 密码为 token（WebDAV）", func() *http.Request {
			r := httptest.NewRequest("PROPFIND", "/", nil)
			r.SetBasicAuth("任意", "secret-token-1234567")
			return r
		}, 200},
		{"Cookie", func() *http.Request {
			r := httptest.NewRequest("GET", "/api/x", nil)
			r.AddCookie(&http.Cookie{Name: cookieName, Value: "secret-token-1234567"})
			return r
		}, 200},
		{"未登录打开页面跳转登录", func() *http.Request { return httptest.NewRequest("GET", "/ui/files/", nil) }, 302},
	}
	for _, c := range cases {
		w := httptest.NewRecorder()
		h.ServeHTTP(w, c.req())
		if w.Code != c.code {
			t.Errorf("%s：期望 %d，实际 %d", c.name, c.code, w.Code)
		}
	}
}

func TestRotatingFile(t *testing.T) {
	p := filepath.Join(t.TempDir(), "hub.log")
	r, err := OpenRotating(p, 100, 3)
	if err != nil {
		t.Fatal(err)
	}
	line := []byte(strings.Repeat("x", 40) + "\n")
	for i := 0; i < 20; i++ {
		r.Write(line)
	}
	r.Close()
	for _, suffix := range []string{"", ".1", ".2", ".3"} {
		if _, err := os.Stat(p + suffix); err != nil {
			t.Errorf("缺少 %s", filepath.Base(p+suffix))
		}
	}
	if _, err := os.Stat(p + ".4"); err == nil {
		t.Error("最多保留 3 份旧日志，不应出现 .4")
	}
	if fi, _ := os.Stat(p); fi.Size() > 100 {
		t.Errorf("当前日志不应超过上限，实际 %d 字节", fi.Size())
	}
}

// TestRecorderSupportsHijack 防止回归：日志包装层必须支持 Hijack，否则 WebSocket 握手会 panic。
func TestRecorderSupportsHijack(t *testing.T) {
	var w http.ResponseWriter = &statusRecorder{ResponseWriter: httptest.NewRecorder()}
	if _, ok := w.(http.Hijacker); !ok {
		t.Fatal("statusRecorder 应实现 http.Hijacker")
	}
}
