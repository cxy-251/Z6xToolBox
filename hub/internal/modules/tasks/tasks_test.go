package tasks

import "testing"

func TestParse(t *testing.T) {
	rec := parseRecents(`  * Recent #0: Task{19dc528 #614 type=home I=com.spocky.projengmenu/.ui.home.MainActivity U=0 rootTaskId=1 visible=true}
    lastActiveTime=80162187 (inactive for 1798s)
  * Recent #1: Task{9c0c7ae #656 type=standard A=10070:com.cxinventor.file.explorer U=0 visible=false}
    userId=0 effectiveUid=u0a70 mCallingUid=u0a45
    affinity=10070:com.cxinventor.file.explorer
    lastActiveTime=80169668 (inactive for 1791s)`)
	if r := rec["com.spocky.projengmenu"]; !r.home || r.task != 614 {
		t.Fatalf("桌面任务解析错误：%+v", r)
	}
	if r := rec["com.cxinventor.file.explorer"]; r.home || r.task != 656 || r.idle != 1791 {
		t.Fatalf("普通任务解析错误：%+v", r)
	}
	mem := parsePS("USER PID RSS NAME\nu0_a69 5948 119432 com.github.metacubex.clash.meta\nu0_a69 6015 183184 com.github.metacubex.clash.meta:background\nshell 1 2 sh\n")
	if mem["com.github.metacubex.clash.meta"] != 119432+183184 || len(mem) != 1 {
		t.Fatalf("进程内存累计错误：%+v", mem)
	}
}
