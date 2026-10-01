using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class StockHomeSkinDebloatData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "stock-home-skin-debloat",
        Title = "11. 极米官方桌面与主题皮肤组件停用（com.xgimi.home / skin*）",
        Group = "深度定制",
        Summary = "停用 GMUI 官方启动器及其关联的屏保与主题皮肤包，消除开机广告与流媒体瀑布流，实现第三方桌面直达。",
        Sections =
        [
            new ContentSection
            {
                Heading = "组件功能定位与停用依据",
                Text = "官方桌面及关联包具体职责说明：",
                BulletPoints =
                [
                    "`com.xgimi.home`：GMUI 官方桌面。负责主屏渲染、顶部轮播海报与爱奇艺/芒果影视推荐流。停用后开机及 Home 键直达 Projectivy。",
                    "`com.xgimi.screensaver`：官方屏保。闲置时展示壁纸与商推海报。停用后由 Projectivy 自带屏保引擎接管。",
                    "`com.xgimi.skinmanager`：GMUI 皮肤管理器。负责官方桌面主题样式的分发与动态加载。",
                    "`com.xgimi.skinconfig`：官方桌面皮肤配置文件与属性映射。",
                    "`com.xgimi.skin.classicblue`：经典蓝内置壁纸主题包。",
                    "`com.xgimi.skin.black`：暗黑内置壁纸主题包。",
                    "`com.xgimi.skin.lightblue`：浅蓝内置壁纸主题包。"
                ]
            },
            new ContentSection
            {
                Heading = "执行指令与状态验证",
                Text = "通过 ADB 冻结官方桌面与全套皮肤包，验证 Home 键焦点切换：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "停用官方桌面与全套皮肤包",
                        Code = "adb shell pm disable-user --user 0 com.xgimi.home && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.screensaver && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.skinmanager && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.skinconfig && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.skin.classicblue && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.skin.black && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.skin.lightblue",
                        ExpectedOutput = "Package com.xgimi.home new state: disabled-user\n" +
                                         "Package com.xgimi.screensaver new state: disabled-user\n" +
                                         "Package com.xgimi.skinmanager new state: disabled-user\n" +
                                         "Package com.xgimi.skinconfig new state: disabled-user\n" +
                                         "Package com.xgimi.skin.classicblue new state: disabled-user\n" +
                                         "Package com.xgimi.skin.black new state: disabled-user\n" +
                                         "Package com.xgimi.skin.lightblue new state: disabled-user"
                    },
                    new CodeBlock
                    {
                        Label = "发送 Home 键事件验证新桌面焦点接管",
                        Code = "adb shell input keyevent 3 && \\\n" +
                               "adb shell dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'",
                        ExpectedOutput = "mCurrentFocus=Window{... com.spocky.projengmenu/com.spocky.projengmenu.ui.home.MainActivity}\n" +
                                         "mFocusedApp=ActivityRecord{... com.spocky.projengmenu/.ui.home.MainActivity ...}"
                    }
                ]
            }
        ]
    };
}
