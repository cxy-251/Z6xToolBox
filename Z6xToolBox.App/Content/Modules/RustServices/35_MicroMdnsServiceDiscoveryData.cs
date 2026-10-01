using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class MicroMdnsServiceDiscoveryData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "micro-mdns-service-discovery",
        Title = "35. 自研轻量 mDNS/DNS-SD 局域网服务发现与广播器：多播 UDP 零配置网络（Z6X RustMdnsAnnouncer）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的零配置 mDNS 广播守护器，自动在局域网宣布极米上的 WebDAV、SSH 与媒体服务，常驻内存仅 1MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米投影仪在开启了 WebDAV、SSH 或文件共享后，如果局域网 IP 发生变动，电脑或手机必须重新查找 IP 并修改连接地址。Linux 传统的 Avahi-daemon 依赖 D-Bus 与多个守护进程，在 Android 上极难移植且内存开销大。\n\n" +
                       "使用 Rust 自研的轻量 mDNS 广播守护服务，直接加入 `224.0.0.251:5353` 多播组，响应来自局域网的 Apple Bonjour / Android NSD 查询请求。自动将 `z6x.local` 域名解析到当前 Wi-Fi IP，并广播 `_webdav._tcp`、`_ssh._tcp` 服务记录，常驻物理内存仅约 1MB。",
                BulletPoints =
                [
                    "零配置域名解析：局域网设备直接通过 `z6x.local` 访问电视，无需记住动态 IP。",
                    "自动多播服务声明：手机端文件管理器可直接在“发现附近设备”中找到极米。",
                    "轻量独立交付：无需 D-Bus 与复杂守护组件，单个 Rust 二进制直接工作。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `mdns-sd`（基于 mio 的异步多播实现）+ IP_ADD_MEMBERSHIP 套接字选项。",
                    "响应防风暴机制：遵循 RFC 6762 协议规范，内置随机截断延迟（20ms~120ms）响应查询，防止局域网多播风暴。",
                    "IP 变更自动重新广播：检测到本地网卡 IP 重新获取（DHCP 续租）时，自动广播 Goodbye 包并重新宣告新地址记录。",
                    "【参考开源项目】avahi（经典 Linux mDNS 实现，本模块为其微型纯 Rust 代替版本）；mdns-sd（Rust 生态主流服务发现库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 mDNS 广播并在 PC 端发现服务：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 mDNS 服务宣告并在电脑端验证发现",
                        Code = "# 1. 启动 mDNS 广播守护（宣告本机主机名为 z6x，开放 WebDAV 与 SSH）\n" +
                               "nohup /data/local/tmp/z6x_mdns \\\n" +
                               "  -hostname z6x \\\n" +
                               "  -services \"_webdav._tcp:8080,_ssh._tcp:2222\" > /data/local/tmp/mdns.log 2>&1 &\n\n" +
                               "# 2. PC 端（Linux/macOS）通过 avahi-browse 或 dns-sd 探测\n" +
                               "avahi-browse -rt _webdav._tcp\n\n" +
                               "# 3. 直接通过主机名 ping 连通性\n" +
                               "ping z6x.local",
                        ExpectedOutput = "+ wlan0 IPv4 z6x WebDAV File Service  _webdav._tcp local\n= wlan0 IPv4 z6x WebDAV File Service  _webdav._tcp local\n  hostname = [z6x.local]\n  address = [192.168.1.100]\n  port = [8080]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "路由器 AP 隔离与 IGMP Snooping：部分家庭路由器默认开启了 Wi-Fi 客户端隔离或未开启 IGMP 组播侦听，可能导致 224.0.0.251 多播包无法跨设备穿透，需在路由器后台关闭 AP 隔离。",
                    "Wi-Fi 多播锁（MulticastLock）：Android 系统为了省电可能会在息屏时丢弃无线网卡组播包，需保证投影仪处于工作状态或在后台持有组播套接字。"
                ]
            }
        ]
    };
}
