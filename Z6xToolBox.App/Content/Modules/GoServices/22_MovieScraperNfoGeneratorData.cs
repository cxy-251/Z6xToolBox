using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class MovieScraperNfoGeneratorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "movie-scraper-nfo-generator",
        Title = "22. 自研影视刮削与 NFO 自动生成器：目录监听与 TMDB 元数据补全（Z6X Scraper）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的轻量媒体刮削探针，监听 U 盘下载目录，检测到新增视频自动调用 TMDB 抓取海报、简介并生成标准 .nfo 元数据文件。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "通过离线下载或手动拷入 U 盘的电影和美剧文件，通常命名为带有发布组后缀的复杂字符串（如 `Movie.Name.2024.1080p.WEB-DL.x265...`）。电视上的播放器（如 Kodi、Nova Player）直接打开时只能看到文件名，没有海报封面、剧情简介与演职员名单。\n\n" +
                       "自研该刮削探针后，极米在后台静默监听 U 盘目录。一旦下载完成新增视频，探针自动通过正则表达式提取标题与年份，调用 TMDB API 下载对应的高清电影海报、背景图并生成同名 `.nfo` 文件。任何播放器打开直接呈现精美电影海报墙。",
                BulletPoints =
                [
                    "自动清洗混乱文件名：智能正则提取电影名、剧集季数与集数。",
                    "自动补齐海报墙与元数据：下载同名 poster.jpg 与符合 Kodi/Jellyfin 标准的 `.nfo` XML 文件。",
                    "静默无人值守：配合离线下载服务，下载完成即刻自动刮削，开机即看海报墙。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `fsnotify/fsnotify`（Linux inotify 目录监听）+ TMDB v3 REST API（单文件体积约 5MB）。",
                    "什么是 inotify 目录事件监听？Linux 内核提供的文件系统事件监控机制。Go 协程监听 U 盘下载目录，当检测到 `IN_CLOSE_WRITE`（写入关闭，代表下载真正完成）时才触发刮削任务，避免在文件还在下载写盘过程中误触发。",
                    "什么是 Kodi 兼容的 NFO 标准？国际通用的纯文本 XML 格式媒体元数据。内容包含 `<movie><title>...</title><plot>...</plot><rating>...</rating></movie>`，所有主流本地播放器均优先读取本地 NFO 避免重复向外网抓取。",
                    "【参考开源项目】go-tmdb（参考其针对 TheMovieDb API 的类型封装与请求处理）；tinyMediaManager（桌面刮削工具元数据组织规范参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动影视刮削守护进程：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动自动刮削守护进程",
                        Code = "# 1. 启动刮削探针（监听 U 盘下载目录，指定 TMDB API Key）\n" +
                               "nohup /data/local/tmp/z6x_scraper \\\n" +
                               "  -watch /storage/XXXX-XXXX/Downloads \\\n" +
                               "  -apikey \"YOUR_TMDB_API_KEY\" \\\n" +
                               "  -lang zh-CN > /data/local/tmp/scraper.log 2>&1 &\n\n" +
                               "# 2. 本地模拟手动触发单视频刮削测试\n" +
                               "/data/local/tmp/z6x_scraper -file \"/storage/XXXX-XXXX/Downloads/Inception.2010.mp4\"",
                        ExpectedOutput = "[Scraper] Matched: 盗梦空间 (2010)\n[Scraper] Saved: Inception.2010.nfo\n[Scraper] Saved: Inception.2010-poster.jpg"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "刮削服务排坑指南：",
                BulletPoints =
                [
                    "问题 1：TMDB API 域名在内网连接超时（DNS 污染）。原因与解决：国内网络访问 `api.themoviedb.org` 经常超时。可在 Go 后端内置反向代理配置或 Hosts 映射指向可靠 CDN 节点，确保刮削请求稳定。",
                    "问题 2：同名电影匹配错误。正则表达式应支持识别括号内的年份 `(2024)` 与发布组特征，并在匹配多个候选结果时默认按发行年份最接近的一项精确命中。"
                ]
            }
        ]
    };
}
