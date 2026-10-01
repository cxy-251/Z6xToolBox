using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class FileDeduplicationHardlinkOptimizerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "file-deduplication-hardlink-optimizer",
        Title = "57. 自研 U 盘跨目录重复文件排查与硬链接去重器：哈希抽样（Z6X HardlinkOpt）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的文件查重与硬链接去重工具，通过快速分段哈希扫描 U 盘中重复的剧集或照片并建立硬链接节省空间，常驻内存仅 12MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接移动硬盘或 U 盘在使用时间较长后，用户经常在不同文件夹（如下载目录、电影分类目录、电视剧合集）中重复拷贝同一份几十 GB 的高清蓝光原盘或无损音乐，无谓浪费宝贵的外置存储空间。\n\n" +
                       "使用 Go 自研的轻量文件去重服务，对指定存储目录进行智能两阶段扫描：先按文件大小精准分组，对相同大小的文件仅抽取头、中、尾特定 16KB 数据块计算哈希；确认完全一致后，通过 Linux 底层 `link` 系统调用建立硬链接（Hardlink）并删除重复实体。既保留不同路径下的文件可见性，又释放出重复占用的物理扇区，常驻物理内存约 12MB。",
                BulletPoints =
                [
                    "分段采样快速排重：无需读取几十 GB 的全量文件即可毫秒级锁定重复项。",
                    "硬链接空间无损释放：两处路径均可正常索引播放，物理存储空间立减一半。",
                    "支持只读模拟运行（Dry-Run）：先输出预计节省空间报表，确认无误再执行。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `syscall.Link` + `cespare/xxhash/v2`（快速非加密哈希算法）。",
                    "两阶段筛查算法：第一阶段对比 `os.FileInfo.Size()`，大小不同直接排除；第二阶段对相同大小的文件进行 xxHash 增量比对，避免大文件 I/O 跑满。",
                    "同 Inode 跳过机制：通过比对 `syscall.Stat_t.Ino`，已建立过硬链接的同源文件直接忽略，防止重复计算。",
                    "【参考开源项目】rmlint（Linux 极速重复文件查找工具）；jdupes（经典硬链接查重工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "执行 U 盘目录重复文件扫描并查看报告：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行重复文件排查与硬链接替换测试",
                        Code = "# 1. 模拟扫描 U 盘中的视频目录（仅输出重复清单与可节省空间）\n" +
                               "/data/local/tmp/z6x_dedup -dir /mnt/media_rw/USB_DISK/Movies -dry-run\n\n" +
                               "# 2. 执行真正的硬链接去重优化\n" +
                               "/data/local/tmp/z6x_dedup -dir /mnt/media_rw/USB_DISK/Movies -link",
                        ExpectedOutput = "[Scan] Indexed 1,240 files across 84 folders in 1.8s\n[Duplicate Found] 14 pairs of duplicate files (Total: 48.2 GB)\n[Hardlink] Linked /Movies/Action/Film.mkv -> /Downloads/Film.mkv\n[Result] Successfully reclaimed 48.2 GB physical space."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "文件系统跨卷与格式限制：硬链接不能跨物理分区建立，且 FAT32/exFAT 文件系统本身不支持硬链接（返回 `EPERM`），此工具仅适用于格式化为 ext4 或 NTFS 格式的外置存储介质。",
                    "写时修改影响：建立硬链接后修改其中一个文件会同步影响另一路径，通常针对只读播放的静态音视频文件使用。"
                ]
            }
        ]
    };
}
