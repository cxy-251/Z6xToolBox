using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class JitterFreeAudioPcmMixerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "jitter-free-audio-pcm-mixer",
        Title = "3. 自研无 GC 抖动音频环形混音器：低延迟 Wi-Fi 串流与 ALSA 硬件直写（Z6X RustAudio）",
        Group = "Rust原生服务",
        Summary = "利用 Rust 确定性时延与无锁队列特性自研的实时音频混音服务，接收局域网 PCM 音频流直接推送到 ALSA 驱动，彻底消除 GC 引起的爆音。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在用 Go 开发音频接收端时，尽管网络传输很顺畅，但播放过程中每隔几十秒就会偶发听到一次轻微的“噼啪”爆音（Audio Glitch/Popping）。这是因为 Go 的垃圾回收器（GC）周期性 STW 哪怕只有两三毫秒，也会导致音频环形缓冲区（RingBuffer）短时欠载（Underrun）。\n\n" +
                       "使用 Rust 重构音频底层混音与写入逻辑后，由于完全不存在垃圾回收机制，音频处理线程可保持确定性的纳秒级响应；通过无锁 SPSC（单生产者单消费者）环形队列将网络 PCM 流直接推入 ALSA 音频硬件节点，实现零爆音、零卡顿的高保真 Wi-Fi 音频串流。",
                BulletPoints =
                [
                    "彻底消除 GC 暂停爆音：确定性的无锁内存推进，彻底根治音频欠载缺陷。",
                    "低至 20ms 的超低延迟：Steam Deck 游戏音画同步，支持作为无线扩音音箱。",
                    "直接对接硬件驱动：跳过 Android Java 混音层，直写系统 `/dev/snd/pcmC0D0p` 节点。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `rtrb`（实时无锁环形队列）+ `alsa` crate（ALSA 纯 C-FFI 绑定）。",
                    "什么是音频欠载（Buffer Underrun）？声卡硬件以恒定速率（如 48000 次/秒）从内存读取采样点播放。如果软件层因 GC 停顿或线程抢占未能及时送入下一批数据，声卡读取到空白数据瞬间就会产生电平突变，人耳听到的就是“啪”的爆音。Rust 保证主播放线程永远不申请堆内存、永远不加锁（Lock-Free），杜绝欠载。",
                    "为什么比 C 语言更安全？实时音频多线程极易发生死锁或悬空指针，Rust 编译器在编译阶段通过所有权模型（Send/Sync）保证环形队列跨线程传输绝对安全。",
                    "【参考开源项目】cpal（Rust 跨平台底层音频库）；snapcast（参考其双环形缓冲抖动平滑算法）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动 Rust 音频混音引擎：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动无锁音频渲染守护进程",
                        Code = "# 1. 启动 Rust 音频直写引擎（监听 8099 端口，直写声卡设备 hw:0,0）\n" +
                               "nohup /data/local/tmp/z6x_rust_audio \\\n" +
                               "  -device \"hw:0,0\" \\\n" +
                               "  -buffer 20ms \\\n" +
                               "  -port 8099 > /data/local/tmp/audio_rust.log 2>&1 &\n\n" +
                               "# 2. 本地查看声卡硬件播放状态\n" +
                               "cat /proc/asound/card0/pcm0p/sub0/status",
                        ExpectedOutput = "state: RUNNING\nowner_pid   : ... (z6x_rust_audio)\nappl_ptr    : ..."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "底层音频排坑指南：",
                BulletPoints =
                [
                    "问题 1：ALSA 设备节点被系统 AudioServer 独占（Device or resource busy）。原因与解决：Android 的 `audioserver` 进程默认占用了底层声卡句柄。若提示独占，Rust 音频后端需优雅降级为通过 OpenSL ES / Oboe 原生 C 接口接入系统音频混音管道，依然能享受无 GC 零抖动的收益。",
                    "问题 2：网络抖动丢包。在 UDP 传输时配置 15ms 的 Jitter Buffer 平滑网络抖动包。"
                ]
            }
        ]
    };
}
