using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class DebloatStreamRecommendData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "debloat-stream-recommend",
        Title = "5. 影视流媒体聚合推荐与弹窗消息拦截（stream.video / msgcenter / newappmarket）",
        Group = "深度定制",
        Summary = "停用官方聚合芒果/爱奇艺等平台的在线视频流服务、通知中心推送与应用市场推荐位。",
        Sections =
        [
            new ContentSection
            {
                Heading = "实机执行停用指令",
                Text = "实测执行停用命令，对三项核心推荐与推送组件进行隔离：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行视频流服务、消息中心与新应用市场停用",
                        Code = "pm disable-user --user 0 com.xgimi.stream.video && \\\n" +
                               "pm disable-user --user 0 com.xgimi.msgcenter && \\\n" +
                               "pm disable-user --user 0 com.xgimi.newappmarket",
                        ExpectedOutput =
                            "Package com.xgimi.stream.video new state: disabled-user   # 停用聚合流媒体服务\n" +
                            "Package com.xgimi.msgcenter new state: disabled-user      # 停用通知消息推送弹窗\n" +
                            "Package com.xgimi.newappmarket new state: disabled-user   # 停用预装市场广告推荐位"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "实机停用状态与桌面稳定性验证",
                Text = "确认停用后桌面 `com.xgimi.home` 运行稳定，瀑布流第三方视频聚合卡片不再拉取更新：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询禁用状态与桌面前台焦点",
                        Code = "pm list packages -d | grep -E \"stream.video|msgcenter|newappmarket\" && dumpsys window | grep mCurrentFocus",
                        ExpectedOutput =
                            "package:com.xgimi.newappmarket\n" +
                            "package:com.xgimi.stream.video\n" +
                            "package:com.xgimi.msgcenter\n" +
                            "mCurrentFocus=Window{... u0 com.xgimi.home/...MyAppActivity}   # 桌面运行正常"
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
                        Label = "重新激活推荐与推送组件",
                        Code = "pm enable com.xgimi.stream.video && pm enable com.xgimi.msgcenter && pm enable com.xgimi.newappmarket",
                        ExpectedOutput = "Package ... new state: enabled"
                    }
                ]
            }
        ]
    };
}
