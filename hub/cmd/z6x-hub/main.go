// z6x-hub：运行在极米 Z6X Pro 上的常驻服务。一个进程、一个配置文件，按配置开关各功能模块。
// 用法：z6x-hub -c hub.yaml          启动
//
//	z6x-hub -c hub.yaml -check   只校验配置，不启动
package main

import (
	"context"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"
	_ "time/tzdata" // 安卓没有 /etc/localtime，内嵌时区数据以便按本地时间记录日志

	"z6x/hub/internal/core"
	"z6x/hub/internal/modules/control"
	"z6x/hub/internal/modules/files"
	"z6x/hub/internal/modules/lanscan"
	"z6x/hub/internal/modules/metrics"
	"z6x/hub/internal/modules/notify"
	"z6x/hub/internal/modules/paste"
	"z6x/hub/internal/modules/speed"
	"z6x/hub/internal/modules/webshell"
	"z6x/hub/internal/modules/wol"
)

func main() {
	cfgPath := flag.String("c", "hub.yaml", "配置文件路径")
	checkOnly := flag.Bool("check", false, "只校验配置，不启动")
	version := flag.Bool("version", false, "输出版本号")
	flag.Parse()

	setLocalTimezone()
	if *version {
		fmt.Println(core.Version)
		return
	}
	cfg, err := core.LoadConfig(*cfgPath)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(2)
	}
	if *checkOnly {
		fmt.Println("配置校验通过")
		return
	}

	// 相对路径都以配置文件所在目录为基准，这样无论从哪里启动，日志和数据都在同一处。
	base := filepath.Dir(*cfgPath)
	abs := func(p string) string {
		if filepath.IsAbs(p) {
			return p
		}
		return filepath.Join(base, p)
	}
	if err := os.MkdirAll(abs(cfg.DataDir), 0o755); err != nil {
		fmt.Fprintln(os.Stderr, "无法创建数据目录：", err)
		os.Exit(1)
	}
	logFile, err := core.OpenRotating(abs(cfg.LogFile), 5<<20, 3)
	if err != nil {
		fmt.Fprintln(os.Stderr, "无法打开日志文件：", err)
		os.Exit(1)
	}
	defer logFile.Close()
	log := slog.New(slog.NewTextHandler(io.MultiWriter(logFile, os.Stderr), nil))

	h := core.New(cfg, log)
	h.SetConfigPath(abs(filepath.Base(*cfgPath)))
	h.Add(files.New())
	h.Add(paste.New(nil))
	h.Add(control.New(nil))
	h.Add(wol.New())
	h.Add(metrics.New())
	h.Add(speed.New())
	h.Add(lanscan.New())
	h.Add(notify.New(nil))
	h.Add(webshell.New())

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	if err := h.Run(ctx); err != nil {
		log.Error("hub 异常退出", "err", err)
		os.Exit(1)
	}
}

// setLocalTimezone 读取安卓的时区设置（persist.sys.timezone，如 Asia/Shanghai）作为本地时区。
// 安卓上没有 /etc/localtime，Go 默认使用 UTC，日志时间会与电视显示的时间相差 8 小时。
func setLocalTimezone() {
	if os.Getenv("TZ") != "" {
		return
	}
	out, err := exec.Command("/system/bin/getprop", "persist.sys.timezone").Output()
	if err != nil {
		return
	}
	if loc, err := time.LoadLocation(strings.TrimSpace(string(out))); err == nil {
		time.Local = loc
	}
}
