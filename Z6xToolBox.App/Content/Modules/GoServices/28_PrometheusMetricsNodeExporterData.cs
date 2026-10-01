using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class PrometheusMetricsNodeExporterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "prometheus-metrics-node-exporter",
        Title = "28. 自研 Prometheus 规范系统与存储指标导出器：/metrics 协程输出（Z6X NodeExporter）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量 Prometheus 指标导出器，采集极米 CPU、内存、U 盘 I/O、光机温度与网络流量并暴露 /metrics 端点，常驻内存仅 11MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "对于搭建了全屋监控与 Grafana 仪表盘的家庭服务器管理员，通常希望能将投影仪的运行状态（CPU 占用、内存水位、温度趋势、外接硬盘剩余容量、下载吞吐）纳入统一的 Prometheus 体系监控。\n\n" +
                       "官方 Linux node_exporter 二进制体积大且采集项过于庞杂，包含大量在 Android 上无权限访问的虚拟节点。使用 Go 自研的轻量 NodeExporter，针对极米系统特性仅抓取核心有效指标，并按照 OpenMetrics/Prometheus 文本协议标准格式化输出 `/metrics` 接口，常驻物理内存约 11MB。",
                BulletPoints =
                [
                    "开箱接入 Grafana：标准 Prometheus 文本格式，局域网服务器配置抓取即可出图。",
                    "低开销按需采集：仅在 Prometheus 定时抓取（Scrape）时触发读取，平视处于休眠状态。",
                    "针对 Android 裁剪精简：过滤无效的 systemd/cgroup 报警指标，专注于实际硬件参数。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `prometheus/client_golang`（轻量 Gauge/Counter 收集器）+ `net/http` 单路由监听。",
                    "流式文本格式化：避免在内存中拼接长字符串，采用 `bufio.Writer` 直接向 HTTP 连接流式写入指标文本行，降低 GC 分代扫描压力。",
                    "核心指标覆盖：`z6x_cpu_usage_ratio`、`z6x_mem_available_bytes`、`z6x_thermal_celsius`、`z6x_disk_free_bytes`、`z6x_network_receive_bytes_total`。",
                    "【参考开源项目】node_exporter（官方节点导出器）；process-exporter（轻量进程指标导出器）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动指标服务并使用 curl 抓取 /metrics 验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Prometheus 指标导出器并拉取测试",
                        Code = "# 1. 启动轻量指标导出器（监听本地 9100 端口）\n" +
                               "nohup /data/local/tmp/z6x_exporter -port 9100 > /data/local/tmp/exporter.log 2>&1 &\n\n" +
                               "# 2. PC 端发送 GET 请求验证指标格式\n" +
                               "curl -s http://192.168.1.100:9100/metrics | grep -E '^z6x_'",
                        ExpectedOutput = "# HELP z6x_mem_available_bytes System available memory in bytes\n# TYPE z6x_mem_available_bytes gauge\nz6x_mem_available_bytes 1.8427904e+09\n# HELP z6x_thermal_celsius Temperature in celsius\n# TYPE z6x_thermal_celsius gauge\nz6x_thermal_celsius{zone=\"cpu\"} 56.4\nz6x_thermal_celsius{zone=\"optical\"} 61.8"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "网络流量统计文件路径：Android 11 的无线网卡流量统计可能位于 `/proc/net/dev` 或系统流量统计节点 `/sys/class/net/wlan0/statistics/`，程序内置自动回退探测机制。",
                    "抓取超时设置：由于挂载 U 盘休眠时读取 `statfs` 可能需要 1~2 秒唤醒，Prometheus 抓取超时配置建议设为 10s 以上。"
                ]
            }
        ]
    };
}
