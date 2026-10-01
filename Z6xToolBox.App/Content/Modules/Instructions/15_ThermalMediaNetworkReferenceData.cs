using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class ThermalMediaNetworkReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "thermal-media-network-reference",
        Title = "15. 硬件温度、蓝牙音频与网络抓包（thermal / SurfaceFlinger / tcpdump / iptables）",
        Group = "设备接入",
        Summary = "讲解核心温度读取、CPU 实时调频、蓝牙遥控器诊断、SurfaceFlinger 图层排查及 tcpdump/iptables 网络分析。",
        Sections =
        [
            new ContentSection
            {
                Heading = "硬件温度监控与 CPU 调频策略（在 SSH 与 ADB 下均可执行）",
                Text = "读取联发科 MT9669 芯片各核心与光机温度传感器数值，排查风扇转速与发热降频：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "读取各传感器实时温度（毫摄氏度）",
                        Code = "cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null",
                        ExpectedOutput =
                            "58000   # CPU 核心温度（58℃）\n" +
                            "54000   # 光机内部温度（54℃）\n" +
                            "49000   # 主板供电温度（49℃）",
                        Note = "数值除以 1000 即为摄氏度。若超过 85℃ 系统会触发过热保护降低光机亮度。"
                    },
                    new CodeBlock
                    {
                        Label = "查看 CPU 核心当前实时运行频率与调频模式",
                        Code = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq && cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor",
                        ExpectedOutput =
                            "1500000     # 当前 CPU 主频为 1.5 GHz（单位 kHz）\n" +
                            "schedutil   # 基于内核调度器的动态升降频策略"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "蓝牙遥控器与音频通道诊断",
                Text = "排查遥控器信号弱、按键丢帧及 HDMI eARC / 蓝牙音箱延迟：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看蓝牙遥控器连接状态与信号强度（需 ADB 权限）",
                        Code = "dumpsys bluetooth_manager | grep -iE \"connected|device|rssi\"",
                        ExpectedOutput =
                            "Connected devices: [aa:bb:cc:11:22:33]   # 极米蓝牙遥控器 MAC\n" +
                            "RSSI: -52 dBm                           # 信号良好（数值大于 -70 均正常）"
                    },
                    new CodeBlock
                    {
                        Label = "查看音频底层路由与通道状态",
                        Code = "dumpsys media.audio_flinger | grep -A 5 \"Output thread\"",
                        ExpectedOutput =
                            "Output thread ... type 0 (DIRECT):\n" +
                            "  Sample rate: 48000   # 当前音频采样率 48kHz\n" +
                            "  Format: 0x1 (pcm16)  # 16 位立体声 PCM 直通"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SurfaceFlinger 图层合成与屏幕掉帧分析",
                Text = "排查界面卡顿是哪个应用图层导致的：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出当前屏幕正在渲染的所有图层栈",
                        Code = "dumpsys SurfaceFlinger --list",
                        ExpectedOutput =
                            "com.xgimi.home/com.xgimi.home.MainActivity#0      # 极米自带桌面底图层\n" +
                            "InputMethod#0                                     # 输入法层\n" +
                            "StatusBar#0                                       # 系统状态栏层\n" +
                            "ColorFade#0                                       # 屏幕亮度遮罩层"
                    },
                    new CodeBlock
                    {
                        Label = "检测显示刷新间隔与 VSync 延迟统计",
                        Code = "dumpsys SurfaceFlinger --latency",
                        ExpectedOutput = "16666666   # 纳秒单位（对应 60Hz 刷新率的标准 16.6ms 帧周期）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "网络抓包与本地内核防火墙",
                Text = "分析电视后台请求域名与流量规则：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "使用 tcpdump 抓取电视网络数据包",
                        Code = "tcpdump -i any -s 0 -w /sdcard/capture.pcap -c 1000 && adb pull /sdcard/capture.pcap ./",
                        ExpectedOutput = "1000 packets captured   # 抓取 1000 个包并拉取到本地用 Wireshark 打开"
                    },
                    new CodeBlock
                    {
                        Label = "查看 Linux 内核防火墙规则过滤链",
                        Code = "iptables -L -n -v",
                        ExpectedOutput = "Chain INPUT (policy ACCEPT 120 packets, 15K bytes)   # 查看网络数据包放行状态"
                    }
                ]
            }
        ]
    };
}
