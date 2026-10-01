using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LanForwardingProxyNodeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lan-forwarding-proxy-node",
        Title = "24. 自研局域网流量转发代理：轻量 SOCKS5/HTTP 隧道与内网分流（Z6X ProxyNode）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的轻量本地转发代理节点，为 Steam Deck、手机等设备提供局域网中继跳板与流量调度，免除复杂客户端配置。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在家庭局域网环境中，某些特殊设备（如 Steam Deck 掌机或复古游戏机）在下载特定资源或访问特定内网网段时，因无法安装复杂的代理客户端而经常面临连通困难。\n\n" +
                       "自研该代理中继节点后，极米在后台充当一个纯净的局域网流量跳板。Steam Deck 只需在网络设置里填入极米的 IP 与端口（如 `192.168.0.109:1080`），即可借由极米的网络栈实现流量透明中转与加速，不需要在掌机本地安装额外环境。",
                BulletPoints =
                [
                    "掌机/移动端零客户端配置：系统自带的代理设置直接填极米 IP，即填即用。",
                    "低功耗透明中继：纯 Go 高并发协程调度，千兆流量吞吐时 CPU 占用 < 3%。",
                    "双协议支持：同时支持标准的 SOCKS5 握手协议与通用 HTTP CONNECT 隧道代理。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net`（TCP 字节全双工转发）+ SOCKS5 RFC 1928 协议解析（单文件体积约 4MB）。",
                    "什么是全双工字节中继（`io.Copy` 管道）？当客户端（如 Steam Deck）发起 TCP 连接请求时，Go 服务解析目标地址，与远端服务器建立第二条 TCP 连接。随后启动两个协程通过 `io.Copy` 实现双向字节零拷贝传输，整个过程不解密、不缓存数据内容，保证最高吞吐与绝对隐私。",
                    "为什么适合跑在 Z6X Pro 上？由于只涉及底层 TCP 数据包转发，不涉及任何高负荷的音视频解码或加解密计算，物理内存占用恒定在 6MB ~ 10MB 之间。",
                    "【参考开源项目】go-socks5（HashiCorp 经典实现的纯 Go SOCKS5 协议库）；goproxy（纯 Go 打造的高性能 HTTP 代理框架）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动代理节点并通过 curl 测试代理转发：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动代理服务并测试中继转发",
                        Code = "# 1. 启动轻量 SOCKS5/HTTP 代理服务（监听 1080 端口）\n" +
                               "nohup /data/local/tmp/z6x_proxynode -port 1080 > /data/local/tmp/proxy.log 2>&1 &\n\n" +
                               "# 2. PC 终端指定通过极米代理发起 HTTP 请求测试\n" +
                               "curl -x socks5h://192.168.0.109:1080 http://myip.ipip.net",
                        ExpectedOutput = "当前 IP：... 来自于：..."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "代理节点排坑指南：",
                BulletPoints =
                [
                    "问题 1：连接句柄泄漏（too many open files）。原因与解决：长时间运行大量网络连接时，若远端关闭未及时释放 socket 会耗尽文件描述符。Go 代码中务必使用带有超时的 `net.Dialer`，并在连接断开时显式执行 `CloseRead()` 与 `CloseWrite()`。",
                    "问题 2：UDP 转发支持。普通 SOCKS5 代理若需支持某些联机游戏语音（UDP 协议），需开启 SOCKS5 UDP ASSOCIATE 模式。"
                ]
            }
        ]
    };
}
