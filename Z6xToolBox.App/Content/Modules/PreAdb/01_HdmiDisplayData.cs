using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class HdmiDisplayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "hdmi-display",
        Title = "1. Steam Deck 副屏接入与 2K 分辨率排查",
        Group = "设备接入",
        Summary = "解决 Steam Deck 连接投影仪黑屏无信号问题，排查 2K 时序冲突，并提供 kscreen-doctor 完整控制命令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "黑屏无信号原因分析",
                Text = "Steam Deck 此前曾连接过 2K/144Hz 外部显示器，KDE 桌面服务（KWin）在本地持久化缓存了该输出端口的配置时序。\n\n" +
                       "当使用 Type-C 转 HDMI 线接入极米 Z6X Pro 时，Deck 会自动复用上一次记录的 2K/144Hz 信号输出；而 Z6X Pro 的 HDMI 接口最大仅支持 1080p@60Hz（或 4K@60Hz 降采样），无法识别 144Hz 高刷时序，导致 EDID 握手超时、投影仪直接提示“无信号”并黑屏。\n\n" +
                       "将 Deck 该接口输出手动重置为 1920x1080@60Hz 点对点输出后，画面即刻恢复正常。",
                BulletPoints =
                [
                    "对焦距离限制：Z6X Pro 激光/TOF 最低有效对焦距离为 0.8 米，若距离过近会导致对焦算法拉风箱且画面模糊。",
                    "避免 4K 缩放：虽然投影仪支持接收 4K@60Hz 信号，但由于物理光机是 1080p，4K 降采样会导致字体边缘发虚，日常当作副屏务必保持 1080p 点对点输出。"
                ]
            },
            new ContentSection
            {
                Heading = "分辨率调整命令与预期输出",
                Text = "在 Steam Deck 终端（桌面模式）使用 KDE 屏幕控制工具调整输出：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前所有显示接口与状态",
                        Code = "kscreen-doctor -o",
                        ExpectedOutput =
                            "Output: 1 eDP-1 enabled connected priority 1 ... 1280x800@60\n" +
                            "Output: 2 HDMI-A-1 enabled connected priority 2 ... 1920x1080@60  # 预期应显示 HDMI-A-1 已连接并处于 1080p",
                        Note = "若 HDMI-A-1 状态显示为 disconnected，检查拓展坞或线缆连接。"
                    },
                    new CodeBlock
                    {
                        Label = "强制将外接 HDMI 设置为 1080p@60Hz 输出",
                        Code = "kscreen-doctor output.HDMI-A-1.mode.1920x1080@60",
                        ExpectedOutput = "（执行成功无错误输出，投影仪画面点亮）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "kscreen-doctor 常用工具控制命令全集",
                Text = "kscreen-doctor 是 KDE Wayland/X11 下免图形界面的屏幕控制工具，常用备用命令整理如下：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "设置副屏相对主屏的物理位置（放置在 Deck 右侧）",
                        Code = "kscreen-doctor output.HDMI-A-1.position.1280,0",
                        Note = "1280,0 表示副屏紧接在 Deck 掌机屏幕（1280x800）右侧，鼠标可向右直接滑入投影仪。"
                    },
                    new CodeBlock
                    {
                        Label = "关闭外接投影仪输出（不拔线休眠副屏）",
                        Code = "kscreen-doctor output.HDMI-A-1.disable",
                        Note = "重新点亮副屏执行：kscreen-doctor output.HDMI-A-1.enable"
                    },
                    new CodeBlock
                    {
                        Label = "将外接副屏旋转 90 度或 180 度（竖屏投屏时备用）",
                        Code = "kscreen-doctor output.HDMI-A-1.rotation.normal\n# 旋转选项：normal（正常）、left（逆时针90度）、right（顺时针90度）、inverted（倒置180度）"
                    },
                    new CodeBlock
                    {
                        Label = "设置副屏画面缩放比例（100% 点对点）",
                        Code = "kscreen-doctor output.HDMI-A-1.scale.1"
                    }
                ]
            }
        ]
    };
}
