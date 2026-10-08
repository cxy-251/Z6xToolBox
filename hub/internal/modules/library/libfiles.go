package library

import (
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"z6x/hub/internal/core"
)

// libURL 生成资源库中某个文件的访问地址（/lib/<库 id>/<相对路径>）。
func libURL(lib, rel string) string {
	parts := strings.Split(filepath.ToSlash(rel), "/")
	for i, p := range parts {
		parts[i] = urlPathEscape(p)
	}
	return "/lib/" + urlPathEscape(lib) + "/" + strings.Join(parts, "/")
}

func (m *Module) libFileRoutes(r core.Router) {
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
