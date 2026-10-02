package library

import (
	_ "embed"
	"net/http"

	"z6x/hub/internal/core"
)

func (m *Module) pageRoutes(r core.Router) {
	r.HandleFunc("GET /ui/library/{$}", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "资源库", lobbyHTML) })
	r.HandleFunc("GET /ui/library/read", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "漫画", readerHTML) })
	r.HandleFunc("GET /ui/library/matrix/{$}", m.matrixPage)
	m.readingPageRoutes(r)
	r.HandleFunc("GET /ui/library/clips", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "短视频", clipsHTML) })
}

//go:embed web/lib.css.html
var libCSS string

//go:embed web/lobby.html
var lobbyHTMLBody string

var lobbyHTML = libCSS + lobbyHTMLBody

//go:embed web/reader.html
var readerHTML string

//go:embed web/clips.html
var clipsHTMLBody string

var clipsHTML = libCSS + clipsHTMLBody
