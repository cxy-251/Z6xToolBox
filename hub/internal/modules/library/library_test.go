package library

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"encoding/json"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"z6x/hub/internal/core"
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

// TestEmptyListsAreArrays 防止回归：资源库为空时接口应返回 []，返回 null 会让页面停在「读取中」。
func TestEmptyListsAreArrays(t *testing.T) {
	m := New()
	m.cfg = Config{Internal: t.TempDir(), Storage: t.TempDir()}
	if got := m.listManga(); got == nil {
		t.Error("漫画列表为空时应返回空切片而不是 nil")
	}
	if got := m.listCreators(); got == nil {
		t.Error("作者列表为空时应返回空切片而不是 nil")
	}
	if got, _ := m.listClips("抖音", "无", 0, 10); got == nil {
		t.Error("视频列表为空时应返回空切片而不是 nil")
	}
}

// TestMediaChannelsAndLikes：扫描与 omni-deck 相同结构的短视频，按频道取视频，点赞格式与 omni-deck 一致。
func TestMediaChannelsAndLikes(t *testing.T) {
	root := t.TempDir()
	initLibrary(root, "测试")
	for _, f := range []string{"抖音/作者甲/2025-01-12_标题_1.mp4", "抖音/作者甲/2024-03-01_更早_2.mp4", "抖音/作者乙/3.mp4", "快手/作者丙/4.mp4", "抖音/作者甲/图集/1.jpg"} {
		p := filepath.Join(root, mediaRoot, "shortvideo", filepath.FromSlash(f))
		os.MkdirAll(filepath.Dir(p), 0o755)
		os.WriteFile(p, []byte("x"), 0o644)
	}
	os.MkdirAll(filepath.Join(root, mediaRoot, "audio", "standard", "专辑一"), 0o755)
	os.WriteFile(filepath.Join(root, mediaRoot, "audio", "standard", "专辑一", "01.mp3"), []byte("x"), 0o644)

	m := New()
	m.env = &core.Env{Config: &core.Config{DataDir: t.TempDir()}, Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
	m.cfg = Config{Roots: []string{root}, Internal: t.TempDir(), Storage: t.TempDir()}
	m.likes.path = filepath.Join(m.env.Config.DataDir, "likes.json")
	m.likes.load()

	all := m.channelVideos("all", 0)
	if len(all) != 4 {
		t.Fatalf("应有 4 条视频（图集不算），实际 %d", len(all))
	}
	a := m.channelVideos("douyin:作者甲", 0)
	if len(a) != 2 || a[0].Rel != "作者甲/2024-03-01_更早_2.mp4" {
		t.Fatalf("作者频道应按日期从早到晚：%+v", a)
	}
	s1, s2 := m.channelVideos("all", 42), m.channelVideos("all", 42)
	for i := range s1 {
		if s1[i] != s2[i] {
			t.Fatal("同一个 seed 的随机顺序应当一致")
		}
	}
	m.likes.set("douyin", "作者乙/3.mp4", true)
	if got := m.channelVideos("liked", 0); len(got) != 1 || got[0].Folder != "作者乙" {
		t.Fatalf("点赞频道不正确：%+v", got)
	}
	raw, _ := os.ReadFile(m.likes.path)
	var saved map[string][]string
	if json.Unmarshal(raw, &saved) != nil || len(saved["douyin"]) != 1 || saved["tiktok"] == nil {
		t.Fatalf("点赞文件应与 omni-deck 格式相同：%s", raw)
	}
	if sc := m.audioScopes(); len(sc) != 2 || sc[1].ID != "std:专辑一" {
		t.Fatalf("音声范围不正确：%+v", sc)
	}
	if c := (matrixConfig{Layout: 5, Slots: []matrixSlot{{ChannelID: "x", Sound: "?"}}}).clean(); c.Layout != 3 || len(c.Slots) != 3 || c.Slots[0].Sound != "video" {
		t.Fatalf("配置清理不正确：%+v", c)
	}
}

// TestParseEPUB：按 spine 顺序分章，标题取自 h1/h2，脚本不进入正文。
func TestParseEPUB(t *testing.T) {
	p := filepath.Join(t.TempDir(), "书.epub")
	f, _ := os.Create(p)
	zw := zip.NewWriter(f)
	add := func(name, body string) { w, _ := zw.Create(name); w.Write([]byte(body)) }
	add("mimetype", "application/epub+zip")
	add("META-INF/container.xml", `<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>`)
	add("OEBPS/content.opf", `<package><metadata><dc:title>测试书</dc:title><dc:creator>某人</dc:creator></metadata>
<manifest><item id="c2" href="text/c2.xhtml"/><item href="text/c1.xhtml" id="c1"/><item id="empty" href="text/e.xhtml"/></manifest>
<spine><itemref idref="c1"/><itemref idref="empty"/><itemref idref="c2"/></spine></package>`)
	add("OEBPS/text/c1.xhtml", `<html><head><title>x</title></head><body><h2>第一章 开始</h2><p>第一段&amp;</p><script>alert(1)</script><p>第二段</p></body></html>`)
	add("OEBPS/text/e.xhtml", `<html><body><div> </div></body></html>`)
	add("OEBPS/text/c2.xhtml", `<html><body><p>没有标题的一章，第一段比较长，超过三十个字的话会被截断显示为标题的一部分啊啊啊</p></body></html>`)
	zw.Close()
	f.Close()
	title, author, ch, err := parseEPUB(p)
	if err != nil || title != "测试书" || author != "某人" {
		t.Fatalf("元数据不正确：%q %q %v", title, author, err)
	}
	if len(ch) != 2 || ch[0].Title != "第一章 开始" || len(ch[0].Paras) != 3 || ch[0].Paras[1] != "第一段&" {
		t.Fatalf("分章不正确：%+v", ch)
	}
	for _, p := range ch[0].Paras {
		if strings.Contains(p, "alert") {
			t.Fatal("脚本内容不应进入正文")
		}
	}
	if !strings.HasSuffix(ch[1].Title, "…") {
		t.Fatalf("无标题章节应取首段前 30 字：%q", ch[1].Title)
	}
}
