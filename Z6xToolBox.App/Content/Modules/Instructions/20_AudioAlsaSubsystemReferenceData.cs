using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class AudioAlsaSubsystemReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "audio-alsa-subsystem-reference",
        Title = "20. 音频路由与ALSA驱动控制（audio / cards / tinymix）",
        Group = "设备接入",
        Summary = "讲解 Android Framework 音频路由策略、底层 ALSA 声卡节点查询与 tinyalsa 混音器参数。",
        Sections =
        [
            new ContentSection
            {
                Heading = "AudioService 与音频策略路由（dumpsys audio）",
                Text = "查询当前音频输出端点（内置喇叭、HDMI eARC 或蓝牙音箱）及各音轨音量分度：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前激活的音频输出设备与音量等级",
                        Code = "dumpsys audio | grep -iE \"Devices: speaker|Stream volume|mMode\"",
                        ExpectedOutput =
                            "Devices: speaker   # 当前主输出设备为内置扬声器（哈曼卡顿调音单元）\n" +
                            "- STREAM_MUSIC:   # 媒体音乐通道当前音量等级\n" +
                            "   Index: 11 (range: 0-15)   # 当前音量 11，最大刻度 15\n" +
                            "mMode = MODE_NORMAL   # 当前音频模式为常规媒体播放模式（非通话/VoIP模式）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "底层 ALSA 声卡与 PCM 设备节点（/proc/asound/）",
                Text = "Linux 内核直接挂载的音频硬件通道，绕过应用层直接确认物理声卡状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出系统所有注册的 ALSA 物理与虚拟声卡",
                        Code = "cat /proc/asound/cards",
                        ExpectedOutput =
                            " 0 [MT8516 ]: MT8516 - MT8516   # 0号声卡：联发科 MT9669 SoC 内置主音频编解码芯片\n" +
                            " 1 [Dummy ]: Dummy - Dummy ALSA Card   # 1号声卡：虚拟环回测试音频卡"
                    },
                    new CodeBlock
                    {
                        Label = "查看 PCM 录音与播放通道拓扑",
                        Code = "cat /proc/asound/pcm",
                        ExpectedOutput =
                            "00-00: mt8516-pcm-playback : : playback 1   # 0号卡0号设备：主 PCM 音频播放通道\n" +
                            "00-01: mt8516-pcm-capture : : capture 1   # 0号卡1号设备：主麦克风阵列采样录音通道"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "硬件混音器寄存器排查（tinymix）",
                Text = "查看与调节声卡 DSP 底层增益与数字滤波通道：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出底层混音控制项",
                        Code = "tinymix | head -n 8",
                        ExpectedOutput =
                            "0	INT	1	Master Playback Volume: 85 (0-100)   # 硬件主放音音量增益百分比\n" +
                            "1	BOOL	1	Speaker Function: On                 # 内置喇叭物理功放供电开关\n" +
                            "2	BOOL	1	Spdif Output Switch: Off             # 光纤/同轴 S/PDIF 数字音频输出开关"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`cat /proc/asound/*`：SSH（UID 10068）与 ADB 均可读取，属于 Linux procfs 开放节点。",
                    "`dumpsys audio`：SSH 下受限仅输出部分公开信息；ADB 下拥有系统服务全部访问权限。",
                    "`tinymix` 设置寄存器：SSH 无权写入 `/dev/snd/*` 字符设备（Operation not permitted）；ADB（UID 2000 且拥有 audio 组权限）可直接调试。"
                ]
            }
        ]
    };
}
