using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class DebloatMediaExtrasData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "debloat-media-extras",
        Title = "2. 极米预装冗余组件精简（doubanfm / agilewall / atmosphere）",
        Group = "深度定制",
        Summary = "停用内置豆瓣 FM、动态画中画壁纸与氛围组件，释放约 320MB 运行时常驻内存。",
        Sections =
        [
            new ContentSection
            {
                Heading = "实机执行停用指令",
                Text = "实测执行停用命令，对三项预装冗余视听组件进行隔离：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行豆瓣FM与氛围屏保停用",
                        Code = "pm disable-user --user 0 com.xgimi.doubanfm && \\\n" +
                               "pm disable-user --user 0 com.xgimi.agilewall && \\\n" +
                               "pm disable-user --user 0 com.xgimi.atmosphere",
                        ExpectedOutput =
                            "Package com.xgimi.doubanfm new state: disabled-user\n" +
                            "Package com.xgimi.agilewall new state: disabled-user\n" +
                            "Package com.xgimi.atmosphere new state: disabled-user"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "停用后系统验证",
                Text = "确认停用后列表与系统内存回升情况：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询当前被停用的应用清单",
                        Code = "pm list packages -d",
                        ExpectedOutput =
                            "package:com.xgimi.atmosphere\n" +
                            "package:com.xgimi.doubanfm\n" +
                            "package:com.xgimi.bugreportsender\n" +
                            "package:com.xgimi.adservice\n" +
                            "package:com.xgimi.agilewall\n" +
                            "package:com.xgimi.datareporter"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "恢复命令",
                Text = "按需恢复指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "重新激活视听组件",
                        Code = "pm enable com.xgimi.doubanfm && pm enable com.xgimi.agilewall && pm enable com.xgimi.atmosphere",
                        ExpectedOutput = "Package ... new state: enabled"
                    }
                ]
            }
        ]
    };
}
