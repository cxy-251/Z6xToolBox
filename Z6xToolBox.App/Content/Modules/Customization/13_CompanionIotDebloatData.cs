using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class CompanionIotDebloatData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "companion-iot-debloat",
        Title = "13. 伴生外设与物联协议守护精简（iot / mateservice / user）",
        Group = "深度定制",
        Summary = "停用极米智能家居生态联动、手机无屏助手桥接、无线麦克风/副音箱服务及优酷芒果账号绑定守护。",
        Sections =
        [
            new ContentSection
            {
                Heading = "组件功能定位与停用依据",
                Text = "各后台伴生服务的具体功能分析：",
                BulletPoints =
                [
                    "`com.xgimi.iot`：极米 IoT 物联中枢。负责与极米生态硬件及华为 HiLink 互联，单机使用无意义。",
                    "`com.xgimi.smartconnect`：极米多设备互联协同服务。负责局域网跨设备协同与发现握手。",
                    "`com.xgimi.mobilebridgeservice`：手机无屏助手 App 专用桥接通道。不使用官方手机 App 遥控即可安全停用。",
                    "`com.xgimi.smartaccessories`：外接智能配件联动管理。负责极米转盘、专用环境光感支架等外设控制。",
                    "`com.xgimi.mateservice`：极米官方无线麦克风及外置低音炮伴生服务。负责专用 2.4G/蓝牙外设音频串流。",
                    "`com.xgimi.user`：极米会员与第三方账号绑定。常驻后台执行 `YoukuBindService` 与 `MgBindService` 优酷芒果账号轮询。",
                    "`com.xgimi.soundermodeservice`：关屏独立蓝牙音箱模式。负责机器熄屏时将投影仪切为蓝牙音箱播放，如不需要可安全停用。"
                ]
            },
            new ContentSection
            {
                Heading = "执行指令与状态验证",
                Text = "执行批量停用并清理常驻后台进程：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "批量停用伴生与物联服务",
                        Code = "adb shell pm disable-user --user 0 com.xgimi.iot && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.smartconnect && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.mobilebridgeservice && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.smartaccessories && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.mateservice && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.user && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.soundermodeservice",
                        ExpectedOutput = "Package com.xgimi.iot new state: disabled-user\n" +
                                         "Package com.xgimi.smartconnect new state: disabled-user\n" +
                                         "Package com.xgimi.mobilebridgeservice new state: disabled-user\n" +
                                         "Package com.xgimi.smartaccessories new state: disabled-user\n" +
                                         "Package com.xgimi.mateservice new state: disabled-user\n" +
                                         "Package com.xgimi.user new state: disabled-user\n" +
                                         "Package com.xgimi.soundermodeservice new state: disabled-user"
                    },
                    new CodeBlock
                    {
                        Label = "验证当前停用组件清单",
                        Code = "adb shell pm list packages -d | grep -E 'iot|mate|user|bridge|smart'",
                        ExpectedOutput = "package:com.xgimi.mobilebridgeservice\n" +
                                         "package:com.xgimi.mateservice\n" +
                                         "package:com.xgimi.iot\n" +
                                         "package:com.xgimi.smartconnect\n" +
                                         "package:com.xgimi.user\n" +
                                         "package:com.xgimi.smartaccessories\n" +
                                         "package:com.xgimi.xgimiiotserver"
                    }
                ]
            }
        ]
    };
}
