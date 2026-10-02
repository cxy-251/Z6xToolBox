package tasks

import (
	_ "embed"
	"net/http"

	"z6x/hub/internal/core"
)

//go:embed web/tasks.html
var pageHTML string

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "任务管理", pageHTML)
}
