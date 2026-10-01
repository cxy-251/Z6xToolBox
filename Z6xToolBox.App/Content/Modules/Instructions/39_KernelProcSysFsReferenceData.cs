using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class KernelProcSysFsReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "kernel-proc-sysfs-reference",
        Title = "39. 内核虚拟文件系统与句柄限制（file-nr / entropy_avail / printk）",
        Group = "设备接入",
        Summary = "实测系统文件句柄分配极限（file-nr）、随机数发生器熵池（entropy_avail）与内核日志级别。",
        Sections =
        [
            new ContentSection
            {
                Heading = "系统文件句柄占用实测（/proc/sys/fs/file-nr）",
                Text = "排查高并发网络应用报 `Too many open files` 异常：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看已分配文件句柄与内核最大支持上限",
                        Code = "cat /proc/sys/fs/file-nr",
                        ExpectedOutput =
                            "12768	0	345112\n" +
                            "# 第1列（12768）：系统当前已分配打开的文件句柄总数\n" +
                            "# 第2列（0）：已分配但未使用的句柄数\n" +
                            "# 第3列（345112）：Linux 内核允许的最大文件句柄总上限"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "随机数熵池健康度（entropy_avail）",
                Text = "排查 TLS/SSL 密钥生成与加密连接握手时延：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "读取当前可用内核熵池字节数",
                        Code = "cat /proc/sys/kernel/random/entropy_avail",
                        ExpectedOutput = "256   # 默认内核硬件随机器保底熵值，保证加密随机数生成不阻塞"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`/proc/sys/*` 大多数状态只读节点：SSH（UID 10068）与 ADB（UID 2000）均可读取；但写入修改（例如调节 `file-max` 或 `printk`）均需要 Root 权限。"
                ]
            }
        ]
    };
}
