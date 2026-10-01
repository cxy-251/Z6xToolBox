package z6x.content.manual

import z6x.framework.Host
import z6x.framework.module

val DumpsysSettings = module("dumpsys-settings", "系统服务与设置：dumpsys、settings、wm、cmd") {
    keywords = "dumpsys · service list · settings · content · wm · cmd overlay · svc · battery"
    overview = """
        安卓的大部分功能由「系统服务」提供（窗口、包管理、电源、网络……）。`dumpsys 服务名` 打印某个服务的全部状态，`settings` 读写系统设置，`wm` 管显示，`cmd 服务名` 给服务下命令。
    """
    verified("2026-10-01")

    steps("系统服务") {
        read("一共有多少系统服务", "service list | head -3", Host.Adb) {
            captured("2026-10-01", """
                Found 203 services:
                0	DockObserver: []
                1	InputTransferService: [com.huanglong.input.IInputTransferService]
            """)
            note = "每个都能 `dumpsys 名字`。`com.huanglong.*` 是海思平台自己的服务。"
        }
        read("当前焦点窗口", "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", Host.Adb) {
            varies = true
            note = "详见「屏幕上现在是谁」。"
        }
        read("按 oom 分组的内存排行", "dumpsys meminfo --oom | head -12", Host.Adb) {
            varies = true
            note = "详见「进程、内存与信号」。"
        }
    }

    steps("settings：系统设置") {
        read("读设置", "settings get global adb_enabled; settings get system screen_off_timeout", Host.Adb) {
            captured("2026-10-01", """
                0
                2147483647
            """)
            note = """
                设置分三张表：`global`（全局）、`system`（系统偏好）、`secure`（安全相关）。
                adb_enabled 是 0（ADB 是绕过设置开的，见「ADB 基础」）；screen_off_timeout 2147483647 毫秒约等于永不熄屏。
            """
        }
        change("写设置", "settings put global adb_enabled 1", Host.Adb) {
            note = "旧版用它「打开 ADB」。这台机器的 ADB 不靠它，改了也没区别。"
        }
        read("列出整张表", "settings list global | head", Host.Adb) { varies = true }
        read("用 content 查同一张表", "content query --uri content://settings/global --projection name:value | head -3", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                Row: 0 name=adb_wifi_enabled, value=0
                Row: 1 name=low_battery_sound_timeout, value=0
                Row: 2 name=car_undock_sound, value=/product/media/audio/ui/Undock.ogg
            """)
            note = "设置其实存在一个 ContentProvider（数据提供者）里，`content` 命令可以直接查任意 Provider，比如 `content://settings/secure`。"
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
            note = "数字越小，界面元素越小、能显示的内容越多。恢复：`wm density reset`（回到 240）。分辨率同理：`wm size 1280x720` / `wm size reset`。"
        }
        read("Android 12 没有 overscan 了", "wm help | grep -iE 'overscan|density|size'", Host.Adb) {
            captured("2026-10-01", """
                  size [reset|WxH|WdpxHdp] [-d DISPLAY_ID]
                    Return or override display size.
                  density [reset|DENSITY] [-d DISPLAY_ID]
                    Return or override display density.
            """)
            note = "老电视调画面边缘用的 `wm overscan`，在这台 Android 12 的 wm 里已经没有了（帮助里找不到）。投影画面大小用投影仪自带的梯形校正 / 缩放调。"
        }
    }

    steps("cmd 与 svc：给服务下命令") {
        read("运行时资源覆盖层（RRO）", "cmd overlay list | head -5", Host.Adb) {
            captured("2026-10-01", """
                android
                [ ] com.android.internal.display.cutout.emulation.corner
                [ ] com.android.internal.display.cutout.emulation.double
                [ ] com.android.internal.systemui.navbar.gestural_wide_back
                [ ] com.android.internal.systemui.onehanded.gestural
            """)
            note = "Overlay 是只含资源的包，用来替换别的应用的样式。`[ ]` 未启用，`[x]` 启用。见「系统里还剩什么」的无界面组件。"
        }
        change("展开 / 收起通知栏", "cmd statusbar expand-notifications\ncmd statusbar collapse", Host.Adb) {
            note = "没有在这台电视上测试，电视的系统界面可能没有手机那样的通知栏。"
        }
        change("开关 Wi-Fi", "svc wifi disable\nsvc wifi enable", Host.Adb) {
            note = "**通过 Wi-Fi 连着 ADB 时别关 Wi-Fi**，否则连接立刻断开，只能去电视上手动打开。"
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
            note = "`present: false`：没有电池，level 42 是个占位值。连「交流供电」都显示 false，系统其实不关心这些。"
        }
        change("伪装电池状态 / 恢复", "cmd battery set ac 1\ncmd battery set level 100\ncmd battery reset", Host.Adb) {
            note = "测试 App 在不同电量下的行为时用。对投影仪没有实际意义。"
        }
    }

    related("adb-basics", "process-memory", "focus-window", "system-packages")
}

val ProcessMemory = module("process-memory", "进程、内存与信号") {
    keywords = "ps · top · meminfo · smaps_rollup · kill · oom · file-nr"
    overview = """
        看谁在占 CPU 和内存、某个进程用了多少、怎么结束进程。shell 身份能看所有进程，但只能管自己启动的进程。
    """
    verified("2026-10-01")

    steps("进程") {
        read("按关键词找进程", "ps -A | grep -iE 'clash|sshd|z6x'", Host.Adb) {
            varies = true
            note = "`-A` 列出所有进程。第 1 列用户、第 2 列 PID、第 5 列实际内存（KB）。"
        }
        read("CPU 占用排行（取一次）", "top -b -n 1 -m 10", Host.Adb) {
            varies = true
            note = """
                `-b` 非交互、`-n 1` 只刷新一次、`-m 10` 只显示前 10 个。默认按 CPU 排序。
                只要几列：`top -b -n 1 -m 5 -o PID,USER,%CPU,RES,ARGS`。
                顶部汇总里的 800%cpu 是因为有 8 个核心，每个核心算 100%。
            """
        }
        read("按内存排序", "ps -A -o PID,RSS,NAME -k -RSS | head -10", Host.Adb) {
            varies = true
            note = "`-k -RSS` 按 RSS（实际内存）从大到小。SmartTube 播放时约 300MB。"
        }
    }

    steps("内存") {
        read("按 oom 分组的内存", "dumpsys meminfo --oom | head -12", Host.Adb) {
            varies = true
            note = "按进程的重要程度分组：Native（底层服务）、Persistent（常驻）、Foreground（前台）、Cached（缓存，最先被杀）。分组依据就是 oom_score_adj，见「Go 服务」。"
        }
        read("某个进程的精确内存", "cat /proc/\$(pidof z6x_go_server)/smaps_rollup | head -4", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                00010000-7fe2cb9000 ---p 00000000 00:00 0                                [rollup]
                Rss:                3892 kB
                Pss:                3892 kB
                Pss_Anon:           1256 kB
            """)
            note = """
                **Rss** 实际占用；**Pss** 把和别的进程共享的内存按比例分摊后的值，多进程比较时更公平。
                只能看**自己的**进程；看 system_server 会 Permission denied。
            """
        }
        danger("清空系统缓存（需要 root）", "echo 3 > /proc/sys/vm/drop_caches", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "sh: can't create /proc/sys/vm/drop_caches: Permission denied")
            note = "shell 做不到。也没必要：缓存会在需要内存时自动释放。"
        }
    }

    steps("信号：结束和控制进程") {
        read("系统支持的信号", "kill -l | head -3", Host.Adb) {
            captured("2026-10-01", """
                 1    HUP Hangup                        33     33 Signal 33
                 2    INT Interrupt                     34     34 Signal 34
                 3   QUIT Quit                          35     35 Signal 35
            """)
            note = "常用：15 TERM（请求退出，默认）、9 KILL（强制结束）、3 QUIT（Java 进程收到后会输出一份线程堆栈转储，排查卡死用）。"
        }
        read("能不能给某个进程发信号（不真的发）", "kill -0 \$(pidof system_server) 2>&1; echo exit=\$?", Host.Adb) {
            expectsError = true
            captured("2026-10-01", """
                /system/bin/sh: kill: 3216: Operation not permitted
                exit=1
            """)
            note = "**信号 0 只检查权限，不做任何事**，很适合做安全的实验。shell 不能给系统和其他应用的进程发信号；给自己启动的 Go 服务发，exit=0。"
        }
        change("结束自己启动的进程", "kill \$(pidof z6x_go_server)", Host.Adb) {
            note = "其他应用的进程用 `am force-stop 包名` 结束。"
        }
    }

    steps("内核计数器") {
        read("文件句柄：已用 / 0 / 上限", "cat /proc/sys/fs/file-nr", Host.Adb) {
            varies = true
            captured("2026-10-01", "12237\t0\t345112")
            note = "整个系统打开了约 1.2 万个文件（含网络连接），上限 34 万，远没到。"
        }
        read("随机数熵池", "cat /proc/sys/kernel/random/entropy_avail", Host.Adb) {
            varies = true
            captured("2026-10-01", "3267")
            note = "加密要用的随机数来源。这个版本的内核里它只是参考值，不会再因为它低而卡住。"
        }
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版 `top -s cpu` 报错（`not integer: cpu`），toybox 的 top 默认就按 CPU 排序，不用加。
            • 旧版 `kill -3` 给应用进程触发转储，shell 没有这个权限（见上面 kill -0 的实验）。例子里的 `com.xgimi.dueros` 也不存在。
            • 旧版 `smaps_rollup` 以 system_server 为例，shell 读不了，改成自己的进程。
        """)
    }

    related("go-server", "proc-metrics", "dumpsys-settings")
}

val StoragePartitions = module("storage-partitions", "存储与分区") {
    keywords = "df · du · mount · /proc/partitions · sm · dm · 极米分区"
    overview = """
        这台投影仪的 eMMC 被分成 53 个分区：系统、vendor 只读且已满，数据分区 50G，还有三个极米私有分区存光机校准等出厂数据。
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
            note = "`/`（系统）和 `/vendor` 100% 是正常的：只读分区做成刚好装满。可用空间看 `/data`。"
        }
        read("目录占用排行", "du -sk /data/local/tmp/* | sort -rn | head", Host.Adb) {
            varies = true
            note = "`-s` 汇总、`-k` 以 KB 为单位（方便数字排序），`sort -rn` 从大到小。toybox 的 sort 不支持 `-h`，所以不用 `du -sh`。shell 看不了 /data 下的大部分目录。"
        }
    }

    steps("分区与挂载") {
        read("分区数量", "grep -c mmcblk0p /proc/partitions", Host.Adb) {
            captured("2026-10-01", "53")
        }
        read("系统分区怎么挂载的", "mount | grep -E ' / | /product | /vendor '", Host.Adb) {
            captured("2026-10-01", """
                /dev/block/dm-0 on / type ext4 (ro,seclabel,nodev,relatime,inode_readahead_blks=8)
                dev/block/by-name/product on /product type ext4 (ro,seclabel,relatime,inode_readahead_blks=8)
                /dev/block/dm-1 on /vendor type ext4 (ro,seclabel,relatime,inode_readahead_blks=8)
            """)
            note = """
                全是 `ro`（只读）。系统和 vendor 挂在 `dm-0`、`dm-1` 上，这是 device-mapper 虚拟出来的块设备（`/dev/block/mapper/` 下有 system、vendor）。
                安卓通常用它来做 **dm-verity**（校验分区没被篡改），但这台机器上没查到能确认 verity 状态的属性。
            """
        }
        danger("把系统分区改成可写（需要 root）", "mount -o remount,rw /vendor", Host.Adb) {
            note = "shell 做不到。`adb remount` 需要 adbd 以 root 运行（`ro.debuggable=1`），这台是 0，也不行。没有 root 就别想改系统文件。"
        }
        read("A/B 分区？", "getprop ro.boot.slot_suffix; which bootctl", Host.Adb) {
            expectsError = true
            note = "两个都没有输出：**不是 A/B 分区设备**，升级不是「装到另一个槽位再切换」那种方式。"
        }
    }

    steps("外接存储") {
        read("磁盘和卷", "sm list-disks; sm list-volumes all", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                private mounted null
                emulated;0 mounted null
            """)
            note = "插 U 盘时会多出一个 disk 和 `public:x,y` 卷。现在只有内部存储。"
        }
        change("安全移除 U 盘", "sm unmount public:8,1", Host.Adb) {
            note = "卷 ID 用上一条查。卸载后再拔，防止数据没写完。"
        }
    }

    steps("极米私有分区") {
        read("挂载点", "mount | grep -i xgimi", Host.Adb) {
            captured("2026-10-01", """
                /dev/block/mmcblk0p50 on /mnt/vendor/xgimiconfig type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)
                /dev/block/mmcblk0p51 on /mnt/vendor/xgimidatabase type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)
                /dev/block/mmcblk0p52 on /mnt/vendor/xgimisps type ext4 (rw,seclabel,nosuid,nodev,noatime,data=ordered)
            """)
            note = "三个可写分区，但属于 root，shell 只能看不能改。"
        }
        read("xgimiconfig 里有什么", "ls /mnt/vendor/xgimiconfig", Host.Adb) {
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
            note = "按机型编号分的目录，**G0073 就是这台**（见「案例：查出真实型号和芯片」）。同一个固件支持好几个机型。"
        }
        read("xgimidatabase 里有什么", "ls /mnt/vendor/xgimidatabase", Host.Adb) {
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
            note = "`trapezoidcorrect_points_offset.ini` 从名字看是梯形校正的点位偏移，`pantilt` 是云台（俯仰）相关。**只看不改**：改坏了画面校正可能出问题。"
        }
        read("系统 PATH 里也有它", "echo \$PATH", Host.Adb) {
            captured("2026-10-01", "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/mnt/vendor/xgimidatabase/xbin")
            note = "最后一项是 `/mnt/vendor/xgimidatabase/xbin`，但这个目录**实际不存在**，大概是预留的。"
        }
    }

    steps("升级与 Recovery") {
        read("升级日志", "ls /cache/recovery/", Host.Adb) {
            expectsError = true
            captured("2026-10-01", "ls: /cache/recovery/: Permission denied")
            note = "shell 看不了。系统里也没有 `update_engine_client`（A/B 升级用的工具）。"
        }
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版有两篇讲 A/B 槽位、`bootctl`、`update_engine_client`，这台机器**都没有**，不是 A/B 设备。
            • 旧版 `du -sh … | sort -hr` 在 toybox 下报错，改成 `du -sk | sort -rn`。
            • 极米私有分区、PATH 里的 xgimidatabase 旧版说对了；补充了 xbin 目录其实不存在。
        """)
    }

    related("native-exec", "ssh-probe", "find-real-model")
}
