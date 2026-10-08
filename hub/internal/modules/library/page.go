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
	r.HandleFunc("GET /ui/library/storage", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "存储与转移", lobbyHTML) })
	r.HandleFunc("GET /ui/library/read", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "漫画", readerHTML) })
	r.HandleFunc("GET /ui/library/matrix/{$}", m.matrixPage)
	m.readingPageRoutes(r)
	r.HandleFunc("GET /ui/library/clips", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "短视频", clipsHTML) })
}

//go:embed web/lib.css.html
var libCSS string

//go:embed web/home.html
var homeHTML string

//go:embed web/lobby.html
var lobbyHTMLBody string

var lobbyHTML = libCSS + lobbyHTMLBody

//go:embed web/reader.html
var readerHTML string

//go:embed web/clips.html
var clipsHTMLBody string

var clipsHTML = libCSS + clipsHTMLBody
