package library

// 短视频「推荐」：加权随机。每个作品按规则得到一个权重，用随机种子做加权随机排列
// （Efraimidis–Spirakis：键值 = u^(1/权重)，按键值从大到小），同一个种子结果固定，网页分页读取时前后对得上；
// 在网页上再点一次「推荐」即换一个种子。规则与权重在 hub.yaml 的 library.recommend 中配置：
//   - 喜欢过其作品的博主、置顶的博主，其作品权重加倍；
//   - 最近若干天内的新作品权重加倍；
//   - 最近若干小时内看过的作品不出现（播放记录只保存在设备上）；
//   - 同一个博主最多连续出现若干个，超出的往后挪。

import (
	"encoding/json"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// RecommendConfig 是推荐的参数。
type RecommendConfig struct {
	LikedCreator  float64 `yaml:"liked_creator"`  // 喜欢过其作品的博主：权重倍数
	PinnedCreator float64 `yaml:"pinned_creator"` // 置顶的博主：权重倍数
	RecentDays    float64 `yaml:"recent_days"`    // 多少天内算新作品
	RecentBoost   float64 `yaml:"recent_boost"`   // 新作品：权重倍数
	SkipHours     float64 `yaml:"skip_hours"`     // 多少小时内看过的作品不再推荐
	MaxRun        int     `yaml:"max_run"`        // 同一个博主最多连续出现几个
}

// history 是播放记录：平台 → 相对路径 → 最近一次播放的时间（Unix 秒）。
type history struct {
	mu   sync.Mutex
	path string
	data map[string]map[string]int64
}

func (h *history) load() {
	h.data = map[string]map[string]int64{}
	if b, err := os.ReadFile(h.path); err == nil {
		json.Unmarshal(b, &h.data)
	}
}

// record 记下一次播放，并清理超过 keep 的旧记录，写入文件。
func (h *history) record(platform, rel string, keep time.Duration) error {
	h.mu.Lock()
	defer h.mu.Unlock()
	now := time.Now().Unix()
	if h.data[platform] == nil {
		h.data[platform] = map[string]int64{}
	}
	h.data[platform][rel] = now
	for _, m := range h.data {
		for k, t := range m {
			if now-t > int64(keep.Seconds()) {
				delete(m, k)
			}
		}
	}
	b, _ := json.Marshal(h.data)
	os.MkdirAll(filepath.Dir(h.path), 0o755)
	tmp := h.path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, h.path)
}

func (h *history) since(platform string, cutoff int64) map[string]bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	out := map[string]bool{}
	for rel, t := range h.data[platform] {
		if t >= cutoff {
			out[rel] = true
		}
	}
	return out
}

// recommendCache 保留最近一次计算的结果：同一个平台与种子的后续分页直接取用。
type recommendCache struct {
	mu       sync.Mutex
	platform string
	seed     int64
	at       time.Time
	list     []*svItem
}

// recommend 返回某平台按种子加权随机排列的作品。
func (m *Module) recommend(platform string, seed int64) []*svItem {
	c := &m.recCache
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.platform == platform && c.seed == seed && c.list != nil && time.Since(c.at) < recommendCacheTTL {
		return c.list
	}
	cfg := m.cfg.Recommend
	items := m.platformItems(platform)
	now := time.Now()
	seen := m.history.since(platform, now.Add(-time.Duration(cfg.SkipHours*float64(time.Hour))).Unix())
	likedCreators := map[string]bool{}
	for _, it := range items {
		if m.likes.has(platform, it.Rel) {
			likedCreators[it.Folder] = true
		}
	}
	recent := dateInt(now.Add(-time.Duration(cfg.RecentDays * 24 * float64(time.Hour))))
	rng := rand.New(rand.NewSource(seed))
	type keyed struct {
		it  *svItem
		key float64
	}
	var ks []keyed
	for _, it := range items {
		if seen[it.Rel] {
			continue
		}
		w := 1.0
		if likedCreators[it.Folder] {
			w *= cfg.LikedCreator
		}
		if m.pins.has(platform, it.Folder) {
			w *= cfg.PinnedCreator
		}
		if it.date >= recent || (it.date == 0 && it.mtime >= now.Add(-time.Duration(cfg.RecentDays*24*float64(time.Hour))).Unix()) {
			w *= cfg.RecentBoost
		}
		ks = append(ks, keyed{it, math.Pow(rng.Float64(), 1/w)})
	}
	sort.Slice(ks, func(i, j int) bool { return ks[i].key > ks[j].key })
	list := make([]*svItem, len(ks))
	for i, k := range ks {
		list[i] = k.it
	}
	list = spreadCreators(list, cfg.MaxRun)
	c.platform, c.seed, c.at, c.list = platform, seed, now, list
	return list
}

// spreadCreators 让同一个博主最多连续出现 maxRun 个：超出的作品往后挪到第一个合适的位置。
func spreadCreators(list []*svItem, maxRun int) []*svItem {
	if maxRun <= 0 {
		return list
	}
	out := make([]*svItem, 0, len(list))
	var pending []*svItem
	run := func() (string, int) {
		if len(out) == 0 {
			return "", 0
		}
		f, n := out[len(out)-1].Folder, 0
		for i := len(out) - 1; i >= 0 && out[i].Folder == f; i-- {
			n++
		}
		return f, n
	}
	place := func() { // 先放积压的作品
		for i := 0; i < len(pending); i++ {
			if f, n := run(); pending[i].Folder != f || n < maxRun {
				out = append(out, pending[i])
				pending = append(pending[:i], pending[i+1:]...)
				i = -1
			}
		}
	}
	for _, it := range list {
		place()
		if f, n := run(); it.Folder == f && n >= maxRun {
			pending = append(pending, it)
			continue
		}
		out = append(out, it)
	}
	place()
	return append(out, pending...) // 剩下的只能连续放在最后
}

// dateInt 把时间换成 YYYYMMDD 整数（与文件名中的日期比较）。
func dateInt(t time.Time) int {
	y, mo, d := t.Date()
	return y*10000 + int(mo)*100 + d
}
