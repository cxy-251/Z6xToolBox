using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LocalDnsAdblockForwarderData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "local-dns-adblock-forwarder",
        Title = "3. 家庭网络辅助中枢：轻量 DNS 缓存与分流转发服务",
        Group = "Go原生服务",
        Summary = "部署静态编译的 Mosdns 或单文件版 AdGuard Home，通过高位端口转发实现内网 DNS 加速解析与规则拦截。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端很多 App（包括视频播放软件、电视系统自身）在启动时都会频繁请求广告和埋点上报域名；家庭手机在浏览网页时，域名解析通常需要经过运营商 DNS（耗时约 30ms~80ms，且偶发劫持）。\n\n" +
                       "极米 Z6X Pro 拥有充裕的可用内存（1.5GB）与 Wi-Fi 6 低延迟连接。在投影仪后台运行轻量 DNS 缓存服务，可以将全家设备访问过的域名缓存在本地内存中，实现 0 毫秒极速响应，并在网络底层直接阻断电视广告和遥测域名请求。",
                BulletPoints =
                [
                    "拦截电视开屏与视频贴片广告：在域名解析层面将广告服务器解析为 `0.0.0.0`，直接阻止广告拉取。",
                    "局域网秒级解析缓存：常用网站第二次打开无需再经过外网 DNS 递归查询，内网响应延迟 < 1ms。",
                    "防止运营商 DNS 劫持：通过集成 DoH（DNS-over-HTTPS）或 DoT，向上游发送加密查询，杜绝运营商弹窗劫持。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 纯静态编译版 Mosdns（或 AdGuard Home 静态 ELF 单文件，约 18MB）。",
                    "什么是 DNS 缓存？用户访问网址时，系统需先向 DNS 服务器询问 IP 地址。本地 DNS 服务将查询过的结果暂存在内存中，下次直接从内存返回，省去外网传输往返时间（RTT）。",
                    "什么是非特权高位端口（Port 5353）？Linux 标准 DNS 协议运行在 UDP 53 端口上，但该端口属于特权端口（< 1024），Android 非 Root 用户无权绑定。因此服务绑定在 `:5353` 端口上，再通过主路由器的一条 NAT 端口重定向规则（把 LAN 口 53 转发至投影仪 5353）解决特权限制。",
                    "为什么适合跑在 Z6X Pro 上？Mosdns 纯 Go 编写且并发性能极高，处理家庭局域网日常几千次 DNS 请求仅消耗不到 1% 的 CPU，物理内存占用稳定在 25MB ~ 40MB。"
                ]
            },
            new ContentSection
            {
                Heading = "部署与启动命令",
                Text = "推送 Mosdns 静态文件与精简规则并启动：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Mosdns 守护进程",
                        Code = "# 1. 推送二进制与配置文件\n" +
                               "adb push mosdns /data/local/tmp/ && \\\n" +
                               "adb shell chmod 755 /data/local/tmp/mosdns\n\n" +
                               "# 2. 后台启动守护进程（配置文件监听 5353 端口）\n" +
                               "adb shell \"nohup /data/local/tmp/mosdns start -c /data/local/tmp/config.yaml > /data/local/tmp/mosdns.log 2>&1 &\"",
                        ExpectedOutput = "# Mosdns 后台常驻运行，监听 0.0.0.0:5353"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "配置与运行中的常见问题：",
                BulletPoints =
                [
                    "问题 1：启动报错 listen udp :53: bind: permission denied。原因与解决：指定了默认的 53 端口触发了 Linux 权限拦截。必须在配置文件中将监听端口改为 1024 以上的非特权端口（如 5353 或 5354）。",
                    "问题 2：加载海量规则导致内存超限。原因与解决：若直接引入桌面端包含几百万条规则的庞大广告包，内存会飙升至 200MB+，容易被 Android LMK 强制终止。在投影仪端建议仅加载针对电视系统广告、常见视频广告的精简规则库（条数控制在 3 万条以内），内存即可保持在 30MB 左右。",
                    "问题 3：DNS 环路死锁（Looping）。原因与解决：若配置上游服务器时不小心写成了本机 IP 或路由器自身 IP，会导致查询死循环打满 CPU。上游务必明确指向公网可靠服务（如腾讯 DNSPod `119.29.29.29` 或阿里 DNS `223.5.5.5`）。"
                ]
            }
        ]
    };
}
