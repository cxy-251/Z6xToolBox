using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class PowerNetworkInputCrashReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "power-network-input-crash-reference",
        Title = "16. 电源休眠、网络接口、输入事件与底层崩溃（power / wifi / getevent / tombstones）",
        Group = "设备接入",
        Summary = "深入解析系统待机发热与唤醒锁排查、Wi-Fi 协商速率与 VPN 接口状态、底层按键事件捕获及 C/C++ Native 崩溃转储分析。",
        Sections =
        [
            new ContentSection
            {
                Heading = "待机发热与唤醒锁排查（dumpsys power / deviceidle）",
                Text = "排查电视屏幕熄灭后后台为何依然发热耗电、哪些应用阻止系统进入深度休眠：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前持有 WakeLock 的应用与后台服务（需 ADB 权限）",
                        Code = "dumpsys power | grep -A 8 \"Wake Locks: size=\"",
                        ExpectedOutput =
                            "Wake Locks: size=1\n" +
                            "  PARTIAL_WAKE_LOCK 'doubtservice_daemon' ... (uid=1000, pid=1024)   # 极米后台服务持有部分唤醒锁，阻止 CPU 休眠"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统低电耗模式（Doze）状态与白名单",
                        Code = "dumpsys deviceidle whitelist",
                        ExpectedOutput =
                            "system,com.xgimi.doubtservice,1000   # 处于系统免休眠白名单中的包名\n" +
                            "system,com.xgimi.home,1000"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Wi-Fi 协商速率与 VPN 虚拟网卡状态",
                Text = "排查无线投屏卡顿、5G 频段握手及代理 VPN 是否建立成功：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前 Wi-Fi 频段、信道与物理协商速率",
                        Code = "dumpsys wifi | grep -iE \"mWifiInfo|Link speed|Frequency\" | head -n 4",
                        ExpectedOutput =
                            "mWifiInfo SSID: Home_5G BSSID: ... MAC: ...               # 连接的 Wi-Fi 名称\n" +
                            "Link speed: 866Mbps                                       # 物理协商速率 866Mbps（5GHz 802.11ac）\n" +
                            "Frequency: 5200MHz                                        # 工作在 5GHz 频段"
                    },
                    new CodeBlock
                    {
                        Label = "查看当前系统默认活动网络与 VPN 接口（tun0）",
                        Code = "dumpsys connectivity | grep -E \"Active network|tun0\" | head -n 3",
                        ExpectedOutput =
                            "Active network: 100 (WIFI)                               # 当前主默认网络为 Wi-Fi\n" +
                            "InterfaceName: tun0                                       # Clash/VPN 虚拟网卡已成功挂载"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "遥控器与外设底层输入事件监听（getevent）",
                Text = "排查按键无响应、键位错乱及游戏手柄映射：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出系统输入设备列表与对应的 Linux 事件节点",
                        Code = "dumpsys input | grep -E \"Device [0-9]+:|Classes:|Path:\"",
                        ExpectedOutput =
                            "Device 3: XGIMI Remote Controller   # 极米蓝牙遥控器\n" +
                            "  Path: /dev/input/event2           # 对应的底层 Linux 事件节点\n" +
                            "  Classes: KEYBOARD | ALPHAKEY      # 属于键盘类输入设备"
                    },
                    new CodeBlock
                    {
                        Label = "实时捕获指定事件节点的物理扫描码与按键名",
                        Code = "getevent -l /dev/input/event2",
                        ExpectedOutput =
                            "/dev/input/event2: EV_KEY KEY_ENTER DOWN   # 捕获到确认键按下事件\n" +
                            "/dev/input/event2: EV_KEY KEY_ENTER UP     # 捕获到确认键抬起事件\n" +
                            "/dev/input/event2: EV_SYN SYN_REPORT 00000000"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "底层 C/C++ 崩溃转储分析（Native Crash）",
                Text = "排查因缺少 64 位库、SO 库段错误（SIGSEGV）导致的底层崩溃：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看系统生成的底层 Native 崩溃转储日志列表",
                        Code = "ls -lt /data/tombstones/ | head -n 5",
                        ExpectedOutput =
                            "-rw------- 1 system system 45210 ... tombstone_00   # 最近一次底层 C/C++ 崩溃转储文件\n" +
                            "-rw------- 1 system system 38120 ... tombstone_01"
                    },
                    new CodeBlock
                    {
                        Label = "打印当前正在运行或卡死进程的线程底层堆栈（debuggerd）",
                        Code = "debuggerd -b 1130",
                        ExpectedOutput =
                            "----- pid 1130 at ... -----\n" +
                            "\"main\" prio=5 tid=1 Native\n" +
                            "  #00 pc 0004a120  /system/lib/libc.so (__epoll_pwait+20)   # 打印当前进程主线程底层调用栈"
                    }
                ]
            }
        ]
    };
}
