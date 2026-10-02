package library

import (
	_ "embed"
	"net/http"

	"z6x/hub/internal/core"
)

// 音声与小说的网页。功能参照 omni-deck 的音声画廊与小说画廊（web/features/audio、novels），按 hub 的接口重新实现。
// 名称与正文一律用 textContent 写入页面。

func (m *Module) readingPageRoutes(r core.Router) {
	r.HandleFunc("GET /ui/library/audio/{$}", func(w http.ResponseWriter, _ *http.Request) { rawPage(w, audioHTML) })
	r.HandleFunc("GET /ui/library/novels/{$}", func(w http.ResponseWriter, _ *http.Request) { rawPage(w, novelsHTML) })
}

func rawPage(w http.ResponseWriter, s string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write([]byte(s))
}

//go:embed web/media-head.html
var mediaHead string

//go:embed web/audio.html
var audioHTMLBody string

var audioHTML = mediaHead + audioHTMLBody

//go:embed web/novels.html
var novelsHTMLBody string

var novelsHTML = mediaHead + novelsHTMLBody
