using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class ZeroTierMoonPlanetaryRelayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zerotier-moon-planetary-relay",
        Title = "33. 自研私有 Tailscale DERP 与网状穿透中继节点：家庭中转辅助（Z6X DerpNode）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型私有 DERP（Designated Encrypted Relay for Packets）转发节点，帮助家庭局域网与外网设备在对称型 NAT 下建立打洞穿透，常驻内存仅 16MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在外出通过 Tailscale 或自建虚拟网访问家庭极米投影仪时，如果手机处于移动基站复杂的对称型 NAT（Symmetric NAT）环境，P2P 直连打洞往往会失败。此时若依赖官方位于海外的公用 DERP 服务器中继，延迟常高达 200ms 以上且经常断连。\n\n" +
                       "使用 Go 自研的轻量私有 DERP 中继服务，在极米本地监听非特权 HTTPS/WSS 端口（如 `:8443`）。作为家庭网络内的低延迟中转锚点，当外网手机与家庭其他设备无法打洞时，自动通过该中继线路由极米协助完成加密报文转发，延迟仅取决于家庭宽带实际 RTT，常驻物理内存约 16MB。",
                BulletPoints =
                [
                    "自建家庭私有中继：替代海外高延迟公共 DERP，改善打洞失败时的中转速度。",
                    "端到端加密零泄密：中继节点仅负责加密数据包盲转发，无法解密用户载荷。",
                    "低开销单文件交付：纯 Go 原生实现，剥离官方复杂的账号体系，仅保留核心中继功能。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `tailscale.com/derp`（核心中继协议）+ 自签名 TLS/Let's Encrypt 支持。",
                    "Packet Relay 零拷贝转发：解析数据包目的节点公钥后，直接在网络 Socket 间流式转交，不进行内存反序列化。",
                    "STUN 服务合设：内置 UDP 3478 STUN 端口，辅助外出设备快速探测自身的公网映射 IP 与端口变化规律。",
                    "【参考开源项目】derper（Tailscale 官方 DERP 中继实现）；headscale（开源自建协调服务）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动私有 DERP 中继并验证 STUN 连通性：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动私有 DERP 转发节点",
                        Code = "# 1. 启动轻量 DERP 节点（监听 8443 端口，STUN 端口 3478）\n" +
                               "nohup /data/local/tmp/z6x_derp \\\n" +
                               "  -addr :8443 \\\n" +
                               "  -stun-port 3478 \\\n" +
                               "  -cert-mode manual \\\n" +
                               "  -cert-dir /data/local/tmp/certs > /data/local/tmp/derp.log 2>&1 &\n\n" +
                               "# 2. 查看节点启动日志\n" +
                               "cat /data/local/tmp/derp.log",
                        ExpectedOutput = "[DERP] Server listening on https://0.0.0.0:8443\n[STUN] UDP STUN responder listening on :3478\n[Mesh] Registered local node pubkey: [48 bytes hex]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "公网证书验证：自建私有 DERP 通常使用自签名证书，客户端配置文件需显式指定 `InsecureForTests: true` 或导入私有 CA 证书。",
                    "宽带上行带宽占用：当中继多台设备传输大文件时会占满家庭宽带上行，可在启动参数中配置 `-max-bandwidth 30Mbps` 进行限速保护。"
                ]
            }
        ]
    };
}
