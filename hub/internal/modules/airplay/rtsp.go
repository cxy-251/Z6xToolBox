package airplay

// RTSP 会话：苹果设备连接后依次发送 OPTIONS（带身份校验）、ANNOUNCE（音频格式与加密的 AES 密钥）、
// SETUP（交换 UDP 端口）、RECORD（开始）、SET_PARAMETER（音量）、FLUSH（拖动进度）、TEARDOWN（结束）。

import (
	"bufio"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"strconv"
	"strings"
)

type rtspRequest struct {
	method, uri string
	headers     map[string]string
	body        []byte
}

func readRequest(br *bufio.Reader) (*rtspRequest, error) {
	line, err := br.ReadString('\n')
	if err != nil {
		return nil, err
	}
	parts := strings.Fields(line)
	if len(parts) < 2 {
		return nil, fmt.Errorf("请求行格式错误：%q", line)
	}
	req := &rtspRequest{method: parts[0], uri: parts[1], headers: map[string]string{}}
	for {
		l, err := br.ReadString('\n')
		if err != nil {
			return nil, err
		}
		l = strings.TrimRight(l, "\r\n")
		if l == "" {
			break
		}
		if k, v, ok := strings.Cut(l, ":"); ok {
			req.headers[strings.ToLower(strings.TrimSpace(k))] = strings.TrimSpace(v)
		}
	}
	if n, _ := strconv.Atoi(req.headers["content-length"]); n > 0 {
		if n > maxRTSPBody {
			return nil, fmt.Errorf("请求体过大：%d 字节", n)
		}
		req.body = make([]byte, n)
		if _, err := io.ReadFull(br, req.body); err != nil {
			return nil, err
		}
	}
	return req, nil
}

func writeResponse(w io.Writer, cseq string, headers [][2]string) error {
	var b strings.Builder
	b.WriteString("RTSP/1.0 200 OK\r\nCSeq: " + cseq + "\r\nServer: AirTunes/105.1\r\nAudio-Jack-Status: connected; type=analog\r\n")
	for _, h := range headers {
		b.WriteString(h[0] + ": " + h[1] + "\r\n")
	}
	b.WriteString("\r\n")
	_, err := io.WriteString(w, b.String())
	return err
}

// challengeResponse：挑战值 + 本机 IP + 6 字节设备标识，不足 32 字节补零，用私钥做 PKCS#1 签名（不做哈希），
// base64 去掉末尾的等号（与 shairport-sync 的 apple_challenge 相同）。
func challengeResponse(key *rsa.PrivateKey, challenge string, local net.IP, hwid []byte) (string, error) {
	ch, err := decodeB64(challenge)
	if err != nil || len(ch) > 16 {
		return "", fmt.Errorf("Apple-Challenge 格式错误")
	}
	buf := append([]byte{}, ch...)
	if ip4 := local.To4(); ip4 != nil {
		buf = append(buf, ip4...)
	} else {
		buf = append(buf, local.To16()...)
	}
	buf = append(buf, hwid...)
	for len(buf) < 32 {
		buf = append(buf, 0)
	}
	sig, err := rsa.SignPKCS1v15(nil, key, crypto.Hash(0), buf)
	if err != nil {
		return "", err
	}
	return strings.TrimRight(base64.StdEncoding.EncodeToString(sig), "="), nil
}

// decodeB64 兼容有无末尾等号的 base64。
func decodeB64(s string) ([]byte, error) {
	s = strings.TrimSpace(s)
	if b, err := base64.StdEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	return base64.RawStdEncoding.DecodeString(strings.TrimRight(s, "="))
}

// announce 是 ANNOUNCE 中 SDP 的内容。
type announce struct {
	aesKey, aesIV []byte // 未加密的流为空
	codec         string // AppleLossless 或 L16
	fmtp          string
}

func parseAnnounce(key *rsa.PrivateKey, sdp string) (*announce, error) {
	a := &announce{}
	var rsaKey, iv string
	for _, l := range strings.Split(sdp, "\n") {
		l = strings.TrimSpace(l)
		switch {
		case strings.HasPrefix(l, "a=rsaaeskey:"):
			rsaKey = strings.TrimPrefix(l, "a=rsaaeskey:")
		case strings.HasPrefix(l, "a=aesiv:"):
			iv = strings.TrimPrefix(l, "a=aesiv:")
		case strings.HasPrefix(l, "a=rtpmap:"):
			f := strings.Fields(strings.TrimPrefix(l, "a=rtpmap:"))
			if len(f) > 1 {
				a.codec, _, _ = strings.Cut(f[1], "/")
			}
		case strings.HasPrefix(l, "a=fmtp:"):
			a.fmtp = strings.TrimPrefix(l, "a=fmtp:")
		}
	}
	if a.codec != "AppleLossless" && a.codec != "L16" {
		return nil, fmt.Errorf("不支持的音频格式：%q（只支持 ALAC 与 PCM）", a.codec)
	}
	if rsaKey != "" {
		ct, err := decodeB64(rsaKey)
		if err != nil {
			return nil, fmt.Errorf("rsaaeskey 格式错误")
		}
		if a.aesKey, err = rsa.DecryptOAEP(sha1.New(), rand.Reader, key, ct, nil); err != nil {
			return nil, fmt.Errorf("解不开 AES 密钥：%w", err)
		}
		if a.aesIV, err = decodeB64(iv); err != nil || len(a.aesIV) != 16 {
			return nil, fmt.Errorf("aesiv 格式错误")
		}
	}
	return a, nil
}

// parseVolume 解析 SET_PARAMETER 的「volume: -20.5」：-144 为静音，-30～0 dB 映射为线性倍数。
func parseVolume(body string) (float64, bool) {
	for _, l := range strings.Split(body, "\n") {
		if v, ok := strings.CutPrefix(strings.TrimSpace(l), "volume:"); ok {
			db, err := strconv.ParseFloat(strings.TrimSpace(v), 64)
			if err != nil {
				return 0, false
			}
			return db, true
		}
	}
	return 0, false
}

// parseTransport 从 SETUP 的 Transport 头中取出对方的 control_port 与 timing_port。
func parseTransport(t string) (control, timing int) {
	for _, p := range strings.Split(t, ";") {
		k, v, _ := strings.Cut(p, "=")
		n, _ := strconv.Atoi(v)
		switch strings.TrimSpace(k) {
		case "control_port":
			control = n
		case "timing_port":
			timing = n
		}
	}
	return
}
