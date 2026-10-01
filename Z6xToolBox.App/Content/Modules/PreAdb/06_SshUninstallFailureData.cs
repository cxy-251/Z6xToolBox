using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class SshUninstallFailureData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "ssh-uninstall-failure",
        Title = "6. SSH 尝试删除自带软件报错（转向 ADB 的原因）",
        Group = "设备接入",
        Summary = "记录在 SSH 中执行 pm uninstall 报错 NullPointerException 与 am 报 SecurityException，说明为何无法用 SSH 精简软件而必须转向 ADB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "尝试在 SSH 中删除极米自带预装",
                Text = "在连上 SimpleSSHD 查明设备有 57 个极米预装应用后，我们首先尝试在 SSH 终端里直接用 pm uninstall 指令卸载无用应用，以清理后台与存储空间。"
            },
            new ContentSection
            {
                Heading = "pm uninstall 报错空指针崩溃与根因",
                Text = "在 SSH 终端执行卸载命令时，系统直接抛错中断：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "在 SSH 中执行卸载自带应用命令",
                        Code = "pm uninstall -k --user 0 com.xgimi.minitvfactory",
                        ExpectedOutput =
                            "Exception occurred while dumping:\n" +
                            "java.lang.NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference\n" +
                            "  at com.android.server.appop.AppOpsService.checkPackage(AppOpsService.java:3212)\n" +
                            "  at com.android.server.pm.PackageInstallerService.uninstall(PackageInstallerService.java:1011)\n" +
                            "  # 报错根因: SimpleSSHD 运行在普通沙箱（UID 10068），不是 root 也不是 shell\n" +
                            "  # Android 12 校验调用方包名为空，直接抛出空指针异常崩溃",
                        Note = "这证明在普通应用沙箱内无法调用 PackageInstallerService 的卸载接口。"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "am start 报错权限拒绝与转向 ADB",
                Text = "尝试在 SSH 中用 am 命令拉起组件同样被系统拒绝：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "在 SSH 中尝试用 am 启动隐藏设置组件",
                        Code = "am start -n com.android.settings/.Settings",
                        ExpectedOutput =
                            "java.lang.SecurityException: Permission Denial: start at ... from pid=... uid=10068 not allowed\n" +
                            "  # 报错根因: am 脚本内部指定调用者身份为 com.android.shell (UID 2000)\n" +
                            "  # 系统内核校验 SimpleSSHD 的真实 UID 10068 与声明的 2000 不一致，强制拒绝",
                        Note = "结论：SSH 终端无法删除任何自带应用，也无法随意启动受保护组件。要完全接管系统，必须拿到真正的 shell (UID 2000) 权限，这是我们必须寻找开启 ADB 的核心原因。"
                    }
                ]
            }
        ]
    };
}
