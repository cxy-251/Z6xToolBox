package files

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func setup(t *testing.T) (*Roots, string) {
	t.Helper()
	base := t.TempDir()
	share := filepath.Join(base, "share")
	os.MkdirAll(filepath.Join(share, "movies"), 0o755)
	os.WriteFile(filepath.Join(share, "movies", "a.mkv"), []byte("data"), 0o644)
	os.WriteFile(filepath.Join(base, "secret.txt"), []byte("不应被访问"), 0o644)
	// 模拟 /storage：一个 U 盘卷和应被忽略的 emulated、self
	storage := filepath.Join(base, "storage")
	for _, d := range []string{"emulated", "self", "1A2B-3C4D"} {
		os.MkdirAll(filepath.Join(storage, d), 0o755)
	}
	return &Roots{Static: []Root{{Name: "内部存储", Path: share}}, USB: true, StorageDir: storage}, base
}

func TestResolveRejectsTraversal(t *testing.T) {
	r, _ := setup(t)
	for _, p := range []string{"/内部存储/../secret.txt", "/内部存储/movies/../../secret.txt", "/../secret.txt"} {
		real, err := r.resolve(p)
		if err == nil && real != "" {
			if _, statErr := os.Stat(real); statErr == nil && filepath.Base(real) == "secret.txt" {
				t.Errorf("%s 越界访问到了 %s", p, real)
			}
		}
	}
}

func TestResolveUnknownRoot(t *testing.T) {
	r, _ := setup(t)
	if _, err := r.resolve("/不存在/x"); !errors.Is(err, os.ErrNotExist) {
		t.Errorf("未配置的根目录应返回不存在，实际：%v", err)
	}
}

func TestListIncludesUSBButNotEmulated(t *testing.T) {
	r, _ := setup(t)
	names := map[string]bool{}
	for _, root := range r.List() {
		names[root.Name] = true
	}
	if !names["U盘-1A2B-3C4D"] || names["U盘-emulated"] || names["U盘-self"] || !names["内部存储"] {
		t.Errorf("根目录列表不正确：%v", names)
	}
}

func TestCannotDeleteRoot(t *testing.T) {
	r, _ := setup(t)
	if err := r.RemoveAll(context.Background(), "/内部存储"); !errors.Is(err, os.ErrPermission) {
		t.Errorf("删除共享根目录应被拒绝，实际：%v", err)
	}
	if err := r.RemoveAll(context.Background(), "/"); !errors.Is(err, os.ErrPermission) {
		t.Errorf("删除虚拟顶层应被拒绝，实际：%v", err)
	}
}

func TestTopDirListing(t *testing.T) {
	r, _ := setup(t)
	f, err := r.OpenFile(context.Background(), "/", os.O_RDONLY, 0)
	if err != nil {
		t.Fatal(err)
	}
	infos, _ := f.Readdir(-1)
	if len(infos) != 2 {
		t.Fatalf("顶层应列出 2 个根目录，实际 %d 个", len(infos))
	}
	if _, err := r.OpenFile(context.Background(), "/新文件", os.O_CREATE|os.O_WRONLY, 0o644); err == nil {
		t.Error("不应允许在虚拟顶层创建文件")
	}
}
