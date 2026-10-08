package airplay

import (
	"bytes"
	"crypto"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"net"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func testKey(t *testing.T) *rsa.PrivateKey {
	der, _ := base64.StdEncoding.DecodeString(airportKeyDER)
	k, err := x509.ParsePKCS1PrivateKey(der)
	if err != nil {
		t.Fatal(err)
	}
	return k
}

func TestChallengeAndAnnounce(t *testing.T) {
	k := testKey(t)
	ch := base64.StdEncoding.EncodeToString([]byte("0123456789abcdef"))
	resp, err := challengeResponse(k, ch, net.IPv4(192, 168, 0, 104), []byte{2, 1, 2, 3, 4, 5})
	if err != nil {
		t.Fatal(err)
	}
	sig, err := base64.RawStdEncoding.DecodeString(resp)
	if err != nil {
		t.Fatal(err)
	}
	want := append([]byte("0123456789abcdef"), 192, 168, 0, 104, 2, 1, 2, 3, 4, 5)
	for len(want) < 32 {
		want = append(want, 0)
	}
	if err := rsa.VerifyPKCS1v15(&k.PublicKey, crypto.Hash(0), want, sig); err != nil {
		t.Fatalf("签名不能用公钥验证：%v", err)
	}
	aesKey := []byte("fedcba9876543210")
	ct, _ := rsa.EncryptOAEP(sha1.New(), rand.Reader, &k.PublicKey, aesKey, nil)
	sdp := "v=0\r\nm=audio 0 RTP/AVP 96\r\na=rtpmap:96 AppleLossless\r\na=fmtp:96 352 0 16 40 10 14 2 255 0 0 44100\r\n" +
		"a=rsaaeskey:" + strings.TrimRight(base64.StdEncoding.EncodeToString(ct), "=") + "\r\na=aesiv:" + base64.StdEncoding.EncodeToString([]byte("0000111122223333")) + "\r\n"
	a, err := parseAnnounce(k, sdp)
	if err != nil || !bytes.Equal(a.aesKey, aesKey) || a.codec != "AppleLossless" {
		t.Fatalf("ANNOUNCE 解析不正确：%+v %v", a, err)
	}
	if db, ok := parseVolume("volume: -12.5\r\n"); !ok || db != -12.5 {
		t.Errorf("音量解析不正确")
	}
	if c, tm := parseTransport("RTP/AVP/UDP;unicast;interleaved=0-1;mode=record;control_port=6001;timing_port=6002"); c != 6001 || tm != 6002 {
		t.Errorf("Transport 解析不正确")
	}
}

// 加密的 PCM（L16）包经 UDP 送到 stream，解密、排序后写入播放器（这里用 cat 写入文件）
func TestStreamDecryptAndReorder(t *testing.T) {
	key, iv := []byte("fedcba9876543210"), []byte("0000111122223333")
	st, err := newStream(&announce{codec: "L16", aesKey: key, aesIV: iv}, t.Logf)
	if err != nil {
		t.Fatal(err)
	}
	out := filepath.Join(t.TempDir(), "pcm")
	if err := st.startPlayer([]string{"/bin/sh", "-c", "cat > " + out}); err != nil {
		t.Fatal(err)
	}
	block, _ := aes.NewCipher(key)
	mk := func(seq uint16, fill byte) []byte {
		pcmBE := bytes.Repeat([]byte{fill, 0}, 35) // 70 字节：64 字节加密，6 字节不加密
		enc := append([]byte{}, pcmBE...)
		cipher.NewCBCEncrypter(block, iv).CryptBlocks(enc[:64], enc[:64])
		h := make([]byte, 12)
		h[0], h[1] = 0x80, 0x60
		binary.BigEndian.PutUint16(h[2:], seq)
		return append(h, enc...)
	}
	c, _ := net.DialUDP("udp4", nil, st.audio.LocalAddr().(*net.UDPAddr))
	for _, p := range []struct {
		seq  uint16
		fill byte
	}{{100, 1}, {102, 3}, {101, 2}} { // 102 先于 101 到达
		c.Write(mk(p.seq, p.fill))
		time.Sleep(20 * time.Millisecond)
	}
	time.Sleep(100 * time.Millisecond)
	st.close()
	time.Sleep(100 * time.Millisecond)
	got, _ := os.ReadFile(out)
	if len(got) != 3*70 {
		t.Fatalf("应输出 3 个包共 210 字节：%d", len(got))
	}
	// 大端 PCM 换为小端后，每个包的第一个采样依次为 1、2、3
	for i, want := range []byte{1, 2, 3} {
		if got[i*70] != 0 || got[i*70+1] != want {
			t.Errorf("第 %d 个包内容不正确（排序或解密有误）：% x", i, got[i*70:i*70+4])
		}
	}
}

func TestPushFillsLostPacket(t *testing.T) {
	var buf bytes.Buffer
	st := &stream{pending: map[uint16][]byte{}, out: nopCloser{&buf}}
	st.push(10, []byte{1})
	for i := uint16(12); i < 12+reorderWindow+1; i++ { // 11 一直没到
		st.push(i, []byte{2})
	}
	if st.lost.Load() != 1 || buf.Len() != 1+framesPerPacket*bytesPerFrame+reorderWindow+1 {
		t.Fatalf("丢包应以一包静音代替：丢 %d，输出 %d 字节", st.lost.Load(), buf.Len())
	}
	st.push(11, []byte{9}) // 迟到的包被丢弃
	if bytes.Contains(buf.Bytes(), []byte{9}) {
		t.Error("迟到的包不应写入")
	}
}

type nopCloser struct{ *bytes.Buffer }

func (nopCloser) Close() error { return nil }
