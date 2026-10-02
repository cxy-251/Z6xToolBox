package library

import (
	"archive/tar"
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func tarOf(t *testing.T, entries ...tar.Header) *bytes.Buffer {
	t.Helper()
	var buf bytes.Buffer
	tw := tar.NewWriter(&buf)
	for _, h := range entries {
		h := h
		body := []byte("data")
		if h.Typeflag == tar.TypeReg {
			h.Size = int64(len(body))
		}
		if h.Mode == 0 {
			h.Mode = 0o644
		}
		tw.WriteHeader(&h)
		if h.Typeflag == tar.TypeReg {
			tw.Write(body)
		}
	}
	tw.Close()
	return &buf
}

func TestExtractTarSafe(t *testing.T) {
	dir := t.TempDir()
	top, files, _, err := extractTar(tarOf(t,
		tar.Header{Name: "Game/", Typeflag: tar.TypeDir, Mode: 0o755},
		tar.Header{Name: "Game/www/index.html", Typeflag: tar.TypeReg},
		tar.Header{Name: "Game/link", Typeflag: tar.TypeSymlink, Linkname: "/etc/passwd"},
	), dir)
	if err != nil || top != "Game" || files != 1 {
		t.Fatalf("top=%q files=%d err=%v", top, files, err)
	}
	if _, err := os.Lstat(filepath.Join(dir, "Game", "link")); err == nil {
		t.Error("符号链接应被跳过")
	}
}

func TestExtractTarRejects(t *testing.T) {
	cases := map[string][]tar.Header{
		"上级目录": {{Name: "../evil", Typeflag: tar.TypeReg}},
		"绝对路径": {{Name: "/tmp/evil", Typeflag: tar.TypeReg}},
		"多个顶层": {{Name: "A/x", Typeflag: tar.TypeReg}, {Name: "B/y", Typeflag: tar.TypeReg}},
		"中途越界": {{Name: "A/../../evil", Typeflag: tar.TypeReg}},
	}
	for name, hs := range cases {
		dir := t.TempDir()
		if _, _, _, err := extractTar(tarOf(t, hs...), dir); err == nil {
			t.Errorf("%s：应被拒绝", name)
		}
	}
}

func TestResolveCIAndWithin(t *testing.T) {
	base := t.TempDir()
	os.MkdirAll(filepath.Join(base, "img", "Pictures"), 0o755)
	os.WriteFile(filepath.Join(base, "img", "Pictures", "Actor1.png"), []byte("x"), 0o644)
	got, err := resolveCI(base, "IMG/pictures/actor1.PNG")
	if err != nil || got != filepath.Join(base, "img", "Pictures", "Actor1.png") {
		t.Fatalf("不区分大小写的匹配失败：%s %v", got, err)
	}
	if _, err := resolveCI(base, "../../etc/passwd"); err == nil {
		t.Error("越界路径应被拒绝")
	}
}

func TestInjectRuntime(t *testing.T) {
	out := string(injectRuntime([]byte(`<!DOCTYPE html><html><HEAD lang="x"><script src="js/main.js"></script></HEAD></html>`)))
	i, j := strings.Index(out, "rpg-runtime.js"), strings.Index(out, "js/main.js")
	if i < 0 || i > j {
		t.Fatalf("runtime 应插在游戏脚本之前：%s", out)
	}
}

func TestDiscoverAndInit(t *testing.T) {
	internal, storage := t.TempDir(), t.TempDir()
	os.MkdirAll(filepath.Join(storage, "emulated"), 0o755)
	usb := filepath.Join(storage, "1A2B-3C4D")
	if _, err := initLibrary(filepath.Join(internal, defaultDir), "机身"); err != nil {
		t.Fatal(err)
	}
	if _, err := initLibrary(usb, "U盘"); err != nil { // U 盘根目录本身即资源库
		t.Fatal(err)
	}
	libs := discover(nil, internal, storage)
	if len(libs) != 2 || libs[0].Location != "internal" || libs[1].Location != "usb" {
		t.Fatalf("发现结果不正确：%+v", libs)
	}
	if _, err := os.Stat(filepath.Join(usb, "standalone_games", "rpg_games")); err != nil {
		t.Error("应建好与 omni-deck 相同的目录结构")
	}
}

func TestNaturalLess(t *testing.T) {
	if !naturalLess("page2.jpg", "page10.jpg") || naturalLess("page10.jpg", "page2.jpg") {
		t.Error("自然排序不正确")
	}
}

func TestScanGamesAndSaveDir(t *testing.T) {
	root := t.TempDir()
	initLibrary(root, "测试")
	g := filepath.Join(root, "standalone_games", "rpg_games", "001 - Test", "www")
	os.MkdirAll(g, 0o755)
	os.WriteFile(filepath.Join(g, "index.html"), []byte("<html></html>"), 0o644)
	games := scanGames([]Lib{{ID: "x", Path: root}})
	got := games["001 - Test"]
	if got == nil || got.Type != "rpg" || got.saveDir != filepath.Join(g, "save") {
		t.Fatalf("扫描结果不正确：%+v", got)
	}
}
