using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class SpeedtestTrackerCollectorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "speedtest-tracker-collector",
        Title = "45. 自研家庭宽带定时测速与链路质量追踪器：周期时延测算（Z6X SpeedTracker）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量网络测速与链路质量定期检测探针，周期性测算宽带上下行速率与抖动趋势并生成历史统计，常驻内存仅 12MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "家庭宽带经常在晚高峰遭遇运营商静默限速、丢包率上升或跨省跨网延迟剧增。用户在看高清流媒体卡顿拉取不动时，往往无法举证是当前宽带质量问题还是视频源问题。\n\n" +
                       "使用 Go 自研的轻量测速探针，在凌晨或闲时周期性（如每 6 小时一次）连接最近的公共 Speedtest 测速节点。多协程并发打流测算实时下载、上传带宽与 P99 抖动，结果持久化为轻量 SQLite 数据库，对外提供简单图表 Web 视图查看历史趋势，常驻物理内存约 12MB（测速时峰值约 22MB）。",
                BulletPoints =
                [
                    "自动记录宽带质量历史：量化追踪家庭网络长周期带宽与延迟波动。",
                    "高峰期避让策略：智能检测内网是否有大流量传输，若正在观影则自动跳过本次测速。",
                    "低开销单文件交付：纯 Go 原生实现 HTTP/TCP 测速协议，无 Python 重型依赖。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `showwin/speedtest-go` 协议库 + 嵌入式 SQLite/bbolt 记录存储。",
                    "多协程并发打流：在 10 秒窗口期内启动 4~8 个 Goroutine 分块下载预设静态垃圾块，利用滑动时间窗口平滑计算瞬时带宽与峰值吞吐。",
                    "前台观影保护锁：通过读取 `/proc/net/dev` 实时流量速率，若检测到近 10 秒平均下行 > 2MB/s，自动判定当前有流媒体播放，推迟测速动作。",
                    "【参考开源项目】speedtest-go（Go 语言官方 Speedtest 客户端）；speedtest-tracker（测速历史记录管理工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "手动触发一次测速并查看测试指标：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行单次家庭宽带上下行测速",
                        Code = "# 1. 运行测速命令自动挑选最佳测速节点并测算\n" +
                               "/data/local/tmp/z6x_speedtest -once -json\n\n" +
                               "# 2. 查看测速结果 JSON 输出\n" +
                               "cat /data/local/tmp/last_speedtest.json",
                        ExpectedOutput = "{\n  \"server\": \"China Telecom (Guangzhou)\",\n  \"latency_ms\": 12.4,\n  \"download_mbps\": 482.6,\n  \"upload_mbps\": 48.2,\n  \"jitter_ms\": 1.1\n}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "测速流量消耗把控：全速测算千兆宽带单次可能消耗 500MB~1GB 流量，建议每日测速频次限制在 2~4 次，避免消耗流量包。",
                    "四核 CPU 调度争抢：打流瞬间多个协程并发读写容易造成短时间 CPU 跑满，需使用 `runtime.GOMAXPROCS(2)` 限制最多使用 2 个核心，避免影响后台其他守护进程。"
                ]
            }
        ]
    };
}
