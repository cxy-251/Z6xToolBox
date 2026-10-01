using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class DnsConcurrencyRacerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "dns-concurrency-racer",
        Title = "42. 自研多上游 DNS 并发竞速与内存缓存解析器：最优链路择优（Z6X RustDnsRace）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的多上游并发 DNS 竞速代理，同时向下发阿里、腾讯、Cloudflare 与网关发起查询并采信首包回包，常驻内存仅 1.5MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端打开在线视频或海报墙时，经常遇到首屏加载慢的情况，主要耗时往往在于 DNS 查询慢（部分运营商本地 DNS 响应常超 80ms 甚至丢包）。单靠配置静态单个 DNS 容易受单点网络抖动影响。\n\n" +
                       "使用 Rust 自研的 DNS 并发竞速器，在本地监听 `:53` 或自定义 UDP 端口。当电视发起域名查询时，同时并发向下游 4 个不同上游 DNS 服务器发射查询请求，采信最快到达的有效响应并秒级回传客户端，其余慢速响应自动丢弃。配合 10,000 条纯内存 LRU 缓存，解析命中时延迟 < 1ms，常驻内存仅约 1.5MB。",
                BulletPoints =
                [
                    "多路并发竞速：同时向多个国内外优质 DNS 发包，以最快回包作为最终结果。",
                    "纯内存 LRU 缓存：支持 TTL 自动过期机制，常用视频 CDN 域名毫秒级直出。",
                    "自动过滤污染：剔除返回特定保留 IP（如 127.0.0.1、0.0.0.0）的虚假劫持响应。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `trust-dns-proto` + `tokio`/`mio` 异步事件驱动（单二进制体积约 1.2MB）。",
                    "无锁并发查询池：使用标准 UDP 套接字与 `FuturesUnordered`，在单线程事件循环中管理并发请求，零系统线程上下文切换开销。",
                    "内存安全 LRU 设计：基于固定容量的双向链表与哈希表，超过阈值自动淘汰旧记录，防止长期运行造成内存泄露。",
                    "【参考开源项目】smartdns（C 语言并发 DNS 服务器，本模块为其最小化 Rust 重构实现）；coredns（Go 编写的插件化 DNS 服务）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动并发竞速代理并验证首屏加速效果：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 DNS 并发竞速代理并查询延迟",
                        Code = "# 1. 启动竞速代理（监听本地 5353 端口，配置 4 个上游）\n" +
                               "nohup /data/local/tmp/z6x_dns_race \\\n" +
                               "  -bind 127.0.0.1:5353 \\\n" +
                               "  -upstreams \"223.5.5.5,119.29.29.29,1.1.1.1,192.168.1.1\" \\\n" +
                               "  -cache-size 5000 > /data/local/tmp/dns_race.log 2>&1 &\n\n" +
                               "# 2. 本地测试查询并查看耗时\n" +
                               "/data/local/tmp/bin/busybox nslookup v.qq.com 127.0.0.1:5353",
                        ExpectedOutput = "[Race] Dispatched query 'v.qq.com' to 4 upstreams\n[Race] Winner: 223.5.5.5 in 11.2ms (Discarded remaining 3 responses)\n[Cache] Stored 'v.qq.com' (TTL: 300s), Query returned in 12ms."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "Android 系统属性 DNS 注入：由于非 root 无法直接修改 `/etc/resolv.conf`，需在 shell 中通过 `setprop net.dns1 127.0.0.1` 引导系统优先走本地代理。",
                    "上游 EDNS Client Subnet（ECS）：部分 CDN 依据 ECS 返回就近节点，建议将国内主节点（如阿里 DNS）设为优先比对池，避免被国外 DNS 分配至海外 CDN 节点。"
                ]
            }
        ]
    };
}
