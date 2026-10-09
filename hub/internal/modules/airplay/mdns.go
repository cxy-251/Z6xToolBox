package airplay

// 局域网广播（mDNS / DNS-SD）：让苹果设备在「音频输出」中发现这个音箱。
// 只实现所需的最小部分：加入 224.0.0.251:5353 组播，回应对 _raop._tcp 的查询，
// 并在启动时与之后每隔一段时间主动通告一次（设备休眠时可能收不到查询，主动通告让对方的缓存保持有效）。
// 不查询网卡（手机 Termux 中 netlink 被禁止），本机地址用「向组播地址建 UDP 连接」得到的源地址。

import (
	"context"
	"net"
	"strings"
	"sync"
	"syscall"
	"time"

	"golang.org/x/net/dns/dnsmessage"
	"golang.org/x/net/ipv4"
)

var mdnsGroup = &net.UDPAddr{IP: net.IPv4(224, 0, 0, 251), Port: 5353}

const (
	// mdnsTTL：记录的有效期（秒）。用 DNS-SD 对服务记录推荐的 75 分钟：手机息屏后 CPU 休眠，既收不到查询
	// 也发不出通告，有效期短（原为 2 分钟）时苹果设备很快把音箱从列表中去掉；有效期长则仍会显示，
	// 选中后对方直接连接，网络数据会唤醒手机（2026-10-09 实测：息屏后搜不到）。
	mdnsTTL      = 4500
	announceGap  = 60 * time.Second // 主动通告的间隔
	serviceType  = "_raop._tcp.local."
	servicesEnum = "_services._dns-sd._udp.local."
)

type responder struct {
	instance string // 如 0A1B2C3D4E5F@手机.local 中 @ 前为设备标识
	host     string // 如 z6x-手机.local.
	port     int
	txt      []string
	log      func(msg string, args ...any)

	mu   sync.Mutex
	conn *net.UDPConn
	pc   *ipv4.PacketConn
	stop chan struct{}
}

// localIP 返回本机在局域网中的 IPv4 地址（不发送数据，只让系统选择路由）。
func localIP() net.IP {
	c, err := net.Dial("udp4", mdnsGroup.String())
	if err != nil {
		return nil
	}
	defer c.Close()
	return c.LocalAddr().(*net.UDPAddr).IP.To4()
}

func (r *responder) start() error {
	lc := net.ListenConfig{Control: func(_, _ string, c syscall.RawConn) error {
		var err error
		c.Control(func(fd uintptr) {
			// 系统或其他程序可能也在监听 5353：允许共用
			syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, syscall.SO_REUSEADDR, 1)
			err = syscall.SetsockoptInt(int(fd), syscall.SOL_SOCKET, 0xf /* SO_REUSEPORT */, 1)
		})
		return err
	}}
	pconn, err := lc.ListenPacket(context.Background(), "udp4", "0.0.0.0:5353")
	if err != nil {
		return err
	}
	conn := pconn.(*net.UDPConn)
	pc := ipv4.NewPacketConn(conn)
	if err := pc.JoinGroup(nil, mdnsGroup); err != nil {
		conn.Close()
		return err
	}
	pc.SetMulticastTTL(255)
	pc.SetMulticastLoopback(true)
	r.mu.Lock()
	r.conn, r.pc, r.stop = conn, pc, make(chan struct{})
	r.mu.Unlock()
	go r.serve(conn)
	go r.announceLoop()
	return nil
}

func (r *responder) close() {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.conn == nil {
		return
	}
	r.send(0) // 告别：有效期为 0，对方立即从列表中移除
	close(r.stop)
	r.conn.Close()
	r.conn = nil
}

func (r *responder) announceLoop() {
	r.mu.Lock()
	stop := r.stop
	r.mu.Unlock()
	for i := 0; ; i++ {
		r.mu.Lock()
		if r.conn != nil {
			r.send(mdnsTTL)
		}
		r.mu.Unlock()
		wait := announceGap
		if i < 2 {
			wait = time.Second // 启动时连发三次，按 mDNS 的惯例
		}
		select {
		case <-stop:
			return
		case <-time.After(wait):
		}
	}
}

func (r *responder) serve(conn *net.UDPConn) {
	buf := make([]byte, 9000)
	for {
		n, _, err := conn.ReadFromUDP(buf)
		if err != nil {
			return
		}
		var p dnsmessage.Parser
		h, err := p.Start(buf[:n])
		if err != nil || h.Response {
			continue
		}
		qs, err := p.AllQuestions()
		if err != nil {
			continue
		}
		for _, q := range qs {
			name := strings.ToLower(q.Name.String())
			if name == serviceType || name == servicesEnum || name == strings.ToLower(r.instanceName()) || name == strings.ToLower(r.host) {
				r.mu.Lock()
				if r.conn != nil {
					r.send(mdnsTTL)
				}
				r.mu.Unlock()
				break
			}
		}
	}
}

func (r *responder) instanceName() string { return r.instance + "." + serviceType }

// send 以组播发送完整的记录（PTR、SRV、TXT、A）。调用时持有 r.mu。
func (r *responder) send(ttl uint32) {
	ip := localIP()
	if ip == nil {
		return
	}
	b := dnsmessage.NewBuilder(nil, dnsmessage.Header{Response: true, Authoritative: true})
	b.EnableCompression()
	b.StartAnswers()
	svc, _ := dnsmessage.NewName(serviceType)
	inst, err := dnsmessage.NewName(r.instanceName())
	if err != nil {
		r.log("音箱名称不能用于局域网广播", "err", err)
		return
	}
	host, _ := dnsmessage.NewName(r.host)
	enum, _ := dnsmessage.NewName(servicesEnum)
	hdr := func(n dnsmessage.Name, t dnsmessage.Type, flush bool) dnsmessage.ResourceHeader {
		class := dnsmessage.ClassINET
		if flush {
			class |= 1 << 15 // cache-flush：本机独有的记录
		}
		return dnsmessage.ResourceHeader{Name: n, Type: t, Class: class, TTL: ttl}
	}
	b.PTRResource(hdr(enum, dnsmessage.TypePTR, false), dnsmessage.PTRResource{PTR: svc})
	b.PTRResource(hdr(svc, dnsmessage.TypePTR, false), dnsmessage.PTRResource{PTR: inst})
	b.SRVResource(hdr(inst, dnsmessage.TypeSRV, true), dnsmessage.SRVResource{Port: uint16(r.port), Target: host})
	b.TXTResource(hdr(inst, dnsmessage.TypeTXT, true), dnsmessage.TXTResource{TXT: r.txt})
	var a [4]byte
	copy(a[:], ip)
	b.AResource(hdr(host, dnsmessage.TypeA, true), dnsmessage.AResource{A: a})
	msg, err := b.Finish()
	if err != nil {
		r.log("局域网广播报文生成失败", "err", err)
		return
	}
	r.conn.WriteToUDP(msg, mdnsGroup)
}

// txtRecords 是 RAOP 服务的属性（与 shairport-sync 相同的取值）：PCM 与 ALAC、RSA 加密、16 位 44.1kHz 立体声、无密码。
func txtRecords() []string {
	return []string{"txtvers=1", "ch=2", "cn=0,1", "et=0,1", "ek=1", "sv=false", "sm=false", "tp=UDP",
		"md=0,2", "ss=16", "sr=44100", "pw=false", "vn=3", "da=true", "am=z6x-hub", "vs=105.1"}
}
