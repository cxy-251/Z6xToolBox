using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class BilibiliLiveDanmuStreamRecorderData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "bilibili-live-danmu-stream-recorder",
        Title = "52. 自研直播间弹幕协议监听与直播流录制器：长连接解析（Z6X LiveRecord）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的直播流与弹幕同步录制服务，解析主流直播平台 WebSocket 协议并直接保存原始 FLV/HLS 流至 U 盘，常驻内存仅 18MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "经常有用户希望在夜间录制特定主播的发布会、游戏赛事或歌会直播，并在第二天通过大屏回看。如果用电脑开机挂着录制会产生较大电费与噪音，且录制下来的直播流常常缺失同步弹幕。\n\n" +
                       "使用 Go 自研的轻量直播录制服务，保持极米低功耗待机运行。服务基于 WebSocket 长连接监听直播平台弹幕服务器（解析 ProtoBuf/Brotli 数据包生成带时间轴的 `.xml` 弹幕文件），同时以流式写入将原始直播视频流直存外接 U 盘，常驻物理内存约 18MB。",
                BulletPoints =
                [
                    "开播自动监测录制：设定房间号后后台轮询，检测到开播即刻无感拉流落盘。",
                    "音画弹幕同步留存：生成标准 Ass/Xml 弹幕字幕，播放器回放时可开启弹幕飘过。",
                    "低功耗夜间作业：投影仪整机休眠仅保留 SoC 运行，耗电远低于 PC 主机。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `gorilla/websocket` + `andybalholm/brotli`（纯 Go 解压）+ HTTP 流式拉流器。",
                    "弹幕包协议拆包：按照平台 16 字节头部规范（PacketLen、HeaderLen、ProtoVer、OpCode、Seq）解码载荷，处理 Brotli 压缩数据帧，单次解码耗时 < 1ms。",
                    "流式写入与定额切片：支持按文件大小（如 2GB）或录制时长（如 1 小时）自动切分文件，避免单文件异常损坏导致全段录制丢失。",
                    "【参考开源项目】bilibili-live-api（纯 Go 直播协议封装）；BililiveRecorder（成熟直播录制参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动指定房间号录制并在控制台查看日志：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动直播间自动监听与录制服务",
                        Code = "# 1. 启动录制服务（监控房间号 123456，存入 U 盘）\n" +
                               "nohup /data/local/tmp/z6x_liverec \\\n" +
                               "  -room 123456 \\\n" +
                               "  -out /mnt/media_rw/USB_DISK/Live/ \\\n" +
                               "  -save-danmu > /data/local/tmp/liverec.log 2>&1 &\n\n" +
                               "# 2. 查看录制进度与弹幕抓取条数\n" +
                               "tail -n 10 /data/local/tmp/liverec.log",
                        ExpectedOutput = "[Live] Connected to room 123456 (Status: LIVE, Title: 'Official Event')\n[Stream] Recording 1080p60 stream to room_123456_20261001.flv\n[Danmu] Captured 1,420 danmaku messages -> written to room_123456_20261001.xml"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "U 盘写入性能瓶颈：高码率直播（如 20Mbps）持续写入时可能导致廉价 U 盘发热掉速，需配置内部 8MB 内存缓冲区吸收 I/O 抖动。",
                    "平台心跳保活机制：每 30 秒需向弹幕服务器发送特定的心跳包（Heartbeat Frame），否则会被服务端强行掐断连接。"
                ]
            }
        ]
    };
}
