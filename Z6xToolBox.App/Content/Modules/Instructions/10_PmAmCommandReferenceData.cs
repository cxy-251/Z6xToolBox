using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class PmAmCommandReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "pm-am-reference",
        Title = "10. 包管理 pm 与活动管理 am 指令详解",
        Group = "设备接入",
        Summary = "深入解析 Android 应用管理 pm 与活动调度 am 的全参数用法，并标注 SSH 与 ADB 权限差异。",
        Sections =
        [
            new ContentSection
            {
                Heading = "pm 与 am 的权限执行边界（SSH vs ADB）",
                Text = "在 Android 系统中，pm 与 am 本质上是两个 shell 脚本，底层通过 Binder 调用系统核心服务：\n\n" +
                       "• SSH（SimpleSSHD，UID 10068）：只能执行只读查询（如 pm list packages、pm path、am monitor），调用任何涉及系统修改的接口（如 pm uninstall、pm clear、am start 受限组件）均会报 NPE 或 SecurityException 被系统拦截。\n" +
                       "• ADB（uid=2000）：拥有完整的 shell 权限组，可自由卸载、冻结、授予权限、启动任意未加白名单保护的组件。"
            },
            new ContentSection
            {
                Heading = "pm 包查询、过滤与物理路径定位",
                Text = "在 SSH 或 ADB 下均可执行的包查询指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "常用包列表过滤选项",
                        Code = "pm list packages -3                  # 查看用户安装的所有第三方包\n" +
                               "pm list packages -s                  # 查看系统自带的原厂预装包\n" +
                               "pm list packages -d                  # 查看当前已被停用或冻结的应用\n" +
                               "pm list packages -e                  # 查看当前处于启用状态的应用\n" +
                               "pm list packages -u                  # 包含已被卸载但仍保留了数据的包\n" +
                               "pm list packages -f                  # 同时显示该包对应的 APK 文件存放路径\n" +
                               "pm list packages -i                  # 显示安装该应用的来源安装器包名",
                        ExpectedOutput =
                            "package:/data/app/.../base.apk=com.github.catvod   # 格式: package:物理路径=包名\n" +
                            "package:/system/priv-app/...=com.xgimi.minitvfactory"
                    },
                    new CodeBlock
                    {
                        Label = "精确定位指定包名的 APK 物理存储路径",
                        Code = "pm path com.xgimi.minitvfactory",
                        ExpectedOutput = "package:/system/priv-app/Minitvfactory/Minitvfactory.apk   # 存放在只读系统分区"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "pm 应用卸载、数据清空、冻结与免弹窗授权（需 ADB 权限）",
                Text = "必须在 ADB（uid=2000）下运行的系统级应用管理操作：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "卸载系统自带预装应用（当前主用户）",
                        Code = "adb shell pm uninstall -k --user 0 <目标包名>\n" +
                               "# 参数解析:\n" +
                               "# --user 0: 指定用户为当前主用户（电视单用户模式）\n" +
                               "# -k: 保留应用私有数据（若要连同数据一同清除，去掉 -k）",
                        ExpectedOutput = "Success"
                    },
                    new CodeBlock
                    {
                        Label = "清空应用所有缓存与私有数据（比卸载重装更快恢复初始）",
                        Code = "adb shell pm clear <目标包名>",
                        ExpectedOutput = "Success"
                    },
                    new CodeBlock
                    {
                        Label = "冻结与解除冻结系统应用（安全停用后台，防止系统崩溃）",
                        Code = "adb shell pm disable-user --user 0 <目标包名>   # 停用该组件，系统不再调度\n" +
                               "adb shell pm enable <目标包名>                   # 重新激活该应用"
                    },
                    new CodeBlock
                    {
                        Label = "恢复误删的系统自带应用（免刷机急救）",
                        Code = "adb shell cmd package install-existing <目标包名>",
                        ExpectedOutput = "Package <目标包名> installed for user: 0   # 恢复成功，底层只读 APK 重新注册"
                    },
                    new CodeBlock
                    {
                        Label = "免界面弹窗直接向应用授予或撤销危险权限",
                        Code = "adb shell pm grant com.v2ray.ang android.permission.POST_NOTIFICATIONS   # 授予通知权限\n" +
                               "adb shell pm revoke com.v2ray.ang android.permission.POST_NOTIFICATIONS  # 撤销通知权限"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "am 组件启动、杀进程与系统广播调度",
                Text = "Activity 启动与后台进程生命周期控制：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动指定 Activity 界面",
                        Code = "adb shell am start -n com.xgimi.minitvfactory/.MainActivity\n" +
                               "# 常用参数:\n" +
                               "# -n: 指定完整组件名称（包名/类名）\n" +
                               "# -W: 等待启动完成并打印启动耗时统计\n" +
                               "# -S: 启动前先强杀目标应用的旧进程",
                        ExpectedOutput =
                            "Starting: Intent { cmp=com.xgimi.minitvfactory/.MainActivity }\n" +
                            "Status: ok\n" +
                            "LaunchState: COLD        # 冷启动状态\n" +
                            "TotalTime: 320           # 启动总耗时 320 毫秒"
                    },
                    new CodeBlock
                    {
                        Label = "调用系统默认应用打开指定 URL 网页",
                        Code = "adb shell am start -a android.intent.action.VIEW -d \"http://192.168.0.21:8000/\"",
                        ExpectedOutput = "Starting: Intent { act=android.intent.action.VIEW dat=http://192.168.0.21:8000/ }"
                    },
                    new CodeBlock
                    {
                        Label = "强杀应用全部后台进程",
                        Code = "adb shell am force-stop <目标包名>\n" +
                               "# 说明: 终止该包下的所有 Activity、Service 服务及清除 PendingIntent 定时器"
                    },
                    new CodeBlock
                    {
                        Label = "发送系统开机完成广播（测试自启动）",
                        Code = "adb shell am broadcast -a android.intent.action.BOOT_COMPLETED",
                        ExpectedOutput = "Broadcasting: Intent { act=android.intent.action.BOOT_COMPLETED }\nBroadcast completed: result=0"
                    }
                ]
            }
        ]
    };
}
