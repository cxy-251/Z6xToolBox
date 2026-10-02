// Package sshd 是 hub 内置的 SSH 服务：以 hub 的身份运行，投影仪上即 shell 身份（uid 2000），
// 登录后的权限与 adb shell 相同，可以读取按键、进程等普通应用读不到的信息。
//
// 用来代替 SimpleSSHD（普通应用身份，权限很低）。原提案「Dropbear 以 shell 身份运行」需要交叉编译 C 代码，
// Deck 上没有 C 交叉编译器，改为用 Go 官方的 golang.org/x/crypto/ssh 实现，与 hub 一起部署。
//
//   - 只接受密钥登录（配置中列出的公钥），不支持密码；
//   - 端口与 HTTP 端口一样由网络守卫管理：只在局域网地址上监听，离开可信网络时关闭；
//   - 支持交互式终端（伪终端，窗口大小随客户端调整）和执行单条命令；不支持端口转发与 sftp。
package sshd

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/binary"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"syscall"

	"golang.org/x/crypto/ssh"

	"z6x/hub/internal/core"
	"z6x/hub/internal/pty"
)

type Config struct {
	Port int `yaml:"port"`
	// AuthorizedKeys 为允许登录的公钥（与 authorized_keys 文件中的行格式相同）。
	AuthorizedKeys []string `yaml:"authorized_keys"`
	Shell          string   `yaml:"shell"`
	MaxSessions    int      `yaml:"max_sessions"`
}

type Module struct {
	cfg    Config
	env    *core.Env
	server *ssh.ServerConfig
	keys   map[string]string // 公钥（wire 格式）→ 注释
	mu     sync.Mutex
	conns  map[*ssh.ServerConn]bool
}

func New() *Module { return &Module{conns: map[*ssh.ServerConn]bool{}} }

func (m *Module) Name() string { return "sshd" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{Port: 8022, Shell: "/system/bin/sh", MaxSessions: 4}
	if err := env.Config.Decode("sshd", &m.cfg); err != nil {
		return err
	}
	m.keys = map[string]string{}
	for _, line := range m.cfg.AuthorizedKeys {
		k, comment, _, _, err := ssh.ParseAuthorizedKey([]byte(line))
		if err != nil {
			return fmt.Errorf("authorized_keys 中有无法解析的公钥：%v", err)
		}
		m.keys[string(k.Marshal())] = comment
	}
	if len(m.keys) == 0 {
		return errors.New("sshd 需要在 authorized_keys 中至少列出一个公钥（只支持密钥登录）")
	}
	signer, err := hostKey(filepath.Join(env.Config.DataDir, "sshd_host_ed25519"))
	if err != nil {
		return fmt.Errorf("主机密钥：%w", err)
	}
	m.server = &ssh.ServerConfig{
		PublicKeyCallback: func(c ssh.ConnMetadata, k ssh.PublicKey) (*ssh.Permissions, error) {
			if comment, ok := m.keys[string(k.Marshal())]; ok {
				return &ssh.Permissions{Extensions: map[string]string{"comment": comment}}, nil
			}
			return nil, errors.New("公钥未授权")
		},
		ServerVersion: "SSH-2.0-z6x-hub",
	}
	m.server.AddHostKey(signer)
	env.ServeRaw(m.cfg.Port, m.serve)
	env.Log.Info("SSH 主机密钥指纹", "sha256", ssh.FingerprintSHA256(signer.PublicKey()))
	return nil
}

func (m *Module) Stop(context.Context) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for c := range m.conns {
		c.Close()
	}
	return nil
}

func (m *Module) Routes(core.Router) {}

// hostKey 读取主机密钥，不存在时生成一个 ed25519 密钥并保存（权限 600）。
func hostKey(path string) (ssh.Signer, error) {
	if b, err := os.ReadFile(path); err == nil {
		return ssh.ParsePrivateKey(b)
	}
	_, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return nil, err
	}
	block, err := ssh.MarshalPrivateKey(priv, "z6x-hub host key")
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, err
	}
	if err := os.WriteFile(path, pem.EncodeToMemory(block), 0o600); err != nil {
		return nil, err
	}
	return ssh.NewSignerFromKey(priv)
}

// serve 接受连接；监听器被网络守卫关闭时返回，并断开所有现有连接。
func (m *Module) serve(ln net.Listener) {
	for {
		c, err := ln.Accept()
		if err != nil {
			m.Stop(context.Background())
			return
		}
		go m.handle(c)
	}
}

func (m *Module) handle(raw net.Conn) {
	m.mu.Lock()
	full := len(m.conns) >= m.cfg.MaxSessions
	m.mu.Unlock()
	if full {
		raw.Close()
		return
	}
	conn, chans, reqs, err := ssh.NewServerConn(raw, m.server)
	if err != nil {
		m.env.Log.Warn("SSH 登录失败", "remote", raw.RemoteAddr().String(), "err", err)
		raw.Close()
		return
	}
	m.mu.Lock()
	m.conns[conn] = true
	m.mu.Unlock()
	m.env.Log.Info("SSH 登录", "remote", conn.RemoteAddr().String(), "key", conn.Permissions.Extensions["comment"])
	defer func() {
		m.mu.Lock()
		delete(m.conns, conn)
		m.mu.Unlock()
		conn.Close()
	}()
	go ssh.DiscardRequests(reqs) // 不支持全局请求（端口转发等）
	for nc := range chans {
		if nc.ChannelType() != "session" {
			nc.Reject(ssh.UnknownChannelType, "只支持 session")
			continue
		}
		ch, creqs, err := nc.Accept()
		if err != nil {
			continue
		}
		go m.session(ch, creqs)
	}
}

type ptyReq struct {
	term       string
	cols, rows uint32
}

// session 处理一个会话：先收集 pty-req、env，再在 shell 或 exec 请求时启动进程。
func (m *Module) session(ch ssh.Channel, reqs <-chan *ssh.Request) {
	defer ch.Close()
	var pr *ptyReq
	var ptmx *os.File
	env := []string{"HOME=" + home(), "PATH=" + os.Getenv("PATH"), "SHELL=" + m.cfg.Shell}
	for req := range reqs {
		switch req.Type {
		case "pty-req":
			term, rest, ok := parseString(req.Payload)
			if ok && len(rest) >= 8 {
				pr = &ptyReq{term: term, cols: binary.BigEndian.Uint32(rest), rows: binary.BigEndian.Uint32(rest[4:])}
			}
			req.Reply(ok, nil)
		case "env":
			req.Reply(true, nil) // 接受但忽略客户端的环境变量（例如 LANG），避免影响设备上的程序
		case "window-change":
			if ptmx != nil && len(req.Payload) >= 8 {
				pty.SetSize(ptmx, uint16(binary.BigEndian.Uint32(req.Payload[4:])), uint16(binary.BigEndian.Uint32(req.Payload)))
			}
		case "shell", "exec":
			var cmd *exec.Cmd
			if req.Type == "exec" {
				line, _, ok := parseString(req.Payload)
				if !ok {
					req.Reply(false, nil)
					continue
				}
				cmd = exec.Command(m.cfg.Shell, "-c", line)
			} else {
				cmd = exec.Command(m.cfg.Shell, "-l")
			}
			cmd.Dir = home()
			cmd.Env = env
			var err error
			if pr != nil {
				ptmx, err = m.startPTY(cmd, pr, env)
			} else {
				err = m.startPipes(cmd, ch)
			}
			req.Reply(err == nil, nil)
			if err != nil {
				fmt.Fprintf(ch.Stderr(), "启动失败：%v\r\n", err)
				return
			}
			if ptmx != nil {
				go io.Copy(ptmx, ch)
				go io.Copy(ch, ptmx)
			}
			go func() {
				status := 0
				if err := cmd.Wait(); err != nil {
					var ee *exec.ExitError
					if errors.As(err, &ee) {
						status = ee.ExitCode()
					} else {
						status = 1
					}
				}
				if ptmx != nil {
					ptmx.Close()
				}
				ch.SendRequest("exit-status", false, binary.BigEndian.AppendUint32(nil, uint32(status)))
				ch.Close()
			}()
		default:
			req.Reply(false, nil) // 不支持 subsystem（sftp）、x11、agent 转发等
		}
	}
}

func (m *Module) startPTY(cmd *exec.Cmd, pr *ptyReq, env []string) (*os.File, error) {
	ptmx, tty, err := pty.Open()
	if err != nil {
		return nil, err
	}
	defer tty.Close()
	pty.SetSize(ptmx, uint16(pr.rows), uint16(pr.cols))
	term := pr.term
	if term == "" {
		term = "xterm-256color"
	}
	cmd.Env = append(env, "TERM="+term)
	cmd.Stdin, cmd.Stdout, cmd.Stderr = tty, tty, tty
	// shell 成为新会话的首进程，并以伪终端为控制终端，Ctrl+C 等信号才能正常工作
	cmd.SysProcAttr = &syscall.SysProcAttr{Setsid: true, Setctty: true}
	if err := cmd.Start(); err != nil {
		ptmx.Close()
		return nil, err
	}
	return ptmx, nil
}

func (m *Module) startPipes(cmd *exec.Cmd, ch ssh.Channel) error {
	cmd.Stdout, cmd.Stderr = ch, ch.Stderr()
	in, err := cmd.StdinPipe()
	if err != nil {
		return err
	}
	if err := cmd.Start(); err != nil {
		return err
	}
	go func() { io.Copy(in, ch); in.Close() }()
	return nil
}

// home 是登录后的工作目录：安卓上为 shell 身份的 /data/local/tmp，其他系统（单元测试）为根目录。
func home() string {
	if fi, err := os.Stat("/data/local/tmp"); err == nil && fi.IsDir() {
		return "/data/local/tmp"
	}
	return "/"
}

// parseString 读取 SSH 协议中的字符串（4 字节长度 + 内容）。
func parseString(b []byte) (string, []byte, bool) {
	if len(b) < 4 {
		return "", nil, false
	}
	n := binary.BigEndian.Uint32(b)
	if uint32(len(b)-4) < n {
		return "", nil, false
	}
	return string(b[4 : 4+n]), b[4+n:], true
}
