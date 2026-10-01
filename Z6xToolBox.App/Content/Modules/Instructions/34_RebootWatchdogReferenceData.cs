using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class RebootWatchdogReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "reboot-watchdog-reference",
        Title = "34. 硬件看门狗与电源重置模式（reboot / watchdog / sysrq）",
        Group = "设备接入",
        Summary = "讲解投影仪看门狗硬件保护机制、reboot 参数（关机/恢复模式）与终端控制命令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "系统重启与关机参数实测（reboot）",
                Text = "实测 toybox reboot 支持的参数与引导指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "常用重启与关机指令",
                        Code = "reboot           # 正常软重启回到 GMUI 系统\n" +
                               "reboot -p        # 关机并切断光机与主板电源（Power off）\n" +
                               "reboot recovery  # 重启并进入 Recovery 模式进行双清或卡刷",
                        ExpectedOutput = "# 执行后 ADB 链路主动断开，设备进入指定硬件电源状态"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "硬件看门狗节点（/dev/watchdog）",
                Text = "投影仪高热或主芯片死锁时的自救硬件通道：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看硬件看门狗设备节点",
                        Code = "ls -la /dev/watchdog*",
                        ExpectedOutput = "crw------- 1 root root 10, 130 /dev/watchdog   # 硬件看门狗字符设备，由 init 或内核线程周期喂狗"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`reboot`：SSH（UID 10068）无权调用系统重启（抛出 Permission denied 或被 SELinux 拒绝）；ADB（UID 2000 拥有 `android.permission.REBOOT`）可正常执行重启与关机。"
                ]
            }
        ]
    };
}
