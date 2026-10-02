package files

import (
	"context"
	"errors"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"golang.org/x/net/webdav"
)

// Root 是共享出去的一个目录。Name 是它在共享中的显示名称。
type Root struct {
	Name string `yaml:"name"`
	Path string `yaml:"path"`
}

// Roots 把多个目录合并成一个虚拟的顶层：/内部存储/…、/U盘-XXXX/…。
// 所有路径解析都经过 resolve，越界（..）和未配置的目录一律拒绝。
type Roots struct {
	Static []Root
	// USB 为 true 时，自动把 /storage 下的 U 盘卷（如 /storage/1A2B-3C4D）加入共享。
	USB        bool
	StorageDir string
}

var errOutside = errors.New("路径不在共享目录内")

// List 返回当前全部根目录（静态配置的加上检测到的 U 盘）。
func (r *Roots) List() []Root {
	out := append([]Root(nil), r.Static...)
	if r.USB {
		dir := r.StorageDir
		if dir == "" {
			dir = "/storage"
		}
		ents, _ := os.ReadDir(dir)
		for _, e := range ents {
			n := e.Name()
			if n == "emulated" || n == "self" || n == "primary" || strings.HasPrefix(n, ".") {
				continue
			}
			out = append(out, Root{Name: "U盘-" + n, Path: filepath.Join(dir, n)})
		}
	}
	return out
}

// resolve 把共享中的路径（如 /内部存储/Movies/a.mkv）换成真实路径。
// 返回 real=="" 表示这是虚拟顶层本身。
func (r *Roots) resolve(name string) (real string, err error) {
	clean := path.Clean("/" + name)
	if clean == "/" {
		return "", nil
	}
	parts := strings.SplitN(strings.TrimPrefix(clean, "/"), "/", 2)
	for _, root := range r.List() {
		if root.Name != parts[0] {
			continue
		}
		base := filepath.Clean(root.Path)
		if len(parts) == 1 {
			return base, nil
		}
		p := filepath.Join(base, filepath.FromSlash(parts[1]))
		if p != base && !strings.HasPrefix(p, base+string(filepath.Separator)) {
			return "", errOutside
		}
		return p, nil
	}
	return "", os.ErrNotExist
}

// ---- 实现 webdav.FileSystem ----

func (r *Roots) Mkdir(_ context.Context, name string, perm os.FileMode) error {
	p, err := r.resolve(name)
	if err != nil {
		return err
	}
	if p == "" {
		return os.ErrPermission
	}
	return os.Mkdir(p, perm)
}

func (r *Roots) OpenFile(_ context.Context, name string, flag int, perm os.FileMode) (webdav.File, error) {
	p, err := r.resolve(name)
	if err != nil {
		return nil, err
	}
	if p == "" {
		if flag&(os.O_WRONLY|os.O_RDWR|os.O_CREATE) != 0 {
			return nil, os.ErrPermission
		}
		return &topDir{roots: r.List()}, nil
	}
	return os.OpenFile(p, flag, perm)
}

func (r *Roots) RemoveAll(_ context.Context, name string) error {
	p, err := r.resolve(name)
	if err != nil {
		return err
	}
	if p == "" || r.isRoot(p) {
		return os.ErrPermission // 不允许删除共享根目录本身
	}
	return os.RemoveAll(p)
}

func (r *Roots) Rename(_ context.Context, oldName, newName string) error {
	a, err := r.resolve(oldName)
	if err != nil {
		return err
	}
	b, err := r.resolve(newName)
	if err != nil {
		return err
	}
	if a == "" || b == "" || r.isRoot(a) {
		return os.ErrPermission
	}
	return os.Rename(a, b)
}

func (r *Roots) Stat(_ context.Context, name string) (os.FileInfo, error) {
	p, err := r.resolve(name)
	if err != nil {
		return nil, err
	}
	if p == "" {
		return dirInfo("/"), nil
	}
	fi, err := os.Stat(p)
	if err != nil {
		return nil, err
	}
	if r.isRoot(p) {
		// 根目录在共享中显示为配置的名称
		for _, root := range r.List() {
			if filepath.Clean(root.Path) == p {
				return renamed{fi, root.Name}, nil
			}
		}
	}
	return fi, nil
}

func (r *Roots) isRoot(p string) bool {
	for _, root := range r.List() {
		if filepath.Clean(root.Path) == p {
			return true
		}
	}
	return false
}

// topDir 是虚拟顶层目录，列出各个根目录。
type topDir struct {
	roots []Root
	read  bool
}

func (d *topDir) Close() error                   { return nil }
func (d *topDir) Read([]byte) (int, error)       { return 0, os.ErrInvalid }
func (d *topDir) Seek(int64, int) (int64, error) { return 0, nil }
func (d *topDir) Write([]byte) (int, error)      { return 0, os.ErrPermission }
func (d *topDir) Stat() (os.FileInfo, error)     { return dirInfo("/"), nil }
func (d *topDir) Readdir(count int) ([]os.FileInfo, error) {
	if d.read {
		return nil, nil
	}
	d.read = true
	var out []os.FileInfo
	for _, r := range d.roots {
		if fi, err := os.Stat(r.Path); err == nil {
			out = append(out, renamed{fi, r.Name})
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Name() < out[j].Name() })
	return out, nil
}

type dirInfo string

func (d dirInfo) Name() string       { return string(d) }
func (d dirInfo) Size() int64        { return 0 }
func (d dirInfo) Mode() fs.FileMode  { return fs.ModeDir | 0o755 }
func (d dirInfo) ModTime() time.Time { return time.Time{} }
func (d dirInfo) IsDir() bool        { return true }
func (d dirInfo) Sys() any           { return nil }

type renamed struct {
	os.FileInfo
	name string
}

func (r renamed) Name() string { return r.name }
