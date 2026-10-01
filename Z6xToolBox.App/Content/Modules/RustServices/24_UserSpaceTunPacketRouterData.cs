using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class UserSpaceTunPacketRouterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "user-space-tun-packet-router",
        Title = "24. 自研动态虚拟 Tun 网卡 IP 流量分流器：用户态报文分发（Z6X RustTunRouter）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的用户态 TUN 虚拟网卡报文路由器，基于轻量网络栈 smoltcp 拦截并按目标 IP 分流，常驻内存仅 2.2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在受限环境下的 Android 电视系统中，常规 VPN 或代理客户端常驻内存通常需要 30MB ~ 80MB，且由于不断分配解析缓冲，极易被系统低内存杀死机制（LMK）清理掉。\n\n" +
                       "使用 Rust 自研的 TUN 虚拟设备路由器，直接通过 `/dev/tun` 设备接口读写原始 IP 报文，依托极简 TCP/IP 协议栈 smoltcp 进行无堆分配的数据包重组与分流。实现局域网直连、内网穿透与代理分流，常驻物理内存仅约 2.2MB。",
                BulletPoints =
                [
                    "微型网络协议栈：采用 smoltcp 纯嵌入式协议栈，无动态堆分配，内存安全固定。",
                    "用户态报文拆包与转发：在用户空间直接解析 IPv4 头部，按目标网段做策略路由。",
                    "低开销抗杀死：物理内存常驻仅 2.2MB，远低于传统代理客户端，有效免疫 LMK 杀进程。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `tun-rs` + `smoltcp`（no-std 模式网络栈）+ Linux IFF_TUN 标志位配置。",
                    "无动态分配的 Socket 表：smoltcp 使用预分配静态数组维护 TCP 连接状态，杜绝运行过程中的内存膨胀与内存碎片。",
                    "与传统代理客户端内存对比：传统客户端常驻内存 40MB ~ 70MB；RustTunRouter 内存稳定在 2.2MB，单核负载 < 2%。",
                    "【参考开源项目】smoltcp（针对裸机与嵌入式系统的紧凑 TCP/IP 栈）；tun2socks（经典 TUN 转 SOCKS 桥接工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 TUN 设备路由并测试流量劫持转发：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动用户态 TUN 报文路由器",
                        Code = "# 1. 启动轻量 TUN 路由服务并绑定上游代理\n" +
                               "nohup /data/local/tmp/z6x_tun_router \\\n" +
                               "  -dev tun0 \\\n" +
                               "  -upstream 127.0.0.1:1080 \\\n" +
                               "  -routes \"192.168.100.0/24\" > /data/local/tmp/tun.log 2>&1 &\n\n" +
                               "# 2. 查看虚拟接口状态与抓包日志\n" +
                               "cat /data/local/tmp/tun.log",
                        ExpectedOutput = "[TUN] Interface 'tun0' opened (MTU: 1500)\n[smoltcp] IP route 192.168.100.0/24 registered\n[TUN] Active session count: 3, MemRSS: 2248 kB"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "/dev/tun 设备访问权限：Android 默认对 `/dev/tun` 加了 SELinux 策略限制。如果在普通 adb shell 下打开报错，可通过系统的 VPNService 接口创建 fd 传递给 Rust，或者在具备适当组权限的环境下运行。",
                    "MTU 长度对齐：部分流媒体对 UDP 分片极其敏感，建议将 TUN 虚拟网卡的 MTU 显式指定为 1400 字节，防止数据包溢出被路由器静默丢弃。"
                ]
            }
        ]
    };
}
