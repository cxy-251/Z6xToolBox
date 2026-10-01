using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LogSanitizerRotatorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "log-sanitizer-rotator",
        Title = "12. 自研零开销日志轮转与脱敏器：SIMD 过滤与定额归档（Z6X RustLogSanitizer）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的流式日志过滤器与自动轮转工具，基于 SIMD 文本匹配实时擦除日志中的 Token、MAC 地址与局域网 IP，并按定额大小自动分割压缩归档。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "后台自研微服务长期运行后，会产生大量包含家庭网络敏感信息（Wi-Fi 密码、路由器公网 IP、设备序列号、API 密钥）的调试日志；若日志文件不加限制地持续写入，不仅可能泄露家庭隐私，还会迅速占满极米有限的内部闪存存储。\n\n" +
                       "使用 Rust 自研的轻量日志脱敏轮转工具后，管道输入的所有日志在落盘前被实时进行模式匹配过滤，敏感凭证被自动脱敏为 `***`；同时当单个日志达到 10MB 时自动切分并执行后台压缩，最多保留 3 份历史归档，彻底解决隐私泄露与磁盘爆满隐患。",
                BulletPoints =
                [
                    "自动抹除敏感信息：实时脱敏 IP、Token 与设备序列号，安全分享日志。",
                    "严格定额存储：单文件达 10MB 自动轮转，防止磁盘日志溢出。",
                    "微秒级流式处理：利用 CPU 向量指令扫描字符串，处理延迟 < 5μs。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `regex-automata`（Aho-Corasick 多模式自动机）+ 纯 Rust 滚动文件写入器（单文件体积约 750KB）。",
                    "什么是 Aho-Corasick 多模式自动机？传统正则表达式多规则匹配需要重复扫描多次文本；AC 自动机在内存中构建一棵前缀树，单次遍历字符流即可同时完成 IP、MAC、Token 等数十种敏感特征的匹配与替换，吞吐率达数百 MB/s。",
                    "为什么比 logrotate 更适合？标准 Linux 的 `logrotate` 是重度 cron 脚本，无法做实时内存级正则脱敏；Rust 工具作为管道接收器（`app | z6x_sanitizer`）无缝串联在服务后方，常驻内存仅约 1.2MB。",
                    "【参考开源项目】vector（Datadog 开源的高性能 Rust 日志流水线参考其字段脱敏模块）；ripgrep（参考其 SIMD 字符串扫描逻辑）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "使用管道接入业务服务进行脱敏与滚动测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动日志脱敏管道测试",
                        Code = "# 1. 模拟微服务输出日志并经由脱敏工具写入轮转文件\n" +
                               "echo \"[Auth] Login from IP: 192.168.0.105 with Token: sec_9988aabb\" | \\\n" +
                               "  /data/local/tmp/z6x_sanitizer -max-size 10M -keep 3 -out /data/local/tmp/app.log\n\n" +
                               "# 2. 查看过滤后的实际落盘日志内容\n" +
                               "cat /data/local/tmp/app.log",
                        ExpectedOutput = "[Auth] Login from IP: 192.168.0.*** with Token: sec_***"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "日志脱敏排坑指南：",
                BulletPoints =
                [
                    "问题 1：管道阻塞导致上游服务假死。当极米 I/O 繁忙时，写日志卡死会导致上游业务进程一同挂起。工具必须配置非阻塞环形缓冲写入（Non-blocking Writer），遇到写入慢时丢弃低优先级 debug 日志，保障上游服务畅通。",
                    "问题 2：误伤普通数字。正则规则需限定严格上下文前缀（如 `Token: ` 或 `key=`），避免将普通端口号误脱敏。"
                ]
            }
        ]
    };
}
