package library

// 短视频页（与 omni-deck 的短视频画廊相同的用法）：按平台分标签，按博主筛选，作品网格分页加载，
// 视频与图集都列出。数据来自 media.go 的扫描结果（常驻内存），不另外扫描目录，也不生成封面。

import (
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"z6x/hub/internal/core"
)

// platformItems 返回某平台的全部作品（视频与图集），按日期从新到旧。
func (m *Module) platformItems(platform string) []*svItem {
	videos, _ := m.media()
	ix := &m.mediaIx
	ix.mu.Lock()
	albums := ix.albums[platform]
	ix.mu.Unlock()
	all := make([]*svItem, 0, len(videos[platform])+len(albums))
	all = append(all, videos[platform]...)
	all = append(all, albums...)
	sort.SliceStable(all, func(i, j int) bool {
		a, b := all[i], all[j]
		if a.date != b.date {
			return a.date > b.date
		}
		return a.mtime > b.mtime
	})
	return all
}

func (m *Module) shortvideoRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/shortvideo/platforms", func(w http.ResponseWriter, _ *http.Request) {
		plats := m.platformList()
		out := make([]map[string]any, 0, len(plats))
		for _, p := range plats {
			items := m.platformItems(p.ID)
			thumbs := 0
			for _, it := range items {
				if it.Thumb != "" {
					thumbs++
				}
			}
			out = append(out, map[string]any{"id": p.ID, "title": p.Dir, "count": len(items), "thumbs": thumbs})
		}
		core.WriteJSON(w, out)
	})
	r.HandleFunc("GET /api/library/shortvideo/folders", func(w http.ResponseWriter, req *http.Request) {
		p := req.URL.Query().Get("platform")
		count, liked := map[string]int{}, map[string]int{}
		for _, it := range m.platformItems(p) {
			count[it.Folder]++
			if m.likes.has(p, it.Rel) {
				liked[it.Folder]++
			}
		}
		out := make([]map[string]any, 0, len(count))
		for name, n := range count {
			out = append(out, map[string]any{"name": name, "count": n, "liked": liked[name], "pinned": m.pins.has(p, name)})
		}
		sort.Slice(out, func(i, j int) bool { return naturalLess(out[i]["name"].(string), out[j]["name"].(string)) })
		core.WriteJSON(w, out)
	})
	r.HandleFunc("POST /api/library/shortvideo/pin", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		p, folder := q.Get("platform"), q.Get("folder")
		if p == "" || folder == "" {
			core.WriteError(w, http.StatusBadRequest, "缺少 platform 或 folder")
			return
		}
		if err := m.pins.set(p, folder, q.Get("pinned") == "1"); err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]bool{"pinned": q.Get("pinned") == "1"})
	})
	// played：播放器打开一个作品时记录（推荐时跳过最近看过的；记录保存时间为跳过时长的两倍）
	r.HandleFunc("POST /api/library/shortvideo/played", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		keep := time.Duration(max(m.cfg.Recommend.SkipHours, 1) * 2 * float64(time.Hour))
		if err := m.history.record(q.Get("platform"), q.Get("rel_path"), keep); err != nil {
			core.WriteError(w, http.StatusInternalServerError, err.Error())
			return
		}
		core.WriteJSON(w, map[string]bool{"ok": true})
	})
	// items：folder 为空表示全部博主；q 按标题与博主筛选；liked=1 只列出喜欢的。
	r.HandleFunc("GET /api/library/shortvideo/items", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		p, folder, kw, onlyLiked := q.Get("platform"), q.Get("folder"), strings.ToLower(q.Get("q")), q.Get("liked") == "1"
		off, _ := strconv.Atoi(q.Get("offset"))
		lim, _ := strconv.Atoi(q.Get("limit"))
		if lim <= 0 || lim > maxItemsPerRequest {
			lim = m.cfg.PageSize
		}
		source := m.platformItems(p)
		if q.Get("recommend") == "1" { // 推荐：加权随机排列，种子由网页给出（再点一次「推荐」即换种子）
			seed, _ := strconv.ParseInt(q.Get("seed"), 10, 64)
			source = m.recommend(p, seed)
		}
		var list []*svItem
		for _, it := range source {
			if folder != "" && it.Folder != folder {
				continue
			}
			if kw != "" && !strings.Contains(strings.ToLower(it.Rel), kw) {
				continue
			}
			if onlyLiked && !m.likes.has(p, it.Rel) {
				continue
			}
			list = append(list, it)
		}
		off = max(0, min(off, len(list)))
		end := min(off+lim, len(list))
		out := make([]map[string]any, 0, end-off)
		for _, it := range list[off:end] {
			kind, url := "video", it.URL
			if it.Images != nil {
				kind, url = "images", it.Images[0]
			}
			out = append(out, map[string]any{"rel_path": it.Rel, "title": it.title(), "folder": it.Folder, "kind": kind,
				"url": url, "images": it.Images, "thumb": it.Thumb, "liked": m.likes.has(p, it.Rel), "date": it.date})
		}
		core.WriteJSON(w, map[string]any{"total": len(list), "offset": off, "items": out})
	})
}
