using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class WebRtcSignalingScreenShareHubData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "webrtc-signaling-screen-share-hub",
        Title = "56. 自研 WebRTC 浏览器免插件低延迟信令服务端：网页投屏网关（Z6X WebRtcHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的 WebRTC 房间与 SDP 信令交换网关，实现电脑/手机浏览器免安装任何客户端直接向电视低延迟投屏，常驻内存仅 18MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "访客或朋友使用电脑向极米投屏进行 PPT 汇报或照片展示时，经常遇到对方电脑未安装投屏软件、或者系统的 AirPlay/Miracast 搜不到设备的尴尬情况。\n\n" +
                       "使用 Go 自研的 WebRTC 信令网关，电视屏幕展示一个简易网页地址或二维码（如 `http://192.168.1.100:8095`）。访客用 Chrome/Safari 扫码打开网页，点击“共享屏幕”即可通过标准 WebRTC 协议将画面和声音推送到极米播放，端到端延迟控制在 100ms 以内，常驻物理内存约 18MB。",
                BulletPoints =
                [
                    "浏览器免插件投屏：只要有现代浏览器即可发起屏幕共享，跨平台兼容度高。",
                    "超低点对点延迟：基于 WebRTC 原生 UDP/SRTP 传输，延迟仅 80ms~150ms。",
                    "开箱即用信令交换：内置 WebSocket 信令中继与简易 STUN 穿透支持。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `pion/webrtc/v3`（纯 Go 实现的完整 WebRTC 协议栈）+ WebSocket 信令服务器。",
                    "纯 Go 原生解包：无需任何 C++ libwebrtc 繁琐编译，利用 Pion 直接解析 VP8/VP9/H264 与 Opus 编码的 RTP 数据包并送交本地播放管道。",
                    "房间状态快速收敛：单房间单会话设计，后进入的推流端自动抢占或拒绝，避免多设备信令并发错乱。",
                    "【参考开源项目】pion/webrtc（Go 生态成熟 WebRTC 实现）；livekit（基于 Pion 的实时音视频架构参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 WebRTC 投屏网关并查看信令握手状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 WebRTC 投屏信令与媒体接收端",
                        Code = "# 1. 启动 WebRTC 服务（监听 8095 端口，包含静态投屏引导页）\n" +
                               "nohup /data/local/tmp/z6x_webrtc \\\n" +
                               "  -port 8095 \\\n" +
                               "  -udp-mux 8096 > /data/local/tmp/webrtc.log 2>&1 &\n\n" +
                               "# 2. 查看监听状态与房间连接回执\n" +
                               "cat /data/local/tmp/webrtc.log",
                        ExpectedOutput = "[WebRTC] Signaling server ready at http://0.0.0.0:8095\n[ICE] UDP Single-port Mux opened on :8096\n[PeerConnection] Received SDP Offer from 192.168.1.105 (Chrome 122)\n[Media] Incoming Track: video (H264, 1920x1080@30fps) -> State: Connected"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "HTTPS 限制与本地安全上下文：现代浏览器（除 localhost 外）仅在 HTTPS 协议下允许调用 `getDisplayMedia` 屏幕录制 API，生产环境需配置内置自签名或有效 SSL 证书访问。",
                    "UDP 单端口复用（UDPMux）：为避免为每个连接开启随机 UDP 端口触发防火墙拦截，服务强制开启 `UDPMux` 将所有 WebRTC 媒体流收束至单一 UDP 8096 端口。"
                ]
            }
        ]
    };
}
