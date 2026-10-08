package core

import (
	"fmt"
	"html"
	"net/http"
)

// pageCSS 是所有页面共用的样式：适配手机竖屏，也能在电视浏览器中使用；跟随系统深浅色。
const pageCSS = `
:root{--bg:#f6f7f9;--card:#fff;--fg:#1d2330;--muted:#6b7385;--accent:#2f6fed;--line:#dfe3ea;--ok:#1f9d55;--bad:#d64545}
@media (prefers-color-scheme:dark){:root{--bg:#14171d;--card:#1d2129;--fg:#e6e9ef;--muted:#9aa3b2;--accent:#6c9bff;--line:#2c323d;--ok:#4cc38a;--bad:#ff6b6b}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,-apple-system,"Noto Sans CJK SC",sans-serif}
main{max-width:720px;margin:0 auto;padding:16px}
h1{font-size:20px;margin:4px 0 16px}
a{color:var(--accent)}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px;margin:0 0 12px}
input,textarea,select{font:inherit;color:inherit;background:var(--bg);border:1px solid var(--line);border-radius:8px;padding:10px;width:100%}
textarea{min-height:96px}
button{font:inherit;border:0;border-radius:10px;padding:10px 14px;background:var(--accent);color:#fff;cursor:pointer;min-height:44px}
button.ghost{background:transparent;color:var(--fg);border:1px solid var(--line)}
button:active{opacity:.7}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.list{list-style:none;padding:0;margin:0}
.list li{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px;margin-bottom:8px}
.running{color:var(--ok)}.failed{color:var(--bad)}.stopped{color:var(--muted)}
small,.muted{color:var(--muted)}
pre{white-space:pre-wrap;word-break:break-all;margin:0}
.msg{min-height:1.5em;color:var(--muted)}
`

// Page 输出一个完整页面。body 由调用方保证已转义。
func Page(w http.ResponseWriter, title, body string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	fmt.Fprintf(w, `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>%s</title><style>%s</style></head><body><main>
<p class="home"><small><a href="/">z6x-hub</a></small></p><h1>%s</h1>%s</main>
<script>if(window.top!==window.self){document.querySelector('.home').remove();document.querySelector('h1').remove()}</script></body></html>`,
		html.EscapeString(title), pageCSS, html.EscapeString(title), body)
}
