package library

import (
	_ "embed"
	"net/http"
)

// 多联放映页面：参照 omni-deck 网页版的多联放映（web/features/shortvideo_matrix），按 hub 的接口重新实现。
//   - 设置页：几屏、每屏的频道 / 随机 / 静音 / 声音（视频原声或音声），设置保存在 hub，下次打开即是上次的设置；
//   - 播放：各屏并排，点击一屏即成为「焦点屏」，顶栏只控制焦点屏；顶栏自动隐藏，可固定；
//   - 声音 = 手动静音 或（焦点出声 且 不是焦点屏）；
//   - 横屏视频在读到画面尺寸后跳过，放不了的视频跳到下一条，连续多条失败则停止，不在坏列表上空转。
// 文件名、频道名一律用 textContent 写入页面。

func (m *Module) matrixPage(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write([]byte(matrixHTML))
}

//go:embed web/matrix.html
var matrixHTML string
