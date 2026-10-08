package library

// 音乐与音声的封面：直接读取 mp3 的 ID3v2 标签中内嵌的图片（APIC / PIC 帧），原样返回，
// 不解码、不缩放，也不需要 ffmpeg。手机上的音乐基本都带内嵌封面（2026-10-08 抽查 40 首全部带）。

import (
	"bytes"
	"crypto/sha1"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"image"
	"image/jpeg"
	_ "image/png"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"z6x/hub/internal/core"
)

// readID3Picture 返回 ID3v2 标签中的第一张图片及其 MIME 类型。
func readID3Picture(r io.Reader) ([]byte, string, error) {
	var hdr [10]byte
	if _, err := io.ReadFull(r, hdr[:]); err != nil || string(hdr[:3]) != "ID3" {
		return nil, "", errors.New("没有 ID3v2 标签")
	}
	ver, flags := hdr[3], hdr[5]
	size := syncsafe(hdr[6:10])
	if flags&0x80 != 0 || size > 16<<20 {
		return nil, "", errors.New("不支持的 ID3 标签") // 整体不同步化（很少见）或大小异常
	}
	tag := make([]byte, size)
	if _, err := io.ReadFull(r, tag); err != nil {
		return nil, "", err
	}
	if ver >= 3 && flags&0x40 != 0 && len(tag) >= 4 { // 扩展头
		n := int(binary.BigEndian.Uint32(tag))
		if ver == 4 {
			n = syncsafe(tag[:4])
		} else {
			n += 4
		}
		if n > len(tag) {
			return nil, "", errors.New("扩展头大小异常")
		}
		tag = tag[n:]
	}
	for len(tag) > 0 {
		var id string
		var body []byte
		if ver == 2 {
			if len(tag) < 6 || tag[0] == 0 {
				break
			}
			id = string(tag[:3])
			n := int(tag[3])<<16 | int(tag[4])<<8 | int(tag[5])
			if 6+n > len(tag) {
				break
			}
			body, tag = tag[6:6+n], tag[6+n:]
		} else {
			if len(tag) < 10 || tag[0] == 0 {
				break
			}
			id = string(tag[:4])
			n := int(binary.BigEndian.Uint32(tag[4:8]))
			if ver == 4 {
				n = syncsafe(tag[4:8])
			}
			if 10+n > len(tag) {
				break
			}
			body, tag = tag[10:10+n], tag[10+n:]
		}
		if id != "APIC" && id != "PIC" || len(body) < 4 {
			continue
		}
		enc := body[0]
		var mime string
		rest := body[1:]
		if id == "PIC" { // ID3v2.2：3 个字符的格式（JPG、PNG）
			mime = "image/" + strings.ToLower(string(rest[:3]))
			if mime == "image/jpg" {
				mime = "image/jpeg"
			}
			rest = rest[3:]
		} else {
			i := bytes.IndexByte(rest, 0)
			if i < 0 {
				continue
			}
			mime, rest = string(rest[:i]), rest[i+1:]
		}
		if len(rest) < 1 {
			continue
		}
		rest = rest[1:] // 图片类型
		// 描述：编码 1、2 为 UTF-16，以两个 0 字节结尾；0、3 以一个 0 字节结尾
		if enc == 1 || enc == 2 {
			i := 0
			for i+1 < len(rest) && (rest[i] != 0 || rest[i+1] != 0) {
				i += 2
			}
			if i+2 > len(rest) {
				continue
			}
			rest = rest[i+2:]
		} else {
			i := bytes.IndexByte(rest, 0)
			if i < 0 {
				continue
			}
			rest = rest[i+1:]
		}
		if len(rest) == 0 {
			continue
		}
		if !strings.HasPrefix(mime, "image/") {
			mime = http.DetectContentType(rest)
		}
		return rest, mime, nil
	}
	return nil, "", errors.New("没有内嵌封面")
}

func syncsafe(b []byte) int {
	return int(b[0]&0x7f)<<21 | int(b[1]&0x7f)<<14 | int(b[2]&0x7f)<<7 | int(b[3]&0x7f)
}

// mediaFile 把播放地址（/music/<序号>/… 或 /lib/<库 id>/…）还原为文件路径。
func (m *Module) mediaFile(src string) (string, error) {
	u, err := url.Parse(src)
	if err != nil {
		return "", err
	}
	parts := strings.SplitN(strings.TrimPrefix(u.Path, "/"), "/", 3)
	if len(parts) != 3 {
		return "", errors.New("地址格式不正确")
	}
	switch parts[0] {
	case "music":
		n, err := strconv.Atoi(parts[1])
		if err != nil || n < 0 || n >= len(m.cfg.Music) {
			return "", errors.New("没有这个音乐目录")
		}
		return within(m.cfg.Music[n], parts[2])
	case "lib":
		for _, lib := range m.libs() {
			if lib.ID == parts[1] {
				return within(lib.Path, parts[2])
			}
		}
	}
	return "", errors.New("没有这个资源库")
}

func (m *Module) coverRoutes(r core.Router) {
	// cover?src=<音频的播放地址>：内嵌封面，没有时返回 404（页面显示默认图案）
	r.HandleFunc("GET /api/library/audio/cover", func(w http.ResponseWriter, req *http.Request) {
		p, err := m.mediaFile(req.URL.Query().Get("src"))
		if err != nil || !audioExts[strings.ToLower(pathExt(p))] {
			http.NotFound(w, req)
			return
		}
		f, err := os.Open(p)
		if err != nil {
			http.NotFound(w, req)
			return
		}
		defer f.Close()
		// 内嵌封面常有几百 KB（实测平均 584KB），一屏几十张专辑太重；第一次读取时缩成 300 像素宽的 JPEG 存进缓存
		sum := sha1.Sum([]byte(p))
		cache := filepath.Join(m.env.Config.DataDir, "library", "covers", hex.EncodeToString(sum[:])+".jpg")
		w.Header().Set("Cache-Control", "public, max-age=604800")
		if b, err := os.ReadFile(cache); err == nil {
			w.Header().Set("Content-Type", "image/jpeg")
			w.Write(b)
			return
		}
		raw, mime, err := readID3Picture(f)
		if err != nil {
			http.NotFound(w, req)
			return
		}
		small, err := shrinkImage(raw, 300)
		if err != nil { // 解不开的格式原样返回
			w.Header().Set("Content-Type", mime)
			w.Write(raw)
			return
		}
		os.MkdirAll(filepath.Dir(cache), 0o755)
		os.WriteFile(cache, small, 0o644)
		w.Header().Set("Content-Type", "image/jpeg")
		w.Write(small)
	})
}

// shrinkImage 把 JPEG / PNG 缩到宽度不超过 maxW（按面积平均取色），输出 JPEG。只用标准库。
func shrinkImage(raw []byte, maxW int) ([]byte, error) {
	src, _, err := image.Decode(bytes.NewReader(raw))
	if err != nil {
		return nil, err
	}
	b := src.Bounds()
	sw, sh := b.Dx(), b.Dy()
	if sw <= 0 || sh <= 0 {
		return nil, errors.New("图片尺寸为 0")
	}
	dw := min(maxW, sw)
	dh := max(1, sh*dw/sw)
	dst := image.NewRGBA(image.Rect(0, 0, dw, dh))
	for y := 0; y < dh; y++ {
		y0, y1 := b.Min.Y+y*sh/dh, b.Min.Y+max((y+1)*sh/dh, y*sh/dh+1)
		for x := 0; x < dw; x++ {
			x0, x1 := b.Min.X+x*sw/dw, b.Min.X+max((x+1)*sw/dw, x*sw/dw+1)
			var r, g, bl, n uint32
			for yy := y0; yy < y1; yy++ {
				for xx := x0; xx < x1; xx++ {
					cr, cg, cb, _ := src.At(xx, yy).RGBA()
					r, g, bl, n = r+cr>>8, g+cg>>8, bl+cb>>8, n+1
				}
			}
			i := dst.PixOffset(x, y)
			dst.Pix[i], dst.Pix[i+1], dst.Pix[i+2], dst.Pix[i+3] = uint8(r/n), uint8(g/n), uint8(bl/n), 255
		}
	}
	var out bytes.Buffer
	if err := jpeg.Encode(&out, dst, &jpeg.Options{Quality: 80}); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}

func pathExt(p string) string {
	if i := strings.LastIndexByte(p, '.'); i >= 0 {
		return p[i:]
	}
	return ""
}
