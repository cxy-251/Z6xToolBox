using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class HomeAssistantDeviceBridgeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "home-assistant-device-bridge",
        Title = "6. 智能家居控制桥接网关：轻量 HTTP / MQTT Webhook 中继",
        Group = "Go原生服务",
        Summary = "基于 Go 编写的微型控制网关，接收智能家居（Home Assistant）网络指令，通过本地 Android 原生事件控制投影仪各项硬件功能。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米官方生态封闭，通常只能通过原装蓝牙遥控器或极米 App 操作，无法与家庭中的通用智能家居平台（如 Home Assistant、米家联动、Node-RED）深度整合。\n\n" +
                       "例如，用户无法实现「按下无线开关自动开机并切到 Steam Deck HDMI 画面」、「智能音箱语音调节投影仪音量与静音」、「检测到离家后自动关闭投影仪」。在极米后台部署该轻量控制网关后，任何智能家居设备均可通过一条简单的网络请求（HTTP GET/POST）精准遥控投影仪。",
                BulletPoints =
                [
                    "打通开源智能家居：让 Home Assistant 可无缝控制极米投影仪开关机、音量、信号源切换。",
                    "告别无线 ADB 频繁掉线：由投影仪本地进程直接派发系统事件，不再依赖易超时的远程 ADB 网络连接。",
                    "毫秒级指令响应：本地执行 Linux 底层 input 事件，从收到网络指令到动作生效耗时 < 5ms。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 静态编译微服务（单文件体积约 4MB，内存常驻仅 5MB ~ 8MB）。",
                    "什么是 Webhook / HTTP API？一种简单的反向触发接口。网关监听 `:8086`，暴露例如 `/api/power`（开关机）、`/api/volume?action=up`（音量加）、`/api/source?target=hdmi`（切信号源）等标准 RESTful 端点，Home Assistant 发送一条 HTTP 请求即可完成触发。",
                    "为什么比 Home Assistant 的 Android TV 集成更稳定？Home Assistant 官方方案依赖在服务端通过网络不断与投影仪的 5555 端口保持 TCP ADB 会话，网络抖动或投影仪熄屏时容易频繁报错断联；而本地 Go 微服务直接以 Linux 本地进程调用 `input keyevent`，零网络握手损耗。"
                ]
            },
            new ContentSection
            {
                Heading = "核心联动控制命令对照",
                Text = "网关内部执行的高频控制指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "常用硬件与媒体控制命令",
                        Code = "# 1. 切换 HDMI 信号源（直接拉起 HDMI 全屏活动）\n" +
                               "am start -a android.intent.action.VIEW -d \"xgimi://com.xgimi.home/hdmi\"\n\n" +
                               "# 2. 音量调节与静音控制\n" +
                               "input keyevent 24   # 音量增加（KEYCODE_VOLUME_UP）\n" +
                               "input keyevent 25   # 音量减小（KEYCODE_VOLUME_DOWN）\n" +
                               "input keyevent 164  # 静音/取消静音切换（KEYCODE_VOLUME_MUTE）\n\n" +
                               "# 3. 电源开关机与息屏待机\n" +
                               "input keyevent 26   # 电源按键切换（KEYCODE_POWER）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "智能家居桥接常见问题与对策：",
                BulletPoints =
                [
                    "问题 1：休眠状态下发送按键无响应。原因与解决：若系统已进入深度省电休眠，某些按键事件会被底层挂起。唤醒投影仪时建议优先发送连续两次电源事件（`input keyevent 26 && sleep 1 && input keyevent 26`）或通过 `am start` 唤醒前台。",
                    "问题 2：HDMI 切换失败。原因与解决：若此前彻底卸载了极米官方主页相关组件，部分系统内广播可能变更。备用方案为直接调用按键模拟：`input keyevent 178`（KEYCODE_TV_INPUT）。"
                ]
            }
        ]
    };
}
