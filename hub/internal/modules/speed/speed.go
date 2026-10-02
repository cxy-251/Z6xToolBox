// Package speed 提供局域网测速：下载（服务端在内存中生成数据）、上传（接收后丢弃）和往返延迟。
// 数据不经过磁盘，测得的是投影仪与客户端之间的纯网络速度。
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
<pre id="out">测试本设备与投影仪之间的网速：延迟（20 次取中位数）、下载 100MB、上传 50MB。</pre></div>
<p><small>结果受 Wi-Fi 信号影响较大；投影仪当前连接在 5GHz 频段。命令行测速：<code>curl -o /dev/null http://投影仪IP:8090/api/speed/download?mb=200</code>（需带 token）。</small></p>
<script>
const out=t=>document.getElementById('out').textContent=t;
async function run(){
  const btn=document.getElementById('go');btn.disabled=true;let log='';const add=s=>{log+=s+'\n';out(log)};
  try{
    const rtts=[];for(let i=0;i<20;i++){const t=performance.now();await fetch('/api/speed/ping?'+i,{cache:'no-store'});rtts.push(performance.now()-t)}
    rtts.sort((a,b)=>a-b);add('延迟：'+rtts[10].toFixed(1)+' ms（最小 '+rtts[0].toFixed(1)+' ms）');
    add('下载测试中…');let t=performance.now();const r=await fetch('/api/speed/download?mb=100',{cache:'no-store'});
    const reader=r.body.getReader();let n=0;for(;;){const {done,value}=await reader.read();if(done)break;n+=value.length}
    let s=(performance.now()-t)/1000;log=log.replace('下载测试中…\n','');add('下载：'+(n*8/s/1e6).toFixed(1)+' Mbps（'+(n/1048576).toFixed(0)+' MB，'+s.toFixed(1)+' 秒）');
    add('上传测试中…');const body=new Uint8Array(50<<20);crypto.getRandomValues(body.subarray(0,65536));
    t=performance.now();const u=await (await fetch('/api/speed/upload',{method:'POST',body})).json();s=(performance.now()-t)/1000;
    log=log.replace('上传测试中…\n','');add('上传：'+(u.bytes*8/s/1e6).toFixed(1)+' Mbps（'+(u.bytes/1048576).toFixed(0)+' MB，'+s.toFixed(1)+' 秒）');
  }catch(e){add('失败：'+e.message)}
  btn.disabled=false;
}
</script>`)
}
