package core

import (
	"crypto/subtle"
	"net/http"
	"strings"
)

const cookieName = "z6x_token"

// TokenFrom 从请求中取出 token。支持三种方式：
//   - 请求头 Authorization: Bearer <token>（脚本、工具箱使用）
//   - Cookie z6x_token（浏览器登录后使用）
//   - WebDAV 客户端的 Basic 认证，密码即 token（用户名任意）
func TokenFrom(r *http.Request) string {
	if h := r.Header.Get("Authorization"); strings.HasPrefix(h, "Bearer ") {
		return strings.TrimPrefix(h, "Bearer ")
	}
	if _, pass, ok := r.BasicAuth(); ok {
		return pass
	}
	if c, err := r.Cookie(cookieName); err == nil {
		return c.Value
	}
	return ""
}

// ValidToken 用定长时间比较，避免通过响应时间猜测 token。
func ValidToken(got, want string) bool {
	return want != "" && subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

// RequireToken 包装处理函数：没有正确 token 时，浏览器访问页面跳转到登录页，接口返回 401。
func RequireToken(token string, h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if ValidToken(TokenFrom(r), token) {
			h.ServeHTTP(w, r)
			return
		}
		if r.Method == http.MethodGet && strings.HasPrefix(r.URL.Path, "/ui/") {
			http.Redirect(w, r, "/login?next="+r.URL.Path, http.StatusFound)
			return
		}
		w.Header().Set("WWW-Authenticate", `Basic realm="z6x-hub"`)
		http.Error(w, "需要 token", http.StatusUnauthorized)
	})
}

// RequireTokenFor 供需要独立端口的模块（如 WebDAV）使用。
func (e *Env) RequireTokenFor(h http.Handler) http.Handler {
	return RequireToken(e.Config.Token, h)
}

func setTokenCookie(w http.ResponseWriter, token string) {
	http.SetCookie(w, &http.Cookie{
		Name: cookieName, Value: token, Path: "/", HttpOnly: true,
		SameSite: http.SameSiteStrictMode, MaxAge: 180 * 24 * 3600,
	})
}
