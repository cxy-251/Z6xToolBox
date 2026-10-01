using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class CrashBlackboxLogRecorderData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "crash-blackbox-log-recorder",
        Title = "8. 自研系统日志环形缓冲与崩溃黑匣子：固定内存池与断电落盘（Z6X RustLogBox）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 自研的固定尺寸环形内存日志收集器，实时捕获 Linux 内核 dmesg 与 logcat 关键崩溃堆栈，系统发生异常崩溃时秒级落盘存证。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Android TV 系统自带的 logcat 缓冲区容量很小（仅几十 KB 到几百 KB），当极米发生突然死机、黑屏重启或前台应用严重崩溃时，关键的错误堆栈与崩溃原因瞬间就会被后续刷屏日志覆盖冲刷，用户和开发者完全无法查清究竟是内核 Panic、驱动崩溃还是 OOM 强杀。\n\n" +
                       "使用 Rust 自研黑匣子守护进程后，在物理内存中开辟一块固定 2MB 的无锁环形缓冲区（RingBuffer），持续监听内核与关键系统日志。一旦捕获到致命信号（SIGSEGV、SIGBUS）或系统重启事件，立即将最后的 1000 行核心日志强制刷写到外接 U 盘，为系统故障排查提供关键黑匣子记录。",
                BulletPoints =
                [
                    "精准追溯死机重启原因：系统崩溃最后一刻的关键调用栈永久存盘，不再丢失。",
                    "内存占用恒定不增长：固定尺寸环形内存池覆盖写入，杜绝日志膨胀撑爆内存。",
                    "零闪存磨损：日常运行全部在内存循环读写，仅在检测到故障告警时才真正落盘。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + 纯 Rust 固定容量无锁环形队列（单二进制体积约 650KB）。",
                    "什么是内存固定环形缓冲（RingBuffer）？分配一块固定大小的静态内存数组。最新日志按指针顺次写入，存满后自动回到开头覆盖最旧日志，写入时间复杂度为 O(1)，永远不触发堆重分配，保障长期运行内存占用恒定不变（严格限定在 2MB 以内）。",
                    "内核 dmesg 与 logcat 双通道采集：后台通过读取 `/proc/kmsg`（内核日志流）以及管道重定向 `/system/bin/logcat -b crash,system`，过滤并捕获系统级关键事件。",
                    "【参考开源项目】pstore / ramoops（Linux 内核崩溃内存保留机制实现参考）；slog（Rust 结构化高性能日志框架）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动黑匣子日志监听：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动崩溃黑匣子守护进程",
                        Code = "# 1. 启动黑匣子服务（固定 2MB 环形内存，崩溃落盘指向 U 盘）\n" +
                               "nohup /data/local/tmp/z6x_logbox \\\n" +
                               "  -buf 2M \\\n" +
                               "  -dump /storage/XXXX-XXXX/CrashLogs > /data/local/tmp/logbox.log 2>&1 &\n\n" +
                               "# 2. 本地模拟向黑匣子发送一次手动快照指令\n" +
                               "kill -USR1 $(pidof z6x_logbox)",
                        ExpectedOutput = "[LogBox] Snapshot saved: /storage/XXXX-XXXX/CrashLogs/dump_20261001_1550.log (1000 lines)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "黑匣子排坑指南：",
                BulletPoints =
                [
                    "问题 1：`/proc/kmsg` 权限拒绝。在非 Root shell 下，普通用户可能被禁止直读内核 ring buffer。Rust 服务会自动降级为调用系统的 `/system/bin/dmesg` 工具捕获最新内核错误，保障在受限权限下依然可用。",
                    "问题 2：突然断电导致落盘失败。落盘文件句柄务必显式调用 `fsync()` 系统调用，强制内核将脏页立刻写入 U 盘物理介质，防止断电丢失。"
                ]
            }
        ]
    };
}
