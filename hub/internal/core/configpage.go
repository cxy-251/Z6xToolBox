package core

import (
	"fmt"
	"html"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"syscall"
	"time"
)

// tokenMask 在页面上代替真实 token：显示时替换进去，保存时再换回原值。
const tokenMask = "（已隐藏，保存时保留原 token）"

var tokenLine = regexp.MustCompile(`(?m)^(token:\s*)("?)([^"\n]*)("?)\s*$`)

// SetConfigPath 告诉 hub 配置文件的位置，配置页据此读取和保存。
func (h *Hub) SetConfigPath(p string) { h.cfgPath = p }

func maskToken(yamlText string) string {
	return tokenLine.ReplaceAllString(yamlText, `${1}"`+tokenMask+`"`)
}

func unmaskToken(yamlText, token string) string {
	return tokenLine.ReplaceAllStringFunc(yamlText, func(line string) string {
		m := tokenLine.FindStringSubmatch(line)
		if m[3] == tokenMask {
			return m[1] + `"` + token + `"`
		}
		return line
	})
}

func (h *Hub) configRoutes(mux *http.ServeMux) {
	mux.Handle("GET /api/config", RequireToken(h.cfg.Token, http.HandlerFunc(h.getConfig)))
	mux.Handle("POST /api/config", RequireTokenStrict(h.cfg.Token, http.HandlerFunc(h.saveConfig)))
	mux.Handle("GET /ui/config/{$}", RequireToken(h.cfg.Token, http.HandlerFunc(h.configPage)))
}

func (h *Hub) readMasked() (string, error) {
	raw, err := os.ReadFile(h.cfgPath)
	if err != nil {
		return "", err
	}
	return maskToken(string(raw)), nil
}

func (h *Hub) getConfig(w http.ResponseWriter, _ *http.Request) {
	text, err := h.readMasked()
	if err != nil {
		WriteError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	io.WriteString(w, text)
}

// saveConfig 校验新配置，通过后备份旧配置、写入新配置，并在原进程中重启 hub。
func (h *Hub) saveConfig(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 64<<10))
	if err != nil {
		WriteError(w, http.StatusBadRequest, "配置过大或读取失败")
		return
	}
	text := unmaskToken(string(body), h.cfg.Token)
	if err := h.applyConfig(text); err != nil {
		WriteError(w, http.StatusBadRequest, err.Error())
		return
	}
	WriteJSON(w, map[string]string{"status": "已保存，hub 正在重启（约 2 秒）"})
	h.restartSoon()
}

// applyConfig 校验新配置，通过后备份旧配置（.bak）并写入；不通过则不做任何修改。设置页与配置页共用。
func (h *Hub) applyConfig(text string) error {
	// 先写入同目录的临时文件，用与启动时相同的校验逻辑检查
	dir := filepath.Dir(h.cfgPath)
	tmp, err := os.CreateTemp(dir, ".hub-*.yaml")
	if err != nil {
		return err
	}
	defer os.Remove(tmp.Name())
	tmp.WriteString(text)
	tmp.Close()
	if _, err := LoadConfig(tmp.Name()); err != nil {
		return err
	}
	old, _ := os.ReadFile(h.cfgPath)
	if err := os.WriteFile(h.cfgPath+".bak", old, 0o600); err != nil {
		return fmt.Errorf("备份旧配置失败：%w", err)
	}
	if err := os.Rename(tmp.Name(), h.cfgPath); err != nil {
		return fmt.Errorf("写入配置失败：%w", err)
	}
	os.Chmod(h.cfgPath, 0o600)
	h.log.Info("配置已更新，即将重启", "backup", h.cfgPath+".bak")
	return nil
}

// restartSoon 稍候重启，让响应先发送出去。
func (h *Hub) restartSoon() {
	go func() {
		time.Sleep(stopDelay)
		h.restart()
	}()
}

// restart 用 exec 以相同参数重新执行自身：进程号不变，因此 shell 身份和 -1000 的 oom 分值都得以保留。
// 监听端口和日志文件在 Go 中都带有 close-on-exec 标记，exec 时自动关闭，新进程可以重新绑定。
func (h *Hub) restart() {
	exe, err := os.Executable()
	if err != nil {
		h.log.Error("无法定位可执行文件，重启失败", "err", err)
		return
	}
	if err := syscall.Exec(exe, os.Args, os.Environ()); err != nil {
		h.log.Error("重启失败", "err", err)
	}
}

func (h *Hub) configPage(w http.ResponseWriter, _ *http.Request) {
	text, err := h.readMasked()
	if err != nil {
		Page(w, "配置", "<p>读取配置失败："+html.EscapeString(err.Error())+"</p>")
		return
	}
	Page(w, "配置", fmt.Sprintf(`<div class="card"><p><small>编辑 hub.yaml。保存前会按启动时的规则校验，不通过则不做任何修改；通过后旧配置备份为 hub.yaml.bak，hub 随即重启。token 不在页面上显示，保持占位文字即保留原值。</small></p>
<textarea id="cfg" spellcheck="false" style="min-height:60vh;font-family:monospace;font-size:13px">%s</textarea>
<p class="row"><button onclick="save()">校验并保存</button></p><pre class="msg" id="msg"></pre></div>
<script>
async function save(){const msg=document.getElementById('msg');msg.textContent='保存中…';
  const r=await fetch('/api/config',{method:'POST',body:document.getElementById('cfg').value});const j=await r.json();
  if(!r.ok){msg.textContent=j.error;return}
  msg.textContent=j.status;
  for(let i=0;i<20;i++){await new Promise(s=>setTimeout(s,500));try{const h=await fetch('/api/health');if(h.ok){msg.textContent='已重启，正在返回首页…';setTimeout(()=>location.href='/',800);return}}catch(e){}}
  msg.textContent='hub 未在预期时间内恢复，请在工具箱中查看状态或重新部署。'}
</script>`, html.EscapeString(text)))
}
