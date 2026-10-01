using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class LogcatBufferRotationReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "logcat-buffer-rotation-reference",
        Title = "36. 日志环形缓冲区与崩溃抓取（logcat -g / -G / -b crash）",
        Group = "设备接入",
        Summary = "实测 Android 日志环形缓冲区容量（main/system/crash/kernel）、动态扩容与精准抓取崩溃堆栈。",
        Sections =
        [
            new ContentSection
            {
                Heading = "日志环形缓冲区容量实测（logcat -g）",
                Text = "实测极米 Z6X Pro 各通道环形缓冲区分配情况：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询所有缓冲区当前用量与上限",
                        Code = "logcat -g",
                        ExpectedOutput =
                            "main: ring buffer is 2 MiB (1 MiB consumed, 8 MiB readable)\n" +
                            "system: ring buffer is 2 MiB (512 KiB consumed, 343 KiB readable)\n" +
                            "crash: ring buffer is 2 MiB (0 B consumed, 0 B readable)   # 崩溃专用通道\n" +
                            "kernel: ring buffer is 2 MiB (1 MiB consumed, 17 MiB readable) # 内核日志通道"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "缓冲区动态扩容与清空（logcat -G / -c）",
                Text = "在高频日志输出时防止关键崩溃日志被快速顶替冲刷：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "临时扩大缓冲区至 16MB 并清空历史数据",
                        Code = "logcat -c && logcat -G 16M",
                        ExpectedOutput = "# 执行后所有日志通道扩充至 16 MiB 环形容量"
                    },
                    new CodeBlock
                    {
                        Label = "仅导出最近一次原生或 Java 崩溃堆栈",
                        Code = "logcat -b crash -d",
                        ExpectedOutput = "# 无输出表示当前系统暂无捕获到 Fatal Exception 崩溃"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`logcat`：SSH（UID 10068）受限，只能读取属于本 App 的日志，无法查看 `system` 与 `kernel` 缓冲区；ADB（UID 2000）拥有完整日志缓冲区读取权。",
                    "`logcat -c` 与 `logcat -G`：SSH 执行报权限不足；ADB 具备清空与动态调整缓冲区大小的权限。"
                ]
            }
        ]
    };
}
