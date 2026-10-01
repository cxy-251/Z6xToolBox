using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class ScreenOffWirelessAudioStreamerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "screen-off-wireless-audio-streamer",
        Title = "19. 自研熄屏无线音频接收端：HTTP/RTSP 音频流与系统混音直推（Z6X AudioStreamer）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的局域网无线音频流接收端，在不点亮投影光机的待机状态下，将 Steam Deck 或 PC 的游戏声音无线推送到极米音箱播放。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米 Z6X Pro 配备了专门调校的音响单元，但在日常不看电影（光机关闭）时，该音响几乎完全闲置。\n\n" +
                       "自研该音频流接收端后，极米在关闭光机（息屏、仅耗电几瓦）状态下，通过 Wi-Fi 暴露无线音频接收端。Steam Deck 或 PC 可通过网络将声卡输出无损推送到极米，投影仪变身为局域网无线高保真音箱，免受蓝牙传输距离短与音质压缩劣化的困扰。",
                BulletPoints =
                [
                    "充分利用闲置音响：光机关闭状态下依然作为独立 Wi-Fi 无线音箱工作。",
                    "低延迟高保真：基于 Wi-Fi 5G 传输裸 PCM 或 FLAC 音频流，延迟低于 50ms，远超普通蓝牙 SBC 编码。",
                    "电脑/掌机通用输出：支持通过 PulseAudio/PipeWire 局域网网络串流直接输出。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net`（TCP/HTTP 流监听）+ Android 本地音频混音管道直通（单文件体积约 5MB）。",
                    "什么是裸 PCM 音频流直接写入？发送端将采集到的 48kHz/16-bit 双声道 PCM 音频数据通过 HTTP POST 持续发送给极米。Go 服务在本地通过管道调用系统的 `tinyplay` 工具或 Android AudioTrack API 直接写入硬件音频设备节点，实现零二次重编码解码。",
                    "什么是音频时钟同步（Jitter Buffer）？局域网传输可能产生微小抖动。Go 服务维护一个 30ms 的环形缓冲区（Ring Buffer），自动平滑网络抖动，避免播放声音产生爆音或跳字。",
                    "【参考开源项目】snapcast（参考其局域网音频多房间同步与 PCM 缓冲池设计，提取单房间精简逻辑）；gortsplib（纯 Go 编写的 RTSP 协议流媒体库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动音频接收守护进程并推流测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动音频接收端并在 Deck 上测试推流",
                        Code = "# 1. 极米后台启动音频接收守护进程（监听 8099 端口）\n" +
                               "nohup /data/local/tmp/z6x_audiostreamer -port 8099 > /data/local/tmp/audio.log 2>&1 &\n\n" +
                               "# 2. Steam Deck 终端抓取当前麦克风/声卡声音推流至极米播放\n" +
                               "arecord -f cd -t raw | curl -X POST --data-binary @- http://192.168.0.109:8099/stream",
                        ExpectedOutput = "# 极米音响实时输出 Deck 推送的音频流"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "无线音频排坑指南：",
                BulletPoints =
                [
                    "问题 1：息屏状态下音频硬件被系统断电。原因与解决：Android TV 在完全熄屏时可能切断功放芯片供电。启动音频服务时需通过命令向系统申请 `PARTIAL_WAKE_LOCK` 保持音频 HAL 层处于唤醒待命状态。",
                    "问题 2：采样率不匹配声音变调。确保发送端录制参数与极米硬件默认采样率一致（通常为 48000Hz 16-bit 双声道），避免因重采样失真。"
                ]
            }
        ]
    };
}
