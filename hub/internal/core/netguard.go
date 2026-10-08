package core

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"regexp"
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
//
// 以 shell 身份（ADB 启动）运行时调用 ip 命令：地址取自网卡，指纹取自网关的硬件地址。
// 以普通应用身份（Termux）运行时，安卓禁止应用使用 netlink（ip 命令报 Permission denied），
// 改用 probeAsApp：地址由系统路由选择得出，指纹取自路由器的 UPnP 设备标识。
func ProbeNetwork(iface, token string) (NetState, error) {
	var st NetState
	// 普通应用身份（uid ≥ 10000，如 Termux）下不能调用外部程序查找：安卓应用的 seccomp 过滤
	// 不允许 faccessat2，Go 的 exec.LookPath 会因此收到 SIGSYS 而直接退出（2026-10-02 实测）。
	if os.Getuid() >= 10000 {
		return probeAsApp(token)
	}
	out, err := run("ip", "-4", "-o", "addr", "show", "dev", iface)
	if err != nil {
		return probeAsApp(token)
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
	// missing 是连续取不到网络指纹的次数。手机上读取路由器 UPnP 标识偶尔超时（约每小时一次，下一次即恢复），
	// 取不到不等于换了网络：IP 未变时继续服务，连续 3 次（约 45 秒）取不到才停止。
	missing int
	// gen 每次开始或停止监听时加一。服务退出时若 gen 未变，说明监听器是被意外作废的而非 hub 主动关闭：
	// 安卓在应用进入后台受限、网络变化时会销毁应用的套接字，accept 返回 EINVAL（2026-10-08 手机上实测），
	// 此时进程仍在运行却不再监听。broken 置位后，下一次检查（15 秒内）重新监听。
	gen    int
	broken bool
}

type gateSvc struct {
	name    string
	port    int
	handler http.Handler
	srv     *http.Server
	// raw 不为空时为普通 TCP 服务（如 SSH）：监听后交给 raw 处理
	raw func(net.Listener)
	ln  net.Listener // 当前的监听器（HTTP 与普通 TCP 服务都保存），关闭时直接关闭
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

// AddRaw 登记一个普通 TCP 服务（如 SSH），与 HTTP 端口一样按网络状态开启或关闭。
// serve 在每次开始监听时以新的监听器调用，监听器关闭时应返回。必须在 Run 之前调用。
func (g *Gate) AddRaw(name string, port int, serve func(net.Listener)) {
	g.svcs = append(g.svcs, &gateSvc{name: name, port: port, raw: serve})
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
	case len(g.cfg.Trusted) > 0 && st.Fingerprint == "" && st.IP == g.currentIP() && g.missing < 2:
		g.missing++
		return st.IP, "" // 指纹暂时取不到，IP 未变：继续服务
	case len(g.cfg.Trusted) > 0 && !slices.Contains(g.cfg.Trusted, st.Fingerprint):
		g.missing = 0
		if st.Fingerprint == "" {
			return "", "连续多次取不到网络指纹（路由器 UPnP 无响应）"
		}
		return "", "当前 Wi-Fi 不是可信网络"
	}
	g.missing = 0
	return st.IP, ""
}

func (g *Gate) currentIP() string {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.bound
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
	if addr == g.bound && !g.broken {
		g.reason = reason
		return nil
	}
	if g.broken {
		g.log.Warn("监听被系统意外关闭，重新监听")
		g.broken = false
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
		// 保存监听器并在关闭时直接关闭它：http.Server.Close 只关闭 Serve 已接手的监听器，
		// 开始监听后马上关闭时 Serve 可能尚未运行，监听器会遗留下来（单元测试中约四成概率复现）
		s.ln = ln
		gen := g.gen
		if s.raw != nil {
			go func(s *gateSvc) {
				s.raw(ln)
				g.serviceExited(gen, s.name, nil)
			}(s)
			continue
		}
		// http.Server 关闭后不能再次使用，每次重新监听都新建一个。
		s.srv = &http.Server{Handler: s.handler, ReadHeaderTimeout: 10 * time.Second}
		go func(s *gateSvc, srv *http.Server) {
			err := srv.Serve(ln)
			if errors.Is(err, http.ErrServerClosed) {
				err = nil
			}
			g.serviceExited(gen, s.name, err)
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

// serviceExited 在某个服务的监听结束时调用；不是 hub 主动关闭的（gen 未变）则标记为需要重新监听。
func (g *Gate) serviceExited(gen int, name string, err error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if gen != g.gen {
		return
	}
	g.log.Error("服务异常退出", "service", name, "err", err)
	g.broken = true
}

// closeLocked 关闭全部监听和现有连接（离开可信网络时不应保留任何连接）。
func (g *Gate) closeLocked() {
	g.gen++
	for _, s := range g.svcs {
		if s.srv != nil {
			s.srv.Close()
			s.srv = nil
		}
		if s.ln != nil {
			s.ln.Close()
			s.ln = nil
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

// probeAsApp 是普通应用身份下的网络检查，不需要任何特殊权限：
//   - 地址：向公网地址建立 UDP「连接」（不发送数据），系统按路由选出的本机地址即当前网络的地址；
//     只接受私有地址（局域网），移动数据网络的公网地址视为未连接 Wi-Fi；
//   - 指纹：用 SSDP 查找局域网中的路由器（InternetGatewayDevice），读取其描述文件中的 UDN
//     （每台设备唯一的 uuid）。路由器关闭 UPnP 时取不到指纹，hub 不会对外服务。
func probeAsApp(token string) (NetState, error) {
	var st NetState
	c, err := net.Dial("udp4", "223.5.5.5:53")
	if err != nil {
		return st, nil // 没有可用网络
	}
	ip := c.LocalAddr().(*net.UDPAddr).IP
	c.Close()
	if !ip.IsPrivate() {
		return st, nil
	}
	st.IP = ip.String()
	if udn := gatewayUDN(ip, 2*time.Second); udn != "" {
		st.Fingerprint = fingerprint(token, "upnp:"+udn)
	}
	return st, nil
}

// gatewayUDN 用 SSDP 查找与 local 同网段的路由器，返回其 UDN。
func gatewayUDN(local net.IP, wait time.Duration) string {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: local})
	if err != nil {
		return ""
	}
	defer conn.Close()
	msg := "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\n" +
		"ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"
	if _, err := conn.WriteToUDP([]byte(msg), &net.UDPAddr{IP: net.IPv4(239, 255, 255, 250), Port: 1900}); err != nil {
		return ""
	}
	conn.SetReadDeadline(time.Now().Add(wait))
	buf := make([]byte, 2048)
	for {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			return ""
		}
		loc := ""
		for _, line := range strings.Split(string(buf[:n]), "\r\n") {
			if k, v, ok := strings.Cut(line, ":"); ok && strings.EqualFold(strings.TrimSpace(k), "location") {
				loc = strings.TrimSpace(v)
			}
		}
		// 只读取回复者自己的描述地址，并且必须在同一网段，避免被引导访问其他主机
		u, err := url.Parse(loc)
		if err != nil || u.Hostname() != from.IP.String() || !sameSubnet24(from.IP, local) {
			continue
		}
		if udn := fetchUDN(loc); udn != "" {
			return udn
		}
	}
}

func sameSubnet24(a, b net.IP) bool {
	a4, b4 := a.To4(), b.To4()
	return a4 != nil && b4 != nil && a4[0] == b4[0] && a4[1] == b4[1] && a4[2] == b4[2]
}

var udnRe = regexp.MustCompile(`<UDN>\s*(uuid:[^<\s]+)\s*</UDN>`)

func fetchUDN(loc string) string {
	cl := &http.Client{Timeout: 2 * time.Second}
	resp, err := cl.Get(loc)
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if m := udnRe.FindSubmatch(body); m != nil {
		return strings.ToLower(string(m[1]))
	}
	return ""
}
