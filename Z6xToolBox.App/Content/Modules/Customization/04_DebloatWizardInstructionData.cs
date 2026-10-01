using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class DebloatWizardInstructionData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "debloat-wizard-instruction",
        Title = "4. 极米开机引导与电子说明书清理（instruction30 / bootwizard / payview）",
        Group = "深度定制",
        Summary = "停用一次性出厂向导、静态电子说明书与会员付费收银台弹窗组件。",
        Sections =
        [
            new ContentSection
            {
                Heading = "实机执行停用指令",
                Text = "实测执行停用命令，对三项无用静态与商业化组件进行隔离：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行电子说明书与收银台停用",
                        Code = "pm disable-user --user 0 com.xgimi.instruction30 && \\\n" +
                               "pm disable-user --user 0 com.xgimi.bootwizard && \\\n" +
                               "pm disable-user --user 0 com.xgimi.payview",
                        ExpectedOutput =
                            "Package com.xgimi.instruction30 new state: disabled-user\n" +
                            "Package com.xgimi.bootwizard new state: disabled-user\n" +
                            "Package com.xgimi.payview new state: disabled-user"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "停用清单综合核对",
                Text = "查看当前累计被停用的 12 个极米官方非核心组件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "检索当前所有已禁用应用",
                        Code = "pm list packages -d",
                        ExpectedOutput =
                            "package:com.xgimi.vcontrol\n" +
                            "package:com.xgimi.instruction30\n" +
                            "package:com.xgimi.xgimihilink\n" +
                            "package:com.xgimi.bootwizard\n" +
                            "package:com.xgimi.atmosphere\n" +
                            "package:com.xgimi.doubanfm\n" +
                            "package:com.xgimi.bugreportsender\n" +
                            "package:com.xgimi.adservice\n" +
                            "package:com.xgimi.payview\n" +
                            "package:com.xgimi.agilewall\n" +
                            "package:com.xgimi.datareporter\n" +
                            "package:com.xgimi.xgimiiotserver"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "恢复命令",
                Text = "按需激活指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "重新激活说明书与收银台",
                        Code = "pm enable com.xgimi.instruction30 && pm enable com.xgimi.bootwizard && pm enable com.xgimi.payview",
                        ExpectedOutput = "Package ... new state: enabled"
                    }
                ]
            }
        ]
    };
}
