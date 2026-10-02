package core

import (
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"testing"
	"time"
)

func freePort(t *testing.T) int {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer ln.Close()
	return ln.Addr().(*net.TCPAddr).Port
}

func reachable(port int) bool {
	c, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 300*time.Millisecond)
	if err != nil {
		return false
	}
	c.Close()
	return true
}

// TestGateFollowsNetwork：只在可信网络上监听；离开后关闭，回来后恢复。
func TestGateFollowsNetwork(t *testing.T) {
	trusted := fingerprint("tok", "aa:bb:cc:dd:ee:ff")
	g := NewGate(NetworkConfig{Iface: "wlan0", Trusted: []string{trusted}}, "tok", slog.New(slog.NewTextHandler(io.Discard, nil)))
	state := NetState{IP: "127.0.0.1", Fingerprint: trusted}
	g.probe = func() (NetState, error) { return state, nil }
	port := freePort(t)
	g.Add("main", port, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {}))

	if err := g.check(); err != nil || !reachable(port) {
		t.Fatalf("可信网络上应监听：%v", err)
	}
	state.Fingerprint = fingerprint("tok", "11:22:33:44:55:66") // 换到别的 Wi-Fi
	g.check()
	if reachable(port) {
		t.Fatal("离开可信网络后应停止监听")
	}
	if _, reason := g.Status(); reason == "" {
		t.Error("应说明未监听的原因")
	}
	state = NetState{} // 断开 Wi-Fi
	g.check()
	if reachable(port) {
		t.Fatal("没有 IPv4 地址时不应监听")
	}
	state = NetState{IP: "127.0.0.1", Fingerprint: trusted} // 回到家
	if err := g.check(); err != nil || !reachable(port) {
		t.Fatalf("回到可信网络后应恢复监听：%v", err)
	}
	g.close()
}

func TestFingerprintDependsOnToken(t *testing.T) {
	a, b := fingerprint("t1", "AA:BB:CC:DD:EE:FF"), fingerprint("t1", "aa:bb:cc:dd:ee:ff")
	if a != b {
		t.Error("硬件地址大小写不应影响指纹")
	}
	if a == fingerprint("t2", "aa:bb:cc:dd:ee:ff") {
		t.Error("不同 token 应得到不同指纹，防止反推硬件地址")
	}
}
