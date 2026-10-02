package core

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"sync"
	"time"
)

// 一次性登录码：工具箱用 token 申请一个登录码，再让浏览器打开 /login/code?c=<登录码>，
// 浏览器因此获得登录 Cookie。token 本身不出现在网址和浏览器历史中；登录码 60 秒内有效，只能使用一次。

const loginCodeTTL = 60 * time.Second

type loginCodes struct {
	mu    sync.Mutex
	codes map[string]time.Time // 登录码 → 过期时间
}

func (l *loginCodes) issue() string {
	b := make([]byte, 16)
	rand.Read(b)
	code := hex.EncodeToString(b)
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.codes == nil {
		l.codes = map[string]time.Time{}
	}
	now := time.Now()
	for c, exp := range l.codes { // 顺带清理过期的登录码
		if now.After(exp) {
			delete(l.codes, c)
		}
	}
	l.codes[code] = now.Add(loginCodeTTL)
	return code
}

// redeem 校验并作废登录码。
func (l *loginCodes) redeem(code string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	exp, ok := l.codes[code]
	delete(l.codes, code)
	return ok && time.Now().Before(exp)
}

func (h *Hub) loginLinkRoutes(mux *http.ServeMux) {
	mux.Handle("POST /api/login-code", RequireToken(h.cfg.Token, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		WriteJSON(w, map[string]string{"code": h.codes.issue()})
	})))
	mux.HandleFunc("GET /login/code", func(w http.ResponseWriter, r *http.Request) {
		if !h.codes.redeem(r.URL.Query().Get("c")) {
			Page(w, "登录失败", `<p>登录码无效或已过期（60 秒内有效，只能使用一次）。<a href="/login">输入 token 登录</a></p>`)
			return
		}
		setTokenCookie(w, h.cfg.Token)
		http.Redirect(w, r, "/", http.StatusSeeOther)
	})
}
