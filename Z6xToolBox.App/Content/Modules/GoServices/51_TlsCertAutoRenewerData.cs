using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class TlsCertAutoRenewerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "tls-cert-auto-renewer",
        Title = "51. 自研 ACME 证书自动签发与轮转服务：HTTPS 证书自动续期（Z6X CertGo）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量 ACME 协议客户端，通过 DNS-01 验证为极米上的 WebDAV/Web 服务自动申请并轮转 Let's Encrypt 证书，常驻内存仅 10MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在外网通过域名加密访问极米上的服务时，现代移动端浏览器与播放器对未加密的 HTTP 或自签名自造证书会弹出不安全警告甚至直接拒绝播放。而手动每 90 天到各大云厂商申请免费证书并下载上传极其繁琐。\n\n" +
                       "使用 Go 自研的轻量证书申请服务，基于标准 ACME 协议规范，通过各大云厂商的 DNS API 自动添加 `_acme-challenge` TXT 记录完成域名所有权验证（DNS-01 Challenge）。成功申请免费 Let's Encrypt 泛域名 SSL/TLS 证书并落盘保存，在到期前 30 天自动静默续期，常驻物理内存约 10MB。",
                BulletPoints =
                [
                    "自动全流程证书轮转：自动申请、自动验证、自动续签，消除证书过期烦恼。",
                    "DNS-01 验证无需暴露 80 端口：即使家庭宽带封锁了 80/443 端口也能顺利签发公网可信证书。",
                    "热加载通知机制：证书更新后自动发送信号或调用本地钩子通知其他服务重载证书。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `go-acme/lego/v4`（纯 Go 实现的 ACME 协议客户端）+ 云厂商 DNS 驱动集成。",
                    "ECDSA P-256 证书生成：默认使用现代椭圆曲线算法生成私钥，计算开销与握手报文远小于传统 2048 位 RSA 密钥，更适合 ARM64 处理器。",
                    "非阻塞定时调度：每天仅在设定的特定时间检查一次证书有效期，无任务时线程自动休眠，CPU 占用 0.0%。",
                    "【参考开源项目】lego（Go 语言官方推荐 ACME 客户端）；certbot（经典 Python 证书申请工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "手动触发一次证书申请并检查证书文件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行 Let's Encrypt 证书自动申请测试",
                        Code = "# 1. 运行证书申请工具（通过 Cloudflare DNS-01 验证域名 tv.example.com）\n" +
                               "/data/local/tmp/z6x_certgo \\\n" +
                               "  -domain \"tv.example.com\" \\\n" +
                               "  -provider cloudflare \\\n" +
                               "  -email \"admin@example.com\" \\\n" +
                               "  -out /data/local/tmp/certs\n\n" +
                               "# 2. 查看生成的公钥与私钥\n" +
                               "ls -lh /data/local/tmp/certs/",
                        ExpectedOutput = "[ACME] Registering account for admin@example.com\n[DNS-01] Added TXT record _acme-challenge.tv.example.com\n[ACME] Validated challenge with Let's Encrypt CA\n[Certificate] Saved cert.pem (5.1 kB) and privkey.pem (241 B) [SUCCESS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "DNS 缓存刷新延迟：添加 TXT 记录后需等待各权威 DNS 服务器同步（通常需 10~30 秒），需在配置中将传播等待时间设为 30s 避免提早请求导致验证失败。",
                    "私钥权限保护：生成的 `privkey.pem` 需严格赋予 `0600` 读写权限，避免敏感证书私钥被同机器上的第三方恶意应用盗取。"
                ]
            }
        ]
    };
}
