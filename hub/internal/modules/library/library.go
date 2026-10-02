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
	m.cfg = Config{Internal: "/storage/emulated/0", Storage: "/storage"}
	if err := env.Config.Decode("library", &m.cfg); err != nil {
		return err
	}
	m.thumbDir = filepath.Join(env.Config.DataDir, "library", "thumbs")
	m.likes.path = filepath.Join(env.Config.DataDir, "library", "shortvideo_likes.json")
	m.likes.load()
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
	m.videoRoutes(r)
	m.mediaRoutes(r)
	m.audioRoutes(r)
	m.novelRoutes(r)
	m.importRoutes(r)
	m.pageRoutes(r)
}

// libs 返回当前所有资源库。结果缓存 10 秒：U 盘插拔后最多 10 秒即可被发现。
func (m *Module) libs() []Lib {
	m.mu.Lock()
	defer m.mu.Unlock()
	if time.Since(m.libAt) < 10*time.Second && m.libCache != nil {
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
	if !force && m.gameCache != nil && time.Since(m.gameAt) < 30*time.Second {
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
