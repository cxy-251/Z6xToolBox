using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class RssFeedMediaAggregatorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "rss-feed-media-aggregator",
        Title = "25. 自研 RSS/Atom 播客与影视追更聚合器：定时拉取与离线下载联动（Z6X FeedSync）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量 RSS/Atom 订阅聚合引擎，负责周期性解析影视追更与播客音频 Feed，自动联动本地 Aria2 推送下载，常驻内存仅 12MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "追踪特定电视剧、动漫番剧或高品质音频播客更新时，用户通常需要在手机或电脑上手动刷新发布页并复制磁力链接下载。电视自带的视频软件不仅更新慢，还包含大量片头广告。\n\n" +
                       "使用 Go 自研的轻量 RSS 订阅引擎，电视在后台周期性请求配置的 RSS/Atom 地址，解析最新条目中的 enclosure 多媒体附件或磁力链接。配合哈希去重数据库，发现新剧集后自动通过 JSON-RPC 推送给本地 Aria2 并在大屏弹出已加入下载的通知，常驻物理内存约 12MB。",
                BulletPoints =
                [
                    "自动追更无需人工干预：订阅番剧或播客 RSS，更新后自动触发静默离线下载。",
                    "轻量过滤规则：支持按正则匹配（如 `1080p|2160p|HEVC`）筛选指定压制格式。",
                    "下载完成后无缝入库：下载完毕后直接联动本地 NFO 刮削模块，开机即看。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `mmcdole/gofeed`（流式 XML 解析器）+ `bbolt` 嵌入式键值库（去重）。",
                    "流式 XML 反序列化：采用基于 token 的流式解码替代全量 DOM 树加载，单次解析上百条 Feed 仅消耗不到 2MB 堆内存。",
                    "增量 GUID 去重缓存：将已推送下载的 GUID 写入机身本地 bbolt 单文件数据库，防止重复下载造成存储浪费。",
                    "【参考开源项目】gofeed（Go 生态主流 RSS/Atom 解析库）；autobrr（轻量自动化种子订阅调度工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 RSS 订阅聚合服务并测试手动同步：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 RSS 追更聚合服务",
                        Code = "# 1. 启动订阅服务（每小时检查一次，配置文件指定 Feed 地址）\n" +
                               "nohup /data/local/tmp/z6x_feedsync \\\n" +
                               "  -config /data/local/tmp/feeds.yaml \\\n" +
                               "  -interval 1h > /data/local/tmp/feedsync.log 2>&1 &\n\n" +
                               "# 2. 手动触发一次立即同步\n" +
                               "/data/local/tmp/z6x_feedsync -sync-now -config /data/local/tmp/feeds.yaml\n\n" +
                               "# 3. 查看同步与任务推送日志\n" +
                               "tail -n 10 /data/local/tmp/feedsync.log",
                        ExpectedOutput = "[FeedSync] Parsed 24 items from 'https://feed.example.com/anime.xml'\n[Filter] Matched 1 new item: '[Group] Anime Ep.12 [1080p].torrent'\n[Aria2] Pushed GID: 2089b0a1d4 -> Status: Active"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "外网 RSS 证书校验问题：某些私有追更源使用自签名证书，服务支持配置 `insecure_skip_verify: true` 避免因 Android 系统根证书缺失导致拉取失败。",
                    "夜间休眠网络断开保护：若极米息屏后进入深睡眠，定时器可能延后唤醒，属于正常休眠省电策略，无需额外干预。"
                ]
            }
        ]
    };
}
