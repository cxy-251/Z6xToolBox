// Package metrics 读取 /proc 和 /sys，提供 CPU、内存、温度、存储和网络流量。
// /api/metrics/ 返回 JSON，/metrics 返回 Prometheus 文本格式。
package metrics

import (
	"bufio"
	"context"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
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
	CPUPercent float64            `json:"cpu_percent"`
	MemTotalKB int64              `json:"mem_total_kb"`
	MemAvailKB int64              `json:"mem_available_kb"`
	TempsC     map[string]float64 `json:"temps_c"`
	DataFreeB  uint64             `json:"data_free_bytes"`
	DataTotalB uint64             `json:"data_total_bytes"`
	NetRxB     uint64             `json:"net_rx_bytes"`
	NetTxB     uint64             `json:"net_tx_bytes"`
	UptimeSec  float64            `json:"uptime_sec"`
	Load1      float64            `json:"load1"`
}

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
	s := Snapshot{Time: time.Now().Format(time.RFC3339), TempsC: map[string]float64{}}
	if cur, err := m.readCPU(); err == nil {
		s.CPUPercent = cpuPercent(m.prev, cur)
		m.prev = cur
	}
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
	s.NetRxB, s.NetTxB = readNetDev(m.path("/proc/net/dev"), m.cfg.Iface)
	if raw, err := os.ReadFile(m.path("/proc/uptime")); err == nil {
		fmt.Sscanf(string(raw), "%f", &s.UptimeSec)
	}
	if raw, err := os.ReadFile(m.path("/proc/loadavg")); err == nil {
		fmt.Sscanf(string(raw), "%f", &s.Load1)
	}
	m.mu.Lock()
	m.last = s
	m.mu.Unlock()
}

type cpuTimes struct{ idle, total uint64 }

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

func readNetDev(path, iface string) (rx, tx uint64) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
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
	g("z6x_cpu_percent", "CPU 使用率（百分比）", s.CPUPercent, "")
	g("z6x_mem_total_bytes", "内存总量", s.MemTotalKB*1024, "")
	g("z6x_mem_available_bytes", "可用内存", s.MemAvailKB*1024, "")
	g("z6x_data_free_bytes", "/data 剩余空间", s.DataFreeB, "")
	g("z6x_net_rx_bytes", "网卡累计接收字节", s.NetRxB, "")
	g("z6x_net_tx_bytes", "网卡累计发送字节", s.NetTxB, "")
	g("z6x_uptime_seconds", "开机以来的秒数（含睡眠）", s.UptimeSec, "")
	fmt.Fprintf(w, "# HELP z6x_temp_celsius 温度区温度\n# TYPE z6x_temp_celsius gauge\n")
	for k, v := range s.TempsC {
		fmt.Fprintf(w, "z6x_temp_celsius{zone=%q} %v\n", k, v)
	}
}

func (m *Module) page(w http.ResponseWriter, _ *http.Request) {
	core.Page(w, "系统状态", `<div class="card"><pre id="out">读取中…</pre></div>
<p><small>每 5 秒刷新。原始数据：<a href="/api/metrics/">/api/metrics/</a> · <a href="/metrics">Prometheus 格式</a></small></p>
<script>
const gb=b=>(b/1073741824).toFixed(1)+' GB';
async function load(){
  const s=await (await fetch('/api/metrics/')).json();
  const t=Object.entries(s.temps_c||{}).map(([k,v])=>k+' '+v.toFixed(1)+'℃').join('，');
  document.getElementById('out').textContent=
    'CPU 使用率　'+s.cpu_percent+'%\n'+
    '可用内存　　'+(s.mem_available_kb/1048576).toFixed(2)+' / '+(s.mem_total_kb/1048576).toFixed(2)+' GB\n'+
    '温度　　　　'+t+'\n'+
    '/data 剩余　'+gb(s.data_free_bytes)+' / '+gb(s.data_total_bytes)+'\n'+
    '网络累计　　接收 '+gb(s.net_rx_bytes)+'，发送 '+gb(s.net_tx_bytes)+'\n'+
    '运行时长　　'+(s.uptime_sec/3600).toFixed(1)+' 小时（含睡眠）';
}
load(); setInterval(load,5000);
</script>`)
}
