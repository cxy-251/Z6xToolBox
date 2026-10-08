// Package library 是 hub 所在设备上的「资源库」：读取与 omni-deck 相同结构的资源库（以 omnilibrary.json 标记），
// 在任何设备的浏览器中提供网页游戏、漫画和短视频。
//
// 资源库可以在机身存储（/storage/emulated/0/omni_library）和 U 盘上，按优先级合并展示，与 omni-deck 的多资源库一致。
// 网页游戏使用与 omni-deck 相同的网址规则和存档接口，存档写进游戏目录的 save/，随游戏文件夹一起转移。
package library

import (
	"context"
	"net/url"
	"path/filepath"
	"sync"
	"time"

	"z6x/hub/internal/core"
)

type Config struct {
	// Roots 是额外指定的资源库根目录；机身存储和 U 盘上的资源库会自动发现，一般无需填写。
	Roots []string `yaml:"roots"`
	// Internal 是机身存储的位置，默认 /storage/emulated/0。
	Internal string `yaml:"internal"`
	// Storage 是 U 盘挂载的上级目录，默认 /storage。
	Storage string `yaml:"storage"`
	// Music 是资源库以外的音乐目录（如手机上自行整理的歌曲），在音声页作为「音乐」来源，
	// 按「歌手/专辑/歌曲」的目录结构分组（上一级目录为专辑，再上一级为歌手）。
	Music []string `yaml:"music"`
	// SlideSeconds 是短视频播放器中图集自动翻页的间隔（秒）；最后一张之后与视频播完一样切换作品。
	SlideSeconds float64 `yaml:"slide_seconds"`
	// RescanMinutes：短视频与音声的扫描结果过期时间（分钟），过期后的下一次请求在后台重新扫描。
	RescanMinutes float64 `yaml:"rescan_minutes"`
	// PageSize：短视频网格每次加载的作品数。
	PageSize int `yaml:"page_size"`
	// CoverPx：音乐封面缩小后的宽度（像素）。
	CoverPx int `yaml:"cover_px"`
	// PlayerHideSeconds：短视频播放器无操作多久后隐藏控件（秒）。
	PlayerHideSeconds float64 `yaml:"player_hide_seconds"`
	// Thumbs 是短视频封面生成（手机上的 thumbs.sh）的参数，由 z6x-hub -thumbs-config 交给脚本。
	Thumbs ThumbsConfig `yaml:"thumbs"`
}

// ThumbsConfig 是短视频封面生成的参数。
type ThumbsConfig struct {
	Jobs          int     `yaml:"jobs" json:"jobs"`                     // 不热时同时运行的 ffmpeg 数
	Cool          float64 `yaml:"cool" json:"cool"`                     // 低于此温度（°C）全速
	Hot           float64 `yaml:"hot" json:"hot"`                       // 达到此温度（°C）暂停
	Width         int     `yaml:"width" json:"width"`                   // 封面宽度（像素）
	IntervalHours float64 `yaml:"interval_hours" json:"interval_hours"` // 每轮之间等待多久再检查新作品
}

// DefaultConfig 是资源库模块的默认配置；hub.yaml 中没有写的项使用这里的值（全部默认值只在这里定义一次）。
func DefaultConfig() Config {
	return Config{
		Internal: "/storage/emulated/0", Storage: "/storage",
		SlideSeconds: 1, RescanMinutes: 10, PageSize: 48, CoverPx: 300, PlayerHideSeconds: 2.5,
		Thumbs: ThumbsConfig{Jobs: 3, Cool: 36, Hot: 40, Width: 360, IntervalHours: 6},
	}
}

// normalize 把不合理的值（0、负数、超出范围）换回默认值。
func (c *Config) normalize() {
	d := DefaultConfig()
	fix := func(v *float64, def, lo, hi float64) {
		if *v < lo || *v > hi {
			*v = def
		}
	}
	fixInt := func(v *int, def, lo, hi int) {
		if *v < lo || *v > hi {
			*v = def
		}
	}
	fix(&c.SlideSeconds, d.SlideSeconds, 0.2, 60)
	fix(&c.RescanMinutes, d.RescanMinutes, 1, 24*60)
	fixInt(&c.PageSize, d.PageSize, 12, maxItemsPerRequest)
	fixInt(&c.CoverPx, d.CoverPx, 64, 1024)
	fix(&c.PlayerHideSeconds, d.PlayerHideSeconds, 0.5, 60)
	fixInt(&c.Thumbs.Jobs, d.Thumbs.Jobs, 1, 8)
	fix(&c.Thumbs.Cool, d.Thumbs.Cool, 20, 60)
	fix(&c.Thumbs.Hot, d.Thumbs.Hot, c.Thumbs.Cool+1, 70)
	fixInt(&c.Thumbs.Width, d.Thumbs.Width, 120, 1080)
	fix(&c.Thumbs.IntervalHours, d.Thumbs.IntervalHours, 0.1, 24*7)
}

// ReadConfig 从 hub 配置中取出资源库配置（含默认值），供命令行 -thumbs-config 使用。
func ReadConfig(cfg *core.Config) (Config, error) {
	c := DefaultConfig()
	if err := cfg.Decode("library", &c); err != nil {
		return c, err
	}
	c.normalize()
	return c, nil
}

type Module struct {
	cfg      Config
	env      *core.Env
	thumbDir string
	thumbSem chan struct{}

	mu        sync.Mutex
	libCache  []Lib
	libAt     time.Time
	gameCache map[string]*Game
	gameAt    time.Time
	pageCache map[string]pageIndex

	mediaIx mediaIndex // 短视频与音声的扫描结果（media.go）
	likes   likeStore
	pins    likeStore // 短视频置顶的博主（平台 → 博主名），保存在服务端，各设备一致

	audioProgress progressStore // 音声续听（与 omni-deck 的 audio_progress.json 同格式）
	novelProgress progressStore // 小说阅读进度
	novelCache    novelCache
}

func New() *Module {
	return &Module{thumbSem: make(chan struct{}, 2), pageCache: map[string]pageIndex{}}
}

func (m *Module) Name() string { return "library" }
func (m *Module) Title() string {
	return "资源库：游戏、漫画、短视频、多联放映、音声、小说"
}

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	cfg, err := ReadConfig(env.Config)
	if err != nil {
		return err
	}
	m.cfg = cfg
	m.thumbDir = filepath.Join(env.Config.DataDir, "library", "thumbs")
	m.likes.path = filepath.Join(env.Config.DataDir, "library", "shortvideo_likes.json")
	m.likes.load()
	m.pins.path = filepath.Join(env.Config.DataDir, "library", "shortvideo_pins.json")
	m.pins.load()
	m.audioProgress.path = filepath.Join(env.Config.DataDir, "library", "audio_progress.json")
	m.audioProgress.load()
	m.novelProgress.path = filepath.Join(env.Config.DataDir, "library", "novels_progress.json")
	m.novelProgress.load()
	go m.media() // 启动时在后台完成首次扫描，第一次打开页面不必等待
	libs := m.libs()
	env.Log.Info("资源库", "count", len(libs))
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	m.gameRoutes(r)
	m.mangaRoutes(r)
	m.libFileRoutes(r)
	m.mediaRoutes(r)
	m.shortvideoRoutes(r)
	m.coverRoutes(r)
	m.audioRoutes(r)
	m.novelRoutes(r)
	m.importRoutes(r)
	m.pageRoutes(r)
}

// libs 返回当前所有资源库。结果缓存 10 秒：U 盘插拔后最多 10 秒即可被发现。
func (m *Module) libs() []Lib {
	m.mu.Lock()
	defer m.mu.Unlock()
	if time.Since(m.libAt) < libsCacheTTL && m.libCache != nil {
		return m.libCache
	}
	m.libCache = discover(m.cfg.Roots, m.cfg.Internal, m.cfg.Storage)
	m.libAt = time.Now()
	return m.libCache
}

// games 返回游戏表。结果缓存 30 秒；force 为 true 时立即重新扫描。
func (m *Module) games(force bool) map[string]*Game {
	libs := m.libs()
	m.mu.Lock()
	defer m.mu.Unlock()
	if !force && m.gameCache != nil && time.Since(m.gameAt) < gamesCacheTTL {
		return m.gameCache
	}
	m.gameCache = scanGames(libs)
	m.gameAt = time.Now()
	return m.gameCache
}

func (m *Module) invalidate() {
	m.mu.Lock()
	m.libAt, m.gameAt = time.Time{}, time.Time{}
	m.mu.Unlock()
}

func urlPathEscape(s string) string { return url.PathEscape(s) }

// Settings 声明资源库在设置页中可调的参数（实现 core.SettingsProvider）；默认值取自 DefaultConfig。
func (m *Module) Settings() []core.SettingsGroup {
	d := DefaultConfig()
	const p = "modules.library."
	return []core.SettingsGroup{
		{Title: "📱 短视频与音声", Items: []core.Setting{
			{Key: p + "slide_seconds", Label: "图集翻页间隔", Unit: "秒", Type: "number", Min: 0.2, Max: 60, Step: 0.1, Default: d.SlideSeconds,
				Help: "播放器中图集自动翻页的间隔；最后一张之后与视频播完一样切换作品"},
			{Key: p + "page_size", Label: "每次加载作品数", Unit: "个", Type: "number", Min: 12, Max: maxItemsPerRequest, Step: 1, Default: d.PageSize,
				Help: "短视频网格滚动到底部时每次加载的数量"},
			{Key: p + "player_hide_seconds", Label: "播放器控件自动隐藏", Unit: "秒", Type: "number", Min: 0.5, Max: 60, Step: 0.5, Default: d.PlayerHideSeconds},
			{Key: p + "rescan_minutes", Label: "重新扫描间隔", Unit: "分钟", Type: "number", Min: 1, Max: 1440, Step: 1, Default: d.RescanMinutes,
				Help: "新放进资源库的短视频与音声，最晚多久后出现"},
			{Key: p + "cover_px", Label: "音乐封面宽度", Unit: "像素", Type: "number", Min: 64, Max: 1024, Step: 1, Default: d.CoverPx,
				Help: "封面缩小后的宽度；越大越清晰，加载越慢"},
		}},
		{Title: "🖼️ 短视频封面生成（手机后台）", Items: []core.Setting{
			{Key: p + "thumbs.jobs", Label: "同时生成数", Unit: "个", Type: "number", Min: 1, Max: 8, Step: 1, Default: d.Thumbs.Jobs,
				Help: "温度低于下面的「全速温度」时同时运行的 ffmpeg 数"},
			{Key: p + "thumbs.cool", Label: "全速温度", Unit: "°C", Type: "number", Min: 20, Max: 60, Step: 0.5, Default: d.Thumbs.Cool,
				Help: "低于此温度全速；介于全速温度与暂停温度之间只运行 1 个"},
			{Key: p + "thumbs.hot", Label: "暂停温度", Unit: "°C", Type: "number", Min: 21, Max: 70, Step: 0.5, Default: d.Thumbs.Hot,
				Help: "达到此温度暂停，降到全速温度 + 1°C 以下再继续"},
			{Key: p + "thumbs.width", Label: "封面宽度", Unit: "像素", Type: "number", Min: 120, Max: 1080, Step: 1, Default: d.Thumbs.Width,
				Help: "只影响之后新生成的封面"},
			{Key: p + "thumbs.interval_hours", Label: "检查新作品间隔", Unit: "小时", Type: "number", Min: 0.1, Max: 168, Step: 0.5, Default: d.Thumbs.IntervalHours},
		}},
	}
}
