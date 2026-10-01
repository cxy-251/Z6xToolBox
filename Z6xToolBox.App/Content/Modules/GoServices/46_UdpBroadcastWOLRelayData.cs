using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class UdpBroadcastWOLRelayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "udp-broadcast-wol-relay",
        Title = "46. 自研跨 VLAN 网络唤醒魔术包转发代理：子网广播穿透（Z6X WolRelay）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的 Wake-on-LAN 代理转发器，接收来自公网 VPN 或不同 VLAN 的单播唤醒请求并以二层广播形式在本地局域网转发，常驻内存仅 8MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Wake-on-LAN（网络唤醒）依赖 UDP 端口 7/9 的二层全网广播包（`255.255.255.255` 或子网广播地址）。路由器基于安全策略通常严禁将外部单播请求直接转化为内部广播包（Directed Broadcast），导致在外网或跨 VLAN 时无法远程唤醒家里的台式电脑或 NAS。\n\n" +
                       "极米投影仪通常处于家庭内网核心网段中，使用 Go 自研的轻量唤醒中继器，在极米上监听 HTTP/UDP 单播管理端口。当接收到经过密钥鉴权的唤醒指令时，在极米本地网络接口构造 102 字节魔术包并向局域网广播分发，成功唤醒目标机器，常驻物理内存仅约 8MB。",
                BulletPoints =
                [
                    "突破跨子网广播隔离：允许从外部网络或访客 Wi-Fi 远程唤醒主内网机器。",
                    "具备鉴权防误触发：支持基于预共享密钥（PSK）验证，防止局域网恶意广播乱唤醒。",
                    "单二进制开箱即用：体积仅 8MB，无需在路由器上刷第三方梅林/OpenWrt 固件。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net` 原生套接字（SO_BROADCAST 标志位开启）。",
                    "魔术包格式封装：严格遵循 AMD WOL 标准规范，包头 6 个字节 `0xFF`，紧接着目标网卡 6 字节 MAC 地址连续重复 16 次构成载荷。",
                    "双接口广播自适应：自动遍历极米的以太网（eth0）与无线网卡（wlan0），计算各自真实的子网广播地址（如 `192.168.1.255`），避免部分路由器屏蔽 `255.255.255.255` 全网广播。",
                    "【参考开源项目】wol（标准 Linux 网络唤醒命令行）；etherwake（经典 C 语言唤醒工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动唤醒中继并向目标 MAC 发送唤醒指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动网络唤醒中继并触发电脑开机",
                        Code = "# 1. 启动轻量唤醒代理服务（监听 9099 端口）\n" +
                               "nohup /data/local/tmp/z6x_wol_relay \\\n" +
                               "  -port 9099 \\\n" +
                               "  -secret \"wol_token_123\" > /data/local/tmp/wol.log 2>&1 &\n\n" +
                               "# 2. 发送 HTTP 单播请求唤醒目标 NAS（MAC: 00:11:32:8a:bc:de）\n" +
                               "curl -X POST http://192.168.1.100:9099/wake \\\n" +
                               "  -d \"mac=00:11:32:8a:bc:de&secret=wol_token_123\"",
                        ExpectedOutput = "{\"status\":\"ok\",\"target_mac\":\"00:11:32:8a:bc:de\",\"broadcast_ip\":\"192.168.1.255\"}\n[WOL] Magic packet sent successfully (102 bytes) via wlan0"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "SO_BROADCAST 套接字选项设置：在 Linux 下向广播地址发送 UDP 数据报必须显式配置套接字选项，否则内核会抛出 `permission denied` 异常。",
                    "休眠状态目标设备 ARP 丢失：目标电脑彻底关机后，路由器 ARP 表通常在数分钟内过期，因此发送唤醒包必须使用子网定向广播（如 `192.168.1.255`）而不能使用单播 IP 发送。"
                ]
            }
        ]
    };
}
