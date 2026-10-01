using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class StorageSecurityKernelReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "storage-security-kernel-reference",
        Title = "14. 存储、内核与安全策略（sm / getenforce / dmesg / toybox / screenrecord）",
        Group = "设备接入",
        Summary = "讲解外接存储卷管理 sm、SELinux 安全上下文 getenforce、内核环形日志 dmesg、内置工具集 toybox 及原生录屏 screenrecord。",
        Sections =
        [
            new ContentSection
            {
                Heading = "sm（Storage Manager 外接存储与卷管理器）",
                Text = "用于管理插入电视的 U 盘、移动硬盘与内部存储分区（需 ADB shell 权限）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看外接物理存储设备与分区卷列表",
                        Code = "sm list-disks && sm list-volumes all",
                        ExpectedOutput =
                            "disk:179,0                           # 内部 eMMC 存储芯片\n" +
                            "disk:8,0                             # 外接 USB 物理闪存盘（U 盘）\n" +
                            "public:8,1 mounted 1234-5678         # 外接 FAT32/exFAT 卷，UUID 为 1234-5678，状态为已挂载\n" +
                            "emulated:0 mounted null              # 内部共享存储分区（对应 /sdcard）"
                    },
                    new CodeBlock
                    {
                        Label = "安全卸载外接 U 盘卷（避免强拔导致数据丢失）",
                        Code = "sm unmount public:8,1",
                        ExpectedOutput = "（卸载成功，U 盘指示灯熄灭，可安全拔出）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SELinux 运行模式与安全标签（getenforce / ls -Z）",
                Text = "排查 Android 权限拦截与沙箱阻断的核心诊断工具：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询当前系统 SELinux 强制状态",
                        Code = "getenforce",
                        ExpectedOutput = "Enforcing   # 处于强制拦截模式（所有未在 sepolicy 规则放行的行为均被拒绝）",
                        Note = "若内核处于 Permissive 模式，则仅记录违规警告而不阻断。"
                    },
                    new CodeBlock
                    {
                        Label = "查看文件或进程的 SELinux 安全上下文标签",
                        Code = "ls -Z /data/local/tmp && ps -AZ | grep adbd",
                        ExpectedOutput =
                            "u:object_r:shell_data_file:s0 /data/local/tmp   # 属于 shell 数据类型，ADB 可自由读写\n" +
                            "u:r:adbd:s0                   1560 ... adbd    # adbd 进程处于专属安全域"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "dmesg（Linux 内核环形缓冲区日志）",
                Text = "查看 Linux 5.4 内核级别的驱动加载、HDMI 物理接入与 OOM 杀进程记录（在 SSH 与 ADB 下均可执行）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 HDMI 物理热插拔与 EDID 协商日志",
                        Code = "dmesg | grep -iE \"hdmi|edid|drm|hpd\" | tail -n 5",
                        ExpectedOutput =
                            "[  12.345678] mtk_hdmi: hdmi cable plug in                     # 物理 HDMI 线缆接入热插拔事件\n" +
                            "[  12.567890] drm: edid raw data read success                  # 成功读取显示端 EDID 握手数据\n" +
                            "[  12.789012] mtk_hdmi: set mode 1920x1080@60Hz success       # 内核成功建立 1080p@60Hz 输出时序"
                    },
                    new CodeBlock
                    {
                        Label = "排查系统是否发生过内存耗尽杀进程（Low Memory Killer / OOM）",
                        Code = "dmesg | grep -iE \"oom-killer|killed process\" | tail -n 5",
                        ExpectedOutput = "（若无输出表示运行内存充足，未触发系统强制杀进程）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "toybox、原生录屏与局域网 ARP 邻居表",
                Text = "终端内置工具链、屏幕捕获与网络定位：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看系统内置的完整 Linux 实用工具集合",
                        Code = "toybox",
                        ExpectedOutput =
                            "acpi base64 basename cat chgrp chmod chown chroot cp cpio cut date dd\n" +
                            "df dirname dmesg du echo egrep env fgrep find free grep gzip head id\n" +
                            "ifconfig kill ln ls lsmod md5sum mkdir mktemp mv netstat nohup od pidof\n" +
                            "ping ps pwd readlink realpath rm rmdir sed sleep sort split stat sync\n" +
                            "tail tar tee test time top touch tr traceroute true uname uniq wc which\n" +
                            "# 输出 Android 系统自带的所有 Linux 单一可执行体支持的全部命令"
                    },
                    new CodeBlock
                    {
                        Label = "录制电视屏幕操作视频（最长支持 3 分钟）",
                        Code = "screenrecord --time-limit 10 /sdcard/demo.mp4 && adb pull /sdcard/demo.mp4 ./",
                        ExpectedOutput = "/sdcard/demo.mp4: 1 file pulled   # 录制完成并将视频拉取到本地"
                    },
                    new CodeBlock
                    {
                        Label = "查看局域网 ARP 邻居映射表（定位同一路由器其他设备 IP 与 MAC）",
                        Code = "ip neigh show",
                        ExpectedOutput =
                            "192.168.0.1 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE    # 路由器网关\n" +
                            "192.168.0.21 dev wlan0 lladdr 11:22:33:44:55:66 REACHABLE   # Steam Deck 局域网主机"
                    }
                ]
            }
        ]
    };
}
