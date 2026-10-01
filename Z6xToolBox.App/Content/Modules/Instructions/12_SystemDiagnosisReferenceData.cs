using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class SystemDiagnosisReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "system-diagnosis-reference",
        Title = "12. 常用诊断与网络指令（dumpsys / wm / df / ip / ssh）",
        Group = "设备接入",
        Summary = "解析 Android 底层系统诊断、窗口显示、存储分区、网络排查及进程监控指令，详细标注 SSH 与 ADB 的权限可用性。",
        Sections =
        [
            new ContentSection
            {
                Heading = "系统转储 dumpsys（需 ADB 权限）",
                Text = "dumpsys 用于向 Android 系统各个核心 Binder 服务请求内部状态诊断信息。\n\n" +
                       "注意：在普通 SSH（UID 10068）下执行大多数 dumpsys 服务会报 Permission Denial，获取完整信息必须在 ADB（uid=2000）下运行。",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前处于前台焦点的窗口与 Activity（定位未知应用真实包名）",
                        Code = "dumpsys window | grep -E \"mCurrentFocus|mFocusedApp\"",
                        ExpectedOutput =
                            "mCurrentFocus=Window{... u0 com.xgimi.minitvfactory/com.xgimi.minitvfactory.MainActivity}   # 当前获得焦点的窗口组件\n" +
                            "mFocusedApp=AppWindowToken{... token=... com.xgimi.minitvfactory/...}                     # 当前前台运行的应用包名"
                    },
                    new CodeBlock
                    {
                        Label = "列出系统正在运行的所有底层 Binder 系统服务",
                        Code = "service list",
                        ExpectedOutput =
                            "0   activity: [android.app.IActivityManager]      # 核心 Activity 调度管理服务\n" +
                            "1   package: [android.content.pm.IPackageManager] # 核心包管理安装服务\n" +
                            "2   window: [android.view.IWindowManager]         # 窗口管理器服务",
                        Note = "可以查看当前系统有哪些核心 Binder 服务正在常驻运行。"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统应用内存占用排名（meminfo）",
                        Code = "dumpsys meminfo --oom",
                        ExpectedOutput =
                            "Total PSS by OOM adjustment:\n" +
                            "  78,920K: Native\n" +
                            "  65,400K: com.xgimi.doubtservice (pid 1024)   # 极米后台常驻服务占用约 65MB RAM\n" +
                            "  52,100K: com.xgimi.minitvfactory (pid 1130)  # 工厂应用占用约 52MB RAM"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "wm（Window Manager 窗口与显示控制）",
                Text = "wm 用于检测和覆盖系统物理分辨率与显示密度。查询在 SSH 与 ADB 下均可执行，但修改密度需 ADB 权限：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询物理分辨率与屏幕缩放密度",
                        Code = "wm size && wm density",
                        ExpectedOutput =
                            "Physical size: 1920x1080   # 投影仪物理点对点原生分辨率\n" +
                            "Physical density: 240      # 系统显示缩放密度（240 DPI，hdpi 级别）"
                    },
                    new CodeBlock
                    {
                        Label = "动态修改分辨率或密度测试布局适配",
                        Code = "wm density 320     # 将 DPI 调整为 320（放大界面元素）\n" +
                               "wm density reset   # 重置为原厂默认 240 DPI\n" +
                               "wm size reset      # 重置物理分辨率"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "settings（全局、系统与安全配置读写）",
                Text = "查看与修改 Android SettingsProvider 数据库中的系统级开关配置（修改需 ADB 权限）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看与开启系统级全局 ADB 开关",
                        Code = "settings get global adb_enabled\n" +
                               "settings put global adb_enabled 1",
                        ExpectedOutput = "1   # 1 表示系统底层 ADB 开关处于开启状态"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统锁屏与待机超时时间",
                        Code = "settings get system screen_off_timeout",
                        ExpectedOutput = "600000   # 毫秒单位（600000 即 10 分钟）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "进程与 CPU 监控（top / ps）",
                Text = "在 SSH（普通 UID）与 ADB 下均可直接执行的标准 Linux 进程监控：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "一次性抓取当前 CPU 与内存占用最高的 10 个进程",
                        Code = "top -b -n 1 -m 10 -s cpu",
                        ExpectedOutput =
                            "PID USER         PR  NI VIRT  RES  SHR S[%CPU] %MEM     TIME+ ARGS\n" +
                            "1024 system      20   0 1.2G  65M  32M S  4.2   1.7   0:15.22 com.xgimi.doubtservice\n" +
                            "1130 u0_a68      20   0 980M  52M  28M S  1.1   1.4   0:08.10 com.xgimi.minitvfactory",
                        Note = "快速定位电视后台发热、卡顿是由哪些应用引发的。"
                    },
                    new CodeBlock
                    {
                        Label = "根据关键字搜索正在运行的进程 PID",
                        Code = "ps -A | grep -iE \"xgimi|clash|ssh\"",
                        ExpectedOutput = "u0_a68    1234  ... com.xgimi.minitvfactory   # u0_a68 代表普通 UID 10068"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "df 与 du（存储分区与文件占用分析）",
                Text = "存储空间与分区挂载排查。此两项命令基于 Linux 标准 VFS，在 SSH（普通 UID）与 ADB 下均可直接执行：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看所有挂载分区的容量、已用与余量",
                        Code = "df -h",
                        ExpectedOutput =
                            "Filesystem      Size  Used Avail Use% Mounted on\n" +
                            "/dev/...         50G  1.8G   48G   4% /data/user/0   # 数据分区总容量 50G，已用 1.8G，剩余 48G 可用空间"
                    },
                    new CodeBlock
                    {
                        Label = "分析 /data 目录下各文件夹体积占用",
                        Code = "du -sh /data/* 2>/dev/null | sort -hr | head -n 5",
                        ExpectedOutput =
                            "1.2G  /data/app           # 用户自行安装的所有第三方 APK 存放目录\n" +
                            "380M  /data/data          # 各应用运行时私有数据与缓存\n" +
                            "120M  /data/system        # 系统运行时状态与包管理注册配置\n" +
                            "50M   /data/misc          # Wi-Fi 凭据与底层杂项配置",
                        Note = "快速定位系统缓存、极米日志或大文件存放目录。"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "ip、netstat 与 ssh（网络接口、端口与远程通道）",
                Text = "网络连通性、本地监听端口与 SSH 连接命令。查看 IP 与端口在 SSH 与 ADB 下均可直接执行：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看无线网卡 IP 与子网掩码",
                        Code = "ip -4 addr show wlan0",
                        ExpectedOutput = "inet 192.168.0.109/24 brd 192.168.0.255 scope global wlan0   # 电视局域网 IP 与 /24 掩码"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统当前正在监听的 TCP 端口（排查 5555 与 2222 是否开启）",
                        Code = "netstat -tlpn 2>/dev/null || ss -tlpn",
                        ExpectedOutput =
                            "Proto Recv-Q Send-Q Local Address    Foreign Address  State       PID/Program name\n" +
                            "tcp        0      0 0.0.0.0:2222     0.0.0.0:*        LISTEN      1234/dropbear        # SimpleSSHD 监听中\n" +
                            "tcp        0      0 0.0.0.0:5555     0.0.0.0:*        LISTEN      1560/adbd            # 网络 ADB 监听中"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统默认网关与路由表",
                        Code = "ip route show",
                        ExpectedOutput = "default via 192.168.0.1 dev wlan0 proto dhcp   # 系统默认路由网关为 192.168.0.1"
                    },
                    new CodeBlock
                    {
                        Label = "Steam Deck 登录 SimpleSSHD 远程终端",
                        Code = "ssh -p 2222 deck@192.168.0.109",
                        Note = "初次连接输入电视屏幕显示的 6 位动态密码；若配置了 ~/.ssh/authorized_keys 可免密直接登录。"
                    }
                ]
            }
        ]
    };
}
