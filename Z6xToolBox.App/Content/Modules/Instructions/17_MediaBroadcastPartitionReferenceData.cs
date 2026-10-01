using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class MediaBroadcastPartitionReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "media-broadcast-partition-reference",
        Title = "17. 硬件解码、广播监听、证书体系与分区表（mediacodec / broadcast / cacerts / partitions）",
        Group = "设备接入",
        Summary = "深入解析 MT9669 芯片多媒体硬解能力排查、系统广播监听栈、CA 证书路径及 eMMC 物理分区与挂载参数。",
        Sections =
        [
            new ContentSection
            {
                Heading = "硬件视频解码器排查（MediaCodec）",
                Text = "排查播放 4K、HDR 或 AV1 视频时是否调用联发科硬件解码芯片（需 ADB shell 或普通 SSH 权限）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看芯片支持的硬件视频解码器清单",
                        Code = "dumpsys media.player | grep -iE \"decoder|omx|c2\" | grep -iE \"hevc|avc|av1|vp9\" | head -n 6",
                        ExpectedOutput =
                            "c2.mtk.hevc.decoder    # 联发科 MT9669 硬件 H.265/HEVC 4K 解码器\n" +
                            "c2.mtk.avc.decoder     # 联发科硬件 H.264/AVC 解码器\n" +
                            "c2.mtk.vp9.decoder     # 硬件 VP9 4K 解码器（YouTube 视频硬解核心）\n" +
                            "c2.mtk.av1.decoder     # 硬件 AV1 解码器"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "系统常驻广播监听与通知栈（dumpsys activity broadcasts）",
                Text = "排查哪些极米自带软件监听了开机或网络事件，以便后续做针对性冻结：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看注册了 BOOT_COMPLETED 开机自启动广播的应用列表",
                        Code = "dumpsys activity broadcasts | grep -A 4 \"android.intent.action.BOOT_COMPLETED\"",
                        ExpectedOutput =
                            "ReceiverList{... u0 com.xgimi.home/...}            # 极米自带桌面开机接收器\n" +
                            "ReceiverList{... u0 com.xgimi.doubtservice/...}   # 极米数据上报服务开机自启监听"
                    },
                    new CodeBlock
                    {
                        Label = "查看当前正在常驻通知栏的所有活动通知",
                        Code = "dumpsys notification --noredact | grep -E \"NotificationRecord|pkg=\" | head -n 4",
                        ExpectedOutput =
                            "NotificationRecord(0x...: pkg=com.github.catvod id=1 ...)   # 当前前台播放器常驻通知\n" +
                            "NotificationRecord(0x...: pkg=android id=1704 ...)          # 系统核心状态通知"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "HTTPS 抓包与 CA 证书体系",
                Text = "排查电视端抓包报证书不信任、SSL 握手失败时的底层路径：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看系统级只读受信任 CA 证书目录与用户证书目录",
                        Code = "ls -l /system/etc/security/cacerts/ | head -n 3 && ls -l /data/misc/user/0/cacerts-added/ 2>/dev/null",
                        ExpectedOutput =
                            "-rw-r--r-- 1 root root 4820 ... 00673b5b.0   # Android 系统预装根证书（只读）\n" +
                            "-rw-r--r-- 1 root root 5120 ... 02b73561.0\n" +
                            "# 若在网络设置中安装了代理抓包证书，将保存在 /data/misc/user/0/cacerts-added/ 中",
                        Note = "Android 7+ 默认不信任用户手动安装的 CA 证书，需要应用配置 network_security_config 允许信任 user 证书。"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "eMMC 物理分区结构与实际挂载参数",
                Text = "分析只读系统分区（system/vendor/product）与可写数据分区挂载标志：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看底层 eMMC 物理块设备与分区清单",
                        Code = "cat /proc/partitions | grep -i \"mmcblk0p\" | tail -n 8",
                        ExpectedOutput =
                            " 179       50     131072 mmcblk0p50   # 内部分区块编号 50（约 128MB，通常为 boot/dtbo）\n" +
                            " 179       51    3145728 mmcblk0p51   # 内部分区块编号 51（约 3GB，只读 system 分区）\n" +
                            " 179       53   52428800 mmcblk0p53   # 内部分区块编号 53（约 50GB，对应可写 /data 分区）"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统核心分区的实际挂载读写模式",
                        Code = "mount | grep -iE \" /system | /vendor | /data \"",
                        ExpectedOutput =
                            "/dev/block/... on /system type ext4 (ro,seclabel,nodev,noatime)   # ro: 只读挂载，不可直接修改文件\n" +
                            "/dev/block/... on /vendor type ext4 (ro,seclabel,nodev,noatime)   # ro: 芯片厂商驱动只读分区\n" +
                            "/dev/block/... on /data type f2fs (rw,seclabel,nosuid,nodev)      # rw: 可读写数据分区（采用 f2fs 高性能闪存文件系统）"
                    }
                ]
            }
        ]
    };
}
