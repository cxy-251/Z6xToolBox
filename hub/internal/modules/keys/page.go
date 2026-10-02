package keys

import (
	_ "embed"
	"net/http"

	"z6x/hub/internal/core"
)

//go:embed web/keys.html
var pageHTML string

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "遥控器按键", pageHTML)
}
