using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class TcpdumpNetworkPacketCaptureData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "tcpdump-network-packet-capture",
        Title = "8. 网络通信与抓包排错：静态 tcpdump 部署与流量分析",
        Group = "开发环境",
        Summary = "部署纯静态 ARM64 版 tcpdump 网络分析工具，排查局域网微服务握手失败、丢包与投屏协议异常，支持导出 pcap 报文在 Wireshark 中深入分析。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在调试自己开发的网络服务（如 WebDAV、DLNA、MQTT、自定义 Webhook）时，常常遇到「客户端提示连接超时、但不知道数据包到底有没有进极米网卡」的窘境。仅靠在应用代码里打印日志，很难排查三次握手异常、TCP 乱序重传或路由器防火墙丢包。\n\n" +
                       "部署静态 `tcpdump` 后，可在极米无线网卡（`wlan0`）入口处直接抓取底层数据帧，清晰透视每一个 TCP SYN 握手、HTTP 请求头与 DNS 应答报文，并能导出标准 `.pcap` 文件导入 PC 端的 Wireshark 进行图形化分析。",
                BulletPoints =
                [
                    "底层网络透视：直接确认局域网请求是否穿透路由器到达极米网卡。",
                    "抓取协议报文细节：分析投屏协议（SSDP/SOAP）、m3u8 直链防盗链请求头的真实交互。",
                    "生成标准 pcap 抓包库：导出文件后在 PC 端 Wireshark 中进行图形化时序回放。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：C 语言编写、由 musl 静态链接编译的 ARM64 `tcpdump` + 嵌入式 `libpcap` 库（单文件约 2.2MB）。",
                    "什么是原始套接字（Packet Socket）抓包？网络协议栈通常将底层协议头剥离后才交给应用层。libpcap 通过 Linux 提供的 `AF_PACKET` 原始套接字与 BPF（Berkeley Packet Filter）内核过滤器，在网卡驱动刚收到以太网帧时直接复制一份副本，不影响业务程序正常处理。",
                    "BPF 高性能过滤语法：只抓取关注端口的流量（如 `port 8085 or port 8096`），避免大量无用局域网广播包淹没调试日志。",
                    "【参考开源项目】tcpdump & libpcap 官方源码库（网络工程师必备的标准抓包底座）。"
                ]
            },
            new ContentSection
            {
                Heading = "常用抓包诊断命令集",
                Text = "实机常用网络排错指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "典型网络排错与抓包指令",
                        Code = "# 1. 实时监听 8085 端口的所有 TCP 通信头（排查 WebDAV 请求）\n" +
                               "/data/local/tmp/bin/tcpdump -i wlan0 -nn -s0 -A 'tcp port 8085'\n\n" +
                               "# 2. 抓取投屏 SSDP 组播发现报文（UDP 1900 端口）\n" +
                               "/data/local/tmp/bin/tcpdump -i wlan0 -nn 'udp port 1900'\n\n" +
                               "# 3. 抓取 100 个数据包保存为 pcap 文件，供 Wireshark 分析\n" +
                               "/data/local/tmp/bin/tcpdump -i wlan0 -c 100 -w /data/local/tmp/traffic.pcap 'port 8088'",
                        ExpectedOutput = "listening on wlan0, link-type EN10MB (Ethernet), snapshot length 262144 bytes\n100 packets captured"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "tcpdump 运维排坑指南：",
                BulletPoints =
                [
                    "问题 1：报错 socket: Operation not permitted。原因与解决：Android 内核严格限制普通 UID 创建 `AF_PACKET` 原始套接字。在非 Root 的 shell 环境下，若内核 SELinux 策略阻止了 shell 域使用 raw socket，可通过测试端口监听（`nc -l`）结合外部 PC 发送端单向抓包作为补充验证手段。",
                    "问题 2：长时间抓包耗尽闪存。使用 `-c <数量>` 限制抓包帧数，或使用 `-C <兆字节>` 启用循环滚动写入。"
                ]
            }
        ]
    };
}
