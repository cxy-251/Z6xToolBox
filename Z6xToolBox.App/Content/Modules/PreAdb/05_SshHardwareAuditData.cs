using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class SshHardwareAuditData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "hardware-vitals",
        Title = "5. SSH 终端建立与全机参数探测",
        Group = "设备接入",
        Summary = "极米 Z6X Pro 实机参数全览，包含 CPU 架构、系统版本、内存存储与各项 SSH 探测指令回显。",
        Sections =
        [
            new ContentSection
            {
                Heading = "核心硬件与系统概览",
                Text = "通过网络 SSH（SimpleSSHD）连入电视底层 Shell，实测获取到 Z6X Pro 的各项底层参数如下：",
                BulletPoints =
                [
                    "芯片平台：联发科 MT9669（开发代号 huanglong），4 核 Cortex-A73 处理器",
                    "架构分离：Linux 5.4.180 内核为 64 位（armv8l），用户空间系统库裁剪为 32 位（armeabi-v7a）",
                    "系统版本：Android 12，API 级别 31",
                    "物理内存：总内存 3.5 GB（3630528 kB），空闲可用约 1.8 GB",
                    "存储空间：/data 分区总容量 50 GB，系统预装后剩余 48 GB",
                    "光机分辨率：0.33 英寸 DMD 芯片，物理点对点 1920x1080@60Hz，屏幕密度 240 DPI",
                    "调试接口：网络 ADB 端口 5555（默认开放且无需授权指纹，uid=2000）"
                ]
            },
            new ContentSection
            {
                Heading = "CPU 架构与内核位宽查询",
                Text = "确认系统是 64 位内核还是 32 位环境。返回结果证实用户空间只能运行 32 位（armeabi-v7a）动态库：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 CPU ABI 与系统内核架构",
                        Code = "getprop ro.product.cpu.abi && uname -m",
                        ExpectedOutput =
                            "armeabi-v7a   # 用户空间 ABI：32 位 ARM 环境，所有 APK 原生 SO 库必须包含 32 位\n" +
                            "armv8l        # 内核架构：64 位 ARMv8 内核，运行在 32 位兼容模式",
                        Note = "若安装仅支持 arm64-v8a 的纯 64 位 APK，安装器会直接报错 INSTALL_FAILED_NO_MATCHING_ABIS。"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "主板芯片平台与 Android 系统版本",
                Text = "查询主板芯片代号与系统版本：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看芯片代号与系统 SDK 级别",
                        Code = "getprop ro.board.platform && getprop ro.build.version.release && getprop ro.build.version.sdk",
                        ExpectedOutput =
                            "huanglong     # 芯片代号：联发科 MT9669 平台内部代号\n" +
                            "12            # 系统版本：Android 12\n" +
                            "31            # SDK 版本：Android 12 对应的 API Level 31"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "内存与存储空间实测",
                Text = "检查实际可用的运行内存以及数据分区剩余容量：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询物理总内存与当前可用内存",
                        Code = "cat /proc/meminfo | grep -E \"MemTotal|MemAvailable\"",
                        ExpectedOutput =
                            "MemTotal:        3630528 kB   # 实际可用物理总内存（硬件开销与显存预留后约 3.5GB，标称 4GB）\n" +
                            "MemAvailable:    1842100 kB   # 当前系统剩余可用内存（约 1.8GB）"
                    },
                    new CodeBlock
                    {
                        Label = "查询内部存储 /data 分区使用情况",
                        Code = "df -h /data",
                        ExpectedOutput =
                            "Filesystem      Size  Used Avail Use% Mounted on\n" +
                            "/dev/...         50G  1.8G   48G   4% /data/user/0   # 数据分区总容量 50G，已用仅 1.8G，剩余 48G 可自由支配"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "显示分辨率与系统包数量统计",
                Text = "查询光机输出分辨率、屏幕密度以及系统预装应用数量：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询物理分辨率与 DPI 密度",
                        Code = "wm size && wm density",
                        ExpectedOutput =
                            "Physical size: 1920x1080   # DMD 光机物理点对点原生分辨率\n" +
                            "Physical density: 240      # 系统显示缩放密度（hdpi，240 DPI）"
                    },
                    new CodeBlock
                    {
                        Label = "统计系统总包数与极米私有预装应用数",
                        Code = "echo -n \"总包数: \" && pm list packages | wc -l && echo -n \"极米内置包数: \" && pm list packages -s | grep xgimi | wc -l",
                        ExpectedOutput =
                            "总包数: 183        # 系统当前安装的所有软件包数量\n" +
                            "极米内置包数: 57   # 极米官方内置私有定制包数量"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "常用系统属性（getprop）快捷排查命令集",
                Text = "以下命令用于快速排查设备属性与状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "批量查询品牌、设备型号、序列号与系统版本",
                        Code = "getprop ro.product.brand && getprop ro.product.model && getprop ro.serialno && getprop ro.build.display.id",
                        ExpectedOutput =
                            "XGIMI         # 设备品牌\n" +
                            "Z6X Pro       # 设备型号\n" +
                            "0123456789    # 设备出厂 SN 序列号\n" +
                            "GMUI_...      # 极米定制系统固件版本号"
                    },
                    new CodeBlock
                    {
                        Label = "排查当前网络与 IP 地址",
                        Code = "ip -4 addr show wlan0 | grep inet",
                        ExpectedOutput = "inet 192.168.0.109/24 brd 192.168.0.255 scope global wlan0   # 电视局域网 IP 地址"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "当前 SSH 阶段的权限边界说明",
                Text = "此时尚未连上 ADB，仅是通过普通应用 SimpleSSHD（UID 10068）建立的 Shell。\n\n" +
                       "• 为什么查询命令能执行：Linux 内核状态（/proc/meminfo）、只读属性（getprop）、磁盘用量（df）对所有普通应用开放只读权限，因此能正常查出硬件参数。\n" +
                       "• 为什么修改与删除执行不了：一旦调用涉及系统特权的服务（如卸载自带应用、跨应用启动受限组件），Android 12 内核会强制校验 UID 是否为 0(root) 或 2000(shell)，普通 UID 10068 会被立即拦截并抛错中断。"
            }
        ]
    };
}
