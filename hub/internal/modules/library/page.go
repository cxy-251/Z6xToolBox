package library

import (
	_ "embed"
	"encoding/json"
	"html"
	"io"
	"net/http"
	"strings"

	"z6x/hub/internal/core"
)

func (m *Module) pageRoutes(r core.Router) {
	// 资源首页：音视频与游戏分标签，各工具页也作为标签嵌入（首次点开时才加载）
	r.HandleFunc("GET /ui/library/{$}", func(w http.ResponseWriter, _ *http.Request) {
		name := "z6x-hub"
		if m.env.Config.Name != "" {
			name = m.env.Config.Name
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-cache")
		cfg, _ := json.Marshal(map[string]any{"slide_ms": int(m.cfg.SlideSeconds * 1000), "page_size": m.cfg.PageSize,
			"hide_ms": int(m.cfg.PlayerHideSeconds * 1000)})
		page := strings.NewReplacer("{{name}}", html.EscapeString(name), "{{cfg}}", string(cfg)).Replace(homeHTML)
		io.WriteString(w, mediaHead+page)
	})
	// 存储与转移：资源库位置、建立资源库、经 ADB 向手机转移的说明
	r.HandleFunc("GET /ui/library/storage", func(w http.ResponseWriter, _ *http.Request) {
		cfg, _ := json.Marshal(map[string]any{"rescan_minutes": m.cfg.RescanMinutes})
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		io.WriteString(w, mediaHead+strings.ReplaceAll(storageHTML, "{{cfg}}", string(cfg)))
	})
	r.HandleFunc("GET /ui/library/read", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "漫画", readerHTML) })
	r.HandleFunc("GET /ui/library/matrix/{$}", m.matrixPage)
	m.readingPageRoutes(r)
}

//go:embed web/home.html
var homeHTML string

//go:embed web/storage.html
var storageHTML string

//go:embed web/reader.html
var readerHTML string
