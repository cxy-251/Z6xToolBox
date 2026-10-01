using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class DlnaMediaRendererData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "dlna-media-renderer",
        Title = "16. 自研轻量 DLNA 投屏渲染中继：UPnP/AVTransport 与系统播放器调度（Z6X DlnaBridge）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的轻量 DLNA 接收端，将手机投屏的视频/音频直链提取并通过系统原生硬件解码器全屏播放，零广告弹窗干扰。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视自带投屏应用通常附带开屏广告、强制弹窗推广，且在系统精简后容易彻底失效；商业投屏软件体积常超过 80MB，常驻后台占用 100MB+ 内存。\n\n" +
                       "自研该轻量投屏接收端后，极米在家庭 Wi-Fi 中广播标准的 UPnP 媒体渲染器身份。手机端（如哔哩哔哩、腾讯视频、网易云音乐）点击投屏按钮可直接搜索到极米，服务提取视频直链后在本地直接调用 Android 原生硬件播放器全屏播放，彻底消除广告与内存负担。",
                BulletPoints =
                [
                    "纯净投屏体验：完全消除第三方商业投屏软件的开屏广告、弹窗与会员限制。",
                    "低功耗常驻：纯 Go 守护进程物理内存常驻仅 8MB ~ 15MB，待机时几乎零 CPU 消耗。",
                    "硬件解码直通：提取原始流媒体直链交由 MT9669 芯片 VPU 硬解，画质无二次压缩损耗。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net`（UDP SSDP 发现）+ UPnP AVTransport/ConnectionManager 服务实现。",
                    "什么是 SSDP（简单服务发现协议）？DLNA 投屏的第一步。手机在局域网向组播地址 `239.255.255.250:1900` 发送搜索请求，Go 服务响应自身设备描述 XML，手机即可识别出「极米投影仪」设备名。",
                    "什么是 AVTransport 动作处理？手机点击播放时，向 Go 服务的 HTTP 端口发送 SOAP XML 请求（`SetAVTransportURI`），Go 服务解析出视频直链 URL，在极米本地调用 `am start -a android.intent.action.VIEW -d \"<URL>\"` 唤醒全屏硬件播放器。",
                    "【参考开源项目】goupnp（参考其 SSDP 组播广播实现）；dms（参考其 UPnP 设备描述文件与 SOAP 动作解析机制）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动投屏中继守护进程：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 DLNA 投屏渲染中继",
                        Code = "# 1. 后台启动投屏接收端（设备名设为 Z6X_LivingRoom）\n" +
                               "nohup /data/local/tmp/z6x_dlnabridge -name \"Z6X_LivingRoom\" -port 8096 > /data/local/tmp/dlna.log 2>&1 &\n\n" +
                               "# 2. 本地验证 SSDP 设备描述 XML 响应\n" +
                               "curl -s http://127.0.0.1:8096/description.xml | head -n 10",
                        ExpectedOutput = "<?xml version=\"1.0\"?>\n<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n  <device>\n    <friendlyName>Z6X_LivingRoom</friendlyName>"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "DLNA 投屏运维排坑：",
                BulletPoints =
                [
                    "问题 1：手机搜不到投屏设备。原因与解决：Android 系统在 Wi-Fi 睡眠时可能过滤组播包（Multicast）。需确保已配置 Wi-Fi 常开策略（模块 11），并在路由器后台关闭「AP 隔离」与「IGMP Snooping 抑制」。",
                    "问题 2：视频直链带有 防盗链 Header 导致系统播放器黑屏。解决：Go 服务在收到 `SetAVTransportURI` 时，若发现包含特定防盗链参数，可自建微型 HTTP 本地代理中继切片并在响应头中伪造 `Referer`/`User-Agent`。"
                ]
            }
        ]
    };
}
