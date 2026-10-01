using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class M3uPlaylistAggregatorEditorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "m3u-playlist-aggregator-editor",
        Title = "48. 自研 IPTV M3U 播放列表聚合与频道有效性探测器：自动去重清洗（Z6X M3uFilter）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的 IPTV 直播源列表清洗与聚合分发服务，自动探测多源频道可用性与首包延迟，剔除失效流并生成纯净订阅源，常驻内存仅 15MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端很多直播播放软件（如 TiviMate、DIYP）依赖外部网络上的 M3U 直播源列表。但公开源常常包含数千个失效死链、重复台、广告插播源或卡顿极高的跨省源，导致切台时频繁遇到黑屏转圈。\n\n" +
                       "使用 Go 自研的 M3U 清洗与聚合服务，周期性拉取多个订阅源并在内存中解析。通过并发协程对每个频道的流地址执行轻量 HTTP HEAD 或首分片请求测试，测量首包延迟并过滤掉 HTTP 404/500 及超时的死链。聚合生成一份结构清晰、带 EPG 关联的标准 M3U 文件供电视软件订阅，常驻物理内存约 15MB。",
                BulletPoints =
                [
                    "自动剔除死链与卡顿源：并发探测频道存活性，确保订阅列表中每个频道均能秒开。",
                    "多源智能优选与去重：同名频道保留首包延迟最低的最佳源，自动按央视/卫视分组。",
                    "本地订阅分发端点：电视播放器只需订阅 `http://127.0.0.1:8093/live.m3u`，自动保持更新。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http` + 正则流式提取（无外部 C 绑定）。",
                    "并发非阻塞探活池：采用带容量限制的 Worker Pool（设定 16 个并发协程），设置 3 秒单源探测超时，500 个频道在 30 秒内全部完成连通性复核。",
                    "EPG 频道 ID 自动对齐：根据预设的频道标准库（如 CCTV-1、湖南卫视）自动补充 `tvg-id`、`tvg-name` 与台标图标（`tvg-logo`），完善电视节目指南。",
                    "【参考开源项目】m3u-builder（M3U 列表构建工具）；iptv-checker（开源 IPTV 探活工具设计参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 M3U 聚合服务并请求纯净播放列表：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 M3U 探活聚合服务并获取订阅",
                        Code = "# 1. 启动 M3U 聚合服务（监听 8093 端口，加载输入源配置）\n" +
                               "nohup /data/local/tmp/z6x_m3ufilter \\\n" +
                               "  -port 8093 \\\n" +
                               "  -config /data/local/tmp/sources.yaml \\\n" +
                               "  -check-interval 12h > /data/local/tmp/m3u.log 2>&1 &\n\n" +
                               "# 2. 获取清洗后的有效频道播放列表前 10 行\n" +
                               "curl -s http://192.168.1.100:8093/live.m3u | head -n 10",
                        ExpectedOutput = "#EXTM3U x-tvg-url=\"http://epg.example.com/e.xml\"\n#EXTINF:-1 tvg-id=\"CCTV1\" tvg-name=\"CCTV-1\" group-title=\"央视频道\",CCTV-1 综合\nhttp://live.example.com/cctv1.m3u8\n[M3uFilter] Cleaned: 840 total -> 142 valid channels (Filtered 698 dead links in 14.8s)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "User-Agent 模拟与防盗链支持：部分 IPTV 源校验客户端 UA，探测与分发配置需允许设置全局 `user-agent`（如 `okhttp/3.14.9` 或 `TiviMate`）以避免返回 403 错误。",
                    "内存中大列表排序防抖：解析几十兆的大列表时，避免创建过多中间临时 slice，就地执行结构体切片原地过滤，维持内存稳定。"
                ]
            }
        ]
    };
}
