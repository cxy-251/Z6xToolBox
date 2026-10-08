package library

// 短视频封面生成（手机 Termux 中的 thumbs.sh）的状态，显示在设置页。thumbs.sh 与 hub 位于同一目录
// （~/z6x-hub/），以同一个应用身份运行，因此 hub 可以读取它的 PID 文件与日志。

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"

	"z6x/hub/internal/core"
)

// thumbsLogTail 是读取日志末尾的字节数（只需要最近几行）
const thumbsLogTail = 64 << 10

func lastLines(path string, n int) []string {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer f.Close()
	if st, err := f.Stat(); err == nil && st.Size() > thumbsLogTail {
		f.Seek(-thumbsLogTail, io.SeekEnd)
	}
	b, _ := io.ReadAll(f)
	lines := strings.Split(strings.TrimRight(string(b), "\n"), "\n")
	if len(lines) > n {
		lines = lines[len(lines)-n:]
	}
	return lines
}

func (m *Module) thumbsStatus() []string {
	if _, err := os.Stat("thumbs.sh"); err != nil {
		return []string{"此设备没有部署封面生成（只在手机上运行）"}
	}
	var out []string
	running := false
	if raw, err := os.ReadFile("thumbs.pid"); err == nil {
		pid := strings.TrimSpace(string(raw))
		if cmd, err := os.ReadFile("/proc/" + pid + "/cmdline"); err == nil && bytes.Contains(cmd, []byte("thumbs.sh")) {
			running = true
			out = append(out, "状态：运行中（PID "+pid+"）")
		}
	}
	if !running {
		out = append(out, "状态：未运行（在「工具 → 后台任务」中启动）")
	}
	// 封面完成情况（来自最近一次扫描）
	var parts []string
	for _, p := range m.platformList() {
		items := m.platformItems(p.ID)
		n := 0
		for _, it := range items {
			if it.Thumb != "" {
				n++
			}
		}
		parts = append(parts, fmt.Sprintf("%s %d/%d", p.Dir, n, len(items)))
	}
	if len(parts) > 0 {
		out = append(out, "已有封面："+strings.Join(parts, "，")+"（每 "+strconv.FormatFloat(m.cfg.RescanMinutes, 'f', -1, 64)+" 分钟重新统计）")
	}
	// 日志中最近的进度与最后一行
	lines := lastLines("thumbs.log", 200)
	var progress string
	for _, l := range lines {
		if strings.Contains(l, "] ") && strings.Contains(l, "[") {
			progress = l
		}
	}
	if progress != "" {
		out = append(out, "最近进度："+progress)
	}
	if len(lines) > 0 && lines[len(lines)-1] != progress {
		out = append(out, "最后一条日志："+lines[len(lines)-1])
	}
	return out
}

func (m *Module) thumbsRoutes(r core.Router) {
	r.HandleFunc("GET /api/library/thumbs/status", func(w http.ResponseWriter, _ *http.Request) {
		core.WriteJSON(w, map[string]any{"lines": m.thumbsStatus()})
	})
}
