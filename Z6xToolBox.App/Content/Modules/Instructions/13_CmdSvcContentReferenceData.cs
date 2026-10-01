using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class CmdSvcContentReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "cmd-svc-content-reference",
        Title = "13. 服务调度与行为管控（cmd / svc / content / appops）",
        Group = "设备接入",
        Summary = "讲解 Android 框架层服务接口 cmd、底层硬件开关 svc、数据提供者 content 与应用后台行为管控工具 appops。",
        Sections =
        [
            new ContentSection
            {
                Heading = "cmd（Android 系统服务通用调用接口）",
                Text = "cmd 是 Android 7+ 引入的直接与各系统 Binder 服务通信的高效入口（需 ADB shell 权限）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "强制对指定应用执行 AOT 全量预编译（大幅提升 TV 端运行速度）",
                        Code = "cmd package compile -m speed -f com.github.catvod\n" +
                               "# 参数解析:\n" +
                               "# -m speed: 采用最高等级 AOT 全量预编译（将字节码直接编译为机器码）\n" +
                               "# -f: 强制重新编译",
                        ExpectedOutput = "Success"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统所有已安装的运行时覆盖（RRO Overlay）资源层",
                        Code = "cmd overlay list",
                        ExpectedOutput =
                            "com.android.theme.iconpack.rounded: [ ]   # 未激活的图标主题包\n" +
                            "com.android.internal.systemui.navbar.gestural: [x]   # 已启用的手势导航层"
                    },
                    new CodeBlock
                    {
                        Label = "展开或收起电视系统通知栏",
                        Code = "cmd statusbar expand-notifications   # 展开通知栏面板\n" +
                               "cmd statusbar collapse               # 收起通知栏面板"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "svc（基础电源与网络硬件状态控制）",
                Text = "svc 提供免图形界面的基础硬件开关（需 ADB shell 权限）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启停电视 Wi-Fi 硬件连接",
                        Code = "svc wifi disable   # 关闭电视 Wi-Fi 连接\n" +
                               "svc wifi enable    # 开启电视 Wi-Fi 并自动连接已知网络"
                    },
                    new CodeBlock
                    {
                        Label = "控制系统电源关机或重启",
                        Code = "svc power shutdown   # 立即正常关机\n" +
                               "svc power reboot     # 立即正常重启系统"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "content（直接读写 ContentProvider 数据库）",
                Text = "用于直接查询或修改系统设置与第三方应用的数据库表项：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询全局设置表（Global Settings）内容",
                        Code = "content query --uri content://settings/global --projection name:value | head -n 5",
                        ExpectedOutput =
                            "Row: 0 name=adb_enabled, value=1                   # 全局 ADB 开关\n" +
                            "Row: 1 name=device_name, value=极米投影仪           # 设备广播名称\n" +
                            "Row: 2 name=wifi_on, value=1                       # Wi-Fi 启用状态\n" +
                            "Row: 3 name=install_non_market_apps, value=1       # 允许安装未知来源应用"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "appops（应用后台行为与敏感权限管控）",
                Text = "控制比常规运行时权限更底层的系统行为，专用于限制极米自带软件后台常驻与弹窗：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "禁止指定应用在后台驻留运行（压制自启核心指令）",
                        Code = "appops set com.xgimi.doubtservice RUN_IN_BACKGROUND ignore",
                        ExpectedOutput = "（执行成功无报错，该应用后台常驻将被系统严格休眠限制）"
                    },
                    new CodeBlock
                    {
                        Label = "免界面弹窗直接授权悬浮窗（SYSTEM_ALERT_WINDOW）权限",
                        Code = "appops set com.github.catvod SYSTEM_ALERT_WINDOW allow",
                        ExpectedOutput = "（悬浮窗权限直接开启）"
                    },
                    new CodeBlock
                    {
                        Label = "查看目标应用的全部底层 AppOps 权限状态",
                        Code = "appops get com.xgimi.doubtservice",
                        ExpectedOutput =
                            "Uid mode: RUN_IN_BACKGROUND: ignore   # 后台常驻权限已被强制忽略\n" +
                            "Uid mode: WAKE_LOCK: allow            # 唤醒锁权限状态"
                    }
                ]
            }
        ]
    };
}
