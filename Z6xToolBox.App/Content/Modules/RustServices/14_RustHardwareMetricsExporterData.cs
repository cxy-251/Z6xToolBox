using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class RustHardwareMetricsExporterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "rust-hardware-metrics-exporter",
        Title = "14. 自研极简硬件指标导出器：/proc 流式解析与 Prometheus 导出（Z6X RustSensorExporter）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的超轻量硬件监控导出器，单文件仅 600KB，常驻物理内存低于 1MB，替代 15MB 且占用 12MB+ 内存的 Go 官方 node_exporter。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "为了将极米系统的硬件温度、CPU 负荷、网络吞吐汇入 Grafana 仪表盘，通常会部署官方的 Prometheus `node_exporter`。但官方 Go 版体积超过 15MB，启动后常驻内存超过 12MB，且会启动数十个收集器线程，对于仅有 3.5GB 内存的投影仪而言存在资源浪费。\n\n" +
                       "使用 Rust 自研极简导出器后，仅针对极米关键指标（CPU、内存、温度、网络流量）进行流式文本解析，剥离调试符号后体积仅约 600KB，物理内存占用压低至 **800KB ~ 1MB**，以极微小的系统代价提供标准的 Prometheus 监控接口。",
                BulletPoints =
                [
                    "比官方 exporter 节省 90% 内存：常驻物理内存仅约 1MB。",
                    "单二进制体积仅 600KB：编译剥离优化，极度轻便。",
                    "原生 Prometheus 格式：完全兼容 Prometheus、Grafana 与 VictoriaMetrics 抓取。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + 标准库 `std::fs` + 纯文本流式字符串切片（单文件体积约 600KB）。",
                    "零堆分配读取 `/proc`：读取 `/proc/stat` 与 `/proc/meminfo` 时，预分配固定 4KB 栈上数组。使用 `read_to_end` 直接在栈上完成字节分割，全程不申请任何堆内存，杜绝内存碎片化。",
                    "无多余扫描开销：仅保留投影仪关键指标（`z6x_cpu_usage`、`z6x_thermal_celsius`、`z6x_mem_available_bytes`、`z6x_net_rx_bytes`），不运行任何多余的系统探测器。",
                    "【参考开源项目】node_exporter（参考其标准指标命名与帮助说明格式）；procfs（Rust Linux procfs 高性能解析库参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动极简监控导出器并通过 curl 抓取指标：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Rust 极简指标导出器",
                        Code = "# 1. 启动导出器（监听 9102 端口）\n" +
                               "nohup /data/local/tmp/z6x_sensor_exporter -port 9102 > /data/local/tmp/exporter_rust.log 2>&1 &\n\n" +
                               "# 2. 本地抓取标准 Prometheus 格式指标\n" +
                               "curl -s http://127.0.0.1:9102/metrics | head -n 12",
                        ExpectedOutput = "# HELP z6x_thermal_celsius Current thermal sensor temperature\n# TYPE z6x_thermal_celsius gauge\nz6x_thermal_celsius{zone=\"cpu\"} 44.5\n# HELP z6x_mem_available_bytes Linux available memory\nz6x_mem_available_bytes 1598418944"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "极简导出器排坑指南：",
                BulletPoints =
                [
                    "问题 1：Prometheus 抓取超时。由于 `/proc` 是内核伪文件系统，读取开销在微秒级别，若遇到网络握手延迟，设置 HTTP 服务使用轻量无锁异步 IO（`tiny_http` 或标准库监听）。",
                    "问题 2：浮点数解析精度抖动。温度传感器原始值为毫摄氏度（如 44500），在输出时直接用整数除以 1000 格式化为字符串，规避复杂的浮点数运算。"
                ]
            }
        ]
    };
}
