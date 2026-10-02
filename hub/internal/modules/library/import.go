package library

import (
	"archive/tar"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"syscall"

	"z6x/hub/internal/core"
)

// LibStatus 是资源库的状态，含所在分区的剩余空间。
type LibStatus struct {
	Lib
	FreeBytes  uint64 `json:"free_bytes"`
	TotalBytes uint64 `json:"total_bytes"`
}

func (m *Module) importRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/libs", func(w http.ResponseWriter, _ *http.Request) {
		m.invalidate()
		out := []LibStatus{}
		for _, l := range m.libs() {
			s := LibStatus{Lib: l}
			var st syscall.Statfs_t
			if syscall.Statfs(l.Path, &st) == nil {
				s.FreeBytes, s.TotalBytes = st.Bavail*uint64(st.Bsize), st.Blocks*uint64(st.Bsize)
			}
			out = append(out, s)
		}
		core.WriteJSON(w, out)
	})
	r.HandleFunc("GET /api/library/volumes", m.volumes)
	r.HandleFunc("POST /api/library/init", m.initHandler)
	r.HandleFunc("POST /api/library/import", m.importHandler)
}

// volumes 列出可以建立资源库的位置：机身存储和当前插着的 U 盘。
func (m *Module) volumes(w http.ResponseWriter, _ *http.Request) {
	type vol struct {
		Target    string `json:"target"` // 传给 /api/library/init 的值
		Label     string `json:"label"`
		Path      string `json:"path"`
		FreeBytes uint64 `json:"free_bytes"`
		HasLib    bool   `json:"has_library"`
	}
	add := func(out []vol, target, label, root string) []vol {
		var st syscall.Statfs_t
		v := vol{Target: target, Label: label, Path: filepath.Join(root, defaultDir)}
		if syscall.Statfs(root, &st) == nil {
			v.FreeBytes = st.Bavail * uint64(st.Bsize)
		}
		_, v.HasLib = readMarker(v.Path)
		if _, ok := readMarker(root); ok {
			v.HasLib, v.Path = true, root
		}
		return append(out, v)
	}
	out := add(nil, "internal", "机身存储", m.cfg.Internal)
	ents, _ := os.ReadDir(m.cfg.Storage)
	for _, e := range ents {
		n := e.Name()
		if n == "emulated" || n == "self" || n == "primary" || strings.HasPrefix(n, ".") {
			continue
		}
		out = add(out, "usb:"+n, "U 盘 "+n, filepath.Join(m.cfg.Storage, n))
	}
	core.WriteJSON(w, out)
}

// initHandler 在机身存储或某个 U 盘上建立资源库（目录结构与 omni-deck 相同）。
func (m *Module) initHandler(w http.ResponseWriter, r *http.Request) {
	target := r.URL.Query().Get("target")
	var root, label string
	switch {
	case target == "internal":
		root, label = filepath.Join(m.cfg.Internal, defaultDir), "投影仪机身存储"
	case strings.HasPrefix(target, "usb:"):
		name := strings.TrimPrefix(target, "usb:")
		if name == "" || strings.ContainsAny(name, "/\\") || strings.HasPrefix(name, ".") {
			core.WriteError(w, http.StatusBadRequest, "U 盘名称不正确")
			return
		}
		root, label = filepath.Join(m.cfg.Storage, name, defaultDir), "投影仪 U 盘 "+name
	default:
		core.WriteError(w, http.StatusBadRequest, "target 应为 internal 或 usb:<卷名>")
		return
	}
	if l := r.URL.Query().Get("label"); l != "" {
		label = l
	}
	lib, err := initLibrary(root, label)
	if err != nil {
		core.WriteError(w, http.StatusInternalServerError, "建立资源库失败："+err.Error())
		return
	}
	m.invalidate()
	m.env.Log.Info("已建立资源库", "path", root, "id", lib.ID)
	core.WriteJSON(w, lib)
}

// importHandler 接收一个 tar 流，解压为资源库中的一个文件夹（例如一个游戏）。
//
//	tar -C <上级目录> -cf - <文件夹名> | curl -T - "http://投影仪:8090/api/library/import?lib=<库 id>&key=games.rpg"
//
// tar 中的顶层文件夹即目标文件夹。先解压到临时目录，全部完成后再改名，中断时不会留下不完整的游戏。
// 拒绝绝对路径、..、符号链接和设备文件。
func (m *Module) importHandler(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	rel, ok := layout()[q.Get("key")]
	if !ok {
		core.WriteError(w, http.StatusBadRequest, "key 不正确，应为 games.rpg、games.slg、media.manga 等")
		return
	}
	var lib *Lib
	for _, l := range m.libs() {
		if l.ID == q.Get("lib") {
			l := l
			lib = &l
		}
	}
	if lib == nil {
		core.WriteError(w, http.StatusNotFound, "资源库不存在："+q.Get("lib"))
		return
	}
	dest := filepath.Join(lib.Path, rel)
	if err := os.MkdirAll(dest, 0o755); err != nil {
		core.WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	tmp, err := os.MkdirTemp(dest, ".z6x-import-")
	if err != nil {
		core.WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	defer os.RemoveAll(tmp) // 成功时临时目录已为空（内容已移走）
	top, files, bytes, err := extractTar(r.Body, tmp)
	if err != nil {
		core.WriteError(w, http.StatusBadRequest, "解压失败："+err.Error())
		return
	}
	final := filepath.Join(dest, top)
	if _, err := os.Stat(final); err == nil {
		if q.Get("overwrite") != "1" {
			core.WriteError(w, http.StatusConflict, "已存在同名文件夹："+top+"；如需替换请加 overwrite=1")
			return
		}
		old := final + ".z6x-old"
		os.RemoveAll(old)
		if err := os.Rename(final, old); err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		defer os.RemoveAll(old)
	}
	if err := os.Rename(filepath.Join(tmp, top), final); err != nil {
		core.WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	m.invalidate()
	m.env.Log.Info("导入完成", "lib", lib.ID, "key", q.Get("key"), "name", top, "files", files, "bytes", bytes)
	core.WriteJSON(w, map[string]any{"name": top, "files": files, "bytes": bytes, "size": fmtSize(bytes)})
}

// extractTar 把 tar 流解压到 dir，要求所有条目都在同一个顶层文件夹下，返回该文件夹名。
func extractTar(r io.Reader, dir string) (top string, files int, total int64, err error) {
	tr := tar.NewReader(r)
	for {
		h, err := tr.Next()
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return "", 0, 0, err
		}
		name := filepath.Clean(filepath.FromSlash(strings.TrimPrefix(h.Name, "./")))
		if name == "." {
			continue
		}
		if filepath.IsAbs(name) || name == ".." || strings.HasPrefix(name, ".."+string(filepath.Separator)) {
			return "", 0, 0, fmt.Errorf("不安全的路径：%s", h.Name)
		}
		first := strings.SplitN(name, string(filepath.Separator), 2)[0]
		if top == "" {
			top = first
		} else if first != top {
			return "", 0, 0, fmt.Errorf("tar 中应只有一个顶层文件夹，发现 %s 和 %s", top, first)
		}
		target := filepath.Join(dir, name)
		switch h.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, 0o755); err != nil {
				return "", 0, 0, err
			}
		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
				return "", 0, 0, err
			}
			f, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o644)
			if err != nil {
				return "", 0, 0, err
			}
			n, err := io.CopyBuffer(f, tr, make([]byte, 256<<10))
			f.Close()
			if err != nil {
				return "", 0, 0, err
			}
			files++
			total += n
		default:
			// 符号链接、硬链接、设备文件等一律跳过：避免指向资源库以外的位置。
		}
	}
	if top == "" {
		return "", 0, 0, errors.New("tar 为空")
	}
	return top, files, total, nil
}
