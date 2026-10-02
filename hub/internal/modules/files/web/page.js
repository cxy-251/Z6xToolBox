
let cwd='/';
const $=id=>document.getElementById(id), enc=encodeURIComponent;
const size=n=>n>1073741824?(n/1073741824).toFixed(1)+' GB':n>1048576?(n/1048576).toFixed(1)+' MB':(n/1024).toFixed(0)+' KB';
const ext=n=>(n.match(/\.([^.]+)$/)||['',''])[1].toLowerCase();
const KIND={img:'jpg jpeg png gif webp bmp avif',video:'mp4 webm mkv mov m4v',audio:'mp3 flac m4a aac ogg opus wav',
  text:'txt md log json xml yaml yml csv ini conf sh py js go kt java c h cpp html css srt ass lrc',pdf:'pdf'};
const kind=n=>{const e=ext(n);for(const k in KIND)if(KIND[k].split(' ').includes(e))return k;return ''};
const ICON={img:'🖼',video:'🎬',audio:'🎵',text:'📝',pdf:'📕','':'📄'};
const join=(d,n)=>(d==='/'?'':d)+'/'+n;
const fileURL=(p,dl)=>'/api/files/get?path='+enc(p)+(dl?'&download=1':'');
function el(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e}
function btn(text,fn){const b=el('button',text,'ghost');b.onclick=e=>{e.stopPropagation();fn()};return b}
async function api(method,url){const r=await fetch(url,{method});if(!r.ok){let m=r.status;try{m=(await r.json()).error}catch(e){}throw new Error(m)}return r}

async function go(p){
  let items; try{items=await (await api('GET','/api/files/list?path='+enc(p))).json()}catch(e){alert(e.message);return}
  cwd=p; crumb();
  const ul=$('ls'); ul.replaceChildren();
  if(p!=='/'){const li=el('li');const a=el('span','⬆ 上一级','name');a.onclick=()=>go(p.replace(/\/[^\/]+$/,'')||'/');li.append(a);ul.append(li)}
  if(!items.length) ul.append(el('li','（空文件夹）'));
  for(const it of items){
    const full=join(p,it.name), li=el('li'), k=it.dir?'dir':kind(it.name);
    const name=el('span',(it.dir?'📁 ':ICON[k]+' ')+it.name,'name');
    name.onclick=()=>it.dir?go(full):(k?preview(full,k):location.href=fileURL(full,true));
    li.append(name);
    if(!it.dir) li.append(el('small',size(it.size)));
    const ops=el('span','','ops');
    if(p!=='/'){
      if(it.dir) ops.append(btn('打包下载',()=>location.href='/api/files/zip?path='+enc(full)));
      else ops.append(btn('下载',()=>location.href=fileURL(full,true)));
      ops.append(btn('重命名',()=>rename(full,it.name)), btn('删除',()=>del(full,it.name)));
    }
    li.append(ops); ul.append(li);
  }
}
function crumb(){
  const c=$('crumb'); c.replaceChildren();
  const parts=cwd.split('/').filter(Boolean); let acc='';
  const root=el('a','全部共享'); root.onclick=()=>go('/'); c.append(root);
  for(const s of parts){acc+='/'+s;const p=acc;c.append(' / ');const a=el('a',s);a.onclick=()=>go(p);c.append(a)}
}
async function preview(p,k){
  const box=$('pvbox'); box.replaceChildren(); $('pv').style.display='flex';
  const url=fileURL(p);
  if(k==='img'){const i=el('img');i.src=url;box.append(i)}
  else if(k==='video'||k==='audio'){const v=el(k);v.src=url;v.controls=true;v.autoplay=true;box.append(v)}
  else if(k==='pdf'){const f=el('iframe');f.src=url;f.style.cssText='width:90vw;height:80vh;border:0;background:#fff';box.append(f)}
  else{const r=await fetch(url,{headers:{Range:'bytes=0-524287'}});const t=await r.text();box.append(el('pre',t+(r.status===206?'\n…（只显示前 512KB）':'')))}
  box.prepend(el('p',p.split('/').pop()));
  const dl=el('a','下载此文件');dl.href=fileURL(p,true);const q=el('p');q.append(dl);box.append(q);
}
function closePv(){$('pv').style.display='none';$('pvbox').replaceChildren()}
async function newDir(){
  if(cwd==='/'){alert('请先进入一个共享目录');return}
  const n=prompt('新文件夹名称');if(!n)return;
  try{await api('POST','/api/files/mkdir?path='+enc(cwd)+'&name='+enc(n));go(cwd)}catch(e){alert('失败：'+e.message)}
}
async function rename(p,old){
  const n=prompt('新名称',old);if(!n||n===old)return;
  try{await api('POST','/api/files/rename?path='+enc(p)+'&name='+enc(n));go(cwd)}catch(e){alert('失败：'+e.message)}
}
async function del(p,n){
  if(!confirm('删除「'+n+'」？（移入回收站，可找回）'))return;
  try{await api('POST','/api/files/delete?path='+enc(p));go(cwd)}catch(e){alert('失败：'+e.message)}
}
// 多文件上传：逐个上传，显示每个文件的进度；同名文件跳过
function upload(files){
  if(cwd==='/'){$('msg').textContent='请先进入一个共享目录';return}
  const list=[...files]; let i=0, ok=0, fail=[];
  const next=()=>{
    if(i>=list.length){$('msg').textContent='完成：成功 '+ok+' 个'+(fail.length?'，失败 '+fail.length+' 个：'+fail.join('；'):'');go(cwd);return}
    const f=list[i++], x=new XMLHttpRequest();
    x.open('POST','/api/files/upload?path='+enc(join(cwd,f.name)));
    x.upload.onprogress=e=>{if(e.lengthComputable)$('msg').textContent='上传 '+i+'/'+list.length+'：'+f.name+'  '+Math.floor(e.loaded*100/e.total)+'%'};
    x.onload=()=>{if(x.status===200)ok++;else{let m=x.status;try{m=JSON.parse(x.responseText).error}catch(e){}fail.push(f.name+'（'+m+'）')}next()};
    x.onerror=()=>{fail.push(f.name+'（网络中断）');next()};
    x.send(f);
  };
  next();
}
$('file').onchange=e=>{upload(e.target.files);e.target.value=''};
const d=$('drop');
d.ondragover=e=>{e.preventDefault();d.classList.add('on')};
d.ondragleave=()=>d.classList.remove('on');
d.ondrop=e=>{e.preventDefault();d.classList.remove('on');upload(e.dataTransfer.files)};
go('/');
