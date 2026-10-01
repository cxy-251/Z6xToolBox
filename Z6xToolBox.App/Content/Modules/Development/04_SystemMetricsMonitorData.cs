using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class SystemMetricsMonitorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "system-metrics-monitor",
        Title = "4. 原生微服务应用场景与系统监控探针",
        Group = "开发环境",
        Summary = "梳理轻量原生架构在极米投影仪上的典型开发场景，以系统指标采集探针（读取 /proc 数据并暴露 HTTP API）为例说明实现方案。",
        Sections =
        [
            new ContentSection
            {
                Heading = "典型轻量开发场景",
                Text = "在保持物理内存开销极低的前提下，极米 Z6X Pro 可承担如下原生 Linux 微服务负载：",
                BulletPoints =
                [
                    "轻量文件共享与同步：使用单二进制静态编译的 Go/Rust 工具（如静态 WebDAV 服务、微型 HTTP 文件服务器），方便内网跨设备拷贝视频与文件。",
                    "局域网自动化与设备联动探针：提供简单的 HTTP Webhook 或 MQTT 客户端，接收家庭网络自动化通知（如 Home Assistant 控制投影仪休眠、音量调节）。",
                    "网络代理与转发节点：部署静态编译的轻量透明代理或 DNS 缓存转发（如轻量级 DNS/Socks5 工具）。",
                    "硬件健康度与系统指标监控端点：定期采集投影仪 CPU 负荷、风扇温控、可用内存，输出 Prometheus 或 JSON 格式数据。"
                ]
            },
            new ContentSection
            {
                Heading = "无特权获取系统监控指标",
                Text = "非 Root 的 shell 权限即可直接读取 Linux 内核提供的虚拟文件系统获取第一手硬件与系统数据：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "读取 CPU 使用率、物理内存与光机温度",
                        Code = "cat /proc/stat | head -n 1\n" +
                               "cat /proc/meminfo | grep -E 'MemTotal|MemFree|MemAvailable'\n" +
                               "cat /sys/class/thermal/thermal_zone*/temp",
                        ExpectedOutput =
                            "cpu  41295 18234 38291 792182 1204 0 892 0 0 0\n" +
                            "MemTotal:        3670016 kB\n" +
                            "MemFree:          296180 kB\n" +
                            "MemAvailable:    1524300 kB\n" +
                            "45000\n47000"
                    }
                ],
                BulletPoints =
                [
                    "MemAvailable 精准可用值：Android TV 会动态占用 PageCache，仅观察 MemFree 会误以为内存耗尽，MemAvailable（实测约 1.5GB）才是内核真实可立即回收分配给微服务的内存容量。",
                    "温度监控：通过 `/sys/class/thermal/` 实时掌握光机和 CPU 温度，避免投影仪被负载过高导致温控降频。"
                ]
            }
        ]
    };
}
