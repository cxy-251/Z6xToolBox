using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class DrmDisplayUsbReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "drm-display-usb-reference",
        Title = "18. DRM授权、色彩HDR、USB外设与A/B槽位（Widevine / display / lsusb / bootctl）",
        Group = "设备接入",
        Summary = "讲解 Widevine DRM 授权等级排查、HDR 显示能力探测、USB 外设总线枚举及 A/B 系统分区槽位查询。",
        Sections =
        [
            new ContentSection
            {
                Heading = "Widevine DRM 数字版权与安全等级排查",
                Text = "排查流媒体（Netflix、Disney+、HBO）是否支持 4K 高清播放的核心依据：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询 Widevine DRM 等级与厂商安全库",
                        Code = "dumpsys media.drm | grep -iE \"security level|vendor|description|crypto\"",
                        ExpectedOutput =
                            "Security Level: L1   # L1 级别：硬件级安全芯片解密，允许 4K 流媒体播放（若为 L3 则最高仅限 540p/720p）\n" +
                            "Vendor: Google LLC\n" +
                            "Description: Widevine CDM"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "显示能力、色彩格式与 HDR 模式探测",
                Text = "查询光机支持的色彩空间与动态范围协议：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看屏幕 HDR 能力与色彩制式支持",
                        Code = "dumpsys display | grep -iE \"hdr|colormode|supportedmodes\" | head -n 5",
                        ExpectedOutput =
                            "mHdrCapabilities: HdrCapabilities{mSupportedHdrTypes=[2, 3]}   # 2: HDR10, 3: HLG 模式支持\n" +
                            "supportedModes: [{id=1, width=1920, height=1080, fps=60.0}]   # 原生点对点 1080p@60Hz"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "USB 外设总线枚举（lsusb / dumpsys usb）",
                Text = "排查插入的无线飞鼠接收器、蓝牙手柄适配器或外置声卡是否被内核驱动识别：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出 USB 总线所有物理外设的 VID:PID",
                        Code = "lsusb 2>/dev/null || cat /sys/kernel/debug/usb/devices | grep -E \"Vendor=|Product=\"",
                        ExpectedOutput =
                            "Bus 001 Device 002: ID 0e8d:0616 MediaTek Inc.   # 联发科内置无线/蓝牙复合芯片\n" +
                            "Bus 002 Device 003: ID 0951:1666 Kingston Technology   # 金士顿外接 USB 闪存盘"
                    },
                    new CodeBlock
                    {
                        Label = "查看系统 USB 当前连接模式与角色状态",
                        Code = "dumpsys usb | grep -iE \"mCurrentFunctions|connected\"",
                        ExpectedOutput = "mCurrentFunctions: none   # 当前未接宿主控制模式（纯外设 Host 供电模式）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "A/B 系统无缝升级分区槽位查询（bootctl）",
                Text = "查询设备是否使用 A/B（Seamless）双分区方案及当前运行槽位：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前激活的系统插槽（Slot A 或 Slot B）",
                        Code = "bootctl get-current-slot 2>/dev/null || getprop ro.boot.slot_suffix",
                        ExpectedOutput = "_a   # 当前运行在 A 槽位（若固件升级失败会自动回退至 B 槽位）"
                    }
                ]
            }
        ]
    };
}
