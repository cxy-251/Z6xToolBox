using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class HeadlessSystemPackagesData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "headless-system-packages",
        Title = "15. 无界面系统底座（非UI APK）解析与儿童模式清理",
        Group = "深度定制",
        Summary = "梳理活动启动器未收录的底层 Overlay 样式、系统数据库与网络栈 APK，定位并清除官方桌面儿童模式残留条目。",
        Sections =
        [
            new ContentSection
            {
                Heading = "非 UI 系统 APK 分类与职责清单",
                Text = "整机中无独立界面、仅提供底层支撑的 APK 分类汇总：",
                BulletPoints =
                [
                    "样式与切边叠加层（Overlay）：navbar.threebutton/gestural、cutout.emulation.*、frameworkres.overlay、font.notoserifsource。仅包含 XML 布局与系统样式资源，无执行代码。",
                    "底层数据提供者（Provider）：providers.settings（系统设置数据库）、providers.media.module（媒体扫描索引）、providers.tv（电视节目数据表）。",
                    "网络协议与连接栈：networkstack/tethering（TCP/IP协议驱动与热点）、pacprocessor/proxyhandler（代理脚本解析）、captiveportallogin（热点认证）、keychain（CA证书库）、vpndialogs（VPN授权弹窗）。",
                    "极米底层无界面守护进程：inuiserver（IPC进程通信通道）、ui.api（自定义视图库）、appdb（应用元数据存储）、xgimiservice/persistentservice（开机硬件自检与心跳）、rgbdupgrade（激光测距固件维护）。"
                ]
            },
            new ContentSection
            {
                Heading = "儿童模式入口归属与清理方案",
                Text = "针对活动启动器中可见的“儿童模式”条目进行溯源与处理：",
                BulletPoints =
                [
                    "入口归属：儿童模式实际为官方桌面内嵌组件（com.xgimi.home/com.xgimi.childmode.SettingActivity 与 ParentSettingActivity）。",
                    "运行现状：此前已停用 com.xgimi.home，该组件在底层已无法运行，点击报 class does not exist。",
                    "列表移除方案：若需在活动启动器列表中清除该条目，执行针对用户 0 的卸载；如需恢复可随时重新挂载。"
                ],
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "从当前用户空间移除官方桌面残留入口",
                        Code = "adb shell pm uninstall -k --user 0 com.xgimi.home",
                        ExpectedOutput = "Success"
                    },
                    new CodeBlock
                    {
                        Label = "随时恢复官方桌面挂载（免刷机）",
                        Code = "adb shell cmd package install-existing com.xgimi.home",
                        ExpectedOutput = "Package com.xgimi.home installed for user: 0"
                    }
                ]
            }
        ]
    };
}
