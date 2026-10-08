package library

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"encoding/json"
	"image"
	"image/png"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"golang.org/x/text/encoding/simplifiedchinese"

	"gopkg.in/yaml.v3"

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
	// xxx.asar 不存在时使用同名目录 xxx（手机上没有 v101.asar -> v101 这样的软链接）
	os.MkdirAll(filepath.Join(base, "dlc", "v101"), 0o755)
	os.WriteFile(filepath.Join(base, "dlc", "v101", "info.json"), []byte("{}"), 0o644)
	if got, _ := resolveCI(base, "dlc/v101.asar/info.json"); got != filepath.Join(base, "dlc", "v101", "info.json") {
		t.Errorf(".asar 未映射到同名目录：%s", got)
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
	m.cfg = func() Config { c := DefaultConfig(); c.Internal = t.TempDir(); c.Storage = t.TempDir(); return c }()
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
	m.cfg = func() Config {
		c := DefaultConfig()
		c.Roots = []string{root}
		c.Internal = t.TempDir()
		c.Storage = t.TempDir()
		return c
	}()
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

func TestFormats(t *testing.T) {
	// GBK 编码的 TXT：能正确解码并按「第 N 章」分章
	gbk, _ := simplifiedchinese.GBK.NewEncoder().String("序言一段\n第一章 开端\n正文甲\n\n第二章 发展\n正文乙\n")
	p := filepath.Join(t.TempDir(), "a.txt")
	os.WriteFile(p, []byte(gbk), 0o644)
	ch, err := parseTXT(p)
	if err != nil || len(ch) != 3 || ch[1].Title != "第一章 开端" || ch[2].Paras[0] != "正文乙" {
		t.Fatalf("TXT 分章不正确：%+v %v", ch, err)
	}
	// Markdown：标题、代码块、列表、行内标记
	md := parseMarkdown("# 总标题\n\n简介 **粗体** 与 [链接](http://x)\n\n## 第一节\n\n- 项目一\n\n```go\nfmt.Println(1)\n```\n\n## 第二节\n\n内容\n")
	if md[1].Text != "简介 粗体 与 链接" || md[3].Kind != "li" || md[4].Kind != "code" || md[4].Text != "fmt.Println(1)" {
		t.Fatalf("Markdown 解析不正确：%+v", md)
	}
	if doc := splitDoc("x", md); len(doc) != 3 || doc[1].Title != "第一节" {
		t.Fatalf("应按二级标题分章：%+v", doc)
	}
	// RST：下划线标题、:: 代码块、指令略去
	rst := parseRST("标题\n====\n\n说明 ``code`` 示例::\n\n    print(1)\n\n.. note::\n\n   被略去\n\n小节\n----\n\n- 列表\n")
	if rst[0].Kind != "h" || rst[0].Level != 1 || rst[1].Text != "说明 code 示例:" || rst[2].Text != "print(1)" || rst[3].Level != 2 || rst[4].Kind != "li" {
		t.Fatalf("RST 解析不正确：%+v", rst)
	}
}

func TestFrontMatter(t *testing.T) {
	title, body := frontMatter("---\ntitle: \"第 74 章：WebGPU\"\ntags: [a]\n---\n# 正文标题\n内容\n")
	if title != "第 74 章：WebGPU" || !strings.HasPrefix(body, "# 正文标题") {
		t.Fatalf("元数据处理不正确：%q %q", title, body)
	}
	if blocks := parseMarkdown(body); blocks[0].Kind != "h" {
		t.Fatalf("去掉元数据后第一块应为标题：%+v", blocks)
	}
	if _, b := frontMatter("没有元数据\n---\n"); b != "没有元数据\n---\n" {
		t.Fatal("没有元数据时应原样返回")
	}
}

func TestShortvideoItemsAndAlbums(t *testing.T) {
	root := t.TempDir()
	initLibrary(root, "测试")
	for _, f := range []string{"抖音/作者甲/2025-01-12_新_1.mp4", "抖音/作者甲/2024-03-01_旧_2.mp4", "抖音/作者乙/3.mp4",
		"抖音/作者甲/2025-02-01_图集/2.webp", "抖音/作者甲/2025-02-01_图集/10.webp", "抖音/作者甲/封面.jpg",
		"抖音/作者甲/.thumbs/2024-03-01_旧_2.mp4.webp", "抖音/作者甲/.thumbs/2025-02-01_图集.webp", "抖音/作者乙/#话题.mp4", "抖音/作者乙/.thumbs/#话题.mp4.webp"} {
		p := filepath.Join(root, mediaRoot, "shortvideo", filepath.FromSlash(f))
		os.MkdirAll(filepath.Dir(p), 0o755)
		os.WriteFile(p, []byte("x"), 0o644)
	}
	m := New()
	m.env = &core.Env{Config: &core.Config{DataDir: t.TempDir()}, Log: slog.New(slog.NewTextHandler(io.Discard, nil))}
	m.cfg = func() Config {
		c := DefaultConfig()
		c.Roots = []string{root}
		c.Internal = t.TempDir()
		c.Storage = t.TempDir()
		return c
	}()
	m.likes.path = filepath.Join(m.env.Config.DataDir, "likes.json")
	m.likes.load()
	mux := http.NewServeMux()
	m.shortvideoRoutes(mux)
	get := func(url string, v any) {
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest("GET", url, nil))
		if err := json.Unmarshal(rec.Body.Bytes(), v); err != nil {
			t.Fatalf("%s：%v %s", url, err, rec.Body)
		}
	}
	var res struct {
		Total int `json:"total"`
		Items []struct {
			Rel    string   `json:"rel_path"`
			Kind   string   `json:"kind"`
			Images []string `json:"images"`
			Thumb  string   `json:"thumb"`
		} `json:"items"`
	}
	get("/api/library/shortvideo/items?platform=douyin&folder="+url.QueryEscape("作者甲"), &res)
	// 博主目录下直接放的图片（封面.jpg）不算作品；图集按日期排在最前（从新到旧）
	if res.Total != 3 || res.Items[0].Kind != "images" || len(res.Items[0].Images) != 2 || res.Items[2].Rel != "作者甲/2024-03-01_旧_2.mp4" {
		t.Fatalf("作品列表不正确：%+v", res)
	}
	if !strings.HasSuffix(res.Items[0].Thumb, "/.thumbs/2025-02-01_%E5%9B%BE%E9%9B%86.webp") && !strings.HasSuffix(res.Items[0].Thumb, "/.thumbs/2025-02-01_图集.webp") || res.Items[1].Thumb != "" || res.Items[2].Thumb == "" {
		t.Errorf("封面不正确：%q %q %q", res.Items[0].Thumb, res.Items[1].Thumb, res.Items[2].Thumb)
	}
	var b struct {
		Items []struct {
			Thumb string `json:"thumb"`
		} `json:"items"`
	}
	get("/api/library/shortvideo/items?platform=douyin&folder="+url.QueryEscape("作者乙"), &b)
	if len(b.Items) != 2 || b.Items[0].Thumb == "" && b.Items[1].Thumb == "" {
		t.Errorf("名称排在 .thumbs 之前的作品（#话题.mp4）也应找到封面：%+v", b.Items)
	}
	if !strings.HasSuffix(res.Items[0].Images[0], "2.webp") {
		t.Errorf("图集中的图片应按自然顺序排列：%v", res.Items[0].Images)
	}
	m.likes.set("douyin", "作者乙/3.mp4", true)
	get("/api/library/shortvideo/items?platform=douyin&liked=1", &res)
	if res.Total != 1 || res.Items[0].Rel != "作者乙/3.mp4" {
		t.Fatalf("只看喜欢不正确：%+v", res)
	}
	get("/api/library/shortvideo/items?platform=douyin&limit=2&offset=2", &res)
	if res.Total != 5 || len(res.Items) != 2 {
		t.Fatalf("分页不正确：%+v", res)
	}
	if got := m.channelVideos("all", 0); len(got) != 4 {
		t.Fatalf("多联放映不应包含图集：%d", len(got))
	}
	var folders []struct {
		Name  string `json:"name"`
		Count int    `json:"count"`
		Liked int    `json:"liked"`
	}
	get("/api/library/shortvideo/folders?platform=douyin", &folders)
	by := map[string][2]int{}
	for _, f := range folders {
		by[f.Name] = [2]int{f.Count, f.Liked}
	}
	if len(folders) != 2 || by["作者甲"] != [2]int{3, 0} || by["作者乙"] != [2]int{2, 1} {
		t.Fatalf("博主列表不正确：%+v", folders)
	}
}

func TestReadID3Picture(t *testing.T) {
	img := []byte("\x89PNG\r\n\x1a\nfakeimage")
	// ID3v2.3：APIC 帧（编码 1 = UTF-16，描述为空：BOM + 两个 0 字节）
	body := append([]byte{1}, []byte("image/png\x00")...)
	body = append(body, 3, 0xff, 0xfe, 0, 0)
	body = append(body, img...)
	frame := append([]byte("TIT2\x00\x00\x00\x02\x00\x00\x00a"), []byte("APIC")...)
	frame = append(frame, byte(len(body)>>24), byte(len(body)>>16), byte(len(body)>>8), byte(len(body)), 0, 0)
	frame = append(frame, body...)
	n := len(frame)
	tag := append([]byte{'I', 'D', '3', 3, 0, 0, byte(n >> 21 & 0x7f), byte(n >> 14 & 0x7f), byte(n >> 7 & 0x7f), byte(n & 0x7f)}, frame...)
	got, mime, err := readID3Picture(bytes.NewReader(append(tag, "mp3data"...)))
	if err != nil || mime != "image/png" || !bytes.Equal(got, img) {
		t.Fatalf("读取 APIC 失败：%q %s %v", got, mime, err)
	}
	if _, _, err := readID3Picture(bytes.NewReader([]byte("no tag here"))); err == nil {
		t.Error("没有标签时应返回错误")
	}
}

func TestShrinkImage(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 1000, 800))
	for i := range src.Pix {
		src.Pix[i] = 200
	}
	var buf bytes.Buffer
	png.Encode(&buf, src)
	out, err := shrinkImage(buf.Bytes(), 300)
	if err != nil {
		t.Fatal(err)
	}
	img, format, err := image.Decode(bytes.NewReader(out))
	if err != nil || format != "jpeg" || img.Bounds().Dx() != 300 || img.Bounds().Dy() != 240 {
		t.Fatalf("缩放结果不正确：%v %s %v", img.Bounds(), format, err)
	}
	if r, _, _, _ := img.At(10, 10).RGBA(); r>>8 < 190 || r>>8 > 210 {
		t.Errorf("颜色应保持不变：%d", r>>8)
	}
}

func TestReadConfigDefaults(t *testing.T) {
	var cfg core.Config
	err := yaml.Unmarshal([]byte("modules:\n  library:\n    page_size: 30\n    cover_px: 5\n    thumbs:\n      hot: 45\n"), &cfg)
	if err != nil {
		t.Fatal(err)
	}
	c, err := ReadConfig(&cfg)
	if err != nil {
		t.Fatal(err)
	}
	d := DefaultConfig()
	if c.PageSize != 30 || c.Thumbs.Hot != 45 {
		t.Errorf("写了的项应生效：%+v", c)
	}
	if c.SlideSeconds != d.SlideSeconds || c.Thumbs.Cool != d.Thumbs.Cool || c.Thumbs.Jobs != d.Thumbs.Jobs || c.RescanMinutes != d.RescanMinutes {
		t.Errorf("没写的项应为默认值：%+v", c)
	}
	if c.CoverPx != d.CoverPx {
		t.Errorf("不合理的值（cover_px: 5）应换回默认值：%d", c.CoverPx)
	}
}
