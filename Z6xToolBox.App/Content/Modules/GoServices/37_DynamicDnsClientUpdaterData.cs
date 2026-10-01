using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class DynamicDnsClientUpdaterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "dynamic-dns-client-updater",
        Title = "37. 自研多平台 DDNS 动态域名解析同步客户端：IPv6/IPv4 变动直更（Z6X DdnsGo）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量多平台 DDNS 动态域名同步服务，自动探测极米外网 IPv6/IPv4 并在变动时更新阿里云/腾讯云/Cloudflare 解析，常驻内存仅 10MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "现代家用宽带通常具备原生公网 IPv6 地址，许多用户希望通过二级域名（如 `tv.example.com`）在外网直接访问极米上的影视或 WebDAV。但运营商通常会每天重新分配 IPv6 前缀，手动更新 DNS 极其繁琐。\n\n" +
                       "使用 Go 自研的轻量 DDNS 客户端，周期性检查极米网卡绑定的公网 240e/2408/2409 开头的全球单播 IPv6 地址。当地址发生变化时，自动调用阿里云、腾讯云 DNSPod 或 Cloudflare 的 OpenAPI 同步更新 AAAA 记录，常驻物理内存约 10MB。",
                BulletPoints =
                [
                    "自动追踪公网 IPv6 变更：运营商重拨号换 IP 后，秒级自动更新 DNS 记录。",
                    "支持多主流 DNS 平台：同时兼容 Cloudflare、阿里云与腾讯云 DNS API。",
                    "轻量单二进制：无需部署庞大的第三方 Python/Node 脚本，单文件静默运行。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 各云厂商精简签名签名库（零第三方重型 SDK 引入，纯静态编译）。",
                    "网卡原生地址探测：直接调用 Linux `net.InterfaceAddrs()` 遍历网卡，本地提取有效全局单播 IPv6，无需依赖外部慢速查询接口。",
                    "防抖与变更比对：仅在提取出的 IP 与本地缓存不一致时才发起云端 API 调用，避免触发云厂商频率超限拦截（Rate Limit）。",
                    "【参考开源项目】ddns-go（Go 语言流行 DDNS 工具，本模块为其最小化无界面重构版）；inadyn（Linux 经典 DDNS 客户端）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 DDNS 同步客户端并测试手动触发：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 DDNS 动态域名同步服务",
                        Code = "# 1. 启动 DDNS 守护服务（每 10 分钟检测一次，配置文件写入 AccessKey）\n" +
                               "nohup /data/local/tmp/z6x_ddns \\\n" +
                               "  -config /data/local/tmp/ddns.yaml \\\n" +
                               "  -interval 10m > /data/local/tmp/ddns.log 2>&1 &\n\n" +
                               "# 2. 手动执行一次同步探测\n" +
                               "/data/local/tmp/z6x_ddns -check-now -config /data/local/tmp/ddns.yaml\n\n" +
                               "# 3. 查看同步日志\n" +
                               "tail -n 10 /data/local/tmp/ddns.log",
                        ExpectedOutput = "[DDNS] Detected IPv6: 240e:390:xxxx:xxxx::100 on wlan0\n[Cloudflare] Updated AAAA record for 'tv.example.com' -> 240e:390:xxxx:xxxx::100 [SUCCESS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "临时 IPv6 地址与隐私扩展（SLAAC）：Android 系统通常会为网卡生成带临时隐私保护属性的动态 IPv6，程序需过滤掉 `IFA_F_TEMPORARY` 标志，选择稳定的永久公网 IPv6 地址上报。",
                    "云厂商 API 凭证安全存储：配置文件必须设置 `chmod 600 /data/local/tmp/ddns.yaml`，防止机身内部敏感 AccessKey 泄露。"
                ]
            }
        ]
    };
}
