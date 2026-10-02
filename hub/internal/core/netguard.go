package core

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/exec"
	"slices"
	"strings"
	"sync"
	"time"

	"gopkg.in/yaml.v3"
)

// NetworkConfig 限定 hub 在哪个网络上对外提供服务。
//
// 不配置 iface 时监听所有地址（包括 IPv6 公网地址和移动数据网络），只适合位置固定、
// 只连家里网络的设备（投影仪）。手机会随身带出门，应配置 iface 和 trusted：
// hub 只在 Wi-Fi 的局域网 IPv4 地址上监听，并且只在家里的 Wi-Fi 上监听。
type NetworkConfig struct {
	// Iface 是监听的网卡（手机为 wlan0）。只监听它的 IPv4 地址。
	Iface string `yaml:"iface"`
	// Trusted 是可信网络的指纹（由 z6x-hub -trust 在家里的 Wi-Fi 上生成）。
	// 为空时，只要网卡有 IPv4 地址就监听。
	Trusted []string `yaml:"trusted"`
}

// NetState 是网络检查的结果。
type NetState struct {
	IP          string // 网卡的 IPv4 地址，没有时为空
	Fingerprint string // 当前网络的指纹，取不到时为空
}

// fingerprint 由网关的硬件地址和 token 计算得出：配置中不保存原始硬件地址，
// 加入 token 是为了防止通过穷举硬件地址反推出路由器。
func fingerprint(token, mac string) string {
	sum := sha256.Sum256([]byte(token + "|" + strings.ToLower(mac)))
	return hex.EncodeToString(sum[:8])
}

// ProbeNetwork 读取网卡的 IPv4 地址和当前网络的指纹。
// 安卓限制普通程序读取路由和邻居表的方式各不相同，这里统一调用 ip 命令，shell 身份可以执行。
func ProbeNetwork(iface, token string) (NetState, error) {
	var st NetState
	out, err := run("ip", "-4", "-o", "addr", "show", "dev", iface)
	if err != nil {
		return st, err
	}
	// 2: wlan0    inet 192.168.0.104/24 brd ... scope global wlan0
	if f := strings.Fields(out); len(f) >= 4 && f[2] == "inet" {
		st.IP, _, _ = strings.Cut(f[3], "/")
	}
	if st.IP == "" {
		return st, nil
	}
	// 安卓使用策略路由，默认路由不在主路由表中，需要查看全部路由表。
	routes, err := run("ip", "-4", "route", "show", "table", "all")
	if err != nil {
		return st, err
	}
	var gw string
	for _, line := range strings.Split(routes, "\n") {
		f := strings.Fields(line)
		if len(f) >= 5 && f[0] == "default" && f[1] == "via" && slices.Contains(f, "dev") && f[slices.Index(f, "dev")+1] == iface {
			gw = f[2]
			break
		}
	}
	if gw == "" {
		return st, nil
	}
	// 邻居表中可能还没有网关（长时间无通信），先发一个 UDP 包促使系统解析它的硬件地址。
	if c, err := net.DialTimeout("udp4", net.JoinHostPort(gw, "53"), time.Second); err == nil {
		c.Write([]byte{0})
		c.Close()
	}
	for i := 0; i < 3; i++ {
		neigh, err := run("ip", "neigh", "show", gw, "dev", iface)
		if err != nil {
			return st, err
		}
		// 192.168.0.1 lladdr aa:bb:cc:dd:ee:ff REACHABLE
		if f := strings.Fields(neigh); len(f) >= 3 && f[1] == "lladdr" {
			st.Fingerprint = fingerprint(token, f[2])
			return st, nil
		}
		time.Sleep(300 * time.Millisecond)
	}
	return st, nil
}

func run(name string, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, name, args...).Output()
	if err != nil {
		return "", fmt.Errorf("%s %s：%w", name, strings.Join(args, " "), err)
	}
	return string(out), nil
}

// Gate 管理 hub 的所有监听端口（主端口和 WebDAV 等），按网络状态统一开启或关闭。
type Gate struct {
	cfg   NetworkConfig
	token string
	log   *slog.Logger
	probe func() (NetState, error) // 测试时替换

	mu     sync.Mutex
	svcs   []*gateSvc
	bound  string // 当前监听的地址（IP，或 "*" 表示所有地址）；空表示未监听
	reason string // 未监听的原因
}

type gateSvc struct {
	name    string
	port    int
	handler http.Handler
	srv     *http.Server
}

func NewGate(cfg NetworkConfig, token string, log *slog.Logger) *Gate {
	g := &Gate{cfg: cfg, token: token, log: log}
	g.probe = func() (NetState, error) { return ProbeNetwork(cfg.Iface, token) }
	return g
}

// Add 登记一个需要监听的端口。必须在 Run 之前调用。
func (g *Gate) Add(name string, port int, h http.Handler) {
	g.svcs = append(g.svcs, &gateSvc{name: name, port: port, handler: h})
}

// Status 返回当前监听的地址；未监听时返回空字符串和原因。
func (g *Gate) Status() (bound, reason string) {
	if g == nil {
		return "", "尚未启动"
	}
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.bound, g.reason
}

// want 根据网络状态决定应当监听的地址：IP、"*"（所有地址）或空（不监听）及原因。
func (g *Gate) want() (string, string) {
	if g.cfg.Iface == "" {
		return "*", ""
	}
	st, err := g.probe()
	switch {
	case err != nil:
		return "", "无法读取网络状态：" + err.Error()
	case st.IP == "":
		return "", g.cfg.Iface + " 没有 IPv4 地址（未连接 Wi-Fi）"
	case len(g.cfg.Trusted) > 0 && !slices.Contains(g.cfg.Trusted, st.Fingerprint):
		return "", "当前 Wi-Fi 不是可信网络"
	}
	return st.IP, ""
}

// Run 立即检查一次网络并开启监听，之后每隔 interval 检查一次，直到 ctx 结束。
// 首次监听失败（例如端口被占用）时返回错误，hub 拒绝启动。
func (g *Gate) Run(ctx context.Context, interval time.Duration) error {
	if err := g.check(); err != nil {
		return err
	}
	t := time.NewTicker(interval)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			g.close()
			return nil
		case <-t.C:
			if err := g.check(); err != nil {
				g.log.Error("重新监听失败", "err", err)
			}
		}
	}
}

func (g *Gate) check() error {
	addr, reason := g.want()
	g.mu.Lock()
	defer g.mu.Unlock()
	if addr == g.bound {
		g.reason = reason
		return nil
	}
	g.closeLocked()
	g.reason = reason
	if addr == "" {
		g.log.Warn("已停止对外服务", "reason", reason)
		return nil
	}
	host := addr
	if addr == "*" {
		host = ""
	}
	for _, s := range g.svcs {
		ln, err := net.Listen("tcp", net.JoinHostPort(host, fmt.Sprint(s.port)))
		if err != nil {
			g.closeLocked()
			return fmt.Errorf("%s 端口 %d 监听失败：%w", s.name, s.port, err)
		}
		// http.Server 关闭后不能再次使用，每次重新监听都新建一个。
		s.srv = &http.Server{Handler: s.handler, ReadHeaderTimeout: 10 * time.Second}
		go func(s *gateSvc, srv *http.Server) {
			if err := srv.Serve(ln); err != nil && !errors.Is(err, http.ErrServerClosed) {
				g.log.Error("服务异常退出", "service", s.name, "err", err)
			}
		}(s, s.srv)
	}
	g.bound = addr
	g.log.Info("开始对外服务", "addr", addr)
	return nil
}

func (g *Gate) close() {
	g.mu.Lock()
	defer g.mu.Unlock()
	g.closeLocked()
}

// closeLocked 关闭全部监听和现有连接（离开可信网络时不应保留任何连接）。
func (g *Gate) closeLocked() {
	for _, s := range g.svcs {
		if s.srv != nil {
			s.srv.Close()
			s.srv = nil
		}
	}
	g.bound = ""
}

// TrustCurrentNetwork 把当前 Wi-Fi 的指纹加入配置文件的 network.trusted（保留注释，旧文件备份为 .bak）。
// 返回指纹，以及它是否原本就在列表中。
func TrustCurrentNetwork(path string, cfg *Config) (string, bool, error) {
	if cfg.Network.Iface == "" {
		return "", false, errors.New("配置中没有 network.iface（例如 wlan0）")
	}
	st, err := ProbeNetwork(cfg.Network.Iface, cfg.Token)
	if err != nil {
		return "", false, err
	}
	if st.Fingerprint == "" {
		return "", false, fmt.Errorf("%s 未连接 Wi-Fi，或读不到网关", cfg.Network.Iface)
	}
	if slices.Contains(cfg.Network.Trusted, st.Fingerprint) {
		return st.Fingerprint, true, nil
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return "", false, err
	}
	var doc yaml.Node
	if err := yaml.Unmarshal(raw, &doc); err != nil || len(doc.Content) == 0 {
		return "", false, fmt.Errorf("配置格式错误：%v", err)
	}
	root := doc.Content[0]
	network := mapChild(root, "network")
	trusted := mapChild(network, "trusted")
	trusted.Kind, trusted.Tag = yaml.SequenceNode, "!!seq"
	trusted.Content = append(trusted.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: st.Fingerprint})
	var buf bytes.Buffer
	enc := yaml.NewEncoder(&buf)
	enc.SetIndent(2)
	if err := enc.Encode(&doc); err != nil {
		return "", false, err
	}
	if err := os.WriteFile(path+".bak", raw, 0o600); err != nil {
		return "", false, err
	}
	return st.Fingerprint, false, os.WriteFile(path, buf.Bytes(), 0o600)
}

// mapChild 返回映射节点中某个键的值节点，不存在时新建一个空映射。
func mapChild(m *yaml.Node, key string) *yaml.Node {
	for i := 0; i+1 < len(m.Content); i += 2 {
		if m.Content[i].Value == key {
			return m.Content[i+1]
		}
	}
	v := &yaml.Node{Kind: yaml.MappingNode, Tag: "!!map"}
	m.Content = append(m.Content, &yaml.Node{Kind: yaml.ScalarNode, Tag: "!!str", Value: key}, v)
	return v
}
