package library

import (
	"encoding/json"
	"io/fs"
	"math/rand"
	"net/http"
	"os"
	pathpkg "path"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"z6x/hub/internal/core"
)

// 媒体专区（与 omni-deck 的短视频、多联放映、音声兼容）：
//   - 平台标识与目录名和 omni-deck 的 manifest 一致：douyin=抖音、kuaishou=快手、tiktok=TikTok；
//   - 视频的相对路径为「作者/文件名」（相对于平台目录），点赞文件格式与 omni-deck 的
//     var/data/shortvideo_likes.json 相同（{平台: [相对路径…]}），可直接导入；
//   - 多联放映的配置字段与 omni-deck 的 shortvideo_matrix_config.json 相同。
// 设备上没有 ffprobe，不知道视频宽高；横屏视频由网页在读到画面尺寸后跳过（omni-deck 网页版的同一做法）。

type platform struct{ ID, Dir, Label string }

var platforms = []platform{
	{"kuaishou", "快手", "Kwai"},
	{"douyin", "抖音", "Douyin"},
	{"tiktok", "TikTok", "TikTok"},
}

// thumbDir 是短视频封面目录（在 shortvideo/ 下，与平台目录并列；以点开头，omni-deck 与媒体扫描都会跳过）。
const thumbDir = ".z6x-thumbs"

var videoExts = map[string]bool{".mp4": true, ".webm": true, ".mov": true, ".m4v": true, ".mkv": true}

// 与 omni-deck 相同的音频格式。WMA 会列出，但浏览器普遍不能播放。
var audioExts = map[string]bool{".mp3": true, ".m4a": true, ".m4b": true, ".flac": true, ".ogg": true, ".opus": true, ".aac": true, ".wav": true, ".wma": true}

// svItem 是一条短视频。
type svItem struct {
	Platform string   // 平台标识
	Rel      string   // 相对于平台目录：作者/文件名
	Folder   string   // 作者（第一级子目录），直接放在平台目录下的为「未分类」
	URL      string   // 播放地址
	Images   []string // 图集作品（博主目录下装着图片的子目录）的图片地址；视频为空
	Thumb    string   // 预先生成的封面（shortvideo/.z6x-thumbs/<平台>/<相对路径>.webp），没有为空
	date     int      // 文件名开头的日期（YYYYMMDD），没有为 0
	mtime    int64
}

func (it *svItem) title() string {
	return strings.TrimSuffix(filepath.Base(it.Rel), filepath.Ext(it.Rel))
}

// track 是一条音声。
type track struct {
	Title  string `json:"title"`
	Album  string `json:"album"`
	Artist string `json:"artist,omitempty"` // 仅「音乐」来源
	URL    string `json:"stream_url"`
	Rel    string `json:"rel_path"`
	IsNSFW bool   `json:"is_nsfw"`
	Source string `json:"source"` // standard、nsfw、music
}

// mediaIndex 缓存扫描结果。手机上的短视频可达数万条，扫描一遍需要数秒，因此在后台进行，
// 请求时先返回现有结果，超过 refreshAfter 才触发重新扫描。
type mediaIndex struct {
	mu       sync.Mutex
	videos   map[string][]*svItem // 平台 → 视频（按文件名中的日期从早到晚）
	albums   map[string][]*svItem // 平台 → 图集作品（只用于短视频页，多联放映不播放图集）
	tracks   []track
	at       time.Time
	scanning bool
	// ready 在第一次扫描完成时关闭。扫描在进行中（例如启动时的扫描，手机上需要 40 秒到 2 分钟）时，
	// 新请求等它完成，而不是拿到空列表、页面显示「没有内容」
	ready     chan struct{}
	readyOnce sync.Once
}

const refreshAfter = 10 * time.Minute

var datePrefix = regexp.MustCompile(`^(\d{4})-?(\d{2})-?(\d{2})`)

func (m *Module) media() (map[string][]*svItem, []track) {
	ix := &m.mediaIx
	ix.mu.Lock()
	if ix.ready == nil {
		ix.ready = make(chan struct{})
	}
	if ix.videos == nil && ix.scanning {
		ready := ix.ready
		ix.mu.Unlock()
		select {
		case <-ready:
		case <-time.After(3 * time.Minute):
		}
		ix.mu.Lock()
	}
	stale := time.Since(ix.at) > refreshAfter
	first := ix.videos == nil
	if (stale || first) && !ix.scanning {
		ix.scanning = true
		if first {
			ix.mu.Unlock()
			m.scanMedia() // 第一次同步扫描，之后都在后台
			ix.mu.Lock()
		} else {
			go m.scanMedia()
		}
	}
	defer ix.mu.Unlock()
	return ix.videos, ix.tracks
}

func (m *Module) scanMedia() {
	start := time.Now()
	videos := map[string][]*svItem{}
	albums := map[string][]*svItem{}
	albumAt := map[string]*svItem{} // 平台/图集相对路径 → 图集
	var tracks []track
	for _, lib := range m.libs() {
		for _, p := range platforms {
			base := filepath.Join(lib.Path, mediaRoot, "shortvideo", p.Dir)
			// 封面由 Deck 生成后同步过来（scripts/shortvideo_thumbs.sh），hub 不生成；这里只记下哪些作品有封面
			thumbBase := filepath.Join(lib.Path, mediaRoot, "shortvideo", thumbDir, p.Dir)
			thumbs := map[string]string{}
			filepath.WalkDir(thumbBase, func(fp string, d fs.DirEntry, err error) error {
				if err == nil && !d.IsDir() && strings.HasSuffix(fp, ".webp") {
					rel, _ := filepath.Rel(thumbBase, fp)
					thumbs[strings.TrimSuffix(filepath.ToSlash(rel), ".webp")] = libURL(lib.ID, filepath.Join(mediaRoot, "shortvideo", thumbDir, p.Dir, rel))
				}
				return nil
			})
			filepath.WalkDir(base, func(fp string, d fs.DirEntry, err error) error {
				if err != nil {
					return nil
				}
				if d.IsDir() {
					if strings.HasPrefix(d.Name(), ".") {
						return fs.SkipDir
					}
					return nil
				}
				if strings.HasPrefix(d.Name(), ".") {
					return nil
				}
				rel, _ := filepath.Rel(base, fp)
				rel = filepath.ToSlash(rel)
				if isImage(d.Name()) {
					// 图集：博主/作品/图片（至少两级目录）；直接放在博主目录下的图片可能是封面，不算作品
					dir := pathpkg.Dir(rel)
					if strings.Count(rel, "/") < 2 {
						return nil
					}
					key := p.ID + "/" + dir
					al := albumAt[key]
					if al == nil {
						al = &svItem{Platform: p.ID, Rel: dir, Folder: dir[:strings.IndexByte(dir, '/')]}
						if mm := datePrefix.FindStringSubmatch(pathpkg.Base(dir)); mm != nil {
							al.date, _ = strconv.Atoi(mm[1] + mm[2] + mm[3])
						}
						if info, err := d.Info(); err == nil {
							al.mtime = info.ModTime().Unix()
						}
						al.Thumb = thumbs[dir]
						albumAt[key] = al
						albums[p.ID] = append(albums[p.ID], al)
					}
					al.Images = append(al.Images, libURL(lib.ID, filepath.Join(mediaRoot, "shortvideo", p.Dir, filepath.FromSlash(rel))))
					return nil
				}
				if !videoExts[strings.ToLower(filepath.Ext(fp))] {
					return nil
				}
				folder := "未分类"
				if i := strings.IndexByte(rel, '/'); i > 0 {
					folder = rel[:i]
				}
				it := &svItem{Platform: p.ID, Rel: rel, Folder: folder,
					URL: libURL(lib.ID, filepath.Join(mediaRoot, "shortvideo", p.Dir, filepath.FromSlash(rel)))}
				if mm := datePrefix.FindStringSubmatch(d.Name()); mm != nil {
					it.date, _ = strconv.Atoi(mm[1] + mm[2] + mm[3])
				}
				if info, err := d.Info(); err == nil {
					it.mtime = info.ModTime().Unix()
				}
				it.Thumb = thumbs[rel]
				videos[p.ID] = append(videos[p.ID], it)
				return nil
			})
		}
		for _, nsfw := range []bool{false, true} {
			sub := "standard"
			if nsfw {
				sub = "nsfw"
			}
			base := filepath.Join(lib.Path, mediaRoot, "audio", sub)
			filepath.WalkDir(base, func(fp string, d fs.DirEntry, err error) error {
				if err != nil || d.IsDir() || !audioExts[strings.ToLower(filepath.Ext(fp))] || strings.HasPrefix(d.Name(), ".") {
					return nil
				}
				rel, _ := filepath.Rel(base, fp)
				rel = filepath.ToSlash(rel)
				album := "经典单曲" // 与 omni-deck 相同：直接放在根目录下的归入默认专辑
				if nsfw {
					album = "未分类音声"
				}
				if i := strings.IndexByte(rel, '/'); i > 0 {
					album = rel[:i]
				}
				tracks = append(tracks, track{Title: strings.TrimSuffix(d.Name(), filepath.Ext(d.Name())), Album: album, Rel: rel, IsNSFW: nsfw, Source: sub,
					URL: libURL(lib.ID, filepath.Join(mediaRoot, "audio", sub, filepath.FromSlash(rel)))})
				return nil
			})
		}
	}
	// 资源库以外的音乐目录：上一级目录为专辑，再上一级为歌手；地址为 /music/<序号>/<相对路径>
	for n, root := range m.cfg.Music {
		filepath.WalkDir(root, func(fp string, d fs.DirEntry, err error) error {
			if err != nil {
				return nil
			}
			if d.IsDir() && strings.HasPrefix(d.Name(), ".") {
				return fs.SkipDir
			}
			if d.IsDir() || !audioExts[strings.ToLower(filepath.Ext(fp))] || strings.HasPrefix(d.Name(), ".") {
				return nil
			}
			rel, _ := filepath.Rel(root, fp)
			rel = filepath.ToSlash(rel)
			parts := strings.Split(rel, "/")
			album, artist := "未分类", "未知歌手"
			if len(parts) >= 2 {
				album = parts[len(parts)-2]
			}
			if len(parts) >= 3 {
				artist = parts[len(parts)-3]
			}
			segs := strings.Split(rel, "/")
			for k := range segs {
				segs[k] = urlPathEscape(segs[k])
			}
			tracks = append(tracks, track{Title: strings.TrimSuffix(d.Name(), filepath.Ext(d.Name())), Album: album, Artist: artist,
				Rel: strconv.Itoa(n) + "/" + rel, Source: "music", URL: "/music/" + strconv.Itoa(n) + "/" + strings.Join(segs, "/")})
			return nil
		})
	}
	for _, list := range videos {
		sort.Slice(list, func(i, j int) bool { // 与 omni-deck 相同：有日期的按日期从早到晚，没有的排在后面
			a, b := list[i], list[j]
			if (a.date == 0) != (b.date == 0) {
				return a.date != 0
			}
			if a.date != b.date {
				return a.date < b.date
			}
			return a.mtime < b.mtime
		})
	}
	sort.SliceStable(tracks, func(i, j int) bool {
		a, b := tracks[i], tracks[j]
		if a.Source != b.Source {
			return a.Source < b.Source
		}
		if a.Artist != b.Artist {
			return naturalLess(a.Artist, b.Artist)
		}
		if a.Album != b.Album {
			return a.Album < b.Album
		}
		return naturalLess(a.Rel, b.Rel)
	})
	for _, list := range albums {
		for _, al := range list {
			sort.Slice(al.Images, func(i, j int) bool { return naturalLess(al.Images[i], al.Images[j]) })
		}
	}
	ix := &m.mediaIx
	ix.mu.Lock()
	ix.videos, ix.albums, ix.tracks, ix.at, ix.scanning = videos, albums, tracks, time.Now(), false
	if ix.ready == nil {
		ix.ready = make(chan struct{})
	}
	ready := ix.ready
	ix.mu.Unlock()
	ix.readyOnce.Do(func() { close(ready) })
	n := 0
	for _, v := range videos {
		n += len(v)
	}
	m.env.Log.Info("媒体扫描完成", "videos", n, "tracks", len(tracks), "elapsed", time.Since(start).Round(time.Millisecond))
}

// ---------------- 点赞 ----------------

type likeStore struct {
	mu   sync.Mutex
	path string
	data map[string]map[string]bool // 平台 → 相对路径集合
}

func (l *likeStore) load() {
	l.data = map[string]map[string]bool{}
	raw, err := os.ReadFile(l.path)
	if err != nil {
		return
	}
	var in map[string][]string
	if json.Unmarshal(raw, &in) == nil {
		l.merge(in)
	}
}

func (l *likeStore) merge(in map[string][]string) int {
	n := 0
	for p, rels := range in {
		if l.data[p] == nil {
			l.data[p] = map[string]bool{}
		}
		for _, r := range rels {
			if !l.data[p][r] {
				l.data[p][r] = true
				n++
			}
		}
	}
	return n
}

func (l *likeStore) has(p, rel string) bool { l.mu.Lock(); defer l.mu.Unlock(); return l.data[p][rel] }

func (l *likeStore) set(p, rel string, on bool) error {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.data[p] == nil {
		l.data[p] = map[string]bool{}
	}
	if on {
		l.data[p][rel] = true
	} else {
		delete(l.data[p], rel)
	}
	return l.saveLocked()
}

// saveLocked 写成与 omni-deck 相同的格式，先写临时文件再改名。
func (l *likeStore) saveLocked() error {
	out := map[string][]string{}
	for _, p := range platforms {
		out[p.ID] = []string{}
	}
	for p, set := range l.data {
		list := make([]string, 0, len(set))
		for r := range set {
			list = append(list, r)
		}
		sort.Strings(list)
		out[p] = list
	}
	b, _ := json.MarshalIndent(out, "", "  ")
	os.MkdirAll(filepath.Dir(l.path), 0o755)
	tmp := l.path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, l.path)
}

// ---------------- 多联放映 ----------------

type matrixSlot struct {
	ChannelID  string `json:"channel_id"`
	Shuffle    bool   `json:"shuffle"`
	Muted      bool   `json:"muted"`
	Sound      string `json:"sound"`       // video（视频原声）或 audio（以音声代替原声）
	AudioScope string `json:"audio_scope"` // all、std:<专辑>、nsfw:<专辑>
	AudioMode  string `json:"audio_mode"`  // list、single、random
}

type matrixConfig struct {
	Layout     int          `json:"layout"`
	FocusAudio bool         `json:"focus_audio"`
	BarPinned  bool         `json:"bar_pinned"`
	Slots      []matrixSlot `json:"slots"`
}

func defaultMatrixConfig() matrixConfig {
	return matrixConfig{Layout: 3, FocusAudio: true, Slots: []matrixSlot{
		{ChannelID: "liked"}, {ChannelID: "all", Shuffle: true, Muted: true}, {ChannelID: "all", Shuffle: true, Muted: true}}}
}

// clean 只保留认识的取值，缺少的屏用默认值补齐。
func (c matrixConfig) clean() matrixConfig {
	def := defaultMatrixConfig()
	if c.Layout != 2 {
		c.Layout = 3
	}
	for len(c.Slots) < 3 {
		c.Slots = append(c.Slots, def.Slots[len(c.Slots)])
	}
	c.Slots = c.Slots[:3]
	for i := range c.Slots {
		s := &c.Slots[i]
		if s.ChannelID == "" {
			s.ChannelID = "all"
		}
		if s.Sound != "audio" {
			s.Sound = "video"
		}
		if s.AudioScope == "" {
			s.AudioScope = "all"
		}
		if s.AudioMode != "list" && s.AudioMode != "single" {
			s.AudioMode = "random"
		}
	}
	return c
}

func (m *Module) matrixConfigPath() string {
	return filepath.Join(m.env.Config.DataDir, "library", "matrix_config.json")
}

func (m *Module) loadMatrixConfig() matrixConfig {
	c := defaultMatrixConfig()
	if raw, err := os.ReadFile(m.matrixConfigPath()); err == nil {
		var in matrixConfig
		if json.Unmarshal(raw, &in) == nil {
			c = in
		}
	}
	return c.clean()
}

type channel struct {
	ID    string `json:"id"`
	Group string `json:"group"`
	Label string `json:"label"`
	Count int    `json:"count"`
}

// channels：我的点赞、全部平台、每个平台、每个作者（按视频数从多到少）。
func (m *Module) channels() []channel {
	videos, _ := m.media()
	liked, total := 0, 0
	for _, list := range videos {
		total += len(list)
		for _, it := range list {
			if m.likes.has(it.Platform, it.Rel) {
				liked++
			}
		}
	}
	out := []channel{
		{"liked", "常用", "❤️ 我的点赞 (" + strconv.Itoa(liked) + ")", liked},
		{"all", "常用", "🌐 全部平台 (" + strconv.Itoa(total) + ")", total},
	}
	for _, p := range platforms {
		if n := len(videos[p.ID]); n > 0 {
			out = append(out, channel{p.ID + ":", "常用", p.Label + " · 全部 (" + strconv.Itoa(n) + ")", n})
		}
	}
	for _, p := range platforms {
		counts := map[string]int{}
		for _, it := range videos[p.ID] {
			counts[it.Folder]++
		}
		names := make([]string, 0, len(counts))
		for a := range counts {
			names = append(names, a)
		}
		sort.Slice(names, func(i, j int) bool {
			if counts[names[i]] != counts[names[j]] {
				return counts[names[i]] > counts[names[j]]
			}
			return names[i] < names[j]
		})
		for _, a := range names {
			out = append(out, channel{p.ID + ":" + a, p.Label, a + " (" + strconv.Itoa(counts[a]) + ")", counts[a]})
		}
	}
	return out
}

// channelVideos 把频道 id（liked、all、<平台>:、<平台>:<作者>）换成视频列表；seed 非 0 时按它洗牌，
// 同一个 seed 结果固定，网页分页读取时前后两页才对得上。
func (m *Module) channelVideos(id string, seed int64) []*svItem {
	videos, _ := m.media()
	var out []*svItem
	switch id {
	case "liked":
		for _, p := range platforms {
			for _, it := range videos[p.ID] {
				if m.likes.has(it.Platform, it.Rel) {
					out = append(out, it)
				}
			}
		}
	case "all", "random":
		for _, p := range platforms {
			out = append(out, videos[p.ID]...)
		}
	default:
		pid, author, _ := strings.Cut(id, ":")
		for _, it := range videos[pid] {
			if author == "" || it.Folder == author {
				out = append(out, it)
			}
		}
	}
	if seed != 0 {
		out = append([]*svItem(nil), out...)
		r := rand.New(rand.NewSource(seed))
		r.Shuffle(len(out), func(i, j int) { out[i], out[j] = out[j], out[i] })
	}
	return out
}

type audioScope struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Count int    `json:"count"`
}

func scopeOf(t track) string {
	if t.Source == "music" {
		return "music:" // 多联中音乐只作为一个整体范围，歌手太多，不逐个列出
	}
	if t.IsNSFW {
		return "nsfw:" + t.Album
	}
	return "std:" + t.Album
}

func (m *Module) audioScopes() []audioScope {
	_, tracks := m.media()
	counts := map[string]int{}
	for _, t := range tracks {
		counts[scopeOf(t)]++
	}
	out := []audioScope{{"all", "全部音声", len(tracks)}}
	ids := make([]string, 0, len(counts))
	for id := range counts {
		ids = append(ids, id)
	}
	sort.Slice(ids, func(i, j int) bool {
		return counts[ids[i]] > counts[ids[j]] || counts[ids[i]] == counts[ids[j]] && ids[i] < ids[j]
	})
	for _, id := range ids {
		_, name, _ := strings.Cut(id, ":")
		if id == "music:" {
			name = "🎵 音乐（全部）"
		}
		out = append(out, audioScope{id, name, counts[id]})
	}
	return out
}

func (m *Module) mediaRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/matrix/channels", func(w http.ResponseWriter, _ *http.Request) {
		core.WriteJSON(w, map[string]any{"channels": m.channels(), "audio_scopes": m.audioScopes()})
	})
	r.HandleFunc("GET /api/library/matrix/videos", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		seed, _ := strconv.ParseInt(q.Get("seed"), 10, 64)
		off, _ := strconv.Atoi(q.Get("offset"))
		lim, _ := strconv.Atoi(q.Get("limit"))
		if lim <= 0 || lim > 500 {
			lim = 100
		}
		list := m.channelVideos(q.Get("channel_id"), seed)
		if off < 0 || off > len(list) {
			off = len(list)
		}
		end := min(off+lim, len(list))
		out := make([]map[string]any, 0, end-off)
		for _, it := range list[off:end] {
			out = append(out, map[string]any{"platform": it.Platform, "rel_path": it.Rel, "title": it.title(), "folder": it.Folder,
				"stream_url": it.URL, "liked": m.likes.has(it.Platform, it.Rel)})
		}
		core.WriteJSON(w, map[string]any{"total": len(list), "offset": off, "videos": out})
	})
	r.HandleFunc("GET /api/library/matrix/audio", func(w http.ResponseWriter, req *http.Request) {
		scope := req.URL.Query().Get("scope")
		_, tracks := m.media()
		out := []track{}
		for _, t := range tracks {
			if scope == "" || scope == "all" || scopeOf(t) == scope {
				out = append(out, t)
			}
		}
		core.WriteJSON(w, map[string]any{"tracks": out})
	})
	r.HandleFunc("GET /api/library/matrix/config", func(w http.ResponseWriter, _ *http.Request) {
		core.WriteJSON(w, m.loadMatrixConfig())
	})
	r.HandleFunc("POST /api/library/matrix/config", func(w http.ResponseWriter, req *http.Request) {
		var c matrixConfig
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, 64<<10)).Decode(&c); err != nil {
			core.WriteError(w, http.StatusBadRequest, "配置格式错误")
			return
		}
		b, _ := json.MarshalIndent(c.clean(), "", "  ")
		p := m.matrixConfigPath()
		os.MkdirAll(filepath.Dir(p), 0o755)
		if err := os.WriteFile(p+".tmp", b, 0o644); err != nil || os.Rename(p+".tmp", p) != nil {
			core.WriteError(w, http.StatusInternalServerError, "保存失败")
			return
		}
		core.WriteJSON(w, map[string]string{"status": "ok"})
	})
	r.HandleFunc("POST /api/library/shortvideo/like", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		p, rel, on := q.Get("platform"), q.Get("rel_path"), q.Get("liked") == "1"
		if p == "" || rel == "" {
			core.WriteError(w, http.StatusBadRequest, "缺少 platform 或 rel_path")
			return
		}
		if err := m.likes.set(p, rel, on); err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]bool{"liked": on})
	})
	// 导入 omni-deck 的点赞文件（var/data/shortvideo_likes.json），与已有点赞合并。
	r.HandleFunc("POST /api/library/shortvideo/likes/import", func(w http.ResponseWriter, req *http.Request) {
		var in map[string][]string
		if err := json.NewDecoder(http.MaxBytesReader(w, req.Body, 16<<20)).Decode(&in); err != nil {
			core.WriteError(w, http.StatusBadRequest, "格式错误：应为 omni-deck 的 shortvideo_likes.json")
			return
		}
		m.likes.mu.Lock()
		n := m.likes.merge(in)
		err := m.likes.saveLocked()
		m.likes.mu.Unlock()
		if err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]int{"added": n})
	})
	r.HandleFunc("POST /api/library/media/rescan", func(w http.ResponseWriter, _ *http.Request) {
		m.mediaIx.mu.Lock()
		busy := m.mediaIx.scanning
		m.mediaIx.scanning = true
		m.mediaIx.mu.Unlock()
		if !busy {
			go m.scanMedia()
		}
		core.WriteJSON(w, map[string]string{"status": "scanning"})
	})
}
