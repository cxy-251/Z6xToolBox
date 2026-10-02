package files

import (
	"archive/zip"
	"errors"
	"io"
	"io/fs"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"strings"
	"time"

	"z6x/hub/internal/core"
)

// 网页文件管理：新建文件夹、重命名、删除（移入回收站）、打包下载文件夹。
// 路径检查全部复用 Roots（规范化后不得越出共享目录，共享根目录本身不能改名或删除）。

// trashDir 是每个共享目录下的回收站。网页上的「删除」只是移入这里，需要时可从中找回。
const trashDir = ".z6x-trash"

// validName 检查新建或重命名时用户输入的名称：不能为空、不能含路径分隔符、不能是 . 或 ..。
func validName(name string) bool {
	return name != "" && name != "." && name != ".." && !strings.ContainsAny(name, "/\\\x00")
}

func (m *Module) manageRoutes(r core.Router) {
	r.HandleFunc("POST /api/files/mkdir", m.mkdir)
	r.HandleFunc("POST /api/files/rename", m.rename)
	r.HandleFunc("POST /api/files/delete", m.trash)
	r.HandleFunc("GET /api/files/zip", m.zipDir)
}

// mkdir：path 为上级目录，name 为新文件夹名。
func (m *Module) mkdir(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	if !validName(q.Get("name")) {
		core.WriteError(w, http.StatusBadRequest, "名称不正确")
		return
	}
	if err := m.roots.Mkdir(r.Context(), path.Join(q.Get("path"), q.Get("name")), 0o755); err != nil {
		if errors.Is(err, os.ErrExist) {
			core.WriteError(w, http.StatusConflict, "已存在同名项目")
			return
		}
		m.fail(w, err)
		return
	}
	core.WriteJSON(w, map[string]string{"status": "ok"})
}

// rename：在同一目录内改名。目标已存在时拒绝（os.Rename 会直接覆盖文件）。
func (m *Module) rename(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	from, name := q.Get("path"), q.Get("name")
	if !validName(name) {
		core.WriteError(w, http.StatusBadRequest, "名称不正确")
		return
	}
	to := path.Join(path.Dir(path.Clean("/"+from)), name)
	if _, err := m.roots.Stat(r.Context(), to); err == nil {
		core.WriteError(w, http.StatusConflict, "已存在同名项目")
		return
	}
	if err := m.roots.Rename(r.Context(), from, to); err != nil {
		m.fail(w, err)
		return
	}
	core.WriteJSON(w, map[string]string{"status": "ok"})
}

// trash 把文件或文件夹移入所在共享目录的回收站，文件名前加上时间，避免重名。
func (m *Module) trash(w http.ResponseWriter, r *http.Request) {
	p := path.Clean("/" + r.URL.Query().Get("path"))
	parts := strings.SplitN(strings.TrimPrefix(p, "/"), "/", 2)
	if len(parts) < 2 || parts[1] == "" {
		core.WriteError(w, http.StatusForbidden, "不能删除共享根目录")
		return
	}
	if strings.HasPrefix(parts[1], trashDir) {
		core.WriteError(w, http.StatusForbidden, "回收站中的项目请在文件管理器中处理")
		return
	}
	bin := "/" + parts[0] + "/" + trashDir
	if err := m.roots.Mkdir(r.Context(), bin, 0o755); err != nil && !errors.Is(err, os.ErrExist) {
		m.fail(w, err)
		return
	}
	dst := bin + "/" + time.Now().Format("20060102-150405") + "_" + path.Base(p)
	if err := m.roots.Rename(r.Context(), p, dst); err != nil {
		m.fail(w, err)
		return
	}
	m.env.Log.Info("移入回收站", "path", p, "to", dst)
	core.WriteJSON(w, map[string]string{"status": "ok", "trash": dst})
}

// zipDir 把文件夹打包成 zip 边压缩边下载，不在设备上生成临时文件。
// 音乐、视频、图片本身已经压缩过，再压缩几乎不会变小，因此只打包不压缩（Store），速度只受网络限制。
// 超过 4GB 或 65535 个文件时 archive/zip 自动使用 Zip64 格式。
func (m *Module) zipDir(w http.ResponseWriter, r *http.Request) {
	p := r.URL.Query().Get("path")
	real, err := m.roots.resolve(p)
	if err != nil || real == "" {
		m.fail(w, orNotExist(err))
		return
	}
	fi, err := os.Stat(real)
	if err != nil || !fi.IsDir() {
		core.WriteError(w, http.StatusBadRequest, "不是文件夹")
		return
	}
	name := filepath.Base(real)
	w.Header().Set("Content-Type", "application/zip")
	w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+url.PathEscape(name+".zip"))
	zw := zip.NewWriter(w)
	files := 0
	err = filepath.WalkDir(real, func(fp string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil // 读不到的项目跳过，不中断整个下载
		}
		if d.IsDir() && d.Name() == trashDir {
			return fs.SkipDir
		}
		if !d.Type().IsRegular() {
			return nil // 跳过目录本身、符号链接和设备文件
		}
		info, err := d.Info()
		if err != nil {
			return nil
		}
		rel, _ := filepath.Rel(real, fp)
		h, _ := zip.FileInfoHeader(info)
		h.Name = name + "/" + filepath.ToSlash(rel)
		h.Method = zip.Store
		dst, err := zw.CreateHeader(h)
		if err != nil {
			return err
		}
		f, err := os.Open(fp)
		if err != nil {
			return nil
		}
		_, err = io.CopyBuffer(dst, f, make([]byte, 256<<10))
		f.Close()
		files++
		return err // 写入失败通常是客户端断开，停止打包
	})
	if cerr := zw.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		m.env.Log.Warn("打包下载中断", "path", p, "files", files, "err", err)
		return
	}
	m.env.Log.Info("打包下载完成", "path", p, "files", files)
}
