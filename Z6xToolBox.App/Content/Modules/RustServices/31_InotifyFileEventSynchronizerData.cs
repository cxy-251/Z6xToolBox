using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class InotifyFileEventSynchronizerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "inotify-file-event-synchronizer",
        Title = "31. 自研轻量级 inotify 增量文件变动监听器：内核事件驱动触发（Z6X RustInotifySync）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的 Linux inotify 目录增量变动监听守护器，实时捕获下载完成与 U 盘插拔写入，常驻内存仅 700KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端下载目录或外接 U 盘新增电影、音乐或字幕文件时，传统的 Python 或 Shell 脚本多采用定时 `find` 或 `ls` 轮询对比。这种方式对大存储介质极其消耗磁盘 I/O 和 CPU，还会让外接机械硬盘无法休眠。\n\n" +
                       "使用 Rust 自研的轻量文件事件监听器，直接基于 Linux 内核 `inotify_init1` 与 `inotify_add_watch` API，当且仅当发生 `IN_CLOSE_WRITE` 或 `IN_MOVED_TO` 时，由内核事件驱动唤醒，微秒级执行刮削触发或转码触发。全过程无磁盘无谓扫描，常驻物理内存仅约 700KB。",
                BulletPoints =
                [
                    "内核驱动零轮询：无变动时线程挂起，不占用任何磁盘 I/O 读写与 CPU 周期。",
                    "精准事件防误判：只在文件完全写入闭合后触发通知，杜绝下载中途误读半截损坏文件。",
                    "轻量 IPC 事件分发：支持将变动事件通过 UDP/Unix Socket 直发给上层 Go 刮削服务。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `inotify` 裸系统调用封装（二进制体积仅 600KB）。",
                    "事件聚合去重与防抖：内置微型时间窗口（如 500ms），将同一文件的连续写入事件合并为单次有效触发，防止事件风暴。",
                    "递归目录追踪：利用固定深度栈自动维护新创建子目录的 watch descriptor，无需加载第三方文件监控框架。",
                    "【参考开源项目】inotify-tools（Linux 经典 inotify 命令行工具）；notify-rs（Rust 跨平台文件监听库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动文件事件监听器并测试写闭合触发：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 inotify 文件变动监听守护进程",
                        Code = "# 1. 启动监听守护进程（监控 U 盘下载目录，触发脚本回调）\n" +
                               "nohup /data/local/tmp/z6x_inotify \\\n" +
                               "  -watch /mnt/media_rw/USB_DISK/Downloads \\\n" +
                               "  -events IN_CLOSE_WRITE,IN_MOVED_TO \\\n" +
                               "  -exec \"/data/local/tmp/trigger_scraper.sh\" > /data/local/tmp/inotify.log 2>&1 &\n\n" +
                               "# 2. 查看文件就绪事件捕获日志\n" +
                               "cat /data/local/tmp/inotify.log",
                        ExpectedOutput = "[inotify] Watching: /mnt/media_rw/USB_DISK/Downloads (recursive)\n[Event] IN_CLOSE_WRITE detected: 'Movie.2024.1080p.mkv' -> triggered script"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "max_user_watches 上限：Android 内核默认每个用户的 inotify watch 数量有限（如 8192），如果监控数万文件的根目录可能报错 `ENOSPC`，需通过 `echo 65536 > /proc/sys/fs/inotify/max_user_watches` 扩大配额。",
                    "跨文件系统移动限制：从机身内部存储 `mv` 到外置 U 盘并非原子性 rename 操作，而是 copy+delete，监听器需捕获目标目录的 `IN_CLOSE_WRITE` 而非仅仅关注 `IN_MOVED_TO`。"
                ]
            }
        ]
    };
}
