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

func TestLoginCodeSingleUse(t *testing.T) {
	var l loginCodes
	c := l.issue()
	if !l.redeem(c) {
		t.Fatal("新登录码应当有效")
	}
	if l.redeem(c) {
		t.Fatal("登录码只能使用一次")
	}
	l.codes[c] = time.Now().Add(-time.Second)
	if l.redeem(c) {
		t.Fatal("过期的登录码应当无效")
	}
	if l.redeem("") {
		t.Fatal("空登录码应当无效")
	}
}

// TestGateToleratesMissingFingerprint：指纹偶尔取不到且 IP 未变时继续服务，连续 3 次才停止；换了网络立即停止。
func TestGateToleratesMissingFingerprint(t *testing.T) {
	trusted := fingerprint("tok", "aa:bb:cc:dd:ee:ff")
	g := NewGate(NetworkConfig{Iface: "wlan0", Trusted: []string{trusted}}, "tok", slog.New(slog.NewTextHandler(io.Discard, nil)))
	state := NetState{IP: "127.0.0.1", Fingerprint: trusted}
	g.probe = func() (NetState, error) { return state, nil }
	port := freePort(t)
	g.Add("main", port, http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {}))
	defer g.close()
	g.check()
	state.Fingerprint = ""
	g.check()
	g.check()
	if !reachable(port) {
		t.Fatal("指纹连续两次取不到时应继续服务")
	}
	g.check()
	if reachable(port) {
		t.Fatal("连续三次取不到指纹应停止服务")
	}
	state.Fingerprint = trusted
	g.check()
	state.Fingerprint = fingerprint("tok", "11:22:33:44:55:66")
	g.check()
	if reachable(port) {
		t.Fatal("换到其他网络应立即停止")
	}
}

// 安卓会销毁应用的监听套接字（accept 返回 EINVAL），进程仍在运行却不再监听；下一次检查应重新监听。
func TestGateRebindsAfterListenerDestroyed(t *testing.T) {
	trusted := fingerprint("tok", "aa:bb:cc:dd:ee:ff")
	g := NewGate(NetworkConfig{Iface: "wlan0", Trusted: []string{trusted}}, "tok", slog.New(slog.NewTextHandler(io.Discard, nil)))
	g.probe = func() (NetState, error) { return NetState{IP: "127.0.0.1", Fingerprint: trusted}, nil }
	port := freePort(t)
	g.Add("main", port, http.NotFoundHandler())
	if err := g.check(); err != nil || !reachable(port) {
		t.Fatalf("应开始监听：%v", err)
	}
	g.mu.Lock()
	ln := g.svcs[0].ln
	g.mu.Unlock()
	ln.Close() // 模拟被系统作废：不经过 closeLocked
	deadline := time.Now().Add(2 * time.Second)
	for {
		g.mu.Lock()
		broken := g.broken
		g.mu.Unlock()
		if broken {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("监听器意外关闭后应标记为需要重新监听")
		}
		time.Sleep(10 * time.Millisecond)
	}
	if err := g.check(); err != nil || !reachable(port) {
		t.Fatalf("应重新监听：%v", err)
	}
	g.close()
	time.Sleep(50 * time.Millisecond)
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.broken {
		t.Error("主动关闭不应标记为需要重新监听")
	}
}
