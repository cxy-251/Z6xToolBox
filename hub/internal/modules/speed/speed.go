// Package speed 提供局域网测速：下载（服务端在内存中生成数据）、上传（接收后丢弃）和往返延迟。
// 数据不经过磁盘，测得的是 hub 所在设备与客户端之间的纯网络速度。
package speed

import (
	"context"
	"io"
	"net/http"
	"strconv"
	"time"

	"z6x/hub/internal/core"
)

const maxMB = 1024

type Module struct{ block []byte }

func New() *Module {
	// 预先生成 1MB 伪随机数据，下载时反复发送，避免实时生成占用 CPU；
	// 使用伪随机内容是为了不被中间设备压缩而虚高测速结果。
	b := make([]byte, 1<<20)
	x := uint32(2463534242)
	for i := range b {
		x ^= x << 13
		x ^= x >> 17
		x ^= x << 5
		b[i] = byte(x)
	}
	return &Module{block: b}
}

func (m *Module) Name() string                           { return "speed" }
func (m *Module) Title() string                          { return "局域网测速" }
func (m *Module) Start(context.Context, *core.Env) error { return nil }
func (m *Module) Stop(context.Context) error             { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/speed/download", m.download)
	r.HandleFunc("POST /api/speed/upload", m.upload)
	r.HandleFunc("GET /api/speed/ping", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Write([]byte("pong"))
	})
	r.HandleFunc("GET /ui/speed/{$}", m.page)
}

// download 发送 size MB 数据（默认 100，最多 1024）。
func (m *Module) download(w http.ResponseWriter, r *http.Request) {
	mb, _ := strconv.Atoi(r.URL.Query().Get("mb"))
	if mb <= 0 {
		mb = 100
	}
	if mb > maxMB {
		mb = maxMB
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", strconv.Itoa(mb<<20))
	w.Header().Set("Cache-Control", "no-store")
	for i := 0; i < mb; i++ {
		if _, err := w.Write(m.block); err != nil {
			return // 客户端提前结束
		}
	}
}

// upload 读取并丢弃请求体，返回收到的字节数和服务端计时。
func (m *Module) upload(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	n, err := io.CopyBuffer(io.Discard, http.MaxBytesReader(w, r.Body, maxMB<<20), make([]byte, 256<<10))
	if err != nil {
		core.WriteError(w, http.StatusBadRequest, err.Error())
		return
	}
	sec := time.Since(start).Seconds()
	mbps := 0.0
	if sec > 0 {
		mbps = float64(n) * 8 / sec / 1e6
	}
	core.WriteJSON(w, map[string]any{"bytes": n, "seconds": sec, "mbps": int(mbps*10) / 10})
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "局域网测速", `<div class="card"><p class="row"><button onclick="run()" id="go">开始测速</button></p>
<pre id="out">测试本机与 hub 所在设备之间的网速：延迟（20 次取中位数），下载和上传各测 8 秒，过程中实时显示进度。</pre></div>
<p><small>结果受 Wi-Fi 信号和频段影响较大（2.4GHz 明显慢于 5GHz）。命令行测速：<code>curl -o /dev/null http://设备IP:8090/api/speed/download?mb=200</code>（需带 token）。</small></p>
<script>
const SECONDS=8, out=t=>document.getElementById('out').textContent=t;
const mbps=(bytes,sec)=>(bytes*8/sec/1e6).toFixed(1)+' Mbps';
let lines=[];const show=(cur)=>out(lines.join('\n')+(cur?'\n'+cur:''));
async function download(){
  const t0=performance.now();let n=0,last=0;
  const ctrl=new AbortController();const timer=setTimeout(()=>ctrl.abort(),SECONDS*1000);
  try{
    const r=await fetch('/api/speed/download?mb=1024',{cache:'no-store',signal:ctrl.signal});
    if(!r.ok)throw new Error('HTTP '+r.status);
    const reader=r.body.getReader();
    for(;;){const {done,value}=await reader.read();if(done)break;n+=value.length;
      const now=performance.now();if(now-last>500){last=now;show('下载中… '+(n/1048576).toFixed(0)+' MB，'+mbps(n,(now-t0)/1000))}}
  }catch(e){if(e.name!=='AbortError')throw e}finally{clearTimeout(timer)}
  return [n,(performance.now()-t0)/1000];
}
async function upload(){
  const chunk=new Uint8Array(1<<20);for(let i=0;i<chunk.length;i+=65536)crypto.getRandomValues(chunk.subarray(i,i+65536));
  const t0=performance.now();let n=0;
  while((performance.now()-t0)/1000<SECONDS){
    const r=await fetch('/api/speed/upload',{method:'POST',body:chunk,cache:'no-store'});
    if(!r.ok)throw new Error('HTTP '+r.status);
    n+=chunk.length;show('上传中… '+(n/1048576).toFixed(0)+' MB，'+mbps(n,(performance.now()-t0)/1000));
  }
  return [n,(performance.now()-t0)/1000];
}
async function run(){
  const btn=document.getElementById('go');btn.disabled=true;lines=[];
  try{
    const rtts=[];for(let i=0;i<20;i++){const t=performance.now();await fetch('/api/speed/ping?'+i,{cache:'no-store'});rtts.push(performance.now()-t);show('测量延迟… '+(i+1)+'/20')}
    rtts.sort((a,b)=>a-b);lines.push('延迟：'+rtts[10].toFixed(1)+' ms（最小 '+rtts[0].toFixed(1)+' ms）');show();
    let [n,s]=await download();lines.push('下载：'+mbps(n,s)+'（'+(n/1048576).toFixed(0)+' MB，'+s.toFixed(1)+' 秒）');show();
    [n,s]=await upload();lines.push('上传：'+mbps(n,s)+'（'+(n/1048576).toFixed(0)+' MB，'+s.toFixed(1)+' 秒）');show();
  }catch(e){lines.push('失败：'+e.message);show()}
  btn.disabled=false;
}
</script>`)
}
