using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class MultiRoomAudioStreamSyncData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "multi-room-audio-stream-sync",
        Title = "41. 自研多房间音频流同步分发服务：Snapcast 协议兼容（Z6X SnapSync）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的多房间多设备音频同步分发服务端，兼容 Snapcast 客户端协议，实现全屋毫秒级音频对齐广播，常驻内存仅 16MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米投影仪上播放音乐或派对背景音时，如果在客厅、卧室和阳台同时放置了多台手机、平板或智能音箱，通过常规蓝牙或局域网各自播放会产生明显的几十至数百毫秒声画与声音延迟，形成重音回声。\n\n" +
                       "使用 Go 自研的多房间音频同步服务，在本地接收音频输入（从本地管道或网络抓取 PCM 流），按照 Snapcast 协议将音频打包为带微秒级时间戳的数据块。局域网各房间客户端（Android、iOS、PC、树莓派）连接后，依据 NTP 算法自动补偿各自的播放延迟，实现全屋同步对齐播放，常驻物理内存约 16MB。",
                BulletPoints =
                [
                    "毫秒级多端时钟对齐：自动测算各客户端网络抖动并动态插值补偿，消除回声。",
                    "广泛客户端兼容：原生兼容各大平台的 Snapclient 开源客户端应用。",
                    "低开销单文件交付：纯 Go 原生实现音频切片与分发，免除复杂的 C++ 编译链。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + TCP/Websocket 音频流多路复用 + FLAC 纯 Go 快速压缩（`mewkiz/flac`）。",
                    "时间戳同步算法：服务端每秒向客户端广播多次同步包（Time Sync Message），计算往返时延（RTT）与时钟偏移量（Clock Offset），指引客户端动态微调声卡输出采样率。",
                    "无损流式压缩：将 PCM 数据流在内存中以 FLAC 格式实时压缩后再进行广播，节约 50% 局域网 Wi-Fi 带宽。",
                    "【参考开源项目】snapcast（经典多房间同步音频方案）；shairport-sync（AirPlay 多房间同步音频工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动音频同步服务并模拟推流验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Snapcast 兼容音频同步服务器",
                        Code = "# 1. 启动同步服务（监听 1704 端口供客户端连接，1705 端口提供控制）\n" +
                               "nohup /data/local/tmp/z6x_snapsync \\\n" +
                               "  -port 1704 \\\n" +
                               "  -control-port 1705 \\\n" +
                               "  -pipe /data/local/tmp/audio.fifo > /data/local/tmp/snap.log 2>&1 &\n\n" +
                               "# 2. 查看客户端接入与时间对齐状态\n" +
                               "cat /data/local/tmp/snap.log",
                        ExpectedOutput = "[SnapSync] Server listening on :1704 (Stream: 48000:16:2, Codec: FLAC)\n[Client] Connected: LivingRoom_Phone (Latency: 1.8ms, Buffer: 1000ms)\n[Client] Connected: Bedroom_Speaker (Latency: 2.4ms, Buffer: 1000ms)\n[Sync] Clock offset calibrated to within 0.2ms [PASS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "FIFO 管道阻塞防范：如果写入端未打开，读取命名管道可能会挂起，程序需以 `os.O_RDWR` 方式打开 FIFO 避免因为没有写入源导致服务端启动阻塞。",
                    "Wi-Fi 多播性能问题：多端同步数据推荐使用 TCP 单播广播（Snapcast 原生设计），避免无线路由器在 Wi-Fi 多播速率受限时造成音频丢包卡顿。"
                ]
            }
        ]
    };
}
