using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class NativeScreenCastMiracastData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "native-screen-cast-miracast",
        Title = "17. 跨平台免客户端原生投屏调试（Miracast / DLNA / AirPlay）",
        Group = "深度定制",
        Summary = "梳理 iOS、Windows 及安卓手机系统原生投屏链路，实现无需在发送端安装第三方投屏客户端即可直投镜像与视频流。",
        Sections =
        [
            new ContentSection
            {
                Heading = "投屏协议分布与免客户端连接原理",
                Text = "三大平台系统级投屏通道对应关系：",
                BulletPoints =
                [
                    "iOS / macOS：基于 AirPlay 协议。极米系统后台常驻 AirPlayInitService，苹果设备下拉控制中心可直接搜索投影仪镜像。",
                    "Windows 10/11：基于 Miracast（Wi-Fi Display）协议。系统按快捷键 Win + K 直连，无需第三方投屏软件。",
                    "安卓手机（单视频流）：基于 DLNA 协议。任意影视 App 点击播放界面“TV”图标，投影仪自动接管解码播放，两端零安装。",
                    "安卓手机（整机镜像）：基于 Miracast / 无线显示协议。手机下拉控制中心点击“无线投屏/屏幕镜像”直连。"
                ]
            },
            new ContentSection
            {
                Heading = "实机广播调起与故障排查",
                Text = "当 Windows 或安卓手机无法自动发现设备时，在投影仪拉起原生投屏广播界面以激活 Wi-Fi Display 侦听：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "前台调起极米无线投屏接收界面（开启广播侦听）",
                        Code = "adb shell am start -n com.xgimi.wirelessscreen/.activity.NewMainActivity",
                        ExpectedOutput = "Starting: Intent { cmp=com.xgimi.wirelessscreen/.activity.NewMainActivity }"
                    },
                    new CodeBlock
                    {
                        Label = "用毕终止 Miracast 搜网广播（避免天线信道冲突）",
                        Code = "adb shell am force-stop com.xgimi.wirelessscreen",
                        ExpectedOutput = "# 退出搜网界面，释放 2.4GHz 射频天线给蓝牙遥控器独占"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Miracast 射频机制与蓝牙遥控共存排坑",
                Text = "实测调起 Miracast 接收广播后导致蓝牙遥控器严重卡顿的根因分析：",
                BulletPoints =
                [
                    "内部硬件共存仲裁（Coexistence Arbiter）：投影仪主板将 Wi-Fi 与蓝牙集成在同一颗双模 SoC 上并共用同一组天线。Wi-Fi 执行全频段 P2P 搜网时，芯片内部的射频开关（RF Switch）主动挂起蓝牙接收，属于机身内部硬件层面的时隙剥夺，而非外部空间信号干扰。",
                    "按键信号物理级截断：在此状态下，遥控器发出的蓝牙信号在投影仪天线输入端即被硬件丢弃，直观表现为按键严重延迟与丢按键。",
                    "规范使用准则：Miracast 广播仅在 Windows 需要投屏握手时临时拉起；日常保持后台静默状态，禁止长期停留在投屏搜网主页。"
                ]
            }
        ]
    };
}
