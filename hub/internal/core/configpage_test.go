package core

import (
	"strings"
	"testing"
)

func TestTokenMaskRoundTrip(t *testing.T) {
	orig := "listen: \":8090\"\ntoken: \"real-secret-token-12345\"\nmodules: {}\n"
	masked := maskToken(orig)
	if strings.Contains(masked, "real-secret") || !strings.Contains(masked, tokenMask) {
		t.Fatalf("token 未被隐藏：%s", masked)
	}
	if got := unmaskToken(masked, "real-secret-token-12345"); got != orig {
		t.Fatalf("还原后不一致：\n%s", got)
	}
	// 用户在页面上改了 token 时，应保留用户输入的新值
	changed := strings.Replace(masked, tokenMask, "new-token-abcdefghijk", 1)
	if got := unmaskToken(changed, "real-secret-token-12345"); !strings.Contains(got, "new-token-abcdefghijk") {
		t.Fatalf("新 token 被覆盖：%s", got)
	}
}
