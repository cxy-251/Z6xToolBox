// Package metrics 读取 /proc 和 /sys，提供 CPU、内存、温度、存储和网络流量。
// /api/metrics/ 返回 JSON，/metrics 返回 Prometheus 文本格式。
//
// 投影仪上 hub 为 shell 身份，各项都能读取。手机上 hub 在 Termux 中运行（普通应用），安卓不允许读取
// /proc/stat、/proc/loadavg、/proc/uptime、/proc/net/dev 与网卡统计（2026-10-09 实测）：读不到的项为 null，
// 并在 missing 中说明原因，不再显示为 0。替代来源：CPU 各核心的当前与最高频率（/sys/devices/system/cpu，可读），
// 开机时长与负载用 sysinfo 系统调用（可用）。
package metrics

import (
	"bufio"
	"context"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"z6x/hub/internal/core"
)

type Config struct {
	IntervalSec int    `yaml:"interval_sec"`
	Iface       string `yaml:"iface"`
	DataPath    string `yaml:"data_path"`
	// Root 用于测试：把 /proc、/sys 换成别的目录。正常留空。
	Root string `yaml:"root"`
}

// Snapshot 是一次采样的结果。
type Snapshot struct {
	Time       string             `json:"time"`
	CPUPercent *float64           `json:"cpu_percent"`      // 读不到 /proc/stat 时为 null
	CPUFreqPct *float64           `json:"cpu_freq_percent"` // 各核心当前频率之和占最高频率之和的比例
	Clusters   []Cluster          `json:"cpu_clusters"`     // 按最高频率分组的核心（大小核）
	MemTotalKB int64              `json:"mem_total_kb"`
	MemAvailKB int64              `json:"mem_available_kb"`
	TempsC     map[string]float64 `json:"temps_c"`
	TempsKey   []NamedTemp        `json:"temps_key"` // 常看的几项：CPU 最高、GPU、电池、机身
	DataFreeB  uint64             `json:"data_free_bytes"`
	DataTotalB uint64             `json:"data_total_bytes"`
	NetRxB     *uint64            `json:"net_rx_bytes"`
	NetTxB     *uint64            `json:"net_tx_bytes"`
	UptimeSec  float64            `json:"uptime_sec"`
	Load1      float64            `json:"load1"`
	Missing    map[string]string  `json:"missing,omitempty"` // 读不到的项及原因
}

// Cluster 是一组最高频率相同的 CPU 核心。
type Cluster struct {
	Cores  string `json:"cores"` // 如 0-3
	CurMHz int    `json:"cur_mhz"`
	MaxMHz int    `json:"max_mhz"`
}

// NamedTemp 是一项归类后的温度。
type NamedTemp struct {
	Label string  `json:"label"`
	C     float64 `json:"c"`
	Zone  string  `json:"zone"`
}

// keyTemps：页面上优先显示的温度，按温区名称的前缀归类，取同类中最高的读数。
var keyTemps = []struct{ label, prefix string }{
	{"CPU", "cpu"}, {"GPU", "gpu"}, {"电池", "battery"}, {"机身", "quiet_therm"}, {"机身", "skin"},
}

const noPermission = "安卓不允许普通应用读取（hub 在 Termux 中运行）"

type Module struct {
	cfg  Config
	mu   sync.RWMutex
	last Snapshot
	prev cpuTimes
}

func New() *Module { return &Module{} }

func (m *Module) Name() string  { return "metrics" }
func (m *Module) Title() string { return "系统状态" }

func (m *Module) Start(ctx context.Context, env *core.Env) error {
	m.cfg = Config{IntervalSec: 5, Iface: "wlan0", DataPath: "/data"}
	if err := env.Config.Decode("metrics", &m.cfg); err != nil {
		return err
	}
	if m.cfg.IntervalSec < 5 {
		m.cfg.IntervalSec = 5 // 规格要求采样间隔不少于 5 秒
	}
	m.prev, _ = m.readCPU()
	m.sample()
	go func() {
		t := time.NewTicker(time.Duration(m.cfg.IntervalSec) * time.Second)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-t.C:
				m.sample()
			}
		}
	}()
	return nil
}

func (m *Module) Stop(context.Context) error { return nil }

func (m *Module) Routes(r core.Router) {
	r.HandleFunc("GET /api/metrics/{$}", func(w http.ResponseWriter, _ *http.Request) { core.WriteJSON(w, m.Snapshot()) })
	r.HandleFunc("GET /metrics", m.prometheus)
	r.HandleFunc("GET /ui/metrics/{$}", m.page)
}

func (m *Module) Snapshot() Snapshot {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.last
}

func (m *Module) path(p string) string { return filepath.Join(m.cfg.Root, p) }

// sample 采集一次数据。单项读取失败只跳过该项，不让整次采样失败。
func (m *Module) sample() {
	s := Snapshot{Time: time.Now().Format(time.RFC3339), TempsC: map[string]float64{}, Missing: map[string]string{}}
	if cur, err := m.readCPU(); err == nil {
		v := cpuPercent(m.prev, cur)
		s.CPUPercent = &v
		m.prev = cur
	} else {
		s.Missing["cpu_percent"] = "CPU 使用率：" + noPermission + "，改看频率负载"
	}
	s.Clusters, s.CPUFreqPct = m.readFreq()
	mem := readKeyValues(m.path("/proc/meminfo"))
	s.MemTotalKB, s.MemAvailKB = mem["MemTotal"], mem["MemAvailable"]
	zones, _ := filepath.Glob(m.path("/sys/class/thermal/thermal_zone*"))
	for _, z := range zones {
		typ, e1 := os.ReadFile(filepath.Join(z, "type"))
		raw, e2 := os.ReadFile(filepath.Join(z, "temp"))
		if e1 != nil || e2 != nil {
			continue
		}
		// 手机上有几十个温区，其中一些不是真实温度（例如 bcl-warn 读数为 -273）：超出 -40～150 ℃ 的读数视为无效。
		if v, err := strconv.ParseFloat(strings.TrimSpace(string(raw)), 64); err == nil && v/1000 > -40 && v/1000 < 150 {
			s.TempsC[strings.TrimSpace(string(typ))] = v / 1000
		}
	}
	var st syscall.Statfs_t
	if syscall.Statfs(m.cfg.DataPath, &st) == nil {
		s.DataFreeB = st.Bavail * uint64(st.Bsize)
		s.DataTotalB = st.Blocks * uint64(st.Bsize)
	}
	for _, k := range keyTemps {
		best := NamedTemp{Label: k.label, C: -999}
		for zone, c := range s.TempsC {
			if strings.HasPrefix(zone, k.prefix) && c > best.C {
				best.C, best.Zone = c, zone
			}
		}
		if best.Zone != "" && !slices.ContainsFunc(s.TempsKey, func(t NamedTemp) bool { return t.Label == k.label }) {
			s.TempsKey = append(s.TempsKey, best)
		}
	}
	if rx, tx, ok := readNetDev(m.path("/proc/net/dev"), m.cfg.Iface); ok {
		s.NetRxB, s.NetTxB = &rx, &tx
	} else {
		s.Missing["net"] = "网络流量：" + noPermission
	}
	raw, err1 := os.ReadFile(m.path("/proc/uptime"))
	raw2, err2 := os.ReadFile(m.path("/proc/loadavg"))
	if err1 == nil && err2 == nil {
		fmt.Sscanf(string(raw), "%f", &s.UptimeSec)
		fmt.Sscanf(string(raw2), "%f", &s.Load1)
	} else if m.cfg.Root == "" {
		var si syscall.Sysinfo_t // 读不到 /proc 时改用 sysinfo 系统调用（手机上可用）
		if syscall.Sysinfo(&si) == nil {
			s.UptimeSec, s.Load1 = float64(si.Uptime), float64(si.Loads[0])/65536
		}
	}
	m.mu.Lock()
	m.last = s
	m.mu.Unlock()
}

type cpuTimes struct{ idle, total uint64 }

// readFreq 读取各核心的当前与最高频率（/sys/devices/system/cpu/cpuN/cpufreq，普通应用也可读），
// 按最高频率分组为大小核，并计算频率负载：当前频率之和占最高频率之和的比例。CPU 空闲时降频，
// 忙时升频，因此在读不到 /proc/stat 的手机上可以大致反映忙闲。
func (m *Module) readFreq() ([]Cluster, *float64) {
	read := func(p string) int {
		b, err := os.ReadFile(p)
		if err != nil {
			return 0
		}
		v, _ := strconv.Atoi(strings.TrimSpace(string(b)))
		return v / 1000
	}
	var out []Cluster
	var cur, max int
	var start, last, sum, n int
	flush := func() {
		if n > 0 {
			cores := strconv.Itoa(start)
			if last > start {
				cores += "-" + strconv.Itoa(last)
			}
			out[len(out)-1].Cores, out[len(out)-1].CurMHz = cores, sum/n
		}
	}
	for i := 0; ; i++ {
		dir := m.path(fmt.Sprintf("/sys/devices/system/cpu/cpu%d/cpufreq", i))
		if _, err := os.Stat(dir); err != nil {
			if _, err := os.Stat(m.path(fmt.Sprintf("/sys/devices/system/cpu/cpu%d", i))); err != nil {
				break
			}
			continue // 核心离线
		}
		c, mx := read(dir+"/scaling_cur_freq"), read(dir+"/cpuinfo_max_freq")
		if mx == 0 {
			continue
		}
		cur, max = cur+c, max+mx
		if len(out) == 0 || out[len(out)-1].MaxMHz != mx {
			flush()
			out = append(out, Cluster{MaxMHz: mx})
			start, sum, n = i, 0, 0
		}
		last, sum, n = i, sum+c, n+1
	}
	flush()
	if max == 0 {
		return nil, nil
	}
	pct := float64(int(float64(cur)/float64(max)*1000)) / 10
	return out, &pct
}

func (m *Module) readCPU() (cpuTimes, error) {
	f, err := os.Open(m.path("/proc/stat"))
	if err != nil {
		return cpuTimes{}, err
	}
	defer f.Close()
	line, _ := bufio.NewReader(f).ReadString('\n')
	fields := strings.Fields(line)
	if len(fields) < 5 || fields[0] != "cpu" {
		return cpuTimes{}, fmt.Errorf("无法解析 /proc/stat")
	}
	var t cpuTimes
	for i, v := range fields[1:] {
		n, _ := strconv.ParseUint(v, 10, 64)
		t.total += n
		if i == 3 || i == 4 { // idle、iowait
			t.idle += n
		}
	}
	return t, nil
}

func cpuPercent(a, b cpuTimes) float64 {
	dt := float64(b.total - a.total)
	if b.total <= a.total || dt == 0 {
		return 0
	}
	busy := dt - float64(b.idle-a.idle)
	return float64(int(busy/dt*1000)) / 10
}

func readKeyValues(path string) map[string]int64 {
	out := map[string]int64{}
	f, err := os.Open(path)
	if err != nil {
		return out
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		k, v, ok := strings.Cut(sc.Text(), ":")
		if !ok {
			continue
		}
		fs := strings.Fields(v)
		if len(fs) > 0 {
			n, _ := strconv.ParseInt(fs[0], 10, 64)
			out[k] = n
		}
	}
	return out
}

func readNetDev(path, iface string) (rx, tx uint64, ok bool) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	ok = true
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		name, rest, ok := strings.Cut(sc.Text(), ":")
		if !ok || strings.TrimSpace(name) != iface {
			continue
		}
		fs := strings.Fields(rest)
		if len(fs) >= 9 {
			rx, _ = strconv.ParseUint(fs[0], 10, 64)
			tx, _ = strconv.ParseUint(fs[8], 10, 64)
		}
	}
	return
}

func (m *Module) prometheus(w http.ResponseWriter, _ *http.Request) {
	s := m.Snapshot()
	w.Header().Set("Content-Type", "text/plain; version=0.0.4")
	g := func(name, help string, v any, labels string) {
		fmt.Fprintf(w, "# HELP %s %s\n# TYPE %s gauge\n%s%s %v\n", name, help, name, name, labels, v)
	}
	if s.CPUPercent != nil {
		g("z6x_cpu_percent", "CPU 使用率（百分比）", *s.CPUPercent, "")
	}
	if s.CPUFreqPct != nil {
		g("z6x_cpu_freq_percent", "CPU 频率负载（当前频率占最高频率的百分比）", *s.CPUFreqPct, "")
	}
	g("z6x_mem_total_bytes", "内存总量", s.MemTotalKB*1024, "")
	g("z6x_mem_available_bytes", "可用内存", s.MemAvailKB*1024, "")
	g("z6x_data_free_bytes", "/data 剩余空间", s.DataFreeB, "")
	if s.NetRxB != nil {
		g("z6x_net_rx_bytes", "网卡累计接收字节", *s.NetRxB, "")
		g("z6x_net_tx_bytes", "网卡累计发送字节", *s.NetTxB, "")
	}
	g("z6x_uptime_seconds", "开机以来的秒数（含睡眠）", s.UptimeSec, "")
	fmt.Fprintf(w, "# HELP z6x_temp_celsius 温度区温度\n# TYPE z6x_temp_celsius gauge\n")
	for k, v := range s.TempsC {
		fmt.Fprintf(w, "z6x_temp_celsius{zone=%q} %v\n", k, v)
	}
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "系统状态", pageHTML)
}

// pageHTML：卡片 + 用量条；读不到的项显示原因（来自 missing），温度先显示常看的几项，其余可展开。
const pageHTML = `<style>
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px}
.m{border:1px solid var(--line);border-radius:10px;padding:10px 12px}
.m h3{margin:0 0 6px;font-size:13px;font-weight:600;opacity:.75}
.v{font-size:22px;font-weight:600}.v small{font-size:13px;font-weight:400;opacity:.7;margin-left:4px}
.bar{height:6px;border-radius:3px;background:rgba(128,128,128,.25);margin:8px 0 4px;overflow:hidden}.bar i{display:block;height:100%;border-radius:3px}
.sub{font-size:12px;opacity:.7;line-height:1.6}
.temps{display:flex;gap:8px;flex-wrap:wrap;margin-top:4px}.temps span{border:1px solid var(--line);border-radius:12px;padding:2px 9px;font-size:13px}
details{margin-top:8px;font-size:12px;opacity:.8}details div{columns:2;line-height:1.7}
</style>
<div class="grid" id="g">读取中…</div>
<p><small>每 5 秒刷新。原始数据：<a href="/api/metrics/">/api/metrics/</a> · <a href="/metrics">Prometheus 格式</a></small></p>
<script>
const gb=b=>(b/1073741824).toFixed(1)+' GB';
const color=p=>p<60?'#3fb950':p<85?'#d29922':'#f85149';
const tcolor=c=>c<40?'':c<50?'#d29922':'#f85149';
function card(title,value,unit,pct,sub){const d=document.createElement('div');d.className='m';
  const h=document.createElement('h3');h.textContent=title;const v=document.createElement('div');v.className='v';v.textContent=value;
  if(unit){const u=document.createElement('small');u.textContent=unit;v.append(u)}d.append(h,v);
  if(pct!=null){const b=document.createElement('div');b.className='bar';const i=document.createElement('i');i.style.width=Math.min(100,pct)+'%';i.style.background=color(pct);b.append(i);d.append(b)}
  if(sub){const x=document.createElement('div');x.className='sub';x.textContent=sub;d.append(x)}return d}
async function load(){
  const s=await (await fetch('/api/metrics/')).json(),g=document.getElementById('g'),miss=s.missing||{};g.replaceChildren();
  // CPU：能读到使用率时显示使用率，否则显示频率负载
  const cl=(s.cpu_clusters||[]).map(c=>'核心 '+c.cores+'：'+c.cur_mhz+' / '+c.max_mhz+' MHz').join('\n');
  if(s.cpu_percent!=null)g.append(card('CPU 使用率',s.cpu_percent.toFixed(1),'%',s.cpu_percent,'负载 '+s.load1.toFixed(2)+(cl?'\n'+cl:'')));
  else if(s.cpu_freq_percent!=null)g.append(card('CPU 频率负载',s.cpu_freq_percent.toFixed(0),'%',s.cpu_freq_percent,'当前频率占最高频率的比例（'+(miss.cpu_percent||'')+'）\n负载 '+s.load1.toFixed(2)+'\n'+cl));
  const used=s.mem_total_kb-s.mem_available_kb;
  g.append(card('内存',(used/1048576).toFixed(1),'/ '+(s.mem_total_kb/1048576).toFixed(1)+' GB 已用',used/s.mem_total_kb*100,'可用 '+(s.mem_available_kb/1048576).toFixed(1)+' GB'));
  const du=s.data_total_bytes-s.data_free_bytes;
  g.append(card('存储',gb(du).replace(' GB',''),'/ '+gb(s.data_total_bytes)+' 已用',du/s.data_total_bytes*100,'剩余 '+gb(s.data_free_bytes)));
  // 温度：常看的几项，其余可展开
  const t=document.createElement('div');t.className='m';const th=document.createElement('h3');th.textContent='温度';const row=document.createElement('div');row.className='temps';
  for(const k of s.temps_key||[]){const sp=document.createElement('span');sp.textContent=k.label+' '+k.c.toFixed(1)+'℃';sp.title=k.zone;const c=tcolor(k.c);if(c)sp.style.borderColor=c;row.append(sp)}
  t.append(th,row);const all=Object.entries(s.temps_c||{}).sort();
  if(all.length>(s.temps_key||[]).length){const de=document.createElement('details'),su=document.createElement('summary');su.textContent='全部温区（'+all.length+' 个）';const dv=document.createElement('div');
    dv.textContent=all.map(([k,v])=>k+' '+v.toFixed(1)+'℃').join('\n');dv.style.whiteSpace='pre-line';de.append(su,dv);t.append(de)}
  g.append(t);
  g.append(s.net_rx_bytes!=null?card('网络累计',gb(s.net_rx_bytes),'接收',null,'发送 '+gb(s.net_tx_bytes)):card('网络累计','—','',null,miss.net||'读不到'));
  const h=s.uptime_sec/3600;g.append(card('开机时长',h>=24?(h/24).toFixed(1):h.toFixed(1),h>=24?'天':'小时',null,'含睡眠时间'));
  g.querySelectorAll('.sub').forEach(e=>e.style.whiteSpace='pre-line');
}
load(); setInterval(load,5000);
</script>`
