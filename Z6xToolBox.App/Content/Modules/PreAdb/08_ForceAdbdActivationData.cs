using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class ForceAdbdActivationData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "force-adbd-activation",
        Title = "8. 底层属性突破与强开网络 ADB",
        Group = "设备接入",
        Summary = "分析 SimpleSSHD 普通权限局限，通过系统底层属性强开 5555 端口，并提供 ADB 常用完整操作命令集。",
        Sections =
        [
            new ContentSection
            {
                Heading = "SimpleSSHD 的局限性与突破思路",
                Text = "通过 U 盘安装并启动 SimpleSSHD 后，虽然能远程连入电视 Linux Shell，但由于其运行在普通应用沙箱（UID 10068），很多系统操作无法执行：\n\n" +
                       "• 执行 pm uninstall 卸载自带软件时，Android 12 校验调用者身份为 null，抛出 java.lang.NullPointerException 崩溃。\n" +
                       "• 执行 am start 启动组件时，系统校验 UID 不匹配，抛出 SecurityException 权限拒绝。\n\n" +
                       "排查系统底层属性时，发现极米系统已经将底层网络 ADB 的配置预埋好了：\n" +
                       "1. service.adb.tcp.port 属性值预设为 5555；\n" +
                       "2. ro.adb.secure 属性值为 0（意味着免除了弹出 RSA 密钥授权对话框的步骤！）；\n" +
                       "3. 只需在 SimpleSSHD shell 中向系统发送属性指令启动 adbd 守护进程，即可直接对外开放 5555 调试端口。"
            },
            new ContentSection
            {
                Heading = "强开网络 ADB 端口实操",
                Text = "在 SimpleSSHD 终端中执行以下指令拉起 adbd 服务：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看底层 ADB 相关属性配置",
                        Code = "getprop service.adb.tcp.port && getprop ro.adb.secure",
                        ExpectedOutput =
                            "5555   # 系统默认配置的网络调试端口\n" +
                            "0      # 关闭了 adb 安全认证弹窗限制，连接直接放行"
                    },
                    new CodeBlock
                    {
                        Label = "触发系统启动 adbd 守护进程",
                        Code = "setprop ctl.start adbd\n# 或极米特定私有属性：\nsetprop xgimi.remoteDebug.on 1",
                        ExpectedOutput = "（后台 adbd 进程拉起，5555 端口开始监听）"
                    },
                    new CodeBlock
                    {
                        Label = "在 Steam Deck 终端执行网络 ADB 连接",
                        Code = "adb connect 192.168.0.109:5555",
                        ExpectedOutput = "connected to 192.168.0.109:5555   # 连接成功"
                    },
                    new CodeBlock
                    {
                        Label = "核对获得的调试身份权限",
                        Code = "adb -s 192.168.0.109:5555 shell id",
                        ExpectedOutput = "uid=2000(shell) gid=2000(shell) context=u:r:shell:s0   # 获得系统 shell 权限，具备卸载与控制能力"
                    }
                ]
            }
        ]
    };
}
