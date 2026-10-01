using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class HardwareTelemetryWatchdogData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "hardware-telemetry-watchdog",
        Title = "10. 自研硬件看板与系统自愈探针：/proc 解析与定时清理（Z6X SysExporter）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的系统健康度导出器与自愈看门狗，实时提取 Linux 内核温控与内存指标，并在夜间自动清理无用日志与缓存。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米官方系统不向普通用户开放详细的硬件与系统底层健康数据。当长时间挂机或外接微服务时，用户无法获知当前 CPU 的实时负荷、光机温控情况、真实可用内存（MemAvailable）以及存储健康度。\n\n" +
                       "同时，Android 系统在长期开机运行后，`/data/local/tmp/` 容易积累调试临时文件，logcat 日志缓冲区也持续占用内存。自研该探针后，既能提供直观的系统状态 JSON/Web 仪表盘，又能在凌晨 3 点静默执行自愈清理脚本，保证设备长期开机不卡顿、不溢出。",
                BulletPoints =
                [
                    "实时掌握光机温控与 CPU 负荷：掌握芯片真实运行状态，防止散热异常导致高温降频。",
                    "看清真实可用物理内存：避免被 Android TV 虚假的可用内存显示误导（区分 Cache 与 Free）。",
                    "夜间无人值守自愈维护：自动清理系统冗余临时文件与日志缓冲区。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 内置 Ticker 轮询器 + 嵌入式 HTTP 服务（单二进制体积约 4MB）。",
                    "什么是 `/proc` 与 `/sys` 虚拟文件系统？Linux 内核通过内存中的伪文件实时暴露系统状态。Go 语言通过直接读取 `/proc/stat`（计算 CPU 使用率）、`/proc/meminfo`（计算真实内存）、`/sys/class/thermal/thermal_zone*/temp`（获取光机与核心摄氏度），不需要通过慢速的 dumpsys 命令，毫秒级即可拿到最底层硬件指标。",
                    "什么是无侵入式自愈？探针内置轻量定时器，在设定的空闲时间（如凌晨 3:00）调用本地 shell 执行清理指令，避免安装庞大复杂的运维套件。",
                    "【参考开源项目】Prometheus node_exporter（参考其针对 `/proc` 文件系统的纯文本解析算法与内存占用控制）；robfig/cron（参考其轻量级 Crontab 调度器设计）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动探针服务并通过 curl 抓取实时指标：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动探针并获取实时 JSON 状态数据",
                        Code = "# 1. 启动自研硬件探针与清理看门狗（监听 9101 端口）\n" +
                               "nohup /data/local/tmp/z6x_sysexporter -port 9101 > /data/local/tmp/exporter.log 2>&1 &\n\n" +
                               "# 2. 本地发起查询验证 JSON 输出\n" +
                               "curl -s http://127.0.0.1:9101/metrics | jq .",
                        ExpectedOutput = "{\n  \"cpu_usage_percent\": 2.4,\n  \"mem_total_mb\": 3584,\n  \"mem_available_mb\": 1520,\n  \"thermal_cpu_celsius\": 45.2,\n  \"disk_free_gb\": 46.8\n}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "探针开发与运行注意事项：",
                BulletPoints =
                [
                    "问题 1：频繁读取 `/proc` 导致自身 CPU 占用偏高。原因与解决：不要使用高频（如 100ms）轮询。对于投影仪此类设备，指标采样间隔设为 5 秒 ~ 10 秒完全足够，单次读取仅耗时 1 毫秒，CPU 占比低于 0.05%。",
                    "问题 2：温控节点权限变动。原因与解决：部分 Android 版本的 `thermal_zone` 节点需要 `system` 组权限读取。Go 遍历 `/sys/class/thermal/` 时应添加异常捕获，若某个具体 zone 提示权限拒绝则优雅降级跳过，不影响其余传感器的数据输出。"
                ]
            }
        ]
    };
}
