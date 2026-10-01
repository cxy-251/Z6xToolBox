using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class UPnPPortMappingManagerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "upnp-port-mapping-manager",
        Title = "27. 自研 UPnP/IGD 与 NAT-PMP 自动端口映射网关：路由器穿透（Z6X PortMap）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的局域网端口自动映射守护服务，通过 UPnP IGD 与 Apple NAT-PMP 协议向家庭路由器自动申请公网端口转发，常驻内存仅 10MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米上部署了 WebDAV、Samba 或远程管理服务后，如果想在外网访问，用户通常需要手动登录光猫或路由器后台配置端口转发。当路由器重启导致内网 IP 变动时，端口映射会随之失效。\n\n" +
                       "使用 Go 自研的端口映射服务，自动向局域网网关发起 SSDP 广播发现 IGD（Internet Gateway Device）设备，通过 SOAP XML 协议或 NAT-PMP 协议动态请求将外部端口（如外网 28080）自动映射至极米本地对应服务端口。具备心跳续租与公网 IP 变动探测机制，常驻物理内存约 10MB。",
                BulletPoints =
                [
                    "自动打通路由器外网端口：无需人工手动进入路由器管理页面一条条填写映射规则。",
                    "IP 变动自动重新绑定：极米 DHCP IP 或路由器公网 IP 发生变更时，秒级自动更新映射。",
                    "租约到期自动续约：定期向路由器刷新租期（Lease Duration），防止规则失效。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `huin/goupnp` + `jackpal/gateway` + UDP 1900 组播发现。",
                    "多协议兼容探测：优先使用标准 UPnP IGD 1.0/2.0，若路由器关闭 UPnP 则自动降级探测 NAT-PMP（UDP 5351 端口），覆盖各类家用与商用网关。",
                    "轻量生命周期同步：主程序优雅退出时，主动向路由器发送 `DeletePortMapping` 释放公网端口，防止路由器残留僵尸规则占用端口表。",
                    "【参考开源项目】miniupnpc（C 语言经典 UPnP 客户端）；goupnp（Go 原生 UPnP 协议实现库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动自动映射服务并检查路由器映射表：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 UPnP 端口映射并在控制台输出外网地址",
                        Code = "# 1. 启动映射守护进程（将本地 8080 与 2222 映射至路由器外部）\n" +
                               "nohup /data/local/tmp/z6x_portmap \\\n" +
                               "  -rules \"tcp:8080:28080,tcp:2222:22222\" \\\n" +
                               "  -lease 3600 > /data/local/tmp/portmap.log 2>&1 &\n\n" +
                               "# 2. 查看网关发现结果与映射回显\n" +
                               "cat /data/local/tmp/portmap.log",
                        ExpectedOutput = "[UPnP] Found Gateway: ASUS RT-AX86U (IP: 192.168.1.1)\n[UPnP] External IP: 114.248.82.19\n[UPnP] Mapped TCP 192.168.1.100:8080 -> 114.248.82.19:28080 (Lease: 3600s) [OK]\n[UPnP] Mapped TCP 192.168.1.100:2222 -> 114.248.82.19:22222 (Lease: 3600s) [OK]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "路由器 UPnP 功能关闭排查：部分运营商定制光猫或安全固件默认禁用了 UPnP，此时日志会报错 `No IGD found`，需进入路由器管理页面手动开启 UPnP 功能开关。",
                    "大内网 CGNAT 限制：如果宽带没有分配独立公网 IPv4 地址（处于 100.64.0.0/10 运营商级 NAT 内），即使映射成功也无法直接从公网访问，需结合 IPv6 或内网穿透方案。"
                ]
            }
        ]
    };
}
