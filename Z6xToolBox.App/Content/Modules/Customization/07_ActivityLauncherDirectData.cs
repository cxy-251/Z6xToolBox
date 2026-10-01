using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class ActivityLauncherDirectData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "activity-launcher-direct",
        Title = "7. 快捷活动调用与隐藏原生设置直达（Activity Launcher / am start）",
        Group = "深度定制",
        Summary = "利用已安装的 Activity Launcher 直达隐藏的原生 Android TV 设置、蓝牙配对与系统信息页面。",
        Sections =
        [
            new ContentSection
            {
                Heading = "Activity Launcher 命令行拉起实测",
                Text = "实机测试通过 `am start` 唤醒 Activity Launcher 主界面：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Activity Launcher 主活动",
                        Code = "am start -n de.szalkowski.activitylauncher.oss/de.szalkowski.activitylauncher.entrypoint.MainActivity",
                        ExpectedOutput = "Starting: Intent { cmp=de.szalkowski.activitylauncher.oss/de.szalkowski.activitylauncher.entrypoint.MainActivity }"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "直达原生 Android TV 设置（绕过 GMUI 限制）",
                Text = "直接调起原生 Android TV 核心设置页面，用于查看真实系统版本与无障碍服务列表：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "直接拉起原生 Android TV 设置中心",
                        Code = "am start -n com.android.tv.settings/.MainSettings",
                        ExpectedOutput = "Starting: Intent { cmp=com.android.tv.settings/.MainSettings }"
                    },
                    new CodeBlock
                    {
                        Label = "直接调起原生应用详情管理页（以 SmartTube 为例）",
                        Code = "am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:org.smarttube.stable",
                        ExpectedOutput = "Starting: Intent { act=android.settings.APPLICATION_DETAILS_SETTINGS dat=package:org.smarttube.stable }"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "启动器内系统自带 APK 与组件功能清单",
                Text = "活动启动器所列非用户安装的自带 APK 及其组件职责说明：",
                BulletPoints =
                [
                    "com.android.newsettings（极米设置）：主设置界面。包含画面微调、梯形校正、电动对焦、自动避障及音频输出配置。",
                    "com.xgimi.config（硬件配置）：底层硬件配置。包含护眼距离感知、全局动画参数及快捷键长按映射。",
                    "com.xgimi.autokst 与 com.xgimi.tof（光学校准）：驱动正面激光 ToF 传感器测距与对焦校正算法。",
                    "com.xgimi.xrmservice（温控资源管理）：光机发热监控与风道风扇转速调度。",
                    "com.xgimi.windowsystem（画面合成）：投影画面几何位移与梯形变换渲染层。",
                    "com.xgimi.minitvfactory（工程模式）：产线测试中枢。包含老化测试、风扇转速监控与 HDMI EDID 切换。高危项，切勿点击校准重置。",
                    "com.xgimi.tvinput（信号源）：HDMI 1 与 HDMI 2（eARC）硬件视频流接入桥接服务。",
                    "com.xgimi.remote 与 com.xgimi.bluetoothservice（蓝牙协议栈）：极米遥控器专有按键码映射与语音按键交互。",
                    "com.android.bluetooth 与 inputdevices（外设基础）：AOSP 标准蓝牙通信与 USB 键鼠驱动。",
                    "com.xgimi.duertts（语音与TTS）：百度语音引擎，兼具系统无障碍基础监听能力。",
                    "com.xgimi.wirelessscreen（无线投屏）：AirPlay、Miracast 与 DLNA 局域网投屏接收服务。",
                    "com.xgimi.gimiplayer 与 xhplayer（本地硬解）：底层视频硬件硬解码器，支持 3D 左右/上下格式转码播放。",
                    "com.xgimi.filemanager（文件浏览）：本地存储卡与外接 USB 移动硬盘浏览器。",
                    "com.xgimi.systemui（系统UI）：全局音量条 HUD、静音提示及信号源切换浮窗。",
                    "com.xgimi.shutdown（电源管理）：关机选择菜单、定时休眠与电源事件分发器。",
                    "com.xgimi.manager（极米管家）：垃圾扫描、内存优化与后台白名单管理。",
                    "com.sohu.inputmethod.sogou.tv（搜狗输入法）：电视大屏遥控拼音输入法。",
                    "com.android.settings（原生设置）：原生 Android 系统设置。可直达应用权限明细、存储占用及原生开发者选项。",
                    "android / packageinstaller / permissioncontroller（系统核心）：AOSP 系统框架、应用安装器与权限对话框。"
                ]
            }
        ]
    };
}
