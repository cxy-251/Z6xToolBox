using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LockFreeAudioCaptureProbeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lock-free-audio-capture-probe",
        Title = "25. 自研无锁环形队列音频流录制与回放探针：TinyALSA 原始 PCM 监听（Z6X RustAudioCapture）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的底层 PCM 音频捕获与低延迟环形缓冲探针，通过 TinyALSA 裸读声卡节点，常驻内存仅 1.6MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在排查极米投影仪音频爆音、杂音、音画不同步或麦克风语音拾音异常时，常规的 Android AudioRecord API 需要经过 AudioFlinger 与 Binder IPC 多层中转，存在缓冲延迟高、偶发断流无法定位底层根因的问题。\n\n" +
                       "使用 Rust 自研的音频捕获探针，直接调用 Linux `/dev/snd/pcmC0D*` 字符设备节点，通过无锁环形队列（Lock-free RingBuffer）进行零阻塞 PCM 采样数据提取。支持实时计算 RMS 音量分贝、峰值电平与削波警告，物理常驻内存仅约 1.6MB。",
                BulletPoints =
                [
                    "底层直通声卡节点：绕过 Android AudioFlinger 中间件，直接读取 ALSA PCM 数据流。",
                    "无锁双端队列：基于 crossbeam 环形队列，读写线程解耦，零线程锁等待竞争。",
                    "低延迟电平分析：微秒级计算当前音频振幅与失真指示，定位硬件与驱动级爆音。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `alsa-sys`/`tinyalsa-rs` 绑定 + `crossbeam-channel`（无锁单生产者单消费者队列）。",
                    "MMAP 音频传输模式：配置 ALSA `SND_PCM_ACCESS_MMAP_INTERLEAVED`，由 DMA 控制器直接写入应用层映射内存，避免拷贝中断。",
                    "静音与削波自动告警：当检测到连续 3 秒输入为 0 或振幅超过 0dB 达到饱和阈值时，自动记录时间戳黑匣子日志。",
                    "【参考开源项目】tinyalsa（Android 原生轻量 ALSA 库）；cpal（纯 Rust 跨平台音频底层抽象库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 PCM 音频探针并监听实时电平：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动声卡硬件底层采样监听",
                        Code = "# 1. 启动音频探针监听 Card 0 Device 0 (48kHz 双声道 16bit)\n" +
                               "nohup /data/local/tmp/z6x_audio_probe \\\n" +
                               "  -card 0 -device 0 -rate 48000 -ch 2 \\\n" +
                               "  -out /data/local/tmp/probe.pcm > /data/local/tmp/audio.log 2>&1 &\n\n" +
                               "# 2. 查看实时计算的 RMS 电平与削波状态\n" +
                               "tail -n 10 /data/local/tmp/audio.log",
                        ExpectedOutput = "[ALSA] pcmC0D0p opened in MMAP mode\n[Audio] RMS: -18.4 dBFS, Peak: -3.1 dBFS, Clipping: False\n[Audio] Underrun count: 0, RingBuffer usage: 14%"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "声卡设备独占冲突：Android 的 AudioFlinger 可能会以独占模式打开部分 PCM 节点。若 `EBUSY` 报错，可使用具有共享插件属性的 `pcmC0D0c` 捕获节点或临时停止前台音频播放服务再做诊断。",
                    "PCM 文件体积过大：原始 48kHz 16-bit 双声道 PCM 每秒产生约 192KB 数据，长期监听测试务必挂载外置 U 盘存储或开启环形内存覆盖模式。"
                ]
            }
        ]
    };
}
