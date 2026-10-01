using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class NetworkKeepAliveStandbyData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "network-keep-alive-standby",
        Title = "7. 24 小时后台常驻保障：Wakelock 锁与网络待机配置",
        Group = "Go原生服务",
        Summary = "分析 Android TV 熄屏进入低功耗待机时的休眠与断网机制，提供 CPU 唤醒锁与网络保持策略，确保微服务全天候可用。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "前面规划的所有后台服务（WebDAV 文件共享、Aria2 离线下载、DNS 缓存、Navidrome 音乐服务器等），核心目标都是希望投影仪在「不看投影、关掉光机」的待机状态下，依然能 24 小时提供家庭网络服务。\n\n" +
                       "然而 Android TV 默认的电源管理非常激进：按下遥控器关机后，系统通常在 15 分钟后进入深度睡眠（Doze Mode），切断 Wi-Fi 芯片供电并挂起 CPU。这会导致所有后台下载中断、局域网文件打不开、端口失联。本模块解决的核心问题就是：让极米在关闭光机（画面全黑、风扇停转、仅耗电几瓦）的同时，保持主板 CPU 与 Wi-Fi 持续在线，实现真正的全天候无感服务。",
                BulletPoints =
                [
                    "保障服务全天候在线：关掉投影后，离线下载不中断、手机随时能连上听歌和取文件。",
                    "低功耗与静音兼得：仅保持主板芯片微安级供电，发热的光机与高转速风扇处于彻底断电状态，整机功耗仅约 3W ~ 5W。",
                    "避免频繁人工唤醒：不用为了用一下下载或文件服务而特意开机点亮投影仪。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Android 系统全局设置（Settings Global）+ 电源白名单机制（DeviceIdleController）+ Linux 内核 Wakelock。",
                    "什么是 Doze Mode（低电耗模式）？Android 系统的深度省电机制。当检测到屏幕熄灭且无操作时，系统会逐步延缓后台任务并切断网络连接。通过将开发服务加入电池优化白名单，可免受该机制强行冻结。",
                    "什么是 Wi-Fi Sleep Policy（休眠策略）？Android 管理无线网卡工作状态的策略项。系统默认可能为跟随屏幕关闭网卡；将其设置为 `2`（对应系统常量 `WIFI_SLEEP_POLICY_NEVER`），强制网卡在接通电源时保持连接，持续响应局域网数据包。",
                    "设置项会丢失吗？通过 `settings put global` 修改的参数会持久化保存在 `/data/system/users/0/settings_global.xml` 中，投影仪重启后仍然生效。"
                ]
            },
            new ContentSection
            {
                Heading = "全天候在线配置指令",
                Text = "通过 ADB 执行系统级电源与网络参数配置：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "配置熄屏保持网络连接与电源策略",
                        Code = "# 1. 强制 Wi-Fi 在熄屏待机时保持连接（2 = WIFI_SLEEP_POLICY_NEVER）\n" +
                               "settings put global wifi_sleep_policy 2\n\n" +
                               "# 2. 配置接通电源时保持 CPU 活跃（3 = BATTERY_PLUGGED_AC | BATTERY_PLUGGED_USB）\n" +
                               "settings put global stay_on_while_plugged_in 3\n\n" +
                               "# 3. 验证当前生效设置\n" +
                               "settings get global wifi_sleep_policy\n" +
                               "settings get global stay_on_while_plugged_in",
                        ExpectedOutput = "2\n3"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "待机常驻运维排查：",
                BulletPoints =
                [
                    "问题 1：功耗与发热是否会影响光机寿命？解答：完全不会。极米关机实际上仅切断了光机（LED/激光光源）的高压供电，并让散热风扇停转；此时 MT9669 芯片运行微服务的 CPU 占用不足 1%，温度仅 40 度左右，相当于一台超低功耗工控机，对投影光机寿命零损耗。",
                    "问题 2：偶发被系统杀后台（LMK）。解决：非系统签名的原生进程在系统运行超大型 TV 游戏或重度播放时可能被低内存清理（LMK）。若需极端稳固，可通过 `echo -1000 > /proc/<PID>/oom_score_adj` 将关键进程的 OOM 优先级调至最高（免杀）。"
                ]
            }
        ]
    };
}
