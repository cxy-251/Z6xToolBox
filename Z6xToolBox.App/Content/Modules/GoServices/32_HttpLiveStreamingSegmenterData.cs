using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class HttpLiveStreamingSegmenterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "http-live-streaming-segmenter",
        Title = "32. 自研 HLS 实时流切片与 m3u8 索引生成器：TS 分片与跨端点播（Z6X HlsServer）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型 HLS 流媒体切片与分发服务，将本地视频实时拆解为 TS/fMP4 切片并输出 m3u8 列表，常驻内存仅 20MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "如果想把极米外接 U 盘里的高码率视频推送到手机或 iPad 的 Safari 浏览器上播放，由于苹果设备对 MKV/AVI 等封装格式不直接兼容，通常需要转封装为 HLS（HTTP Live Streaming）协议。但传统方案依赖全量 FFmpeg 二进制，体积大且转码非常耗 CPU。\n\n" +
                       "使用 Go 自研的轻量 HLS 切片分发服务，无需转码画面（对 H.264/H.265 直接做流式解复用与 TS/fMP4 重新打包封装）。实时切出 6 秒小分片并动态生成 `.m3u8` 播放列表。局域网 iOS、Android 与浏览器端均可秒开播放，常驻物理内存约 20MB。",
                BulletPoints =
                [
                    "纯转封装零 CPU 转码：原画直通重打包，单核 CPU 占用 < 2%，画面不失真。",
                    "广泛设备兼容：生成的标准 m3u8 列表直接兼容各类原生移动端播放器。",
                    "轻量开箱即用：纯 Go 代码实现解复用与封装，无需外部 FFmpeg 工具包。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `aler9/gortsplib` 底层流解析 + 内存滑动窗口分片缓冲（单二进制体积约 16MB）。",
                    "流式解复用（Demuxing）：直接在内存流中解析 MP4/MKV 的 NALU（网络抽象层单元）与 AAC 音频帧，按关键帧（IDR）边界对齐切分 TS 切片。",
                    "环形切片缓存淘汰：仅在内存中保留最近 5 个 TS 片段，历史切片自动随时间窗口淘汰释放，避免长时间推流导致内存膨胀。",
                    "【参考开源项目】mediamtx（Go 生态主流低延迟流媒体服务器）；livego（纯 Go 直播流中继平台）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 HLS 分发服务并在浏览器测试播放：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 HLS 切片服务并请求 m3u8 列表",
                        Code = "# 1. 启动 HLS 服务（监听 8888 端口，映射 U 盘视频目录）\n" +
                               "nohup /data/local/tmp/z6x_hls \\\n" +
                               "  -port 8888 \\\n" +
                               "  -dir /mnt/media_rw/USB_DISK/Movies > /data/local/tmp/hls.log 2>&1 &\n\n" +
                               "# 2. 请求特定视频的 m3u8 播放清单\n" +
                               "curl -s http://192.168.1.100:8888/stream/sample.mp4/index.m3u8",
                        ExpectedOutput = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:6.000,\nsegment_0.ts\n#EXTINF:6.000,\nsegment_1.ts"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "非标准编码兼容排查：如果原视频使用了较老的 VC-1 或 MPEG-2 编码，Safari 无法硬解 TS 中的非 H.264/HEVC 码流，此方案专为主流 AVC/HEVC 视频设计。",
                    "切片网络并发压力：多端同时拖动进度条可能触发多路快速解复用，需在服务内部限制单个视频的最大并发切片协程数为 4。"
                ]
            }
        ]
    };
}
