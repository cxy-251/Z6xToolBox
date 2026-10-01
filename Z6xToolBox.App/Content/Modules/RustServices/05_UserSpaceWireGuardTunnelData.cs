using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class UserSpaceWireGuardTunnelData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "user-space-wireguard-tunnel",
        Title = "5. 自研纯用户态轻量加密隧道：smoltcp 协议栈与免 Root 组网（Z6X RustTunnel）",
        Group = "Rust原生服务",
        Summary = "基于 Rust 纯用户态 TCP/IP 协议栈自研的轻量加密隧道，无需 Android 内核 TUN/TAP 虚拟网卡设备权限即可建立点对点安全互联。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米 Z6X Pro（非 Root 状态）上，系统严格限制创建 `/dev/net/tun` 虚拟网络设备，导致标准的 VPN 或组网客户端（如 WireGuard、OpenVPN）无法直接启动网卡隧道。\n\n" +
                       "使用 Rust 配合纯用户态 TCP/IP 协议栈（`smoltcp`）后，所有网络包的打包、加密和解密均在用户态内存中完成。对外伪装为一个普通的非特权 UDP/TCP 套接字，无需任何 root 权限即可实现家庭内网设备之间的安全加密通信，内存占用低至 3MB 左右。",
                BulletPoints =
                [
                    "完全绕过 Root 权限限制：纯用户空间重构网络栈，零内核模块依赖。",
                    "低内存高吞吐：单文件无运行时开销，物理内存常驻仅 3MB ~ 5MB。",
                    "加密传输抗审查：基于 ChaCha20-Poly1305 算法快速加解密，保障跨公网数据传输安全。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `smoltcp`（独立于 OS 内核的纯 Rust 用户态网络栈）+ `boringtun`（Cloudflare 开源的 Rust 版 WireGuard 核心）。",
                    "什么是用户态网络协议栈（Userspace Networking）？操作系统内核通常全权负责 IP 路由与 TCP 状态机维护；smoltcp 将整套协议栈以纯 Rust 代码实现在进程内部，直接处理二进制数据包流，通过普通的 `UdpSocket` 接收和发送，彻底摆脱内核网卡设备依赖。",
                    "为什么比 Go 版实现更轻？Go 实现类似逻辑（如 WireGuard-Go）需频繁在 Goroutine 之间传递 `[]byte` 切片引发 GC 压力；Rust 借助 `bytes` 的零拷贝切片（Zero-copy slice）机制，加解密与报文转发全程零内存分配。",
                    "【参考开源项目】boringtun（Cloudflare 官方纯 Rust 用户态 WireGuard 引擎）；smoltcp（嵌入式领域首选的纯 Rust 独立网络协议栈）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动用户态加密隧道：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动用户态隧道服务",
                        Code = "# 1. 启动纯用户态加密中继（监听 UDP 51820 端口）\n" +
                               "nohup /data/local/tmp/z6x_tunnel \\\n" +
                               "  -config /data/local/tmp/tunnel.conf \\\n" +
                               "  -port 51820 > /data/local/tmp/tunnel.log 2>&1 &\n\n" +
                               "# 2. 本地查看隧道握手与连通状态\n" +
                               "curl -s http://127.0.0.1:51821/status",
                        ExpectedOutput = "{\"status\":\"connected\",\"peer\":\"10.0.0.2\",\"tx_bytes\":1048576,\"rx_bytes\":2097152}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "用户态隧道排坑指南：",
                BulletPoints =
                [
                    "问题 1：51820 端口监听受限。若被运营商阻断或系统占用，在配置文件中更换为任意 1024 以上的非特权 UDP 高位端口（如 41820）。",
                    "问题 2：MTU 分片丢包。由于加密报文头会占用额外字节，需在配置中将虚拟接口 MTU 设为 1280 或 1360，避免跨公网传输时因 IP 分片被路由器强行丢弃。"
                ]
            }
        ]
    };
}
