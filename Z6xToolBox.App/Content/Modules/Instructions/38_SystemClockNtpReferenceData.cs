using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class SystemClockNtpReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "system-clock-ntp-reference",
        Title = "38. 系统时间同步与NTP网络对时（date / ntp_server / 掉电时间重置）",
        Group = "设备接入",
        Summary = "分析投影仪无 RTC 纽扣电池导致的断电时间归零根因、命令行强制对时与私有 NTP 服务器配置。",
        Sections =
        [
            new ContentSection
            {
                Heading = "硬件断电时间归零根因",
                Text = "极米 Z6X Pro 内部未配备 RTC 硬件时钟电池，完全依赖断电前保存的时间戳与开机网络 NTP：",
                BulletPoints =
                [
                    "离线局域网或断网关机拔掉电源后，重新上电系统时间会回退至 Linux 内核编译基准时间（1970年或出厂固件打包时间）。",
                    "时间错乱会导致 HTTPS/SSL 握手证书直接校验失败（CertificateExpiredException）。"
                ]
            },
            new ContentSection
            {
                Heading = "命令行强制对时与私有 NTP 配置",
                Text = "在未连接外网的专用投影局域网内手动同步系统时间：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "将当前主机时间同步至投影仪",
                        Code = "adb shell \"date $(date +%m%d%H%M%Y.%S)\"",
                        ExpectedOutput = "# 执行后投影仪系统时间即刻与主机秒级对齐"
                    },
                    new CodeBlock
                    {
                        Label = "指定局域网内自定义 NTP 时间服务器",
                        Code = "settings put global ntp_server 192.168.0.1",
                        ExpectedOutput = "# 无输出表示设置成功，系统网络时间同步服务改从局域网路由获取时间"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`date -s`：修改系统时间需要 `CAP_SYS_TIME` 能力。SSH（UID 10068）调用报 `date: settimeofday: Operation not permitted`；ADB（UID 2000）同样受限，但可通过 `settings put global` 触发系统的自动同步逻辑。"
                ]
            }
        ]
    };
}
