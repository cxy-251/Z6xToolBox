using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class EmbeddedFtsMediaIndexData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "embedded-fts-media-index",
        Title = "27. 自研嵌入式 SQLite 元数据管理与轻量全文检索工具：FTS5 倒排索引（Z6X RustSqliteIndex）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 静态链接嵌入式 SQLite FTS5 的轻量全文检索工具，专为 U 盘数万本地影视音乐建立秒级搜索索引，常驻内存仅 3MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接大容量 U 盘或移动硬盘（如 2TB ~ 4TB）时，内部常存放上万部影视、音乐和文档文件。Android 系统自带的 MediaScanner 扫描慢、经常漏扫且生成庞大的 `.db` 数据库耗尽内部存储；第三方带界面的媒体中心（如 Kodi、Plex）内存常达数百兆，在 3.5GB 内存设备上极易引起卡顿。\n\n" +
                       "使用 Rust 自研的轻量索引工具，静态内嵌 SQLite 3 引擎与 FTS5 全文检索模块，对移动存储的文件路径、ID3 标签、NFO 影视信息进行快速增量扫描并建立倒排索引。索引建立与查询全过程毫秒级响应，查询时物理常驻内存仅约 3MB。",
                BulletPoints =
                [
                    "毫秒级全文检索：基于 SQLite FTS5 倒排索引，在上万条记录中搜索耗时 < 3ms。",
                    "低开销单文件落地：单个 Rust 静态可执行文件（约 2MB），无需 JVM 庞大运行时。",
                    "增量监听更新：结合 inotify 系统调用，文件增删后自动同步索引，避免全盘重扫。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `rusqlite`（编译启用 `bundled` 与 `bundled-fts5` 特性）+ `walkdir` 快速流式遍历。",
                    "分词与中文匹配支持：内置轻量 Unicode61 分词器与三元组（trigram）模糊匹配，支持拼音首字母与关键词片段查找。",
                    "只读查询内存锁定：搜索时采用只读连接（`SQLITE_OPEN_READONLY`）与微型缓存页大小（`cache_size = -2000`），将内存占用锁定在 3MB 以内。",
                    "【参考开源项目】rusqlite（Rust 官方生态主流 SQLite 封装）；ripgrep（高性能遍历与搜索工具，其遍历优化思路）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "执行 U 盘媒体增量建库与毫秒级模糊查询：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行本地媒体建库与毫秒级模糊检索",
                        Code = "# 1. 扫描 U 盘电影目录并构建轻量 FTS5 数据库\n" +
                               "/data/local/tmp/z6x_media_index \\\n" +
                               "  -action index \\\n" +
                               "  -src /mnt/media_rw/USB_DISK/Movies \\\n" +
                               "  -db /data/local/tmp/media.db\n\n" +
                               "# 2. 执行拼音首字母或中文模糊搜索测试\n" +
                               "/data/local/tmp/z6x_media_index \\\n" +
                               "  -action query \\\n" +
                               "  -db /data/local/tmp/media.db \\\n" +
                               "  -keyword \"Interstellar\"",
                        ExpectedOutput = "[FTS5] Indexed 4,820 media files in 1.45s (DB size: 3.2MB)\n[Result #1] /mnt/media_rw/USB_DISK/Movies/Interstellar.2014.2160p.mkv\n[Time] Query finished in 1.8ms"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "数据库文件存储位置：数据库文件必须放置在机身内部存储（如 `/data/local/tmp/`），严禁直接放在 FAT32/exFAT 格式的 U 盘上，以避免 FAT 文件系统对 SQLite WAL 锁机制支持不全导致死锁。",
                    "内存缓存大小约束：通过 SQLite PRAGMA `cache_size = 500` 将页缓存限制在 2MB 以内，防止大文件建库时堆内存增长。"
                ]
            }
        ]
    };
}
