using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class DebloatIotServicesData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "debloat-iot-services",
        Title = "3. 极米IoT与智能家居后台精简（hilink / iotserver / vcontrol）",
        Group = "深度定制",
        Summary = "停用持续占用 CPU 扫描周期的华为 HiLink、极米 IoT 与米家联动常驻服务。",
        Sections =
        [
            new ContentSection
            {
                Heading = "实机常驻进程占用发现",
                Text = "实测发现 `com.xgimi.xgimihilink` 后台累计消耗 CPU 调度时间近 20 分钟：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 IoT 服务运行态进程",
                        Code = "ps -ef | grep -E \"hilink|iotserver|vcontrol\"",
                        ExpectedOutput =
                            "system  4270  2665 6 19:33:35 ? 00:19:35 com.xgimi.xgimihilink        # 华为协议常驻扫描\n" +
                            "system  4804  2665 0 19:33:43 ? 00:00:28 com.xgimi.xgimiiotserver       # 极米 IoT 守护\n" +
                            "system  6244  2665 1 19:33:57 ? 00:01:55 com.xgimi.vcontrol:miot       # 米家联动进程"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "实机执行停用指令",
                Text = "针对不使用智能家居联动的场景执行隔离：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "停用三大智能家居协议栈",
                        Code = "pm disable-user --user 0 com.xgimi.xgimihilink && \\\n" +
                               "pm disable-user --user 0 com.xgimi.xgimiiotserver && \\\n" +
                               "pm disable-user --user 0 com.xgimi.vcontrol",
                        ExpectedOutput =
                            "Package com.xgimi.xgimihilink new state: disabled-user\n" +
                            "Package com.xgimi.xgimiiotserver new state: disabled-user\n" +
                            "Package com.xgimi.vcontrol new state: disabled-user"
                    }
                ],
                BulletPoints =
                [
                    "底层生命周期特性：上述组件以 `system (UID 1000)` 权限运行，且部分带有 `persistent` 标记，停用后在下次开机重启时生效，系统将不再派发并启动其服务树。"
                ]
            },
            new ContentSection
            {
                Heading = "恢复命令",
                Text = "若后续需要连接华为智慧生活或米家：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "恢复 IoT 相关组件",
                        Code = "pm enable com.xgimi.xgimihilink && pm enable com.xgimi.xgimiiotserver && pm enable com.xgimi.vcontrol",
                        ExpectedOutput = "Package ... new state: enabled"
                    }
                ]
            }
        ]
    };
}
