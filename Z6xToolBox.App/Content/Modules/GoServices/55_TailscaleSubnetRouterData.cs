using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class TailscaleSubnetRouterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "tailscale-subnet-router",
        Title = "55. 自研 Tailscale 用户态子网路由广播网关：全屋设备穿透（Z6X SubnetGate）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的用户态子网路由代理，将极米作为家庭网络跳板向 Tailscale 宣告全国内网网段，常驻内存仅 20MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在外出时，如果想访问家庭局域网里的打印机、智能插座或未安装 Tailscale 客户端的老旧设备，通常需要在软路由或专门的工控机上配置子网路由（Subnet Router）。在非 root 的 Android 设备上，开启内核级 IP 转发和 iptables NAT 会受到严格权限阻断。\n\n" +
                       "使用 Go 自研的用户态子网路由服务，基于 Tailscale 官方提供的纯用户态模式（`tsnet`）。无需创建内核 TUN 网卡与配置系统 iptables，直接在用户空间充当 TCP/UDP SOCKS5 与双向连接代理，向个人虚拟网络宣告家庭 `192.168.1.0/24` 子网，常驻物理内存约 20MB。",
                BulletPoints =
                [
                    "非 root 环境免 TUN 运行：基于 `tsnet` 纯应用层运行，绕过系统 SELinux 网卡限制。",
                    "全屋子网一键穿透：在外网直接 ping 或访问家中任意内网 IP。",
                    "自动打洞与节点协商：支持标准 WireGuard 协议加密与 NAT 穿透握手。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `tailscale.com/tsnet` + `tailscale.com/net/netns`（用户态网络命名空间）。",
                    "用户态 TCP 代理转发：在虚拟网内拦截发往目标内网子网的 TCP 连接请求，由 Go 协程在宿主机网络（wlan0）中通过标准 `net.Dial` 建立真实连接并双向复制数据流。",
                    "状态机持久化：将生成的节点加密私钥保存在 `/data/local/tmp/tailscale_state`，重启后无需重新扫码授权登录。",
                    "【参考开源项目】tailscale（官方核心仓库中的 tsnet 嵌入式库）；wireguard-go（Go 语言 WireGuard 基础实现）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动用户态子网路由并查看宣告状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Tailscale 用户态子网路由",
                        Code = "# 1. 启动用户态子网代理（使用 AuthKey 认证并宣告 192.168.1.0/24 网段）\n" +
                               "nohup /data/local/tmp/z6x_subnet \\\n" +
                               "  -authkey \"tskey-auth-xxxxx\" \\\n" +
                               "  -advertise-routes \"192.168.1.0/24\" \\\n" +
                               "  -hostname \"z6x-gateway\" > /data/local/tmp/subnet.log 2>&1 &\n\n" +
                               "# 2. 查看节点连接与子网就绪日志\n" +
                               "tail -n 10 /data/local/tmp/subnet.log",
                        ExpectedOutput = "[tsnet] Starting userspace engine on 100.64.12.34 (Hostname: z6x-gateway)\n[tsnet] Connected to DERP region 1 (Beijing)\n[Route] Advertised subnet: 192.168.1.0/24 (Mode: Userspace NAT proxy) [ONLINE]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "UDP 广播与 ICMP Ping 局限：用户态代理模式原生支持完整的 TCP 与 UDP 转发，但无法响应来自虚拟网的原生 ICMP Echo（Ping）请求，测试连通性建议使用 `curl` 或 `nc`。",
                    "AuthKey 需设置免审批：在 Tailscale 控制台需勾选该 Key 的 `Pre-authorized` 属性，并开启 Route Approval 自动审批，避免后台挂起等待网页确认。"
                ]
            }
        ]
    };
}
