using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LogAggregationLokiLiteData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "log-aggregation-loki-lite",
        Title = "43. 自研嵌入式 Loki 规范日志聚合与查询端点：轻量时间线索引（Z6X LogLoki）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型日志聚合服务，兼容 Grafana Loki 推送与查询协议，为电视上运行的数十个后台微服务提供统一排错检索接口，常驻内存仅 15MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米后台部署了数十个 Go 和 Rust 服务后，各进程日志分散在 `/data/local/tmp/*.log` 各自独立的文件中。当某个服务偶发异常退出时，需要逐个文件用 `grep` 排查，且无法直观按时间线统一对比不同进程间的事件先后顺序。\n\n" +
                       "使用 Go 自研的微型 Loki 日志收集端点，各子服务通过标准 HTTP POST 向本地 `:3100` 端口推送结构化日志流。服务在内存中按时间戳维护环形时间线索引，暴露兼容 Grafana 的 `/loki/api/v1/query_range` 接口，可直接在电脑 Grafana 界面上按应用标签和时间线联调，常驻物理内存约 15MB。",
                BulletPoints =
                [
                    "全服务统一日志入口：告别逐个翻看 log 文件的繁琐，所有原生服务日志一站式检索。",
                    "兼容 Grafana Loki 协议：局域网 Grafana 无需安装额外插件即可直接添加为数据源。",
                    "定额环形内存存储：限制最大存储大小（如 10MB），超时与超额自动覆盖，不伤闪存。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http` + 纯内存有序时间戳二分检索树（无需安装复杂的 Elasticsearch/Promtail）。",
                    "流式 JSON/Snappy 解码：支持解析 Loki 标准的 push API 请求体，利用 Go 协程异步将日志条目插入对应 stream 标签集合中。",
                    "按需落盘策略：平时仅在内存环形队列维护最近 5,000 条日志，仅当接收到 `SIGUSR1` 或关键服务崩溃信号时才强制落盘保存。",
                    "【参考开源项目】loki（Grafana 开源日志聚合系统）；vector（轻量可观测性数据管道）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动轻量日志端点并推送测试日志：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动微型 Loki 日志端点并查询日志",
                        Code = "# 1. 启动轻量 Loki 服务（监听本地 3100 端口）\n" +
                               "nohup /data/local/tmp/z6x_loki \\\n" +
                               "  -port 3100 \\\n" +
                               "  -max-lines 5000 > /data/local/tmp/loki.log 2>&1 &\n\n" +
                               "# 2. 模拟客户端推送结构化日志\n" +
                               "curl -s -X POST http://127.0.0.1:3100/loki/api/v1/push \\\n" +
                               "  -H \"Content-Type: application/json\" \\\n" +
                               "  -d '{\"streams\":[{\"stream\":{\"app\":\"z6x_webdav\"},\"values\":[[\"1710000000000000000\",\"Auth success for user media\"]]}]}'\n\n" +
                               "# 3. 执行 LogQL 查询测试\n" +
                               "curl -s \"http://127.0.0.1:3100/loki/api/v1/query?query=%7Bapp%3D%22z6x_webdav%22%7D\"",
                        ExpectedOutput = "{\"status\":\"success\",\"data\":{\"resultType\":\"streams\",\"result\":[{\"stream\":{\"app\":\"z6x_webdav\"},\"values\":[[\"1710000000000000000\",\"Auth success for user media\"]]}]}}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "纳秒级时间戳排序：日志推送端的时间戳必须单调递增，若遇 NTP 对时导致时钟回拨，LokiLite 需自动将乱序条目纠偏至当前尾部，避免索引断裂报错。",
                    "内存超限硬性熔断：为保证常驻内存不超过 18MB，当累计未查询日志达到条数上限后，严格遵循 FIFO 先进先出淘汰老旧数据流。"
                ]
            }
        ]
    };
}
