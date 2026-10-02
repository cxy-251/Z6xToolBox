// Package paste 让手机把文字和链接发给电视：文字只保存在内存中（最多 100 条，重启即清空），
// 可以输入到电视当前的输入框，或让电视打开链接。
package paste

import (
	"context"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"z6x/hub/internal/android"
	"z6x/hub/internal/core"
)

const maxItems = 100
const maxLen = 8192

type Item struct {
	ID      int    `json:"id"`
	Text    string `json:"text"`
	Time    string `json:"time"`
	Typable bool   `json:"typable"`
	IsURL   bool   `json:"is_url"`
}

type Module struct {
	dev    android.Device
	mu     sync.Mutex
	items  []Item
	nextID int
}

// New 创建模块。r 为 nil 时使用真实的系统命令。
func New(r android.Runner) *Module {
	if r == nil {
		r = android.Exec{}
	}
	return &Module{dev: android.Device{R: r}, nextID: 1}
}

func (m *Module) Name() string                           { return "paste" }
func (m *Module) Title() string                          { return "发送文字到电视" }
func (m *Module) Start(context.Context, *core.Env) error { return nil }
func (m *Module) Stop(context.Context) error             { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/paste/{$}", func(w http.ResponseWriter, _ *http.Request) { core.WriteJSON(w, m.list()) })
	r.HandleFunc("POST /api/paste/{$}", m.add)
	r.HandleFunc("POST /api/paste/{id}/type", m.act(func(ctx context.Context, it Item) error { return m.dev.Text(ctx, it.Text) }))
	r.HandleFunc("POST /api/paste/{id}/open", m.act(func(ctx context.Context, it Item) error { return m.dev.OpenURL(ctx, strings.TrimSpace(it.Text)) }))
	r.HandleFunc("DELETE /api/paste/{id}", m.del)
	r.HandleFunc("GET /ui/paste/{$}", m.page)
}

func (m *Module) list() []Item {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]Item, len(m.items))
	for i, it := range m.items { // 新的在前
		out[len(m.items)-1-i] = it
	}
	return out
}

func (m *Module) add(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Text string `json:"text"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxLen*4)).Decode(&req); err != nil {
		core.WriteError(w, http.StatusBadRequest, "请求格式错误")
		return
	}
	text := strings.TrimRight(req.Text, "\r\n")
	if text == "" || utf8.RuneCountInString(text) > maxLen {
		core.WriteError(w, http.StatusBadRequest, "文字为空或过长（最多 8192 字）")
		return
	}
	t := strings.TrimSpace(text)
	it := Item{Text: text, Time: time.Now().Format("01-02 15:04:05"), Typable: android.IsTypable(text),
		IsURL: (strings.HasPrefix(t, "http://") || strings.HasPrefix(t, "https://")) && !strings.ContainsAny(t, " \n")}
	m.mu.Lock()
	it.ID = m.nextID
	m.nextID++
	m.items = append(m.items, it)
	if len(m.items) > maxItems {
		m.items = m.items[len(m.items)-maxItems:]
	}
	m.mu.Unlock()
	core.WriteJSON(w, it)
}

func (m *Module) find(r *http.Request) (Item, bool) {
	id, _ := strconv.Atoi(r.PathValue("id"))
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, it := range m.items {
		if it.ID == id {
			return it, true
		}
	}
	return Item{}, false
}

func (m *Module) act(f func(context.Context, Item) error) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		it, ok := m.find(r)
		if !ok {
			core.WriteError(w, http.StatusNotFound, "条目不存在")
			return
		}
		if err := f(r.Context(), it); err != nil {
			core.WriteError(w, http.StatusBadRequest, err.Error())
			return
		}
		core.WriteJSON(w, map[string]bool{"ok": true})
	}
}

func (m *Module) del(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.Atoi(r.PathValue("id"))
	m.mu.Lock()
	for i, it := range m.items {
		if it.ID == id {
			m.items = append(m.items[:i], m.items[i+1:]...)
			break
		}
	}
	m.mu.Unlock()
	core.WriteJSON(w, map[string]bool{"ok": true})
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "发送文字到电视", `<div class="card"><textarea id="t" placeholder="粘贴文字或链接"></textarea>
<p class="row"><button onclick="send()">保存</button><button class="ghost" onclick="send(true)">保存并输入到电视</button></p>
<div class="msg" id="msg"></div>
<small>「输入到电视」会把文字输入电视当前的输入框，只支持英文、数字和半角符号；链接可以直接让电视打开。文字只保存在内存中，hub 重启后清空。</small></div>
<ul class="list" id="ls"></ul>
<script>
const msg=t=>document.getElementById('msg').textContent=t;
async function post(u,b){const r=await fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:b?JSON.stringify(b):null});const j=await r.json();if(!r.ok)throw new Error(j.error);return j}
async function send(type){const t=document.getElementById('t').value;if(!t.trim())return;
  try{const it=await post('/api/paste/',{text:t});document.getElementById('t').value='';
    if(type){await post('/api/paste/'+it.id+'/type');msg('已输入到电视')}else msg('已保存')}catch(e){msg('失败：'+e.message)} load()}
async function act(id,a){try{await post('/api/paste/'+id+'/'+a);msg(a==='type'?'已输入到电视':'已在电视上打开')}catch(e){msg('失败：'+e.message)}}
async function del(id){await fetch('/api/paste/'+id,{method:'DELETE'});load()}
async function load(){const items=await (await fetch('/api/paste/')).json(),ul=document.getElementById('ls');ul.innerHTML='';
  for(const it of items){const li=document.createElement('li'),p=document.createElement('pre');p.textContent=it.text;li.append(p);
    const row=document.createElement('div');row.className='row';row.style.marginTop='8px';
    const b=(label,fn,cls)=>{const x=document.createElement('button');x.textContent=label;if(cls)x.className=cls;x.onclick=fn;row.append(x)};
    if(it.typable)b('输入到电视',()=>act(it.id,'type'));if(it.is_url)b('在电视上打开',()=>act(it.id,'open'));
    b('删除',()=>del(it.id),'ghost');const s=document.createElement('small');s.textContent=it.time;row.append(s);li.append(row);ul.append(li)}}
load();
</script>`)
}
