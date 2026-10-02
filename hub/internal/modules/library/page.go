package library

import (
	"net/http"

	"z6x/hub/internal/core"
)

func (m *Module) pageRoutes(r core.Router) {
	r.HandleFunc("GET /ui/library/{$}", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "资源库", lobbyHTML) })
	r.HandleFunc("GET /ui/library/read", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "漫画", readerHTML) })
	r.HandleFunc("GET /ui/library/clips", func(w http.ResponseWriter, _ *http.Request) { core.Page(w, "短视频", clipsHTML) })
}

const libCSS = `<style>
.tabs{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}.tabs button{background:transparent;color:var(--fg);border:1px solid var(--line)}
.tabs button.on{background:var(--accent);color:#fff;border-color:var(--accent)}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}
.cards a{display:block;background:var(--card);border:1px solid var(--line);border-radius:10px;padding:8px;text-decoration:none;color:var(--fg)}
.cards img{width:100%;aspect-ratio:3/4;object-fit:cover;border-radius:6px;background:var(--bg)}
.cards .icon img{aspect-ratio:1/1;object-fit:contain}
.cards span{display:block;font-size:13px;margin-top:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
main{max-width:1100px}
</style>`

const lobbyHTML = libCSS + `<div class="tabs" id="tabs">
<button data-t="games">游戏</button><button data-t="manga">漫画</button><button data-t="videos">短视频</button><button data-t="libs">存储与转移</button></div>
<div class="row" style="margin-bottom:10px"><input id="q" placeholder="按名称筛选" style="max-width:320px"><span class="msg" id="msg"></span></div>
<div id="out"></div>
<script>
const $=id=>document.getElementById(id), enc=encodeURIComponent;
const size=n=>n>1073741824?(n/1073741824).toFixed(1)+' GB':(n/1048576).toFixed(0)+' MB';
let tab=localStorage.getItem('z6x-lib-tab')||'games', data=[];
function card(href,img,name,cls,blank){const a=document.createElement('a');a.href=href;if(blank)a.target='_blank';if(cls)a.className=cls;
  const i=document.createElement('img');i.loading='lazy';i.src=img;i.onerror=()=>i.style.visibility='hidden';const s=document.createElement('span');s.textContent=name;s.title=name;a.append(i,s);return a}
function render(){const q=$('q').value.trim().toLowerCase(),out=$('out');out.innerHTML='';
  const list=data.filter(x=>!q||(x.name||x.Name||'').toLowerCase().includes(q));
  if(tab==='libs')return renderLibs();
  $('msg').textContent='共 '+list.length+' 项';
  const box=document.createElement('div');box.className='cards';
  for(const x of list){
    if(tab==='games')box.append(card('/game/'+enc(x.id)+'/'+x.entry.split('/').map(enc).join('/'),'/icon/'+enc(x.id),x.name,'icon',true));
    else if(tab==='manga')box.append(card('/ui/library/read?id='+x.id,'/api/library/manga/thumb?id='+x.id,x.name));
    else box.append(card('/ui/library/clips?platform='+enc(x.platform)+'&creator='+enc(x.name),'data:,',x.platform+' · '+x.name+'（'+x.count+'）'));
  }
  if(!list.length)out.innerHTML='<p class="muted">没有内容。资源库为空时，请在「存储与转移」中建立资源库并放入内容。</p>';else out.append(box);
}
async function renderLibs(){const out=$('out');$('msg').textContent='';
  const libs=await (await fetch('/api/library/libs')).json(), vols=await (await fetch('/api/library/volumes')).json();
  let h='<div class="card"><b>当前资源库</b><ul class="list" style="margin-top:8px">';
  if(!libs.length)h+='<li>尚未建立资源库</li>';
  for(const l of libs)h+='<li><b></b> <small>'+l.location+' · 剩余 '+size(l.free_bytes)+' / '+size(l.total_bytes)+'</small><br><small><code></code> · id <code></code></small></li>';
  h+='</ul></div><div class="card"><b>可建立资源库的位置</b><ul class="list" style="margin-top:8px" id="vols"></ul></div>'+
    '<div class="card"><b>如何转移</b><p><small>① 大批量（手机）：用数据线连接 Deck，执行 <code>adb push</code> 把文件夹放进 omni_library 的对应目录；或在 Deck 的文件管理器中打开 <code>webdav://设备IP:8091</code>（需启用文件共享模块），直接拷贝进去。<br>'+
    '② 大批量（投影仪）：把 U 盘插到 Deck，在 omni-deck「存储与游戏库」中把它添加为资源库，用 omni-deck 把游戏或媒体移过去；再把 U 盘插到投影仪，几秒内即出现在这里。<br>'+
    '③ 少量：在 Deck 上用 <code>./hub/library-import.sh</code> 通过网络导入一个游戏文件夹（受 Wi-Fi 速度限制）。存档随游戏目录一起转移。<br>④ 网页游戏（RPG Maker、SLG）、CBZ 漫画、MP4 短视频可在浏览器中直接使用；复古游戏和 Ren\'Py 桌面版暂不支持。</small></p></div>';
  out.innerHTML=h;
  const items=out.querySelectorAll('.list')[0].querySelectorAll('li');
  libs.forEach((l,i)=>{const b=items[i].querySelector('b'),c=items[i].querySelectorAll('code');b.textContent=l.label;c[0].textContent=l.path;c[1].textContent=l.id});
  const ul=$('vols');for(const v of vols){const li=document.createElement('li');li.textContent=v.label+'（剩余 '+size(v.free_bytes)+'）';
    if(v.has_library){const s=document.createElement('small');s.textContent='  已有资源库';li.append(s)}
    else{const b=document.createElement('button');b.textContent='在此建立资源库';b.style.marginLeft='8px';
      b.onclick=async()=>{const r=await fetch('/api/library/init?target='+enc(v.target),{method:'POST'});alert(r.ok?'已建立':(await r.json()).error);renderLibs()};li.append(b)}
    ul.append(li)}
}
async function load(t){tab=t;localStorage.setItem('z6x-lib-tab',t);
  document.querySelectorAll('#tabs button').forEach(b=>b.className=b.dataset.t===t?'on':'');
  $('msg').textContent='读取中…';
  const url={games:'/api/library/games',manga:'/api/library/manga',videos:'/api/library/videos'}[t];
  data=url?await (await fetch(url)).json():[];render()}
document.querySelectorAll('#tabs button').forEach(b=>b.onclick=()=>load(b.dataset.t));
$('q').oninput=render;load(tab);
</script>`

const readerHTML = `<style>main{max-width:960px;padding:0}#pages img{display:block;width:100%;min-height:200px;background:#0001}
#bar{position:sticky;top:0;background:var(--card);border-bottom:1px solid var(--line);padding:8px 12px;z-index:2}</style>
<div id="bar" class="row"><a href="/ui/library/">返回资源库</a><b id="title"></b><span class="muted" id="pos"></span></div>
<div id="pages"></div>
<script>
const id=new URLSearchParams(location.search).get('id');
(async()=>{const r=await fetch('/api/library/manga/pages?id='+id);if(!r.ok){document.getElementById('title').textContent='无法打开';return}
  const info=await r.json();document.title=info.name;document.getElementById('title').textContent=info.name;
  const box=document.getElementById('pages');
  // 图片进入视野前不加载，长漫画也不会一次占满内存和网络
  const io=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.src=e.target.dataset.src;io.unobserve(e.target)}}),{rootMargin:'1500px'});
  for(let i=0;i<info.count;i++){const img=document.createElement('img');img.dataset.src='/api/library/manga/page?id='+id+'&n='+i;img.alt='第 '+(i+1)+' 页';box.append(img);io.observe(img)}
  const pos=document.getElementById('pos');
  const posIO=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting)pos.textContent=e.target.alt+' / 共 '+info.count+' 页'}),{threshold:0.5});
  box.querySelectorAll('img').forEach(i=>posIO.observe(i));
  // 遥控器上下键翻屏
  document.addEventListener('keydown',e=>{if(e.key==='ArrowDown'||e.key==='ArrowRight'){scrollBy(0,innerHeight*0.9);e.preventDefault()}
    if(e.key==='ArrowUp'||e.key==='ArrowLeft'){scrollBy(0,-innerHeight*0.9);e.preventDefault()}});
})();
</script>`

const clipsHTML = libCSS + `<p><a href="/ui/library/">返回资源库</a> <b id="title"></b> <span class="muted" id="msg"></span></p>
<div class="cards" id="box"></div><p><button id="more" class="ghost" style="display:none">加载更多</button></p>
<script>
const p=new URLSearchParams(location.search),enc=encodeURIComponent;let off=0;
document.getElementById('title').textContent=p.get('platform')+' · '+p.get('creator');
async function more(){const r=await (await fetch('/api/library/videos/items?platform='+enc(p.get('platform'))+'&creator='+enc(p.get('creator'))+'&offset='+off+'&limit=60')).json();
  const box=document.getElementById('box');
  for(const c of r.items){const d=document.createElement('a');d.href=c.kind==='video'?c.url:c.images[0];d.target='_blank';
    if(c.kind==='video'){const v=document.createElement('video');v.src=c.url+'#t=0.5';v.preload='metadata';v.muted=true;v.style.cssText='width:100%;aspect-ratio:9/16;object-fit:cover;border-radius:6px;background:#000';d.append(v)}
    else{const i=document.createElement('img');i.loading='lazy';i.src=c.images[0];d.append(i)}
    const s=document.createElement('span');s.textContent=(c.kind==='images'?'🖼 '+c.images.length+' 张 · ':'')+c.name;s.title=c.name;d.append(s);box.append(d)}
  off+=r.items.length;document.getElementById('msg').textContent='已显示 '+off+' / '+r.total;
  document.getElementById('more').style.display=off<r.total?'':'none'}
document.getElementById('more').onclick=more;more();
</script>`
