using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class DebloatAdserviceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "debloat-adservice",
        Title = "1. 极米广告与数据上报精准停用（adservice / datareporter）",
        Group = "深度定制",
        Summary = "实机停用开机广告与埋点上传组件，消除开机全屏推送并减少后台无用唤醒。",
        Sections =
        [
            new ContentSection
            {
                Heading = "实机执行停用指令",
                Text = "实测执行停用命令，对三项广告与遥测服务进行隔离：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行广告与遥测上报禁用",
                        Code = "pm disable-user --user 0 com.xgimi.adservice && \\\n" +
                               "pm disable-user --user 0 com.xgimi.datareporter && \\\n" +
                               "pm disable-user --user 0 com.xgimi.bugreportsender",
                        ExpectedOutput =
                            "Package com.xgimi.adservice new state: disabled-user\n" +
                            "Package com.xgimi.datareporter new state: disabled-user\n" +
                            "Package com.xgimi.bugreportsender new state: disabled-user"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "实机停用状态与桌面存活验证",
                Text = "确认被停用的包已生效，并验证官方桌面 `com.xgimi.home` 运行正常：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "检查已禁用包列表与桌面前台焦点",
                        Code = "pm list packages -d && dumpsys window | grep mCurrentFocus",
                        ExpectedOutput =
                            "package:com.xgimi.bugreportsender\n" +
                            "package:com.xgimi.adservice\n" +
                            "package:com.xgimi.datareporter\n" +
                            "mCurrentFocus=Window{... u0 com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity}   # 桌面焦点正常，未发生崩溃"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "恢复命令（容灾方案）",
                Text = "若后续系统更新或遇到异常，可通过以下命令重新激活：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "一键重新启用广告与上报服务",
                        Code = "pm enable com.xgimi.adservice && pm enable com.xgimi.datareporter && pm enable com.xgimi.bugreportsender",
                        ExpectedOutput = "Package ... new state: enabled"
                    }
                ]
            }
        ]
    };
}
