using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class EpgXmlTvCrawlerAggregatorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "epg-xml-tv-crawler-aggregator",
        Title = "59. 自研电视节目指南 XMLTV 定时抓取与生成服务：EPG 节目单补齐（Z6X EpgHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型 EPG 节目预告定时生成服务，每日自动抓取央视与卫视节目单并输出 XMLTV/Gz 格式供电视播放器调用，常驻内存仅 12MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端直播播放软件（TiviMate/Kodi）需要绑定 EPG（电子节目指南）链接才能在选台界面看到当前正在播放什么节目、下一节目预告以及节目回看时间表。公网公共 EPG 地址经常解析超时、被运营商劫持或者节目单严重滞后。\n\n" +
                       "使用 Go 自研的轻量 EPG 生成服务，在极米后台每天凌晨自动从官方开放源（如央视网、各大卫视公开接口）拉取最新 7 天节目表。在本地快速组装为符合 XMLTV 国际标准的 `.xml` 及 Gzip 压缩文件，对外提供本地订阅地址，常驻物理内存约 12MB。",
                BulletPoints =
                [
                    "电视播放器节目单秒出：本地直链拉取，无需请求慢速公网服务器。",
                    "自动对齐频道名称：支持别名智能映射（如将 `CCTV1高清` 自动关联到 `CCTV-1`）。",
                    "Gzip 压缩分发节约带宽：压缩后仅占数百 KB，电视播放器瞬间下载加载完毕。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `encoding/xml` 流式生成器 + Gzip 纯内存快速压缩。",
                    "XMLTV 标准格式规范：严密构造 `<tv generator-info-name=\"z6x_epg\">`、`<channel id=\"...\">` 与 `<programme start=\"...\" stop=\"...\">` 节点树，符合广电与国际开源标准。",
                    "增量缓存与去重：只抓取未来 7 天新增条目，已过期历史条目自动从 XML 中剔除，保持文件体积轻量。",
                    "【参考开源项目】epg-grabber（开源节目单抓取工具）；xmltv（XMLTV 官方标准规范定义）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "手动触发一次 EPG 生成并拉取验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行 EPG 节目单抓取与本地订阅验证",
                        Code = "# 1. 手动抓取主流频道节目单并生成 gzip 压缩文件\n" +
                               "/data/local/tmp/z6x_epg -fetch -out /data/local/tmp/epg.xml.gz\n\n" +
                               "# 2. 查看生成的文件大小与内容头\n" +
                               "ls -lh /data/local/tmp/epg.xml.gz && zcat /data/local/tmp/epg.xml.gz | head -n 15",
                        ExpectedOutput = "-rw-r--r-- 1 shell shell 284K 10月 1日 16:48 epg.xml.gz\n<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"z6x_epg\">\n  <channel id=\"CCTV1\">\n    <display-name>CCTV-1 综合</display-name>\n  </channel>\n[EPG] Generated 12,840 programme entries across 82 channels in 4.8s"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "时区与时间偏移转换：国内节目单接口为东八区北京时间（+0800），XMLTV 节点中的时间戳格式需严格遵循 `YYYYMMDDhhmmss +0800` 格式，防止播放器时间错位导致预告显示不准。",
                    "内存中 XML DOM 树防膨胀：写入时必须使用 `xml.NewEncoder` 边解析边写入输出流，严禁在内存中堆叠全量数百兆的大对象。"
                ]
            }
        ]
    };
}
