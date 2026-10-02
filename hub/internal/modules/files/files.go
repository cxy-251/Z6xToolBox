// Package files 提供局域网文件共享：WebDAV（独立端口，可在各系统的文件管理器中挂载）、
// 支持断点和拖动的 HTTP 下载，以及流式上传。
package files

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"golang.org/x/net/webdav"

	"z6x/hub/internal/core"
)

type Config struct {
	Port  int    `yaml:"port"`
	Roots []Root `yaml:"roots"`
	USB   bool   `yaml:"usb"`
	// MaxUploadMB 限制单个上传文件的大小，0 表示不限。
	MaxUploadMB int64 `yaml:"max_upload_mb"`
}

type Module struct {
	cfg   Config
	roots *Roots
	env   *core.Env
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "files" }
func (m *Module) Title() string { return "文件共享" }

func (m *Module) Start(ctx context.Context, env *core.Env) error {
	m.env = env
	m.cfg = Config{Port: 8091, USB: true}
	if err := env.Config.Decode("files", &m.cfg); err != nil {
		return err
	}
	for _, r := range m.cfg.Roots {
		if r.Name == "" || strings.ContainsAny(r.Name, "/\\") {
			return fmt.Errorf("根目录名称不合法：%q", r.Name)
		}
		if fi, err := os.Stat(r.Path); err != nil || !fi.IsDir() {
			return fmt.Errorf("共享目录不存在或不是目录：%s", r.Path)
		}
	}
	m.roots = &Roots{Static: m.cfg.Roots, USB: m.cfg.USB}

	if m.cfg.Port > 0 {
		h := &webdav.Handler{
			FileSystem: m.roots,
			LockSystem: webdav.NewMemLS(),
			Logger: func(r *http.Request, err error) {
				if err != nil && !errors.Is(err, os.ErrNotExist) {
					env.Log.Warn("WebDAV 出错", "method", r.Method, "path", r.URL.Path, "err", err)
				}
			},
		}
		env.Serve(m.cfg.Port, env.RequireTokenFor(h)) // 端口由 hub 统一按网络状态开启或关闭
	}
	return nil
}

func (m *Module) Stop(ctx context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/files/list", m.list)
	r.HandleFunc("GET /api/files/get", m.get)
	r.HandleFunc("POST /api/files/upload", m.upload)
	r.HandleFunc("GET /ui/files/{$}", m.page)
	m.manageRoutes(r)
}

// Entry 是目录列表中的一项。
type Entry struct {
	Name    string `json:"name"`
	Dir     bool   `json:"dir"`
	Size    int64  `json:"size"`
	ModTime string `json:"mod_time"`
}

func (m *Module) list(w http.ResponseWriter, r *http.Request) {
	p := r.URL.Query().Get("path")
	f, err := m.roots.OpenFile(r.Context(), p, os.O_RDONLY, 0)
	if err != nil {
		m.fail(w, err)
		return
	}
	defer f.Close()
	infos, err := f.Readdir(-1)
	if err != nil {
		m.fail(w, err)
		return
	}
	out := make([]Entry, 0, len(infos))
	for _, fi := range infos {
		out = append(out, Entry{Name: fi.Name(), Dir: fi.IsDir(), Size: fi.Size(), ModTime: fi.ModTime().Format(time.RFC3339)})
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Dir != out[j].Dir {
			return out[i].Dir
		}
		return strings.ToLower(out[i].Name) < strings.ToLower(out[j].Name)
	})
	core.WriteJSON(w, out)
}

// get 下载文件。http.ServeContent 会处理 Range 请求（断点续传、视频拖动）和 If-Modified-Since。
func (m *Module) get(w http.ResponseWriter, r *http.Request) {
	p := r.URL.Query().Get("path")
	real, err := m.roots.resolve(p)
	if err != nil || real == "" {
		m.fail(w, orNotExist(err))
		return
	}
	f, err := os.Open(real)
	if err != nil {
		m.fail(w, err)
		return
	}
	defer f.Close()
	fi, err := f.Stat()
	if err != nil || fi.IsDir() {
		core.WriteError(w, http.StatusBadRequest, "不是文件")
		return
	}
	if r.URL.Query().Get("download") == "1" {
		w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+url.PathEscape(fi.Name()))
	}
	http.ServeContent(w, r, fi.Name(), fi.ModTime(), f)
}

// upload 流式写入：先写 .z6x-upload 临时文件，完整收到后再改名，中断时不会留下不完整的目标文件。
func (m *Module) upload(w http.ResponseWriter, r *http.Request) {
	p := r.URL.Query().Get("path")
	real, err := m.roots.resolve(p)
	if err != nil || real == "" || m.roots.isRoot(real) {
		m.fail(w, orNotExist(err))
		return
	}
	if fi, err := os.Stat(filepath.Dir(real)); err != nil || !fi.IsDir() {
		core.WriteError(w, http.StatusNotFound, "目标目录不存在")
		return
	}
	if _, err := os.Stat(real); err == nil && r.URL.Query().Get("overwrite") != "1" {
		core.WriteError(w, http.StatusConflict, "文件已存在；如需覆盖请加 overwrite=1")
		return
	}
	body := io.Reader(r.Body)
	if m.cfg.MaxUploadMB > 0 {
		body = http.MaxBytesReader(w, r.Body, m.cfg.MaxUploadMB<<20)
	}
	tmp := real + ".z6x-upload"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
	if err != nil {
		m.fail(w, err)
		return
	}
	n, err := io.CopyBuffer(f, body, make([]byte, 256<<10))
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		os.Remove(tmp)
		core.WriteError(w, http.StatusBadRequest, "上传中断："+err.Error())
		return
	}
	if err := os.Rename(tmp, real); err != nil {
		os.Remove(tmp)
		m.fail(w, err)
		return
	}
	m.env.Log.Info("上传完成", "path", p, "bytes", n)
	core.WriteJSON(w, map[string]any{"path": p, "bytes": n})
}

func orNotExist(err error) error {
	if err == nil {
		return os.ErrNotExist
	}
	return err
}

func (m *Module) fail(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, errOutside), errors.Is(err, os.ErrPermission):
		core.WriteError(w, http.StatusForbidden, "禁止访问："+err.Error())
	case errors.Is(err, os.ErrNotExist):
		core.WriteError(w, http.StatusNotFound, "不存在")
	default:
		core.WriteError(w, http.StatusInternalServerError, err.Error())
	}
}
