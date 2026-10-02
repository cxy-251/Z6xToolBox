package library

import (
	"archive/zip"
	"encoding/json"
	"html"
	"io"
	"io/fs"
	"net/http"
	"os"
	"path"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"z6x/hub/internal/core"
)

// 音声与小说（与 omni-deck 兼容）：
//   - 音声在 media_library/audio/standard、audio/nsfw 下，第一级子目录为专辑（media.go 中扫描）；
//     续听记录的键为「std:相对路径」或「nsfw:相对路径」，格式与 omni-deck 的 var/data/audio_progress.json 相同，可直接导入；
//     章节信息读取同名的 .chapters.json 旁车文件（设备上没有 ffprobe，不读 m4b 内嵌章节）。
//   - 小说在 media_library/novels/standard、novels/nsfw 下（EPUB），第一级子目录为分类。
//     EPUB 按其阅读顺序（spine）分章，正文转换为纯文本段落，由网页用 textContent 显示，原书中的脚本和样式不会执行。

// ---------------- 进度记录（音声与小说共用） ----------------

type progressStore struct {
	mu   sync.Mutex
	path string
	data map[string]json.RawMessage
}

func (p *progressStore) load() {
	p.data = map[string]json.RawMessage{}
	if raw, err := os.ReadFile(p.path); err == nil {
		json.Unmarshal(raw, &p.data)
	}
}

func (p *progressStore) get(key string) json.RawMessage {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.data[key]
}

// set 保存一条记录；merge 为 true 时只在本地没有该键时写入（导入时不覆盖较新的本地记录）。
func (p *progressStore) set(entries map[string]json.RawMessage, merge bool) (int, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	n := 0
	for k, v := range entries {
		if merge {
			if _, ok := p.data[k]; ok {
				continue
			}
		}
		p.data[k] = v
		n++
	}
	b, _ := json.MarshalIndent(p.data, "", "  ")
	os.MkdirAll(filepath.Dir(p.path), 0o755)
	if err := os.WriteFile(p.path+".tmp", b, 0o644); err != nil {
		return 0, err
	}
	return n, os.Rename(p.path+".tmp", p.path)
}

func (p *progressStore) routes(r core.Router, prefix string) {
	r.HandleFunc("GET "+prefix, func(w http.ResponseWriter, req *http.Request) {
		v := p.get(req.URL.Query().Get("key"))
		if v == nil {
			v = json.RawMessage("null")
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write(v)
	})
	r.HandleFunc("POST "+prefix, func(w http.ResponseWriter, req *http.Request) {
		var in struct {
			Key   string          `json:"key"`
			Value json.RawMessage `json:"value"`
		}
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, 64<<10)).Decode(&in); err != nil || in.Key == "" || len(in.Value) == 0 {
			core.WriteError(w, http.StatusBadRequest, "需要 key 与 value")
			return
		}
		if _, err := p.set(map[string]json.RawMessage{in.Key: in.Value}, false); err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]string{"status": "ok"})
	})
	// 导入 omni-deck 的记录文件，本地已有的键不覆盖
	r.HandleFunc("POST "+prefix+"/import", func(w http.ResponseWriter, req *http.Request) {
		var in map[string]json.RawMessage
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, 16<<20)).Decode(&in); err != nil {
			core.WriteError(w, http.StatusBadRequest, "格式错误")
			return
		}
		n, err := p.set(in, true)
		if err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]int{"added": n})
	})
}

// ---------------- 音声 ----------------

type chapter struct {
	Title string  `json:"title"`
	Start float64 `json:"start"` // 秒
}

func (m *Module) audioRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/audio", func(w http.ResponseWriter, req *http.Request) {
		_, tracks := m.media()
		nsfw := req.URL.Query().Get("nsfw") == "1"
		out := []track{}
		for _, t := range tracks {
			if t.IsNSFW == nsfw {
				out = append(out, t)
			}
		}
		core.WriteJSON(w, out)
	})
	// 章节：读取与音频同名的 .chapters.json（omni-deck 的旁车文件格式：[{title, start}]）
	r.HandleFunc("GET /api/library/audio/chapters", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		sub := "standard"
		if q.Get("nsfw") == "1" {
			sub = "nsfw"
		}
		out := []chapter{}
		for _, lib := range m.libs() {
			p, err := within(filepath.Join(lib.Path, mediaRoot, "audio", sub), q.Get("rel_path"))
			if err != nil {
				continue
			}
			raw, err := os.ReadFile(strings.TrimSuffix(p, filepath.Ext(p)) + ".chapters.json")
			if err == nil {
				json.Unmarshal(raw, &out)
				break
			}
		}
		core.WriteJSON(w, out)
	})
	m.audioProgress.routes(r, "/api/library/audio/progress")
}

// ---------------- 小说 ----------------

type novel struct {
	ID       string `json:"id"` // 资源库 id 与相对路径的编码，不暴露真实路径
	Title    string `json:"title"`
	Category string `json:"category"`
	Size     int64  `json:"size"`
	NSFW     bool   `json:"nsfw"`
}

func (m *Module) listNovels(nsfw bool) []novel {
	sub := "standard"
	if nsfw {
		sub = "nsfw"
	}
	out := []novel{}
	for _, lib := range m.libs() {
		base := filepath.Join(lib.Path, mediaRoot, "novels", sub)
		filepath.WalkDir(base, func(p string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			if d.IsDir() {
				if strings.HasPrefix(d.Name(), ".") {
					return fs.SkipDir
				}
				return nil
			}
			if strings.ToLower(filepath.Ext(p)) != ".epub" {
				return nil
			}
			rel, _ := filepath.Rel(filepath.Join(lib.Path, mediaRoot, "novels"), p)
			rel = filepath.ToSlash(rel) // standard/分类/书名.epub
			cat := "未分类"
			if parts := strings.Split(rel, "/"); len(parts) > 2 {
				cat = parts[1]
			}
			var size int64
			if fi, err := d.Info(); err == nil {
				size = fi.Size()
			}
			out = append(out, novel{ID: encodeID(lib.ID, rel), Title: strings.TrimSuffix(d.Name(), filepath.Ext(d.Name())),
				Category: cat, Size: size, NSFW: nsfw})
			return nil
		})
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Category != out[j].Category {
			return out[i].Category < out[j].Category
		}
		return naturalLess(out[i].Title, out[j].Title)
	})
	return out
}

func (m *Module) novelPath(id string) (string, bool) {
	libID, rel, ok := decodeID(id)
	if !ok {
		return "", false
	}
	for _, lib := range m.libs() {
		if lib.ID == libID {
			p, err := within(filepath.Join(lib.Path, mediaRoot, "novels"), rel)
			return p, err == nil && strings.ToLower(filepath.Ext(p)) == ".epub"
		}
	}
	return "", false
}

type novelChapter struct {
	Title string   `json:"title"`
	Paras []string `json:"paras"`
}

var (
	reRootfile = regexp.MustCompile(`full-path=["']([^"']+)["']`)
	reItem     = regexp.MustCompile(`(?is)<item\b[^>]*>`)
	reAttr     = regexp.MustCompile(`(?is)\b(id|href)=["']([^"']+)["']`)
	reItemref  = regexp.MustCompile(`(?is)<itemref\b[^>]*idref=["']([^"']+)["']`)
	reDCTitle  = regexp.MustCompile(`(?is)<dc:title[^>]*>(.*?)</dc:title>`)
	reDCAuthor = regexp.MustCompile(`(?is)<dc:creator[^>]*>(.*?)</dc:creator>`)
	reHeading  = regexp.MustCompile(`(?is)<(h[1-3]|title)[^>]*>(.*?)</(?:h[1-3]|title)>`)
	reBody     = regexp.MustCompile(`(?is)<body[^>]*>(.*)</body>`)
	reDrop     = regexp.MustCompile(`(?is)<(script|style|head)[^>]*>.*?</(?:script|style|head)>`)
	reBlockEnd = regexp.MustCompile(`(?i)</(p|div|h[1-6]|li|blockquote|tr)>|<br\s*/?>`)
	reTag      = regexp.MustCompile(`(?s)<[^>]*>`)
)

// textOf 把 HTML 片段转换为纯文本段落。
func textOf(fragment string) []string {
	s := reDrop.ReplaceAllString(fragment, "")
	s = reBlockEnd.ReplaceAllString(s, "\n")
	s = html.UnescapeString(reTag.ReplaceAllString(s, ""))
	var out []string
	for _, line := range strings.Split(s, "\n") {
		if line = strings.TrimSpace(strings.ReplaceAll(line, "　", " ")); line != "" {
			out = append(out, line)
		}
	}
	return out
}

// parseEPUB 按 EPUB 的阅读顺序分章。
func parseEPUB(p string) (title, author string, chapters []novelChapter, err error) {
	zr, err := zip.OpenReader(p)
	if err != nil {
		return "", "", nil, err
	}
	defer zr.Close()
	files := map[string]*zip.File{}
	for _, f := range zr.File {
		files[f.Name] = f
	}
	read := func(name string) string {
		f := files[name]
		if f == nil || f.UncompressedSize64 > 32<<20 {
			return ""
		}
		rc, err := f.Open()
		if err != nil {
			return ""
		}
		defer rc.Close()
		b, _ := io.ReadAll(io.LimitReader(rc, 32<<20))
		return string(b)
	}
	opf := ""
	if mm := reRootfile.FindStringSubmatch(read("META-INF/container.xml")); mm != nil {
		opf = mm[1]
	}
	var order []string
	if opfXML := read(opf); opfXML != "" {
		if mm := reDCTitle.FindStringSubmatch(opfXML); mm != nil {
			title = html.UnescapeString(strings.TrimSpace(mm[1]))
		}
		if mm := reDCAuthor.FindStringSubmatch(opfXML); mm != nil {
			author = html.UnescapeString(strings.TrimSpace(mm[1]))
		}
		hrefs := map[string]string{}
		for _, tag := range reItem.FindAllString(opfXML, -1) {
			var id, href string
			for _, a := range reAttr.FindAllStringSubmatch(tag, -1) {
				if strings.EqualFold(a[1], "id") {
					id = a[2]
				} else {
					href = a[2]
				}
			}
			hrefs[id] = href
		}
		for _, mm := range reItemref.FindAllStringSubmatch(opfXML, -1) {
			if h, ok := hrefs[mm[1]]; ok {
				name := path.Clean(path.Join(path.Dir(opf), strings.SplitN(h, "#", 2)[0]))
				if files[name] != nil {
					order = append(order, name)
				}
			}
		}
	}
	if len(order) == 0 { // 没有 OPF 时按文件名顺序读取所有网页文件
		for _, f := range zr.File {
			n := strings.ToLower(f.Name)
			if strings.HasSuffix(n, ".xhtml") || strings.HasSuffix(n, ".html") || strings.HasSuffix(n, ".htm") {
				order = append(order, f.Name)
			}
		}
		sort.Slice(order, func(i, j int) bool { return naturalLess(order[i], order[j]) })
	}
	for _, name := range order {
		doc := read(name)
		body := doc
		if mm := reBody.FindStringSubmatch(doc); mm != nil {
			body = mm[1]
		}
		paras := textOf(body)
		if len(paras) == 0 {
			continue
		}
		ct := ""
		if mm := reHeading.FindStringSubmatch(body); mm != nil {
			ct = strings.TrimSpace(html.UnescapeString(reTag.ReplaceAllString(mm[2], "")))
		}
		if ct == "" {
			ct = paras[0]
			if r := []rune(ct); len(r) > 30 {
				ct = string(r[:30]) + "…"
			}
		}
		chapters = append(chapters, novelChapter{Title: ct, Paras: paras})
	}
	return title, author, chapters, nil
}

// novelCache 缓存最近打开的一本书的解析结果：阅读时反复翻章不必每次重新解压。
type novelCache struct {
	mu       sync.Mutex
	path     string
	mod      time.Time
	title    string
	author   string
	chapters []novelChapter
}

func (m *Module) openNovel(p string) (string, string, []novelChapter, error) {
	fi, err := os.Stat(p)
	if err != nil {
		return "", "", nil, err
	}
	c := &m.novelCache
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.path == p && c.mod.Equal(fi.ModTime()) {
		return c.title, c.author, c.chapters, nil
	}
	t, a, ch, err := parseEPUB(p)
	if err != nil {
		return "", "", nil, err
	}
	c.path, c.mod, c.title, c.author, c.chapters = p, fi.ModTime(), t, a, ch
	return t, a, ch, nil
}

func (m *Module) novelRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/novels", func(w http.ResponseWriter, req *http.Request) {
		core.WriteJSON(w, m.listNovels(req.URL.Query().Get("nsfw") == "1"))
	})
	// 目录：书名、作者与各章标题（不含正文）
	r.HandleFunc("GET /api/library/novels/toc", func(w http.ResponseWriter, req *http.Request) {
		p, ok := m.novelPath(req.URL.Query().Get("id"))
		if !ok {
			core.WriteError(w, http.StatusNotFound, "小说不存在")
			return
		}
		t, a, ch, err := m.openNovel(p)
		if err != nil {
			core.WriteError(w, http.StatusInternalServerError, "无法读取 EPUB："+err.Error())
			return
		}
		if t == "" {
			t = strings.TrimSuffix(filepath.Base(p), filepath.Ext(p))
		}
		titles := make([]string, len(ch))
		for i, c := range ch {
			titles[i] = c.Title
		}
		core.WriteJSON(w, map[string]any{"title": t, "author": a, "chapters": titles})
	})
	// 某一章的正文
	r.HandleFunc("GET /api/library/novels/chapter", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		p, ok := m.novelPath(q.Get("id"))
		if !ok {
			core.WriteError(w, http.StatusNotFound, "小说不存在")
			return
		}
		_, _, ch, err := m.openNovel(p)
		n, e := strconv.Atoi(q.Get("n"))
		if err != nil || e != nil || n < 0 || n >= len(ch) {
			core.WriteError(w, http.StatusNotFound, "章节不存在")
			return
		}
		core.WriteJSON(w, ch[n])
	})
	m.novelProgress.routes(r, "/api/library/novels/progress")
}
