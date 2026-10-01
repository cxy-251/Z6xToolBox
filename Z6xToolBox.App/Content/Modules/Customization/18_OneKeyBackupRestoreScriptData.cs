using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class OneKeyBackupRestoreScriptData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "one-key-backup-restore-script",
        Title = "18. 一键定制固化与原厂灾备恢复脚本体系",
        Group = "深度定制",
        Summary = "固化全机 31 个组件精简、桌面接管与无障碍注入规则，提供即时可逆的一键应用与出厂恢复自动化脚本。",
        Sections =
        [
            new ContentSection
            {
                Heading = "自动化脚本设计与分工",
                Text = "为保障系统升级或重置后的可维护性，编写了两个相互对冲的独立 Shell 脚本：",
                BulletPoints =
                [
                    "z6x_debloat_apply.sh：一键卸载官方桌面与推荐流、批量冻结 29 个后台伴生包、注入 Projectivy 无障碍服务并校验桌面焦点。",
                    "z6x_debloat_restore.sh：一键重新挂载被卸载的系统应用（install-existing）、解除所有冻结状态并还原官方无障碍与启动器配置。"
                ]
            },
            new ContentSection
            {
                Heading = "实机执行指令与调用规范",
                Text = "脚本位于宿主机运行目录，赋予执行权限后可直接调用：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行一键定制固化",
                        Code = "chmod +x z6x_debloat_apply.sh && ./z6x_debloat_apply.sh 192.168.0.109:5555",
                        ExpectedOutput = "=== 正在连接设备: 192.168.0.109:5555 ===\n" +
                                         "=== 1. 卸载官方桌面与流媒体推荐 ===\n" +
                                         "=== 2. 批量停用广告、OTA、IoT与冗余伴生组件 ===\n" +
                                         "=== 3. 注入并激活 Projectivy 无障碍服务 ===\n" +
                                         "=== 定制固化完成 ==="
                    },
                    new CodeBlock
                    {
                        Label = "执行一键原厂状态恢复",
                        Code = "chmod +x z6x_debloat_restore.sh && ./z6x_debloat_restore.sh 192.168.0.109:5555",
                        ExpectedOutput = "=== 1. 重新挂载官方桌面与流媒体包 ===\n" +
                                         "=== 2. 批量重新启用全部系统组件 ===\n" +
                                         "=== 3. 恢复原生无障碍服务配置 ===\n" +
                                         "=== 恢复操作完成 ==="
                    }
                ]
            },
            new ContentSection
            {
                Heading = "开机校准微调弹窗权限边界解析",
                Text = "针对开机后短暂停留的极米硬件梯形校正提示弹窗排查结论：",
                BulletPoints =
                [
                    "组件归属：该弹窗由特权包 com.android.newsettings 内部的 BootBroadcastReceiver 接收开机广播触发。",
                    "权限限制：Android 12 安全机制严格限制普通 shell（UID 2000）禁用 platform 签名特权包内的单个组件，强制禁用会抛出 SecurityException。",
                    "最佳处理：依赖已激活的 Projectivy 无障碍服务在开机完成后强制将第三方桌面置顶覆盖，或由遥控器按一次返回键直接关闭。"
                ]
            }
        ]
    };
}
