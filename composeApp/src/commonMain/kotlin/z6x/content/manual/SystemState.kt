package z6x.content.manual

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val DumpsysSettings = module("dumpsys-settings", "系统服务与设置：dumpsys、settings、wm、cmd") {
    keywords = "dumpsys · service list · settings · content · wm · cmd overlay · svc · battery"
    overview = """
        安卓的大部分功能由系统服务提供（窗口、包管理、电源、网络等）。`dumpsys 服务名` 输出某个服务的全部状态，`settings` 读写系统设置，`wm` 管理显示，`cmd 服务名` 向服务发送命令。
    """
    verified("2026-10-01")

    steps("系统服务") {
        read("系统服务总数", "service list | head -3", Host.Adb) {
            captured("2026-10-01", """
                Found 203 services:
                0	DockObserver: []
                1	InputTransferService: [com.huanglong.input.IInputTransferService]
            """)
            note = "每个服务都可以用 `dumpsys 名称` 查询。`com.huanglong.*` 是海思平台自有的服务。"
        }
        read("当前焦点窗口", "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", Host.Adb) {
            varies = true
            note = "详见「查看当前焦点窗口」。"
        }
        read("按 oom 分组的内存排行", "dumpsys meminfo --oom | head -12", Host.Adb) {
            varies = true
            note = "详见「进程、内存与信号」。"
        }
    }

    steps("settings：系统设置") {
        read("读取设置", "settings get global adb_enabled; settings get system screen_off_timeout", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                0
                2147483647
            """)
            note = """
                设置分为三张表：`global`（全局）、`system`（系统偏好）、`secure`（安全相关）。
                adb_enabled 重启前为 0，重启后变为 1（见「ADB 基础」）；screen_off_timeout 为 2147483647 毫秒，约等于永不熄屏。
            """
        }
        change("写入设置", "settings put global adb_enabled 1", Host.Adb) {
            note = "旧版用它「打开 ADB」。本机的 ADB 不依赖该设置，修改后没有区别。"
        }
        read("列出整张表", "settings list global | head", Host.Adb) { varies = true }
        read("用 content 查询同一张表", "content query --uri content://settings/global --projection name:value | head -3", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Row: 0 name=adb_wifi_enabled, value=0
                Row: 1 name=low_battery_sound_timeout, value=0
                Row: 2 name=car_undock_sound, value=/product/media/audio/ui/Undock.ogg
            """)
            note = "设置实际存储在一个 ContentProvider（数据提供者）中，`content` 命令可以直接查询任意 Provider，例如 `content://settings/secure`。"
        }
    }

    steps("wm：分辨率与缩放") {
        read("分辨率和密度", "wm size; wm density", Host.Adb) {
            captured("2026-10-01", """
                Physical size: 1920x1080
                Physical density: 240
            """)
        }
        change("调整密度（界面整体放大或缩小）", "wm density 210", Host.Adb) {
            note = "数值越小，界面元素越小，可显示的内容越多。恢复：`wm density reset`（回到 240）。分辨率同理：`wm size 1280x720` / `wm size reset`。"
        }
        read("Android 12 已移除 overscan", "wm help | grep -iE 'overscan|density|size'", Host.Adb) {
            captured("2026-10-01", """
                  size [reset|WxH|WdpxHdp] [-d DISPLAY_ID]
                    Return or override display size.
                  density [reset|DENSITY] [-d DISPLAY_ID]
                    Return or override display density.
            """)
            note = "旧电视用于调整画面边缘的 `wm overscan`，在本机 Android 12 的 wm 中已不存在（帮助中找不到）。投影画面大小应使用投影仪自带的梯形校正或缩放功能调整。"
        }
    }

    steps("cmd 与 svc：向服务发送命令") {
        read("运行时资源覆盖层（RRO）", "cmd overlay list | head -5", Host.Adb) {
            captured("2026-10-01", """
                android
                [ ] com.android.internal.display.cutout.emulation.corner
                [ ] com.android.internal.display.cutout.emulation.double
                [ ] com.android.internal.systemui.navbar.gestural_wide_back
                [ ] com.android.internal.systemui.onehanded.gestural
            """)
            note = "Overlay 是只包含资源的包，用于替换其他应用的样式。`[ ]` 表示未启用，`[x]` 表示已启用。另见「精简后的系统组件与入口」中的无界面组件。"
        }
        change("展开 / 收起通知栏", "cmd statusbar expand-notifications\ncmd statusbar collapse", Host.Adb) {
            note = "未在本机测试，电视的系统界面可能没有手机那样的通知栏。"
        }
        change("开关 Wi-Fi", "svc wifi disable\nsvc wifi enable", Host.Adb) {
            note = "**通过 Wi-Fi 连接 ADB 时不要关闭 Wi-Fi**，否则连接立即断开，只能在电视上手动重新打开。"
        }
        danger("关机 / 重启", "svc power shutdown\nsvc power reboot", Host.Adb)
    }

    steps("电池服务（投影仪没有电池）") {
        read("电池状态", "dumpsys battery | head -12", Host.Adb) {
            captured("2026-10-01", """
                Current Battery Service state:
                  AC powered: false
                  USB powered: false
                  Wireless powered: false
                  Max charging current: 0
                  Max charging voltage: 0
                  Charge counter: 0
                  status: 1
                  health: 1
                  present: false
                  level: 42
                  scale: 100
            """)
            note = "`present: false` 表示没有电池，level 42 只是占位值。连「交流供电」也显示 false，说明系统并不使用这些数据。"
        }
        change("模拟电池状态 / 恢复", "cmd battery set ac 1\ncmd battery set level 100\ncmd battery reset", Host.Adb) {
            note = "用于测试应用在不同电量下的行为，对投影仪没有实际意义。"
        }
    }

    audit {
        change("旧版：wm overscan 报错验证", "wm overscan 0,0,0,0", Host.Adb) {
            verdict = Verdict.Confirmed
            note = "旧记录称 Android 12 已废弃 overscan。实测 `wm help` 中确实没有该子命令，因此未实际执行。"
        }
        change("旧版：查看与开启系统级全局 ADB 开关", "settings get global adb_enabled\nsettings put global adb_enabled 1", Host.Adb) {
            verdict = Verdict.Unverified
            note = "重启前读数为 0，但 ADB 仍然可用（因为是绕过设置开启的）；重启后系统自行变为 1。手动写入的影响未测试。"
        }
        claim("wm density reset：重置为原厂默认 240 DPI。", Verdict.Confirmed, "当前密度 240。")
    }

    related("adb-basics", "process-memory", "focus-window", "system-packages")
}

val ProcessMemory = module("process-memory", "进程、内存与信号") {
    keywords = "ps · top · meminfo · smaps_rollup · kill · oom · file-nr"
    overview = """
        查看 CPU 和内存的占用情况、单个进程的资源用量，以及如何结束进程。shell 身份能看到所有进程，但只能直接控制自己启动的进程。
    """
    verified("2026-10-01")

    steps("进程") {
        read("按关键词查找进程", "ps -A | grep -iE 'clash|sshd|z6x'", Host.Adb) {
            varies = true
            note = "`-A` 列出所有进程。第 1 列为用户，第 2 列为 PID，第 5 列为实际内存（KB）。"
        }
        read("CPU 占用排行（单次采样）", "top -b -n 1 -m 10", Host.Adb) {
            varies = true
            note = """
                `-b` 表示非交互模式，`-n 1` 只刷新一次，`-m 10` 只显示前 10 个，默认按 CPU 排序。
                只显示指定列：`top -b -n 1 -m 5 -o PID,USER,%CPU,RES,ARGS`。
                顶部汇总中的 800%cpu 是因为有 8 个核心，每个核心计为 100%。
            """
        }
        read("按内存排序", "ps -A -o PID,RSS,NAME -k -RSS | head -10", Host.Adb) {
            varies = true
            note = "`-k -RSS` 表示按 RSS（实际内存）从大到小排序。SmartTube 播放时约占用 300MB。"
        }
    }

    steps("内存") {
        read("按 oom 分组的内存", "dumpsys meminfo --oom | head -12", Host.Adb) {
            varies = true
            note = "按进程的重要程度分组：Native（底层服务）、Persistent（常驻）、Foreground（前台）、Cached（缓存，最先被结束）。分组依据即 oom_score_adj，见「Go 服务：交叉编译、部署与常驻运行」。"
        }
        read("单个进程的精确内存", "cat /proc/\$(pidof z6x_go_server)/smaps_rollup | head -4", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                00010000-7fe2cb9000 ---p 00000000 00:00 0                                [rollup]
                Rss:                3892 kB
                Pss:                3892 kB
                Pss_Anon:           1256 kB
            """)
            note = """
                **Rss** 为实际占用；**Pss** 是将与其他进程共享的内存按比例分摊后的值，多个进程比较时更为公平。
                只能查看**自己启动的**进程；查看 system_server 会报 Permission denied，原因见「原理：SSH 与 ADB 的权限差异」。
            """
        }
        danger("清空系统缓存（需要 root）", "echo 3 > /proc/sys/vm/drop_caches", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "sh: can't create /proc/sys/vm/drop_caches: Permission denied")
            note = "shell 无法执行，也没有必要：缓存会在需要内存时自动释放。"
        }
    }

    steps("信号：结束与控制进程") {
        read("系统支持的信号", "kill -l | head -3", Host.Adb) {
            captured("2026-10-01", """
                 1    HUP Hangup                        33     33 Signal 33
                 2    INT Interrupt                     34     34 Signal 34
                 3   QUIT Quit                          35     35 Signal 35
            """)
            note = "常用信号：15 TERM（请求退出，默认值）、9 KILL（强制结束）、3 QUIT（Java 进程收到后会输出线程堆栈转储，用于排查卡死）。"
        }
        read("检查能否向某进程发送信号（不实际发送）", "kill -0 \$(pidof system_server) 2>&1; echo exit=\$?", Host.Adb) {
            varies = true
            expectsError = true
            captured("2026-10-01", """
                /system/bin/sh: kill: 3216: Operation not permitted
                exit=1
            """)
            note = "**信号 0 只检查权限，不执行任何操作**，适合用于安全的实验。shell 无法向系统和其他应用的进程发送信号；向自己启动的 Go 服务发送时，exit=0。"
        }
        change("结束自己启动的进程", "kill \$(pidof z6x_go_server)", Host.Adb) {
            note = "其他应用的进程应使用 `am force-stop 包名` 结束。"
        }
    }

    steps("内核计数器") {
        read("文件句柄：已用 / 0 / 上限", "cat /proc/sys/fs/file-nr", Host.Adb) {
            varies = true
            captured("2026-10-01", "12237\t0\t345112")
            note = "整个系统共打开约 1.2 万个文件（含网络连接），上限为 34 万，远未达到。"
        }
        read("随机数熵池", "cat /proc/sys/kernel/random/entropy_avail", Host.Adb) {
            varies = true
            captured("2026-10-01", "3267")
            note = "加密所需随机数的来源。在此版本内核中它仅作参考，不会再因数值偏低而导致阻塞。"
        }
    }

    audit {
        read("旧版：一次性抓取 CPU 占用最高的 10 个进程", "top -b -n 1 -m 10 -s cpu", Host.Adb) {
            verdict = Verdict.Disproved
            note = "实测报错 `top: not integer: cpu`：toybox 的 `-s` 需要填写列号。去掉 `-s cpu` 即可，默认已按 CPU 排序。"
        }
        read("旧版：获取 system_server 的物理内存消耗", "cat /proc/\$(pidof system_server)/smaps_rollup | head -n 8", Host.Adb) {
            verdict = Verdict.Disproved
            note = "实测报 Permission denied：shell 只能查看自己启动的进程的 smaps_rollup。"
        }
        change("旧版：向卡顿的目标进程发送 SIGQUIT", "kill -3 \$(pidof com.xgimi.dueros) 2>/dev/null", Host.Adb) {
            verdict = Verdict.Disproved
            note = "com.xgimi.dueros **固件中不存在**（相近的是 com.xgimi.duertts）；而且 shell 无权向其他应用的进程发送信号（见 kill -0 实验）。"
        }
        claim("清理 PageCache、dentries 和 inodes：echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || echo \"Permission denied (Requires Root)\"", Verdict.Confirmed,
            "旧记录本身已注明需要 root，实测确实报 Permission denied。")
    }

    related("go-server", "proc-metrics", "dumpsys-settings")
}

val StoragePartitions = module("storage-partitions", "存储与分区") {
    keywords = "df · du · mount · /proc/partitions · sm · dm · 极米分区"
    overview = """
        本机的 eMMC 分为 53 个分区：系统分区和 vendor 分区只读且已占满，数据分区为 50GB，另有三个极米私有分区，存放光机校准等出厂数据。
    """
    verified("2026-10-01")

    steps("容量") {
        read("各分区容量", "df -h | head -12", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Filesystem                Size Used Avail Use% Mounted on
                tmpfs                     1.7G 1.6M  1.7G   1% /dev
                tmpfs                     1.7G    0  1.7G   0% /mnt
                /dev/block/dm-0           703M 701M     0 100% /
                dev/block/by-name/odm      51M  20M   30M  41% /odm
                dev/block/by-name/product 375M  89M  278M  25% /product
                /dev/block/dm-1           1.3G 1.3G     0 100% /vendor
                tmpfs                     1.7G 8.0K  1.7G   1% /apex
                tmpfs                     1.7G 448K  1.7G   1% /linkerconfig
                /dev/block/mmcblk0p53      50G 3.5G   46G   8% /data
                /dev/block/mmcblk0p44     2.4G 320K  2.3G   1% /cache
                /dev/block/mmcblk0p45     2.9M  20K  2.8M   1% /sec_storage
            """)
            note = "`/`（系统）和 `/vendor` 显示 100% 属于正常现象：只读分区按内容大小制作，恰好占满。可用空间应看 `/data`。"
        }
        read("目录占用排行", "du -sk /data/local/tmp/* | sort -rn | head", Host.Adb) {
            varies = true
            note = "`-s` 表示汇总，`-k` 以 KB 为单位（便于数值排序），`sort -rn` 从大到小排序。toybox 的 sort 不支持 `-h`，因此不用 `du -sh`。shell 无法查看 /data 下的大部分目录。"
        }
    }

    steps("分区与挂载") {
        read("分区数量", "grep -c mmcblk0p /proc/partitions", Host.Adb) {
            captured("2026-10-01", "53")
        }
        read("系统分区的挂载方式", "mount | grep -E ' / | /product | /vendor '", Host.Adb) {
            captured("2026-10-01", """
                /dev/block/dm-0 on / type ext4 (ro,seclabel,nodev,relatime,inode_readahead_blks=8)
                dev/block/by-name/product on /product type ext4 (ro,seclabel,relatime,inode_readahead_blks=8)
                /dev/block/dm-1 on /vendor type ext4 (ro,seclabel,relatime,inode_readahead_blks=8)
            """)
            note = """
                均为 `ro`（只读）。系统和 vendor 挂载在 `dm-0`、`dm-1` 上，这是 device-mapper 虚拟出的块设备（`/dev/block/mapper/` 下有 system、vendor）。
                安卓通常借助它实现 **dm-verity**（校验分区是否被篡改），但本机上未找到能确认 verity 状态的属性。
            """
        }
        danger("把系统分区改成可写（需要 root）", "mount -o remount,rw /vendor", Host.Adb) {
            note = "shell 无法执行。`adb remount` 需要 adbd 以 root 运行（`ro.debuggable=1`），本机为 0，同样不可行。没有 root 无法修改系统文件。"
        }
        read("是否为 A/B 分区", "getprop ro.boot.slot_suffix; which bootctl", Host.Adb) {
            expectsError = true
            note = "两者均无输出：**不是 A/B 分区设备**，升级方式不是「安装到另一个槽位后再切换」。"
        }
    }

    steps("外接存储") {
        read("磁盘和卷", "sm list-disks; sm list-volumes all", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                private mounted null
                emulated;0 mounted null
            """)
            note = "插入 U 盘时会多出一个 disk 和 `public:x,y` 卷。目前只有内部存储。"
        }
        change("安全移除 U 盘", "sm unmount public:8,1", Host.Adb) {
            note = "卷 ID 用上一条命令查询。先卸载再拔出，防止数据未写完。"
        }
    }

    steps("极米私有分区") {
        read("挂载点", "mount | grep -i xgimi", Host.Adb) {
            captured("2026-10-01", """
                /dev/block/mmcblk0p50 on /mnt/vendor/xgimiconfig type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)
                /dev/block/mmcblk0p51 on /mnt/vendor/xgimidatabase type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)
                /dev/block/mmcblk0p52 on /mnt/vendor/xgimisps type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)
            """)
            note = "三个分区均可写，但属于 root，shell 只能查看，不能修改。"
        }
        read("xgimiconfig 的内容", "ls /mnt/vendor/xgimiconfig", Host.Adb) {
            captured("2026-10-01", """
                G0073
                G0074
                G0093
                G0094
                G0095
                G0107
                lost+found
                public
            """)
            note = "按机型编号划分的目录，**G0073 即本机**（见「核查：真实型号与芯片」）。同一固件支持多个机型。"
        }
        read("xgimidatabase 的内容", "ls /mnt/vendor/xgimidatabase", Host.Adb) {
            captured("2026-10-01", """
                G0073
                G0074
                G0093
                G0094
                G0095
                lost+found
                pantilt
                trapezoidcorrect_points_offset.ini
            """)
            note = "`trapezoidcorrect_points_offset.ini` 从名称看是梯形校正的点位偏移，`pantilt` 与云台（俯仰）相关。**只查看，不修改**：修改不当可能导致画面校正异常。"
        }
        read("系统 PATH 中也包含它", "echo \$PATH", Host.Adb) {
            captured("2026-10-01", "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/mnt/vendor/xgimidatabase/xbin")
            note = "最后一项为 `/mnt/vendor/xgimidatabase/xbin`，但该目录**实际不存在**，可能是预留路径。"
        }
    }

    steps("升级与 Recovery") {
        read("升级日志", "ls /cache/recovery/", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "ls: /cache/recovery/: Permission denied")
            note = "shell 无法查看。系统中也没有 `update_engine_client`（A/B 升级使用的工具）。"
        }
    }

    audit {
        read("旧版：分析 /data 目录下各文件夹体积占用", "du -sh /data/* 2>/dev/null | sort -hr | head -n 5", Host.Adb) {
            verdict = Verdict.Disproved
            note = "实测报 `sort: Unknown option 'hr'`：toybox 的 sort 不支持 -h。而且 shell 无法读取 /data 下的大部分目录。"
        }
        read("旧版：查看当前激活的系统插槽（Slot A 或 Slot B）", "bootctl get-current-slot 2>/dev/null || getprop ro.boot.slot_suffix", Host.Adb) {
            verdict = Verdict.Disproved
            note = "没有 bootctl，slot_suffix 为空：**不是 A/B 分区设备**。"
        }
        read("旧版：查询当前分区槽位及是否成功引导", "getprop ro.boot.slot_suffix && bootctl is-slot-marked-successful 0 2>/dev/null", Host.Adb) {
            verdict = Verdict.Disproved
        }
        read("旧版：查看 update_engine 守护进程的当前执行阶段", "update_engine_client --status", Host.Adb) {
            verdict = Verdict.Disproved
            note = "系统中没有该工具（它用于 A/B 升级）。"
        }
        read("旧版：读取最近一次刷机或升级安装日志末尾", "tail -n 15 /cache/recovery/last_log 2>/dev/null || cat /metadata/ota/last_status 2>/dev/null || echo 'No recovery log'", Host.Adb) {
            verdict = Verdict.Disproved
            note = "/cache/recovery 无读取权限，/metadata/ota 不存在。"
        }
        danger("旧版：尝试重新挂载只读分区为读写", "mount -o remount,rw /vendor 2>&1 || adb remount", Host.Adb) {
            verdict = Verdict.Unverified
            note = "旧记录的结论是会失败（dm-verity 保护且需要 root）。**未执行**：一旦成功会改动系统分区。从权限上看（shell 非 root、ro.debuggable=0）必然失败。"
        }
        claim("dm-verity 块设备映射：/system、/vendor 挂载在 dm-* 上，由 dm-verity 校验保护，adb remount 报错根因即在于此。", Verdict.Unverified,
            "挂载在 dm-0、dm-1 上**成立**；是否启用 verity，未找到可确认的属性。")
        claim("极米私有分区挂载在 /mnt/vendor/xgimiconfig（光机型号配置目录）、/mnt/vendor/xgimidatabase；系统 PATH 包含 /mnt/vendor/xgimidatabase/xbin。", Verdict.Confirmed,
            "均成立。补充：另有 xgimisps 分区，且 PATH 中的 xbin 目录实际不存在。")
    }

    related("native-exec", "ssh-probe", "find-real-model")
}
