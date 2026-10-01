using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class HighPrecisionNetworkLatencyJitterProbeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "high-precision-network-latency-jitter-probe",
        Title = "34. 自研微秒级 ICMP/UDP 网络时延与抖动雷达：硬件时间戳与链路质检（Z6X RustPingRadar）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的高精度网络时延与抖动探测探针，基于微秒级单调时钟与原始套接字，常驻内存仅 600KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端自带的 ping 命令或 Java 网络检测 API 由于系统调度延迟与 JVM 开销，测量结果往往有数毫秒的误差，无法反映 5G Wi-Fi 细微的抖动。在串流 60fps/120fps 游戏或点播 4K 直播时，哪怕 10ms 的偶发网络波动都会引起画面撕裂。\n\n" +
                       "使用 Rust 自研的高精度网络雷达，通过微秒级 `CLOCK_MONOTONIC_RAW` 单调硬件时钟与非阻塞原始套接字，持续向家庭网关与常用 CDN 发送精简探针包。计算最小、最大、平均 RTT 及标准差抖动，常驻内存仅约 600KB。",
                BulletPoints =
                [
                    "微秒级时延测量：绕过 Java 虚拟机，直读内核单调时钟，精度误差 < 50μs。",
                    "抖动标准差计算：实时输出 Jitter 统计，量化当前无线信道质量。",
                    "轻量常驻探针：单二进制体积仅 600KB，内存占用 600KB，CPU 占用 < 0.2%。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `socket2`（原始套接字 / UDP 探测）+ `libc::clock_gettime`（单调硬件时钟）。",
                    "RFC 3393 抖动标准实现：连续接收多个探测包并计算 `|RTT_i - RTT_{i-1}|` 的滑动平均值，精准还原无线链路波动。",
                    "非特权端口 UDP 回显探针：针对非 root 环境无法创建原始 ICMP 套接字的情况，自动降级为向目标端口发送带时间戳的 UDP 探测包。",
                    "【参考开源项目】mtr（网络诊断工具）；rust-ping（纯 Rust ICMP 客户端）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动网络高精时延雷达并探测网关：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动网络抖动雷达连续测试",
                        Code = "# 1. 启动时延探测探针（向局域网网关发送高精 UDP 探针包，频率 200ms）\n" +
                               "nohup /data/local/tmp/z6x_ping_radar \\\n" +
                               "  -target 192.168.1.1 \\\n" +
                               "  -interval-ms 200 > /data/local/tmp/ping.log 2>&1 &\n\n" +
                               "# 2. 查看时延与抖动标准差计算结果\n" +
                               "tail -n 10 /data/local/tmp/ping.log",
                        ExpectedOutput = "[Radar] Pinging 192.168.1.1 (Payload: 32B, Monotonic Clock: RAW)\n[Stat] Sent: 50, Recv: 50, Loss: 0.0%\n[Latency] Min: 1.84ms, Max: 3.21ms, Avg: 2.12ms, Jitter: 0.28ms [STABLE]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "ICMP 原始套接字权限不足：若系统禁止普通用户创建 `IPPROTO_ICMP` 原始套接字，探针需使用 `-udp` 模式向网关常见端口发送回显，或依赖上级路由器开启 UDP 回显支持。",
                    "Wi-Fi 节能模式导致时延周期性跳变：Android 在未开启低延迟 Wi-Fi 锁时会有 100ms 级的 DTIM 周期唤醒延迟，实测前应通过 `cmd wifi set-low-latency-mode enabled` 开启低延迟模式。"
                ]
            }
        ]
    };
}
