using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class DohDotEncryptedDnsProxyData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "doh-dot-encrypted-dns-proxy",
        Title = "10. 自研轻量 DoH/DoT 加密 DNS 中继：rustls 驱动与防运营商劫持（Z6X RustDoH）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 自研的轻量加密 DNS 转发器，基于纯内存 rustls 库实现 DNS-over-HTTPS 与 DNS-over-TLS 解析，杜绝电视端 DNS 污染且常驻内存低至 2.5MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端很多视频 App 或自带播放器在发起网络请求时，使用的是标准 UDP 53 明文域名查询。家庭宽带运营商常利用明文 DNS 进行流量劫持、域名拦截或插入弹窗广告，导致视频播放地址被重定向或解析超时失败。\n\n" +
                       "使用 Rust 自研轻量加密 DNS 代理后，极米在本地监听非特权端口（如 `:5354`），将局域网内的明文 DNS 请求打包为加密的 DoH（HTTPS）或 DoT（TLS）请求转发至腾讯/阿里/Cloudflare 安全节点。完全摆脱运营商劫持，常驻物理内存仅约 2.5MB。",
                BulletPoints =
                [
                    "从根源杜绝运营商劫持：全链路 TLS 加密查询，中间人无法窥探或篡改解析结果。",
                    "零 OpenSSL 动态依赖：基于纯 Rust 实现的 `rustls`，内存安全且无历史漏洞包袱。",
                    "低延迟长连接复用：保持与上游 DoH 服务器的 HTTP/2 多路复用连接，后续查询延迟 < 15ms。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `h2`（HTTP/2 客户端）+ `rustls`（现代内存安全 TLS 库）+ `tokio`（单文件体积约 1.2MB）。",
                    "什么是 DNS-over-HTTPS（DoH，RFC 8484）？将标准的 DNS 二进制查询数据包封装进 HTTP/2 的 POST 请求体中，走标准 443 端口加密传输，网络监管与运营商只能看到普通的 HTTPS 流量，无法识别与阻断其内容。",
                    "为什么比 C/Go 实现更安全且小巧？Go 版 TLS 握手堆分配频繁且基础内存大；C 版依赖 OpenSSL 动态库移植复杂（体积常超 10MB）；`rustls` 纯静态编译且剥离废弃加密套件，编译后单二进制仅 1MB 出头。",
                    "【参考开源项目】doh-proxy（纯 Rust 实现的 DoH 代理参考）；trust-dns / hickory-dns（Rust 官方生态成熟的 DNS 协议栈）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动 DoH 代理并验证加密解析：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 DoH 代理并执行 dig 加密查询验证",
                        Code = "# 1. 启动 Rust DoH 转发器（监听本地 5354 端口，上游指向阿里 DoH）\n" +
                               "nohup /data/local/tmp/z6x_doh \\\n" +
                               "  -listen 127.0.0.1:5354 \\\n" +
                               "  -upstream \"https://dns.alidns.com/dns-query\" > /data/local/tmp/doh.log 2>&1 &\n\n" +
                               "# 2. 本地测试通过该代理发起查询\n" +
                               "/data/local/tmp/bin/busybox nslookup baidu.com 127.0.0.1:5354",
                        ExpectedOutput = "Server:    127.0.0.1:5354\nAddress:   127.0.0.1:5354\n\nName:      baidu.com\nAddress:   110.242.68.66"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "DoH 代理排坑指南：",
                BulletPoints =
                [
                    "问题 1：启动时无法验证 TLS 证书。原因与解决：Android 根证书存储格式与标准 Linux 不同（位于 `/system/etc/security/cacerts/`）。Rust 代码中使用 `webpki-roots` 将 Mozilla 权威根证书直接硬编码嵌入二进制内部，彻底消除对系统证书库的依赖。",
                    "问题 2：系统启动时时间错误导致证书校验失败。Android 刚开机未同步 NTP 时时间可能为 1970 年，需在解析逻辑中捕获时钟偏差或优先执行一次 NTP 校时。"
                ]
            }
        ]
    };
}
