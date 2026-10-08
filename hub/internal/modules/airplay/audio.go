package airplay

// 音频：从 UDP 收 RTP 包（类型 0x60，重传为 0x56 且前面多 4 字节），按包序号排好，
// AES-128-CBC 解密（只解密 16 字节整数倍的部分，剩余字节原样保留，与 shairport-sync 相同），
// ALAC 解码为 16 位 44.1kHz 立体声 PCM，按音量缩放后写入播放器（Termux 的 pacat → PulseAudio → 安卓音频）。

import (
	"crypto/aes"
	"crypto/cipher"
	"encoding/binary"
	"io"
	"math"
	"net"
	"os/exec"
	"sync"
	"sync/atomic"

	"github.com/alicebob/alac"
)

const (
	framesPerPacket = 352 // ALAC 每包的采样数（AirPlay 1 固定值）
	bytesPerFrame   = 4   // 16 位立体声
	// reorderWindow：乱序与丢包的等待窗口（包数，约 0.4 秒）；超过仍缺的包以静音代替
	reorderWindow = 50
)

type stream struct {
	ann     *announce
	dec     *alac.Alac
	block   cipher.Block
	audio   *net.UDPConn // 音频
	control *net.UDPConn // 同步与重传（只接收，不处理）
	timing  *net.UDPConn // 时间同步（只接收，不处理）
	volume  atomic.Uint64
	player  *exec.Cmd
	out     io.WriteCloser
	log     func(msg string, args ...any)

	mu       sync.Mutex
	pending  map[uint16][]byte
	next     uint16
	started  bool
	closed   bool
	packets  atomic.Int64
	lost     atomic.Int64
	lastFlag string
}

func newStream(ann *announce, log func(string, ...any)) (*stream, error) {
	s := &stream{ann: ann, pending: map[uint16][]byte{}, log: log}
	s.setVolume(0)
	var err error
	if ann.codec == "AppleLossless" {
		if s.dec, err = alac.New(); err != nil {
			return nil, err
		}
	}
	if ann.aesKey != nil {
		if s.block, err = aes.NewCipher(ann.aesKey); err != nil {
			return nil, err
		}
	}
	for _, c := range []**net.UDPConn{&s.audio, &s.control, &s.timing} {
		if *c, err = net.ListenUDP("udp4", &net.UDPAddr{}); err != nil {
			s.close()
			return nil, err
		}
	}
	go s.receive()
	go drain(s.control)
	go drain(s.timing)
	return s, nil
}

func drain(c *net.UDPConn) {
	buf := make([]byte, 2048)
	for {
		if _, _, err := c.ReadFromUDP(buf); err != nil {
			return
		}
	}
}

func port(c *net.UDPConn) int { return c.LocalAddr().(*net.UDPAddr).Port }

// setVolume 设置音量（dB）：-144 为静音，-30～0 映射为线性倍数。
func (s *stream) setVolume(db float64) {
	g := 0.0
	if db > -144 {
		g = math.Pow(10, db/20)
	}
	s.volume.Store(math.Float64bits(g))
}

// startPlayer 启动播放器进程（cmd 为完整路径的参数数组），之后的 PCM 写入其标准输入。
func (s *stream) startPlayer(cmd []string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.player != nil || s.closed {
		return nil
	}
	p := exec.Command(cmd[0], cmd[1:]...)
	in, err := p.StdinPipe()
	if err != nil {
		return err
	}
	if err := p.Start(); err != nil {
		return err
	}
	s.player, s.out = p, in
	go p.Wait()
	return nil
}

// flush 清空排队中的包（拖动进度或暂停后），下一个到达的包作为新的起点。
func (s *stream) flush() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pending = map[uint16][]byte{}
	s.started = false
}

func (s *stream) close() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed {
		return
	}
	s.closed = true
	for _, c := range []*net.UDPConn{s.audio, s.control, s.timing} {
		if c != nil {
			c.Close()
		}
	}
	if s.out != nil {
		s.out.Close()
	}
	if s.player != nil && s.player.Process != nil {
		s.player.Process.Kill()
	}
}

func (s *stream) receive() {
	buf := make([]byte, 4096)
	for {
		n, _, err := s.audio.ReadFromUDP(buf)
		if err != nil {
			return
		}
		pkt := buf[:n]
		if n < 12 {
			continue
		}
		typ := pkt[1] &^ 0x80
		if typ == 0x56 { // 重传：前面多 4 字节
			pkt = pkt[4:]
			typ = 0x60
		}
		if typ != 0x60 || len(pkt) < 12 {
			continue
		}
		seq := binary.BigEndian.Uint16(pkt[2:4])
		pcm := s.decode(pkt[12:])
		if pcm == nil {
			continue
		}
		s.packets.Add(1)
		s.push(seq, pcm)
	}
}

// decode 解密并解码一个音频包，返回 PCM（按音量缩放后）。
func (s *stream) decode(payload []byte) []byte {
	data := append([]byte{}, payload...)
	if s.block != nil {
		n := len(data) &^ 0xf
		if n > 0 {
			cipher.NewCBCDecrypter(s.block, s.ann.aesIV).CryptBlocks(data[:n], data[:n])
		}
	}
	var pcm []byte
	if s.dec != nil {
		pcm = s.dec.Decode(data)
	} else { // L16：大端 PCM，换为小端
		pcm = make([]byte, len(data)&^1)
		for i := 0; i+1 < len(data); i += 2 {
			pcm[i], pcm[i+1] = data[i+1], data[i]
		}
	}
	if len(pcm) == 0 {
		return nil
	}
	g := math.Float64frombits(s.volume.Load())
	if g != 1 {
		for i := 0; i+1 < len(pcm); i += 2 {
			v := float64(int16(binary.LittleEndian.Uint16(pcm[i:]))) * g
			binary.LittleEndian.PutUint16(pcm[i:], uint16(int16(max(-32768, min(32767, v)))))
		}
	}
	return pcm
}

// push 按包序号排序后写给播放器：缺包时等待最多 reorderWindow 个包，仍缺则以静音代替。
func (s *stream) push(seq uint16, pcm []byte) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || s.out == nil {
		return
	}
	if !s.started {
		s.started, s.next = true, seq
	}
	if int16(seq-s.next) < 0 { // 迟到的包（已用静音代替）
		return
	}
	s.pending[seq] = pcm
	for {
		if p, ok := s.pending[s.next]; ok {
			delete(s.pending, s.next)
			s.out.Write(p)
			s.next++
			continue
		}
		if len(s.pending) > reorderWindow { // 等太久：这个包丢了
			s.out.Write(make([]byte, framesPerPacket*bytesPerFrame))
			s.lost.Add(1)
			s.next++
			continue
		}
		break
	}
}
