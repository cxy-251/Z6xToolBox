using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class XgimiDebloatReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "xgimi-debloat-reference",
        Title = "25. 极米系统专有应用图谱与安全精简（GMUI debloat / pm disable）",
        Group = "设备接入",
        Summary = "梳理极米 Z6X Pro 系统预装 57 个 com.xgimi.* 应用组件图谱、安全停用方案与避坑清单。",
        Sections =
        [
            new ContentSection
            {
                Heading = "可安全停用的广告与推送组件（Ad & Telemetry）",
                Text = "停用后可消除开机弹窗、应用内横幅与开机推送，不影响系统核心功能：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "停用极米开机广告与启动守护",
                        Code = "pm disable-user --user 0 com.xgimi.advert\n# 停用数据统计与埋点上报：\npm disable-user --user 0 com.xgimi.tracker 2>/dev/null",
                        ExpectedOutput = "Package com.xgimi.advert new state: disabled-user   # 成功禁用，开机不再拉取广告"
                    },
                    new CodeBlock
                    {
                        Label = "停用自带应用市场与静默下载服务（按需）",
                        Code = "pm disable-user --user 0 com.xgimi.appstore\npm disable-user --user 0 com.xgimi.downloader",
                        ExpectedOutput =
                            "Package com.xgimi.appstore new state: disabled-user\n" +
                            "Package com.xgimi.downloader new state: disabled-user"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "绝对不可停用的关键硬件守护组件（高危红线）",
                Text = "以下组件负责蓝牙遥控器连接、镜头自动梯形校正与光机对焦，停用会导致设备变砖或失控：",
                BulletPoints =
                [
                    "`com.xgimi.deviceservice`：核心设备服务。负责蓝牙遥控器底层按键映射、语音键透传、电动对焦马达控制。绝对不可禁用。",
                    "`com.xgimi.remoteservice`：极米遥控器蓝牙协议栈。禁用后蓝牙遥控器将断连，且无法重新配对。",
                    "`com.xgimi.keystone`：智能梯形校正与避障算法服务。禁用后开机无法完成几何畸变校准，画面会倾斜变形。",
                    "`com.xgimi.minitvfactory`：工厂模式与底层 HDMI 输入信号源切换中枢。禁用后切换 HDMI 输入会黑屏崩溃。"
                ]
            },
            new ContentSection
            {
                Heading = "停用与恢复的标准操作范式",
                Text = "使用 disable-user 替代 uninstall，方便出现异常时随时恢复：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询当前被停用的应用列表",
                        Code = "pm list packages -d",
                        ExpectedOutput = "package:com.xgimi.advert   # 列出所有已被禁用的包名"
                    },
                    new CodeBlock
                    {
                        Label = "重新启用误停用的组件",
                        Code = "pm enable com.xgimi.advert",
                        ExpectedOutput = "Package com.xgimi.advert new state: enabled   # 恢复为正常启用状态"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`pm disable-user` / `pm enable`：SSH（UID 10068）调用会抛出 `java.lang.SecurityException: Neither user 10068 nor current process has android.permission.CHANGE_COMPONENT_ENABLED_STATE`；只有 ADB（UID 2000）具备组件状态修改权限。"
                ]
            }
        ]
    };
}
