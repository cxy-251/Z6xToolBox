// Package notify 把消息发到投影仪的通知界面（不弹出提醒，在通知界面中查看）。
// 适合让 NAS、下载器、智能家居等在任务完成或出现异常时留言。
//
// 实现方式是调用 `cmd notification post`：通知归属 shell 的通知渠道（重要级别为默认的 3），
// 因此进入通知界面而不弹出，这是 2026-10-02 在投影仪上实测的行为。
package notify

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync/atomic"
	"time"
	"unicode/utf8"

	"z6x/hub/internal/android"
	"z6x/hub/internal/core"
)

const (
	maxTitle = 60
	maxText  = 1000
)

type Module struct {
	r   android.Runner
	seq atomic.Int64
	env *core.Env
}

func New(r android.Runner) *Module {
	if r == nil {
		r = android.Exec{}
	}
	return &Module{r: r}
}

func (m *Module) Name() string  { return "notify" }
func (m *Module) Title() string { return "发送通知到电视" }

func (m *Module) Start(_ context.Context, env *core.Env) error {
	m.env = env
	m.seq.Store(time.Now().Unix() % 100000)
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("POST /api/notify", m.post)
	r.HandleFunc("GET /ui/notify/{$}", m.page)
}

// request 兼容两种常见写法：{"title","text"}，以及 Gotify 风格的 {"title","message"}。
type request struct {
	Title   string `json:"title"`
	Text    string `json:"text"`
	Message string `json:"message"`
}

func (m *Module) post(w http.ResponseWriter, r *http.Request) {
	var req request
	if strings.HasPrefix(r.Header.Get("Content-Type"), "application/json") {
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16<<10)).Decode(&req); err != nil {
			core.WriteError(w, http.StatusBadRequest, "请求格式错误")
			return
		}
	} else { // 表单或查询参数，便于 curl -d 调用
		req.Title, req.Text, req.Message = r.FormValue("title"), r.FormValue("text"), r.FormValue("message")
	}
	text := req.Text
	if text == "" {
		text = req.Message
	}
	title := strings.TrimSpace(req.Title)
	if title == "" {
		title = "z6x-hub"
	}
	text = strings.TrimSpace(text)
	if text == "" {
		core.WriteError(w, http.StatusBadRequest, "通知内容不能为空")
		return
	}
	title, text = clip(title, maxTitle), clip(text, maxText)
	if err := m.Send(r.Context(), title, text); err != nil {
		core.WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	core.WriteJSON(w, map[string]bool{"ok": true})
}

// Send 发出一条通知。每条通知使用不同的 tag，新通知不会覆盖旧通知。
// 参数以数组传给 exec，标题和正文中的任何字符都不会被 shell 解释。
func (m *Module) Send(ctx context.Context, title, text string) error {
	tag := fmt.Sprintf("z6x-%d", m.seq.Add(1))
	_, err := m.r.Run(ctx, "/system/bin/cmd", "notification", "post", "-S", "bigtext", "-t", title, tag, text)
	if err != nil {
		return fmt.Errorf("发送通知失败：%w", err)
	}
	if m.env != nil {
		m.env.Log.Info("已发送通知", "tag", tag, "chars", utf8.RuneCountInString(text))
	}
	return nil
}

func clip(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	return string([]rune(s)[:n]) + "…"
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "发送通知到电视", `<div class="card"><input id="title" placeholder="标题（可省略）"><p></p>
<textarea id="text" placeholder="通知内容"></textarea>
<p class="row"><button onclick="send()">发送</button><span class="msg" id="msg"></span></p>
<small>通知进入电视的通知界面，不弹出提醒。其他程序可以这样发送：<br>
<code>curl -H "Authorization: Bearer TOKEN" -d title=下载完成 -d text=文件已保存 http://投影仪IP:8090/api/notify</code></small></div>
<script>
async function send(){const msg=document.getElementById('msg');
  const r=await fetch('/api/notify',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({title:document.getElementById('title').value,text:document.getElementById('text').value})});
  msg.textContent=r.ok?'已发送':'失败：'+(await r.json()).error;if(r.ok)document.getElementById('text').value=''}
</script>`)
}
