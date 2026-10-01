using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class SsdpUpnpDeviceScannerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "ssdp-upnp-device-scanner",
        Title = "53. 自研局域网 SSDP 设备发现与硬件拓扑探测器：组播侦听（Z6X LanScanner）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的局域网 UPnP/SSDP 设备拓扑扫描服务，实时发现并列举局域网内的智能电视、音响、NAS 与路由器，常驻内存仅 10MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在进行家庭多设备联动或投屏时，用户往往不知道局域网里具体有哪些设备在线，各自的 IP 地址、开放的服务端口与设备型号是什么。传统全网段 ping 扫描耗时长且会被防火墙拦截。\n\n" +
                       "使用 Go 自研的轻量 SSDP 探测器，向局域网多播地址 `239.255.255.250:1900` 发送 `M-SEARCH` 广播包并异步收集设备回复。自动解析各设备返回的 XML 描述文档，提取 FriendlyName、ModelName、UDN 与控制 URL，常驻物理内存约 10MB。",
                BulletPoints =
                [
                    "全自动设备拓扑发现：秒级发现局域网内的群晖 NAS、华为音箱、Apple TV 等设备。",
                    "低开销被动侦听：支持后台被动接收设备的 NOTIFY 宣告，设备开机即刻感知。",
                    "暴露 JSON API 接口：向上层其他微服务提供 `/api/devices` 接口便于调用投屏或唤醒。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `koron/go-ssdp`（纯 Go SSDP 协议栈）+ XML 流式提取器。",
                    "M-SEARCH 广播与响应聚合：采用 3 秒搜索超时窗口，通过协程池异步并发抓取回包中的 `LOCATION` XML 网址，解析开销小。",
                    "设备在线状态机维护：内部维护 TTL 倒计时，当设备超过宣告周期未发送心跳包时，自动标记为离线状态。",
                    "【参考开源项目】go-ssdp（Go 语言标准 SSDP 工具库）；gssdp（GNOME 平台 SSDP 协议实现参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "执行一次局域网设备探测并查看输出：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行局域网 SSDP 设备扫描",
                        Code = "# 1. 运行扫描工具搜索局域网所有 UPnP 渲染器与服务\n" +
                               "/data/local/tmp/z6x_scanner -search -timeout 3s -json\n\n" +
                               "# 2. 查看输出的设备列表\n" +
                               "cat /data/local/tmp/devices.json",
                        ExpectedOutput = "[\n  {\"name\":\"Sonos One\",\"ip\":\"192.168.1.108\",\"type\":\"urn:schemas-upnp-org:device:MediaRenderer:1\"},\n  {\"name\":\"Synology DS920+\",\"ip\":\"192.168.1.50\",\"type\":\"urn:schemas-upnp-org:device:Basic:1\"}\n]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "Wi-Fi 多播包拦截防范：路由器开启 AP 隔离时 SSDP 多播会被阻断，测试前需在主路由后台关闭客户端隔离。",
                    "XML 外部实体注入（XXE）防护：解析第三方设备返回的 XML 文件时，必须禁用 DTD 与外部实体加载，防止安全隐患。"
                ]
            }
        ]
    };
}
