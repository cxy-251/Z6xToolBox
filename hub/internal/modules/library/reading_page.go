package library

import (
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

const mediaHead = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><style>
:root{--bg:#0d1117;--card:#161b22;--line:#30363d;--fg:#e6edf3;--muted:#8b949e;--accent:#2f81f7}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:14px system-ui,sans-serif}
button,select,input{font:inherit;color:var(--fg)}
button{background:#21262d;border:1px solid var(--line);border-radius:6px;padding:5px 10px;cursor:pointer}
button.on{background:var(--accent);border-color:var(--accent)}
input[type=search]{background:#0d1117;border:1px solid var(--line);border-radius:6px;padding:6px 8px;min-width:160px}
header{position:sticky;top:0;z-index:2;background:var(--bg);border-bottom:1px solid var(--line);padding:10px 12px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
header h1{font-size:17px;margin:0 8px 0 0}
header a{color:var(--muted)}
.chips{display:flex;gap:6px;flex-wrap:wrap;padding:10px 12px 0}
.chips button{font-size:12px;padding:3px 9px;border-radius:12px}
.muted{color:var(--muted)}
</style>`

const audioHTML = mediaHead + `<title>音声</title><style>
#list{padding:8px 12px 140px}
.tr{display:flex;gap:8px;align-items:center;padding:9px 8px;border-bottom:1px solid var(--line);cursor:pointer}
.tr:hover{background:var(--card)}.tr.on{color:var(--accent)}
.tr .t{flex:1;word-break:break-all}.tr .a{font-size:12px;color:var(--muted)}
#player{position:fixed;left:0;right:0;bottom:0;background:var(--card);border-top:1px solid var(--line);padding:8px 12px;display:none;z-index:3}
#player .row{display:flex;gap:6px;align-items:center;flex-wrap:wrap}
#ptitle{flex:1;min-width:40%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#seek{width:100%;margin:6px 0 2px}
#chap{position:fixed;inset:0;background:rgba(0,0,0,.7);display:none;z-index:4;align-items:flex-end;justify-content:center}
#chap .box{background:var(--card);width:100%;max-width:640px;max-height:70vh;overflow:auto;border-radius:12px 12px 0 0;padding:12px}
#chap .c{padding:8px;border-bottom:1px solid var(--line);cursor:pointer}#chap .c.on{color:var(--accent)}
</style></head><body>
<header><h1>🎧 音声</h1><button data-s="standard" class="on">常规</button><button data-s="nsfw">NSFW</button><button data-s="music" id="musicTab" style="display:none">🎵 音乐</button>
<select id="artist" style="display:none;max-width:220px"></select><input type="search" id="q" placeholder="按标题、专辑筛选"><span class="muted" id="count"></span><span style="flex:1"></span><a href="/ui/library/">返回资源库</a></header>
<div class="chips" id="albums"></div><div id="list">读取中…</div>
<div id="player"><div class="row"><span id="ptitle"></span><span class="muted" id="ptime">0:00</span></div>
<input type="range" id="seek" min="0" max="1000" value="0">
<div class="row"><button id="prev">⏮</button><button id="back">-15s</button><button id="play">⏸</button><button id="fwd">+30s</button><button id="next">⏭</button>
<button id="mode"></button><button id="rate">1x</button><button id="sleep">⏾ 关</button><button id="chapBtn" style="display:none">章节</button></div></div>
<div id="chap"><div class="box" id="chapBox"></div></div>
<audio id="au" preload="metadata"></audio>
<script>
const $=id=>document.getElementById(id), enc=encodeURIComponent, au=$('au');
const el=(t,x,c)=>{const e=document.createElement(t);if(x!=null)e.textContent=x;if(c)e.className=c;return e};
const fmt=t=>{t=Math.floor(t||0);const h=Math.floor(t/3600),m=Math.floor(t%3600/60),s=String(t%60).padStart(2,'0');return h?h+':'+String(m).padStart(2,'0')+':'+s:m+':'+s};
const MODES=[['list','🔁 列表'],['single','🔂 单曲'],['random','🔀 随机']], RATES=[0.75,1,1.25,1.5,1.75,2], SLEEPS=[0,15,30,60,90];
let source='standard', all=[], view=[], album='', shown=0, cur=null, mode=localStorage.getItem('z6x-au-mode')||'list', rate=+localStorage.getItem('z6x-au-rate')||1;
let artist='', sleepIdx=0, sleepAt=0, chapters=[], saveTick=0;
// 续听记录的键：常规、NSFW 与 omni-deck 相同（std:、nsfw: 加相对路径）
const key=t=>({nsfw:'nsfw:',music:'music:'}[t.source]||'std:')+t.rel_path;

async function load(){
  $('list').textContent='读取中…';
  const r=await (await fetch('/api/library/audio?source='+source)).json();
  all=r.tracks;$('musicTab').style.display=r.has_music?'':'none';
  const box=$('albums');box.replaceChildren();album='';artist='';
  // 音乐：歌手很多，用下拉框选歌手，专辑不再单独列出
  const sel=$('artist');sel.style.display=source==='music'?'':'none';
  if(source==='music'){
    const ac={};all.forEach(t=>ac[t.artist]=(ac[t.artist]||0)+1);
    sel.replaceChildren(new Option('全部歌手 ('+Object.keys(ac).length+')',''));
    Object.keys(ac).sort((a,b)=>a.localeCompare(b,'zh')).forEach(a=>sel.append(new Option(a+' ('+ac[a]+')',a)));
    filter();return;
  }
  const counts={};all.forEach(t=>counts[t.album]=(counts[t.album]||0)+1);
  const chip=(name,label)=>{const b=el('button',label);b.onclick=()=>{album=name;[...box.children].forEach(x=>x.className='');b.className='on';filter()};return b};
  const a0=chip('','全部 ('+all.length+')');a0.className='on';box.append(a0);
  Object.keys(counts).sort((a,b)=>counts[b]-counts[a]).forEach(n=>box.append(chip(n,n+' ('+counts[n]+')')));
  filter();
}
function filter(){
  const q=$('q').value.trim().toLowerCase();
  view=all.filter(t=>(!album||t.album===album)&&(!artist||t.artist===artist)&&(!q||t.title.toLowerCase().includes(q)||t.album.toLowerCase().includes(q)||(t.artist||'').toLowerCase().includes(q)));
  $('count').textContent=view.length+' 条';$('list').replaceChildren();shown=0;more();
}
// 条目多时分批显示（NSFW 区有近两千条）
function more(){
  const box=$('list');const old=$('moreBtn');if(old)old.remove();
  for(const t of view.slice(shown,shown+200)){
    const r=el('div',null,'tr');r.append(el('span',t.title,'t'),el('span',t.artist?t.artist+' · '+t.album:t.album,'a'));
    r.onclick=()=>playTrack(t);if(cur&&key(cur)===key(t))r.classList.add('on');r.dataset.k=key(t);box.append(r);
  }
  shown+=200;
  if(shown<view.length){const b=el('button','显示更多（还有 '+(view.length-shown)+' 条）');b.id='moreBtn';b.onclick=more;box.append(b)}
  if(!view.length)box.textContent=source==='music'?'没有音乐。':'没有音声。文件放在资源库的 media_library/audio/standard 或 nsfw 下，第一级子目录为专辑。';
}
async function playTrack(t){
  if(cur)await saveProgress();
  cur=t;localStorage.setItem('z6x-au-last',key(t));
  $('player').style.display='block';$('ptitle').textContent=(t.artist?t.artist+' · ':'')+t.album+' · '+t.title;
  document.querySelectorAll('.tr').forEach(r=>r.classList.toggle('on',r.dataset.k===key(t)));
  au.src=t.stream_url;au.playbackRate=rate;
  let pos=0;try{const p=await (await fetch('/api/library/audio/progress?key='+enc(key(t)))).json();if(p&&p.pos>5&&(!p.dur||p.pos<p.dur-5))pos=p.pos}catch(e){}
  au.addEventListener('loadedmetadata',()=>{if(pos)au.currentTime=pos},{once:true});
  au.play().catch(()=>{});
  chapters=[];$('chapBtn').style.display='none';
  try{chapters=await (await fetch('/api/library/audio/chapters?source='+t.source+'&rel_path='+enc(t.rel_path))).json()}catch(e){}
  $('chapBtn').style.display=chapters.length?'':'none';
}
function saveProgress(){
  if(!cur||!au.duration)return Promise.resolve();
  return fetch('/api/library/audio/progress',{method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({key:key(cur),value:{pos:au.currentTime,dur:au.duration,at:Math.floor(Date.now()/1000)}})}).catch(()=>{});
}
function step(d){
  if(!cur||!view.length)return;let i=view.findIndex(t=>key(t)===key(cur));
  if(mode==='random')i=Math.floor(Math.random()*view.length);else i=(i+d+view.length)%view.length;
  playTrack(view[i]);
}
au.onended=()=>{saveProgress();mode==='single'?(au.currentTime=0,au.play()):step(1)};
au.ontimeupdate=()=>{
  if(au.duration){$('seek').value=au.currentTime/au.duration*1000;$('ptime').textContent=fmt(au.currentTime)+' / '+fmt(au.duration)}
  if(++saveTick%40===0)saveProgress();   // 约每 10 秒保存一次
  if(sleepAt&&Date.now()>=sleepAt){au.pause();sleepAt=0;sleepIdx=0;$('sleep').textContent='⏾ 关'}
};
au.onplay=au.onpause=()=>{$('play').textContent=au.paused?'▶':'⏸';if(au.paused)saveProgress()};
$('seek').oninput=()=>{if(au.duration)au.currentTime=$('seek').value/1000*au.duration};
$('play').onclick=()=>au.paused?au.play():au.pause();
$('prev').onclick=()=>step(-1);$('next').onclick=()=>step(1);
$('back').onclick=()=>au.currentTime=Math.max(0,au.currentTime-15);$('fwd').onclick=()=>au.currentTime+=30;
const showMode=()=>$('mode').textContent=MODES.find(m=>m[0]===mode)[1];
$('mode').onclick=()=>{mode=MODES[(MODES.findIndex(m=>m[0]===mode)+1)%3][0];localStorage.setItem('z6x-au-mode',mode);showMode()};
$('rate').onclick=()=>{rate=RATES[(RATES.indexOf(rate)+1)%RATES.length];au.playbackRate=rate;localStorage.setItem('z6x-au-rate',rate);$('rate').textContent=rate+'x'};
$('sleep').onclick=()=>{sleepIdx=(sleepIdx+1)%SLEEPS.length;const m=SLEEPS[sleepIdx];sleepAt=m?Date.now()+m*60000:0;$('sleep').textContent=m?'⏾ '+m+' 分':'⏾ 关'};
$('chapBtn').onclick=()=>{const b=$('chapBox');b.replaceChildren();chapters.forEach((c,i)=>{
  const r=el('div',fmt(c.start)+'  '+c.title,'c');if(au.currentTime>=c.start&&(i===chapters.length-1||au.currentTime<chapters[i+1].start))r.classList.add('on');
  r.onclick=()=>{au.currentTime=c.start;$('chap').style.display='none'};b.append(r)});$('chap').style.display='flex'};
$('chap').onclick=e=>{if(e.target.id==='chap')$('chap').style.display='none'};
$('q').oninput=filter;
document.querySelectorAll('header button[data-s]').forEach(b=>b.onclick=()=>{source=b.dataset.s;
  document.querySelectorAll('header button[data-s]').forEach(x=>x.className=x===b?'on':'');load()});
$('artist').onchange=()=>{artist=$('artist').value;filter()};
window.addEventListener('pagehide',saveProgress);
showMode();$('rate').textContent=rate+'x';load();
</script></body></html>`

const novelsHTML = mediaHead + `<title>小说</title><style>
#shelf{padding:8px 12px 40px;display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:8px}
.bk{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:10px;cursor:pointer}
.bk .t{font-weight:600;word-break:break-all}.bk .m{font-size:12px;color:var(--muted);margin-top:4px}
#reader{display:none;min-height:100vh}
#reader header{justify-content:space-between}
#rtitle{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#content{max-width:760px;margin:0 auto;padding:16px 18px 80px;line-height:var(--lh,1.9);font-size:var(--fs,18px)}
#content p{margin:0 0 .9em;text-indent:2em}#content h2{font-size:1.2em;text-indent:0;margin:.5em 0 1em}
#content.doc{font-size:calc(var(--fs,18px) - 2px);line-height:1.7}#content.doc p{text-indent:0}
#content h3,#content h4{margin:1.2em 0 .6em}#content pre{background:var(--card);border:1px solid var(--line);border-radius:6px;padding:10px;overflow:auto;font:13px/1.5 ui-monospace,monospace;white-space:pre}
#content blockquote{margin:0 0 .9em;padding-left:12px;border-left:3px solid var(--line);color:var(--muted)}#content ul{margin:0 0 .9em;padding-left:1.4em}
.bk .s{font-size:12px;color:var(--muted);word-break:break-all}
.nav{display:flex;justify-content:space-between;gap:8px;max-width:760px;margin:0 auto;padding:0 18px 40px}
body.sepia{--bg:#f4ecd8;--card:#ebe1c8;--fg:#3b3226;--line:#d6c9a8;--muted:#7a6a50}
body.light{--bg:#fafafa;--card:#eee;--fg:#222;--line:#ddd;--muted:#666}
#toc{position:fixed;inset:0;background:rgba(0,0,0,.6);display:none;z-index:4}
#toc .box{background:var(--card);width:min(420px,90vw);height:100%;overflow:auto;padding:12px}
#toc .c{padding:8px;border-bottom:1px solid var(--line);cursor:pointer}#toc .c.on{color:var(--accent)}
</style></head><body>
<div id="shelfView">
<header><h1>📚 小说</h1><button data-s="standard" class="on">常规</button><button data-s="nsfw">NSFW</button><button data-s="docs">技术文档</button>
<input type="search" id="q" placeholder="按书名筛选"><span class="muted" id="count"></span><span style="flex:1"></span><a href="/ui/library/">返回资源库</a></header>
<div class="chips" id="cats"></div><div id="shelf">读取中…</div></div>
<div id="reader">
<header><button id="close">← 书架</button><span id="rtitle"></span><button id="tocBtn">目录</button>
<button id="fsm">A-</button><button id="fsp">A+</button><button id="theme">🌓</button></header>
<div id="content"></div><div class="nav"><button id="prevC">上一章</button><span class="muted" id="pos"></span><button id="nextC">下一章</button></div></div>
<div id="toc"><div class="box" id="tocBox"></div></div>
<script>
const $=id=>document.getElementById(id), enc=encodeURIComponent;
const el=(t,x,c)=>{const e=document.createElement(t);if(x!=null)e.textContent=x;if(c)e.className=c;return e};
const size=n=>n>1048576?(n/1048576).toFixed(1)+' MB':(n/1024).toFixed(0)+' KB';
let source='standard', books=[], docNav={}, cat='', book=null, toc=[], chap=0, saveTimer=0;
let fs=+localStorage.getItem('z6x-nv-fs')||18, theme=localStorage.getItem('z6x-nv-theme')||'dark';
const applyStyle=()=>{document.documentElement.style.setProperty('--fs',fs+'px');document.body.className=theme==='dark'?'':theme};

async function loadShelf(){
  $('shelf').textContent='读取中…';
  books=await (await fetch('/api/library/novels?source='+source)).json();
  const counts={};books.forEach(b=>counts[b.category]=(counts[b.category]||0)+1);
  const box=$('cats');box.replaceChildren();cat='';
  const chip=(n,l)=>{const b=el('button',l);b.onclick=()=>{cat=n;[...box.children].forEach(x=>x.className='');b.className='on';render()};return b};
  const c0=chip('','全部 ('+books.length+')');c0.className='on';box.append(c0);
  Object.keys(counts).sort().forEach(n=>box.append(chip(n,n+' ('+counts[n]+')')));
  render();
}
function render(){
  const q=$('q').value.trim().toLowerCase(), box=$('shelf');box.replaceChildren();
  const list=books.filter(b=>(!cat||b.category===cat)&&(!q||b.title.toLowerCase().includes(q)||(b.sub||'').toLowerCase().includes(q)));
  $('count').textContent=list.length+' 本';
  for(const b of list.slice(0,600)){const d=el('div',null,'bk');d.append(el('div',b.title,'t'));if(b.sub)d.append(el('div',b.sub,'s'));
    d.append(el('div',b.category+' · '+b.ext.toUpperCase()+' · '+size(b.size),'m'));d.onclick=()=>openBook(b);box.append(d)}
  if(list.length>600)box.append(el('div','只显示前 600 本，请用搜索缩小范围','muted'));
  if(!books.length)box.textContent=source==='docs'?'没有文档。Markdown、RST 文件放在资源库的 media_library/docs 下。':'没有小说。EPUB、TXT 等文件放在资源库的 media_library/novels/standard 或 nsfw 下，第一级子目录为分类。';
}
async function openBook(b){
  book=b;$('shelfView').style.display='none';$('reader').style.display='block';$('content').textContent='打开中…';
  let r;try{r=await (await fetch('/api/library/novels/toc?id='+enc(b.id))).json()}catch(e){$('content').textContent='无法打开';return}
  if(r.error){$('content').textContent=r.error;return}
  toc=r.chapters;docNav={prev:r.prev_doc,next:r.next_doc};book.title=book.title||r.title;
  $('content').className=(r.ext==='md'||r.ext==='markdown'||r.ext==='rst')?'doc':'';
  $('rtitle').textContent=r.title+(r.author?' · '+r.author:'');
  let p=null;try{p=await (await fetch('/api/library/novels/progress?key='+enc(b.id))).json()}catch(e){}
  await showChapter(p&&p.chapter<toc.length?p.chapter:0,p?p.ratio:0);
}
async function showChapter(n,ratio){
  if(!toc.length){$('content').textContent='这本书没有可显示的正文';return}
  chap=Math.max(0,Math.min(n,toc.length-1));
  const c=await (await fetch('/api/library/novels/chapter?id='+enc(book.id)+'&n='+chap)).json();
  const box=$('content');box.replaceChildren();
  if(c.blocks){   // MD、RST：按块显示
    let ul=null;
    for(const b of c.blocks){
      if(b.k!=='li')ul=null;
      if(b.k==='h')box.append(el('h'+Math.min(4,Math.max(2,b.l+1)),b.t));
      else if(b.k==='code')box.append(el('pre',b.t));
      else if(b.k==='quote')box.append(el('blockquote',b.t));
      else if(b.k==='hr')box.append(el('hr'));
      else if(b.k==='li'){if(!ul){ul=el('ul');box.append(ul)}ul.append(el('li',b.t))}
      else box.append(el('p',b.t));
    }
  }else{
    box.append(el('h2',c.title));
    (c.paras||[]).forEach((t,i)=>{if(i===0&&t===c.title)return;box.append(el('p',t))});
  }
  $('pos').textContent=(chap+1)+' / '+toc.length;
  // 第一章之前、最后一章之后：技术文档可接着读同一目录的上一篇、下一篇
  $('prevC').textContent=chap===0&&docNav.prev?'上一篇':'上一章';$('nextC').textContent=chap===toc.length-1&&docNav.next?'下一篇':'下一章';
  $('prevC').disabled=chap===0&&!docNav.prev;$('nextC').disabled=chap===toc.length-1&&!docNav.next;
  requestAnimationFrame(()=>window.scrollTo(0,ratio?ratio*(document.documentElement.scrollHeight-innerHeight):0));
  save();
}
function save(){
  clearTimeout(saveTimer);
  saveTimer=setTimeout(()=>{if(!book)return;const h=document.documentElement.scrollHeight-innerHeight;
    fetch('/api/library/novels/progress',{method:'POST',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({key:book.id,value:{chapter:chap,ratio:h>0?scrollY/h:0,title:book.title,at:Math.floor(Date.now()/1000)}})}).catch(()=>{})},800);
}
window.addEventListener('scroll',()=>{if(book)save()},{passive:true});
$('prevC').onclick=()=>chap===0&&docNav.prev?openBook({id:docNav.prev,title:''}):showChapter(chap-1,0);
$('nextC').onclick=()=>chap===toc.length-1&&docNav.next?openBook({id:docNav.next,title:''}):showChapter(chap+1,0);
$('close').onclick=()=>{save();book=null;$('reader').style.display='none';$('shelfView').style.display='block'};
$('tocBtn').onclick=()=>{const b=$('tocBox');b.replaceChildren();toc.forEach((t,i)=>{const r=el('div',t,'c');if(i===chap)r.classList.add('on');
  r.onclick=()=>{$('toc').style.display='none';showChapter(i,0)};b.append(r)});$('toc').style.display='block';
  const on=b.querySelector('.on');if(on)on.scrollIntoView({block:'center'})};
$('toc').onclick=e=>{if(e.target.id==='toc')$('toc').style.display='none'};
$('fsm').onclick=()=>{fs=Math.max(12,fs-1);localStorage.setItem('z6x-nv-fs',fs);applyStyle()};
$('fsp').onclick=()=>{fs=Math.min(32,fs+1);localStorage.setItem('z6x-nv-fs',fs);applyStyle()};
$('theme').onclick=()=>{theme={dark:'sepia',sepia:'light',light:'dark'}[theme];localStorage.setItem('z6x-nv-theme',theme);applyStyle()};
document.addEventListener('keydown',e=>{if(!book||e.target.tagName==='INPUT')return;if(e.key==='ArrowRight')$('nextC').click();else if(e.key==='ArrowLeft')$('prevC').click()});
$('q').oninput=render;
document.querySelectorAll('#shelfView header button[data-s]').forEach(b=>b.onclick=()=>{source=b.dataset.s;
  document.querySelectorAll('#shelfView header button[data-s]').forEach(x=>x.className=x===b?'on':'');loadShelf()});
applyStyle();loadShelf();
</script></body></html>`
