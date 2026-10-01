using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ZeroCopyPacketSnifferData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zero-copy-packet-sniffer",
        Title = "20. 自研零拷贝网络数据包嗅探与流特征分析器：AF_PACKET 环形缓冲（Z6X RustSniff）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的高效网络抓包与链路特征探测探针，基于 Linux AF_PACKET 环形内存映射，实时统计投屏与串流丢包抖动，内存常驻仅 1.8MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米投影仪上进行无线投屏（AirPlay/Miracast/DLNA）或局域网高码率串流（如 Moonlight、Steam Link）时，画面偶现卡顿、马赛克或音画不同步。传统的 tcpdump 在高发包率下由于多次内核态到用户态的内存拷贝，不仅 CPU 占用高，还容易导致 Wi-Fi 网卡丢包加剧。\n\n" +
                       "使用 Rust 自研的轻量抓包探针，利用 Linux 底层的 `AF_PACKET` PACKET_MMAP 机制，建立内核态与用户态共享环形缓冲区，实现零拷贝数据包捕获。无需将 payload 载入内存，仅实时抽样解析 TCP/UDP 头部，统计 RTT 往返时延、乱序包率与抖动指标。物理内存常驻仅约 1.8MB。",
                BulletPoints =
                [
                    "环形缓冲区零拷贝：通过共享内存直接读取网络帧，避免大量内存分配和拷贝开销。",
                    "低开销流状态监测：纳秒级提取 TCP 序列号与 ACK 时间戳，测算当前 Wi-Fi 抖动。",
                    "轻量报警输出：当丢包率超过阈值时，通过本地套接字向监控总线发出告警。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `libc`（Linux socket AF_PACKET / PACKET_MMAP 系统调用）+ `etherparse` 纯内存头部解析（无 std 依赖）。",
                    "无锁环形队列解析：用户态指针直接追踪内核 ring buffer head/tail，以事件轮询方式处理报文头部，零堆内存分配。",
                    "与 tcpdump 内存对比：tcpdump 抓包高码率视频流时内存常达 15MB 且 CPU 占 10%，RustSniff 内存稳定在 1.8MB，CPU 占用 < 1.5%。",
                    "【参考开源项目】netsniff-ng（高性能 Linux 网络监控工具包）；etherparse（Rust 高速协议头反序列化库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动抓包探针分析当前投屏链路质量：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动网络嗅探并输出流特征分析指标",
                        Code = "# 1. 启动轻量嗅探器监听 wlan0 端口并过滤投屏流量\n" +
                               "nohup /data/local/tmp/z6x_sniff \\\n" +
                               "  -interface wlan0 \\\n" +
                               "  -filter-port 47998 \\\n" +
                               "  -interval 2 > /data/local/tmp/sniff.log 2>&1 &\n\n" +
                               "# 2. 查看实时丢包与抖动分析输出\n" +
                               "tail -n 10 /data/local/tmp/sniff.log",
                        ExpectedOutput = "[Stream] Packets: 12840, Loss: 0.08%, Jitter: 1.42ms, AvgRTT: 8.2ms\n[Status] Wi-Fi Link Quality: Good"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "非 root 权限下 raw socket 限制：部分 Android 系统上普通 shell 用户可能缺少 `CAP_NET_RAW` 能力，若创建 AF_PACKET 报错 Permission Denied，可切换监听本地 UDP 端口或通过已授权 adb 环境赋予 `setcap cap_net_raw+ep z6x_sniff`。",
                    "网卡混杂模式支持：投影仪 Wi-Fi 网卡大多处于 STA 模式，只能捕获目的 MAC 为本机的单播与局域网广播包，无法开启全网段混杂模式，属于预期行为。"
                ]
            }
        ]
    };
}
