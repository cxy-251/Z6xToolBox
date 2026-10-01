using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class RawAlsaHardwareMixerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "raw-alsa-hardware-mixer",
        Title = "40. 自研 ALSA 底层声卡 Mixer 增益与音量调节器：/dev/snd/controlC0 硬件控制（Z6X RustAlsaMixer）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的声卡控制接口混音器调节器，直接操作 Linux /dev/snd/controlC0，实现比系统更精细的音量增益与声道平衡，常驻内存仅 600KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Android 系统界面的音量步进通常固定为 15 级或 30 级，在夜间低音量观影时，经常遇到“升一格太响，降一格听不清台词”的痛点；另外，系统框架层无法单独调节左右声道平衡或耳机前级模拟增益。\n\n" +
                       "使用 Rust 自研的硬件混音控制探针，直接读写 `/dev/snd/controlC0` 节点，通过 ALSA Mixer CTL API 访问底层 DAC/功放芯片（如 ES9018 或内置 Codec）的原生寄存器。支持 0~255 级的平滑微步进音量调节与左右耳独立增益控制，常驻内存仅约 600KB。",
                BulletPoints =
                [
                    "底层硬件音量直调：绕过 Android 粗粒度 AudioService，直写硬件 DAC 衰减器。",
                    "256 级平滑微步进：夜间观影音量精准微调，避免跳阶式爆音或失真。",
                    "声道独立增益平衡：左右声道分离控制，改善因投影摆放偏斜导致的声场失衡。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `alsa-sys` 裸控制绑定 + `SNDRV_CTL_IOCTL_ELEM_READ/WRITE` 系统调用。",
                    "ALSA Control Element 识别：通过 `snd_ctl_elem_id` 匹配硬件名称（如 `Master Playback Volume`、`Speaker Volume`），精准控制对应节点。",
                    "单次调用即退出模式：支持作为独立 CLI 命令行执行毫秒级单次写操作，不产生任何后台常驻进程与内存驻留。",
                    "【参考开源项目】amixer（ALSA 官方命令行调音工具）；alsamixer（ncurses 声卡图形控制器）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "读取当前硬件增益并执行 1 级微步进衰减：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行底层声卡寄存器音量精细调节",
                        Code = "# 1. 扫描声卡 Control 节点并输出可调节音量项\n" +
                               "/data/local/tmp/z6x_mixer -card 0 list\n\n" +
                               "# 2. 精确设置 Master 硬件音量为 184（范围 0-255）\n" +
                               "/data/local/tmp/z6x_mixer -card 0 set -elem \"Master Playback Volume\" -val 184\n\n" +
                               "# 3. 查看当前音量寄存器读数\n" +
                               "/data/local/tmp/z6x_mixer -card 0 get -elem \"Master Playback Volume\"",
                        ExpectedOutput = "[Mixer] Card 0: 'MT9669-Audio-Codec'\n[Element #3] 'Master Playback Volume' (type: INTEGER, range: 0..255)\n[Write] Set element value to 184 (Attenuation: -14.2 dB) [SUCCESS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "Android 框架层音量覆盖：Android 原生 AudioService 在用户按实体遥控器音量键时会覆盖底层寄存器设置，本工具适合在夜间或特殊应用脚本中配合静音宏配合使用。",
                    "硬件过载防啸叫：将硬件增益调至 240 以上可能引起扬声器削波失真甚至烧圈，工具内置了 -12dB 至 0dB 软限幅安全保护。"
                ]
            }
        ]
    };
}
