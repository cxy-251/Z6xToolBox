using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class ScreenMiracastSignalingRelayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "screen-miracast-signaling-relay",
        Title = "40. 自研 RTSP/RTP 投屏会话协商与 SDP 信令中继代理：投屏链路管理（Z6X CastRelay）",
        Group = "Go原生服务",
        Summary = "纯 Go 开发的 RTSP 会话控制与 SDP 信令中继代理，规范化管理投屏协议握手生命周期与心跳保活，常驻内存仅 14MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米在接收来自 Windows 或第三方设备的无线投屏（Miracast / WFD / 自定义 RTSP 流）时，因网络抖动或信令格式微小偏差，经常出现“已连接却黑屏无画面”或“投屏 10 分钟自动断开”的假死问题。\n\n" +
                       "使用 Go 自研的投屏信令中继代理，监听 TCP 554 或 7236 端口，接管投屏客户端与本地播放器之间的 RTSP 信令交互（OPTIONS、DESCRIBE、SETUP、PLAY、TEARDOWN）。修正不规范的 SDP 参数并维持双向心跳（Keep-Alive），确保投屏链路稳定，常驻物理内存约 14MB。",
                BulletPoints =
                [
                    "投屏信令清洗与纠错：修正第三方客户端不标准的 SDP 媒体描述参数。",
                    "自动心跳续期防断流：定时发送 RTSP GET_PARAMETER 保持投屏通道不被系统超时关闭。",
                    "解耦信令与媒体流：信令走 Go 协程管理，音视频裸数据包直通本地播放器。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `aler9/gortsplib/v2`（轻量 RTSP 协议栈）+ 纯 Go 状态机模型。",
                    "CSeq 事务序号自动对齐：透明处理客户端与服务端的 CSeq 匹配与状态迁移，避免因乱序报文导致投屏握手终止。",
                    "RTP/RTCP 端口动态分配：在 SETUP 阶段动态向内核申请一对偶数/奇数非特权端口（如 15550/15551），精准引导音视频流流向本地接收端口。",
                    "【参考开源项目】gortsplib（Go 生态工业标准 RTSP 库）；miraclecast（Linux Miracast 开源实现参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 RTSP 投屏信令代理并抓取握手交互：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动投屏信令代理服务",
                        Code = "# 1. 启动轻量 RTSP 信令代理（监听 7236 端口，转发至本地播放器 5544）\n" +
                               "nohup /data/local/tmp/z6x_cast_relay \\\n" +
                               "  -listen :7236 \\\n" +
                               "  -target 127.0.0.1:5544 > /data/local/tmp/cast_relay.log 2>&1 &\n\n" +
                               "# 2. 查看投屏握手与信令交互日志\n" +
                               "tail -n 10 /data/local/tmp/cast_relay.log",
                        ExpectedOutput = "[RTSP] Listening for cast signaling on :7236\n[Session] Handled OPTIONS/DESCRIBE from 192.168.1.105 (Client: Windows 11 WFD)\n[SDP] Sanitized video format: H264 1920x1080@60fps -> State: PLAYING"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "WFD 与普通 RTSP 差异：Miracast/WFD 在标准 RTSP 基础上扩展了 `wfd_*` 私有头部，代理需开启未知头部透明透传模式，防止因严格语法校验而抛弃报文。",
                    "端口占用检查：Android 系统某些自带乐播投屏等应用可能会在开机后抢占 7236 端口，启动前需确认端口可用或在客户端指定自定义端口。"
                ]
            }
        ]
    };
}
