using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class DmVerityFilesystemReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "dm-verity-filesystem-reference",
        Title = "33. 设备映射与只读文件系统保护（dm-verity / ro mount / remount失败）",
        Group = "设备接入",
        Summary = "剖析 Linux Device Mapper 校验机制（dm-1）、只读挂载与 adb remount 报错根因。",
        Sections =
        [
            new ContentSection
            {
                Heading = "dm-verity 块设备映射实测",
                Text = "查看系统 vendor 与 product 分区如何通过 device-mapper 挂载为只读节点：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看底层块设备映射类型",
                        Code = "mount | grep -E \"dm-|/vendor|/product\" | head -n 4",
                        ExpectedOutput =
                            "/dev/block/dm-1 on /vendor type ext4 (ro,seclabel,relatime,inode_readahead_blks=8)   # dm-1 表示启用了 dm-verity 哈希树校验\n" +
                            "dev/block/by-name/product on /product type ext4 (ro,seclabel,relatime)              # product 只读挂载"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "adb remount 报错根因分析",
                Text = "解释为什么无法直接修改 `/system` 或 `/vendor` 内置文件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "尝试重新挂载只读分区为读写",
                        Code = "mount -o remount,rw /vendor 2>&1 || adb remount",
                        ExpectedOutput = "mount: '/vendor' not in /proc/mounts OR Permission denied / dm-verity is enabled"
                    }
                ],
                BulletPoints =
                [
                    "dm-verity 机制：内核启动时校验文件系统哈希树根（Root Hash）。若未禁用 verity 强制写入，系统检测到哈希不匹配会直接触发内核崩溃重启。",
                    "Z6X Pro 未解锁 Bootloader（`ro.boot.flash.locked=1`），无法通过 `adb disable-verity` 关闭保护。"
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "无论是 SSH（UID 10068）还是 ADB（UID 2000），均无法绕过内核级 dm-verity 强行修改 `/system` 与 `/vendor` 分区；修改系统必须采用免 Root 挂载方案（如 `mount -o bind` 在 root 具备条件下，或直接通过 ADB 调试命令）。"
                ]
            }
        ]
    };
}
