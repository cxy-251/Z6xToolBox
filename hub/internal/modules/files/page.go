package files

import (
	_ "embed"
	"fmt"
	"html"
	"net"
	"net/http"
	"strings"

	"z6x/hub/internal/core"
)

// page 是网页文件管理：浏览、预览、上传（多文件、显示进度）、新建文件夹、重命名、删除（移入回收站）、打包下载。
// 文件名一律通过 textContent 写入页面，不拼接 HTML，避免文件名中的标签被执行。
func (m *Module) page(w http.ResponseWriter, r *http.Request) {
	host := r.Host
	if h, _, err := net.SplitHostPort(r.Host); err == nil {
		host = h
	}
	dav := fmt.Sprintf("http://%s:%d/", host, m.cfg.Port)
	core.Page(w, "文件管理", `<style>
#ls li{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
#ls .name{flex:1;min-width:40%;word-break:break-all;cursor:pointer}
#ls .ops button{padding:2px 8px;font-size:12px}
#crumb a{cursor:pointer}
#drop{border:2px dashed #888;border-radius:8px;padding:14px;text-align:center}
#drop.on{background:rgba(127,127,127,.2)}
#pv{position:fixed;inset:0;background:rgba(0,0,0,.85);display:none;flex-direction:column;align-items:center;justify-content:center;z-index:9;padding:12px}
#pv .box{max-width:96vw;max-height:84vh;overflow:auto;color:#eee}
#pv img,#pv video{max-width:96vw;max-height:84vh}
#pv pre{white-space:pre-wrap;background:#111;padding:12px;max-width:90vw}
</style>
<div class="card"><div id="crumb"></div>
<p class="row"><button class="ghost" onclick="newDir()">新建文件夹</button><button class="ghost" onclick="go(cwd)">刷新</button></p>
<ul class="list" id="ls"></ul></div>
<div class="card"><div id="drop">把文件拖到这里，或 <input type="file" id="file" multiple></div><div class="msg" id="msg"></div></div>
<div class="card"><p><b>挂载为网络盘（WebDAV）</b></p><pre>`+html.EscapeString(dav)+`</pre>
<p><small>用户名任意，密码为 token。大量文件建议用这种方式拷贝。Windows：此电脑 → 映射网络驱动器；Linux 文件管理器：输入 dav://`+html.EscapeString(strings.TrimPrefix(dav, "http://"))+`<br>
网页上的「删除」只是移入该共享目录下的 `+trashDir+` 文件夹，可从中找回。</small></p></div>
<div id="pv" onclick="if(event.target===this)closePv()"><div class="box" id="pvbox"></div><p><button onclick="closePv()">关闭</button></p></div>
<script>`+pageJS+`</script>`)
}

//go:embed web/page.js
var pageJS string
