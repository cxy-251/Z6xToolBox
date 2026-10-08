package metrics

import (
	"os"
	"path/filepath"
	"testing"
)

func TestFreqAndMissing(t *testing.T) {
	root := t.TempDir()
	// 4 个小核（最高 1800MHz）+ 2 个大核（最高 2400MHz）；没有 /proc/stat 与 /proc/net/dev（模拟手机上读不到）
	for i, f := range [][2]string{{"600000", "1800000"}, {"600000", "1800000"}, {"1200000", "1800000"}, {"1200000", "1800000"}, {"2400000", "2400000"}, {"1200000", "2400000"}} {
		d := filepath.Join(root, "sys/devices/system/cpu", "cpu"+string(rune('0'+i)), "cpufreq")
		os.MkdirAll(d, 0o755)
		os.WriteFile(filepath.Join(d, "scaling_cur_freq"), []byte(f[0]), 0o644)
		os.WriteFile(filepath.Join(d, "cpuinfo_max_freq"), []byte(f[1]), 0o644)
	}
	m := &Module{cfg: Config{Root: root, Iface: "wlan0", DataPath: root}}
	m.sample()
	s := m.Snapshot()
	if s.CPUPercent != nil || s.Missing["cpu_percent"] == "" {
		t.Errorf("读不到 /proc/stat 时 CPU 使用率应为 null 并说明原因：%v %v", s.CPUPercent, s.Missing)
	}
	if len(s.Clusters) != 2 || s.Clusters[0].Cores != "0-3" || s.Clusters[0].CurMHz != 900 || s.Clusters[1].Cores != "4-5" || s.Clusters[1].MaxMHz != 2400 {
		t.Errorf("大小核分组不正确：%+v", s.Clusters)
	}
	// (600*2+1200*2+2400+1200) / (1800*4+2400*2) = 7200/12000 = 60%
	if s.CPUFreqPct == nil || *s.CPUFreqPct != 60 {
		t.Errorf("频率负载应为 60%%：%v", s.CPUFreqPct)
	}
	if s.NetRxB != nil || s.Missing["net"] == "" {
		t.Errorf("读不到网络流量时应为 null 并说明原因")
	}
}
