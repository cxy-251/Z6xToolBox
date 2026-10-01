using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LanDnsDhcpServerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lan-dns-dhcp-server",
        Title = "36. 自研局域网轻量 DHCP 备用分配与静态 IP 绑定服务：应急网络分配（Z6X DhcpLite）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型 DHCPv4 协议服务，作为家庭主路由宕机或直连电脑调试时的应急 IP 分配网关，常驻内存仅 9MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "当家庭主路由器故障重启、或者用户用网线将极米与电脑/笔记本进行点对点直连调试时，由于缺少 DHCP 服务器，两端无法自动获取 IP 地址，必须手动配置复杂的静态网段。\n\n" +
                       "使用 Go 自研的轻量 DHCP 服务，监听 UDP 67/68 端口（非 root 模式可配合自定义端口或在具备网络能力时启用）。支持快速响应 DHCP Discover 广播，按预设地址池分配 IP，支持 MAC 地址与固定 IP 绑定，常驻物理内存仅约 9MB。",
                BulletPoints =
                [
                    "点对点直连零配置：网线直接连电脑时自动分配 IP，免去手动设静态 IP 的繁琐。",
                    "轻量单二进制：无需部署 isc-dhcp-server 或 dnsmasq 等重型服务。",
                    "MAC 静态绑定表：可在配置文件中预设电脑、手机的固定 IP，便于统一管理。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `insomniac/dhcp`（纯 Go DHCPv4 协议解析库）+ UDP 原始套接字广播支持。",
                    "ARP 冲突主动探测：在向客户端下发 IP（DHCP Offer）前，自动发送 ARP Request 探测网络中是否已有相同 IP，防止 IP 地址冲突。",
                    "租约内存表持久化：租约表以紧凑 JSON 格式在分配成功后异步落盘，重启后自动载入未过期的租约记录。",
                    "【参考开源项目】core-dhcp（模块化 Go DHCP 引擎）；dnsmasq（经典嵌入式 DNS/DHCP 工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动轻量 DHCP 服务并查看分配状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动应急 DHCP 服务并查看租约",
                        Code = "# 1. 启动 DHCP 服务（分配 192.168.10.100-192.168.10.200 网段）\n" +
                               "nohup /data/local/tmp/z6x_dhcp \\\n" +
                               "  -interface eth0 \\\n" +
                               "  -range 192.168.10.100,192.168.10.200 \\\n" +
                               "  -router 192.168.10.1 > /data/local/tmp/dhcp.log 2>&1 &\n\n" +
                               "# 2. 查看已分配的客户端租约表\n" +
                               "cat /data/local/tmp/dhcp.leases",
                        ExpectedOutput = "[DHCP] Listening on eth0 (Pool: 192.168.10.100 - 192.168.10.200)\n[Lease] Assigned 192.168.10.105 to MAC e4:5f:01:8b:22:90 (Hostname: 'Deck-Console', 86400s)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "非特权模式端口限制：标准 DHCP 端口 67 属于特权端口，若在非 root shell 下运行，需通过 Linux 能力集授权 `setcap cap_net_bind_service=+ep` 或在本地桥接网络运行。",
                    "局域网多 DHCP 服务器冲突：在正常家庭网络下切勿与主路由器同时开启 DHCP，本服务专用于主路由故障应急或网线直连隔离网段。"
                ]
            }
        ]
    };
}
