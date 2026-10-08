package library

// 短视频页（与 omni-deck 的短视频画廊相同的用法）：按平台分标签，按博主筛选，作品网格分页加载，
// 视频与图集都列出。数据来自 media.go 的扫描结果（常驻内存），不另外扫描目录，也不生成封面。

import (
	"net/http"
	"sort"
	"strconv"
	"strings"

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
		out := make([]map[string]any, 0, len(platforms))
		for _, p := range platforms {
			out = append(out, map[string]any{"id": p.ID, "title": p.Dir, "count": len(m.platformItems(p.ID))})
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
			out = append(out, map[string]any{"name": name, "count": n, "liked": liked[name]})
		}
		sort.Slice(out, func(i, j int) bool { return naturalLess(out[i]["name"].(string), out[j]["name"].(string)) })
		core.WriteJSON(w, out)
	})
	// items：folder 为空表示全部博主；q 按标题与博主筛选；liked=1 只列出喜欢的。
	r.HandleFunc("GET /api/library/shortvideo/items", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		p, folder, kw, onlyLiked := q.Get("platform"), q.Get("folder"), strings.ToLower(q.Get("q")), q.Get("liked") == "1"
		off, _ := strconv.Atoi(q.Get("offset"))
		lim, _ := strconv.Atoi(q.Get("limit"))
		if lim <= 0 || lim > 200 {
			lim = 60
		}
		var list []*svItem
		for _, it := range m.platformItems(p) {
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
				"url": url, "images": it.Images, "liked": m.likes.has(p, it.Rel), "date": it.date})
		}
		core.WriteJSON(w, map[string]any{"total": len(list), "offset": off, "items": out})
	})
}
