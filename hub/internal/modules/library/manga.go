package library

import (
	"archive/zip"
	"crypto/sha1"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"image"
	_ "image/gif"
	"image/jpeg"
	_ "image/png"
	"io"
	"io/fs"
	"mime"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"

	"z6x/hub/internal/core"
)

// Manga 是一本漫画（CBZ/ZIP 压缩包）。ID 由资源库 id 和相对路径编码而成，不暴露真实路径。
type Manga struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Size int64  `json:"size"`
	Lib  string `json:"lib"`
}

func encodeID(lib, rel string) string {
	return base64.RawURLEncoding.EncodeToString([]byte(lib + "/" + filepath.ToSlash(rel)))
}

func decodeID(id string) (lib, rel string, ok bool) {
	b, err := base64.RawURLEncoding.DecodeString(id)
	if err != nil {
		return "", "", false
	}
	lib, rel, ok = strings.Cut(string(b), "/")
	return
}

func isImage(name string) bool {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".bmp":
		return true
	}
	return false
}

// naturalLess 按「自然顺序」比较文件名：page2 排在 page10 之前。
func naturalLess(a, b string) bool {
	ra, rb := []rune(strings.ToLower(a)), []rune(strings.ToLower(b))
	i, j := 0, 0
	for i < len(ra) && j < len(rb) {
		if unicode.IsDigit(ra[i]) && unicode.IsDigit(rb[j]) {
			si := i
			for i < len(ra) && unicode.IsDigit(ra[i]) {
				i++
			}
			sj := j
			for j < len(rb) && unicode.IsDigit(rb[j]) {
				j++
			}
			na, _ := strconv.ParseInt(string(ra[si:i]), 10, 64)
			nb, _ := strconv.ParseInt(string(rb[sj:j]), 10, 64)
			if na != nb {
				return na < nb
			}
			continue
		}
		if ra[i] != rb[j] {
			return ra[i] < rb[j]
		}
		i, j = i+1, j+1
	}
	return len(ra)-i < len(rb)-j
}

func (m *Module) listManga() []Manga {
	out := []Manga{} // 空列表也要返回 []，返回 null 会让页面脚本出错
	for _, lib := range m.libs() {
		base := filepath.Join(lib.Path, mediaRoot, "manga")
		filepath.WalkDir(base, func(p string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			if d.IsDir() && strings.HasPrefix(d.Name(), ".") {
				return fs.SkipDir
			}
			ext := strings.ToLower(filepath.Ext(p))
			if d.IsDir() || (ext != ".cbz" && ext != ".zip") {
				return nil
			}
			rel, _ := filepath.Rel(base, p)
			fi, _ := d.Info()
			var size int64
			if fi != nil {
				size = fi.Size()
			}
			out = append(out, Manga{ID: encodeID(lib.ID, rel), Name: strings.TrimSuffix(filepath.Base(p), filepath.Ext(p)), Size: size, Lib: lib.ID})
			return nil
		})
	}
	sort.Slice(out, func(i, j int) bool { return naturalLess(out[i].Name, out[j].Name) })
	return out
}

// mangaPath 把漫画 ID 换回真实路径，并确认它位于某个资源库的漫画目录中。
func (m *Module) mangaPath(id string) (string, bool) {
	libID, rel, ok := decodeID(id)
	if !ok {
		return "", false
	}
	for _, lib := range m.libs() {
		if lib.ID == libID {
			p, err := within(filepath.Join(lib.Path, mediaRoot, "manga"), rel)
			return p, err == nil
		}
	}
	return "", false
}

// pageIndex 缓存每本漫画的图片列表（按路径和修改时间区分），避免每翻一页都重新读取压缩包目录。
type pageIndex struct {
	mod   time.Time
	pages []string
}

func (m *Module) pages(path string) ([]string, error) {
	fi, err := os.Stat(path)
	if err != nil {
		return nil, err
	}
	m.mu.Lock()
	if c, ok := m.pageCache[path]; ok && c.mod.Equal(fi.ModTime()) {
		m.mu.Unlock()
		return c.pages, nil
	}
	m.mu.Unlock()
	zr, err := zip.OpenReader(path)
	if err != nil {
		return nil, err
	}
	defer zr.Close()
	var pages []string
	for _, f := range zr.File {
		if !f.FileInfo().IsDir() && isImage(f.Name) && !strings.HasPrefix(filepath.Base(f.Name), ".") {
			pages = append(pages, f.Name)
		}
	}
	sort.Slice(pages, func(i, j int) bool { return naturalLess(pages[i], pages[j]) })
	m.mu.Lock()
	if len(m.pageCache) > 64 { // 简单的容量控制
		m.pageCache = map[string]pageIndex{}
	}
	m.pageCache[path] = pageIndex{fi.ModTime(), pages}
	m.mu.Unlock()
	return pages, nil
}

func openEntry(path, name string) (io.ReadCloser, func(), error) {
	zr, err := zip.OpenReader(path)
	if err != nil {
		return nil, nil, err
	}
	f, err := zr.Open(name)
	if err != nil {
		zr.Close()
		return nil, nil, err
	}
	return f, func() { f.Close(); zr.Close() }, nil
}

func (m *Module) mangaRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/manga", func(w http.ResponseWriter, _ *http.Request) { core.WriteJSON(w, m.listManga()) })
	r.HandleFunc("GET /api/library/manga/pages", func(w http.ResponseWriter, req *http.Request) {
		p, ok := m.mangaPath(req.URL.Query().Get("id"))
		if !ok {
			core.WriteError(w, http.StatusNotFound, "漫画不存在")
			return
		}
		pages, err := m.pages(p)
		if err != nil {
			core.WriteError(w, http.StatusInternalServerError, "无法读取压缩包："+err.Error())
			return
		}
		core.WriteJSON(w, map[string]any{"count": len(pages), "name": strings.TrimSuffix(filepath.Base(p), filepath.Ext(p))})
	})
	r.HandleFunc("GET /api/library/manga/page", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		p, ok := m.mangaPath(q.Get("id"))
		n, _ := strconv.Atoi(q.Get("n"))
		pages, err := m.pages(p)
		if !ok || err != nil || n < 0 || n >= len(pages) {
			http.NotFound(w, req)
			return
		}
		rc, done, err := openEntry(p, pages[n])
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		defer done()
		if ct := mime.TypeByExtension(strings.ToLower(filepath.Ext(pages[n]))); ct != "" {
			w.Header().Set("Content-Type", ct)
		}
		w.Header().Set("Cache-Control", "public, max-age=86400")
		io.Copy(w, rc)
	})
	r.HandleFunc("GET /api/library/manga/thumb", m.mangaThumb)
}

// mangaThumb 返回漫画封面缩略图（第一页缩小到宽 240 像素的 JPEG），首次生成后缓存在数据目录中。
// 同时最多生成 2 张，避免占满 CPU 影响电视播放。
func (m *Module) mangaThumb(w http.ResponseWriter, req *http.Request) {
	p, ok := m.mangaPath(req.URL.Query().Get("id"))
	fi, err := os.Stat(p)
	if !ok || err != nil {
		http.NotFound(w, req)
		return
	}
	sum := sha1.Sum([]byte(fmt.Sprintf("%s|%d|%d", p, fi.Size(), fi.ModTime().Unix())))
	cache := filepath.Join(m.thumbDir, hex.EncodeToString(sum[:])+".jpg")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	if _, err := os.Stat(cache); err == nil {
		http.ServeFile(w, req, cache)
		return
	}
	m.thumbSem <- struct{}{}
	defer func() { <-m.thumbSem }()
	if _, err := os.Stat(cache); err == nil { // 等待期间可能已由其他请求生成
		http.ServeFile(w, req, cache)
		return
	}
	pages, err := m.pages(p)
	if err != nil || len(pages) == 0 {
		http.NotFound(w, req)
		return
	}
	rc, done, err := openEntry(p, pages[0])
	if err != nil {
		http.NotFound(w, req)
		return
	}
	img, _, err := image.Decode(rc)
	done()
	if err != nil {
		http.Error(w, "无法解码封面："+err.Error(), http.StatusUnsupportedMediaType)
		return
	}
	thumb := resize(img, 240)
	os.MkdirAll(m.thumbDir, 0o755)
	tmp := cache + ".tmp"
	f, err := os.Create(tmp)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	jpeg.Encode(f, thumb, &jpeg.Options{Quality: 78})
	f.Close()
	os.Rename(tmp, cache)
	http.ServeFile(w, req, cache)
}

func resize(src image.Image, width int) image.Image {
	b := src.Bounds()
	if b.Dx() <= width {
		return src
	}
	h := b.Dy() * width / b.Dx()
	dst := image.NewRGBA(image.Rect(0, 0, width, h))
	draw.ApproxBiLinear.Scale(dst, dst.Bounds(), src, b, draw.Src, nil)
	return dst
}
