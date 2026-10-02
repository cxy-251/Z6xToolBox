package sshd

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"io"
	"log/slog"
	"net"
	"strings"
	"testing"

	"golang.org/x/crypto/ssh"
	"gopkg.in/yaml.v3"

	"z6x/hub/internal/core"
)

func TestSSH(t *testing.T) {
	_, priv, _ := ed25519.GenerateKey(rand.Reader)
	signer, _ := ssh.NewSignerFromKey(priv)
	_, other, _ := ed25519.GenerateKey(rand.Reader)
	otherSigner, _ := ssh.NewSignerFromKey(other)
	auth := strings.TrimSpace(string(ssh.MarshalAuthorizedKey(signer.PublicKey()))) + " deck"

	var node yaml.Node
	yaml.Unmarshal([]byte("enable: true\nshell: /bin/sh\nauthorized_keys: [\""+auth+"\"]\n"), &node)
	cfg := &core.Config{DataDir: t.TempDir(), Modules: map[string]yaml.Node{"sshd": *node.Content[0]}}
	var serve func(net.Listener)
	env := &core.Env{Config: cfg, Log: slog.New(slog.NewTextHandler(io.Discard, nil)),
		ServeRaw: func(_ int, s func(net.Listener)) { serve = s }}
	m := New()
	if err := m.Start(context.Background(), env); err != nil {
		t.Fatal(err)
	}
	ln, _ := net.Listen("tcp", "127.0.0.1:0")
	go serve(ln)
	defer ln.Close()

	dial := func(s ssh.Signer) (*ssh.Client, error) {
		return ssh.Dial("tcp", ln.Addr().String(), &ssh.ClientConfig{User: "x", Auth: []ssh.AuthMethod{ssh.PublicKeys(s)},
			HostKeyCallback: ssh.InsecureIgnoreHostKey()})
	}
	if _, err := dial(otherSigner); err == nil {
		t.Fatal("未授权的公钥应被拒绝")
	}
	c, err := dial(signer)
	if err != nil {
		t.Fatalf("授权的公钥应能登录：%v", err)
	}
	defer c.Close()
	s, _ := c.NewSession()
	out, err := s.CombinedOutput("echo hello; exit 3")
	if strings.TrimSpace(string(out)) != "hello" {
		t.Fatalf("命令输出不正确：%q，错误：%v", out, err)
	}
	var ee *ssh.ExitError
	if err == nil || !errorsAs(err, &ee) || ee.ExitStatus() != 3 {
		t.Fatalf("应返回退出码 3：%v", err)
	}
	// 交互式终端：请求伪终端后执行，输出中应有 tty 设备
	s2, _ := c.NewSession()
	s2.RequestPty("xterm", 24, 80, ssh.TerminalModes{})
	out, _ = s2.CombinedOutput("tty")
	if !strings.Contains(string(out), "/dev/pts/") {
		t.Fatalf("伪终端会话应有 tty：%q", out)
	}
}

func errorsAs(err error, target **ssh.ExitError) bool {
	e, ok := err.(*ssh.ExitError)
	if ok {
		*target = e
	}
	return ok
}
