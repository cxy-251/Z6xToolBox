package library

import (
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"z6x/hub/internal/core"
)

// 短视频按 omni-deck 的结构存放：media_library/shortvideo/<平台>/<作者>/。
// 作者目录下的条目有两种：视频文件，或一个装着多张图片的文件夹（图集）。

type Creator struct {
	Platform string `json:"platform"`
	Name     string `json:"name"`
	Count    int    `json:"count"`
}

type Clip struct {
	Name   string   `json:"name"`
	Kind   string   `json:"kind"`             // video 或 images
	URL    string   `json:"url,omitempty"`    // 视频地址
	Images []string `json:"images,omitempty"` // 图集中各图片的地址
}

func isVideo(name string) bool {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".mp4", ".webm", ".mov", ".m4v", ".mkv":
		return true
	}
	return false
}

// libURL 生成资源库中某个文件的访问地址（/lib/<库 id>/<相对路径>）。
func libURL(lib, rel string) string {
	parts := strings.Split(filepath.ToSlash(rel), "/")
	for i, p := range parts {
		parts[i] = urlPathEscape(p)
	}
	return "/lib/" + urlPathEscape(lib) + "/" + strings.Join(parts, "/")
}

func (m *Module) listCreators() []Creator {
	counts := map[[2]string]int{}
	for _, lib := range m.libs() {
		base := filepath.Join(lib.Path, mediaRoot, "shortvideo")
		plats, _ := os.ReadDir(base)
		for _, p := range plats {
			if !p.IsDir() || strings.HasPrefix(p.Name(), ".") {
				continue
			}
			creators, _ := os.ReadDir(filepath.Join(base, p.Name()))
			for _, c := range creators {
				if !c.IsDir() || strings.HasPrefix(c.Name(), ".") {
					continue
				}
				items, _ := os.ReadDir(filepath.Join(base, p.Name(), c.Name()))
				n := 0
				for _, it := range items {
					if (it.IsDir() && !strings.HasPrefix(it.Name(), ".")) || isVideo(it.Name()) {
						n++
					}
				}
				if n > 0 {
					counts[[2]string{p.Name(), c.Name()}] += n
				}
			}
		}
	}
	out := make([]Creator, 0, len(counts))
	for k, n := range counts {
		out = append(out, Creator{Platform: k[0], Name: k[1], Count: n})
	}
	sort.Slice(out, func(i, j int) bool {
		if out[i].Platform != out[j].Platform {
			return out[i].Platform < out[j].Platform
		}
		return naturalLess(out[i].Name, out[j].Name)
	})
	return out
}

// listClips 列出某位作者的条目，新的在前（文件名以日期开头），支持分页。
func (m *Module) listClips(platform, creator string, offset, limit int) ([]Clip, int) {
	all := []Clip{}
	for _, lib := range m.libs() {
		rel := filepath.Join(mediaRoot, "shortvideo", platform, creator)
		dir, err := within(lib.Path, rel)
		if err != nil {
			continue
		}
		items, _ := os.ReadDir(dir)
		for _, it := range items {
			name := it.Name()
			if strings.HasPrefix(name, ".") {
				continue
			}
			switch {
			case it.IsDir():
				imgs, _ := os.ReadDir(filepath.Join(dir, name))
				var urls []string
				for _, im := range imgs {
					if !im.IsDir() && isImage(im.Name()) {
						urls = append(urls, libURL(lib.ID, filepath.Join(rel, name, im.Name())))
					}
				}
				sort.Slice(urls, func(i, j int) bool { return naturalLess(urls[i], urls[j]) })
				if len(urls) > 0 {
					all = append(all, Clip{Name: name, Kind: "images", Images: urls})
				}
			case isVideo(name):
				all = append(all, Clip{Name: strings.TrimSuffix(name, filepath.Ext(name)), Kind: "video", URL: libURL(lib.ID, filepath.Join(rel, name))})
			}
		}
	}
	sort.Slice(all, func(i, j int) bool { return all[i].Name > all[j].Name })
	total := len(all)
	if offset > total {
		offset = total
	}
	end := offset + limit
	if limit <= 0 || end > total {
		end = total
	}
	return all[offset:end], total
}

func (m *Module) videoRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/videos", func(w http.ResponseWriter, _ *http.Request) { core.WriteJSON(w, m.listCreators()) })
	r.HandleFunc("GET /api/library/videos/items", func(w http.ResponseWriter, req *http.Request) {
		q := req.URL.Query()
		off, _ := strconv.Atoi(q.Get("offset"))
		lim, _ := strconv.Atoi(q.Get("limit"))
		if lim <= 0 || lim > maxItemsPerRequest {
			lim = 60
		}
		clips, total := m.listClips(q.Get("platform"), q.Get("creator"), off, lim)
		core.WriteJSON(w, map[string]any{"total": total, "items": clips})
	})
	// /lib/<库 id>/<相对路径>：资源库中的任意文件，支持 Range（视频拖动进度）。
	r.HandleFunc("GET /lib/{lib}/{rest...}", func(w http.ResponseWriter, req *http.Request) {
		for _, lib := range m.libs() {
			if lib.ID != req.PathValue("lib") {
				continue
			}
			p, err := within(lib.Path, req.PathValue("rest"))
			if err != nil {
				http.Error(w, err.Error(), http.StatusForbidden)
				return
			}
			fi, err := os.Stat(p)
			if err != nil || fi.IsDir() {
				http.NotFound(w, req)
				return
			}
			f, err := os.Open(p)
			if err != nil {
				http.Error(w, err.Error(), http.StatusInternalServerError)
				return
			}
			defer f.Close()
			w.Header().Set("Cache-Control", "public, max-age="+strconv.Itoa(cacheFileSec))
			http.ServeContent(w, req, fi.Name(), fi.ModTime(), f)
			return
		}
		http.NotFound(w, req)
	})
}
