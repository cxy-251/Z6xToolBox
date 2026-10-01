using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LowLatencyUdpScreenCastingRelayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "low-latency-udp-screen-casting-relay",
        Title = "30. 自研低开销 UDP 视频流快速转发与 FEC 纠错中继：Reed-Solomon 丢包补偿（Z6X RustUdpRelay）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的局域网 UDP 视频流转发中继器，集成前向纠错（FEC）机制，在 5%~10% Wi-Fi 偶发丢包下无重传恢复画质，常驻内存仅 2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在局域网内进行无线高码率投屏或串流游戏时，Wi-Fi 偶发的空中丢包会导致播放器花屏、绿屏或瞬间卡顿。如果依靠 TCP 或应用层重传（ARQ），在往返 RTT 时间内画面早已失去实时性。\n\n" +
                       "使用 Rust 自研的轻量 UDP 视频中继器，在局域网链路两端加入前向纠错（Reed-Solomon FEC）冗余编码包。当接收端丢失数据包时，无需发起重传，在本地利用矩阵代数运算即刻还原原始数据，将单帧投屏画面还原时延控制在 1ms 以内，常驻内存约 2MB。",
                BulletPoints =
                [
                    "前向纠错快速恢复：在不增加网络往返延时的前提下就地纠正偶发丢包。",
                    "零拷贝 UDP 转发：利用 Linux recvmmsg/sendmmsg 批量处理网络套接字数据包。",
                    "低资源占用：常驻物理内存稳定在 2MB 以内，CPU 单核开销低。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `reed-solomon-erasure`（纯 Rust 实现的 SIMD 加速纠错库）+ `socket2`。",
                    "SIMD 向量加速：自动调用 ARM64 NEON 指令集完成有限域伽罗瓦字段的矩阵相乘，降低 FEC 编解码开销。",
                    "批量报文收发：借助 `recvmmsg` 单次系统调用读取最多 32 个 UDP 数据报，降低系统调用上下文切换开销。",
                    "【参考开源项目】kcp（快速可靠 ARQ 协议）；reed-solomon-erasure（Rust 生态主流纠删码库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 UDP 转发与前向纠错中继：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动视频流 UDP FEC 中继服务",
                        Code = "# 1. 启动 FEC 恢复中继服务（监听 8554 端口，转发至本地 8555）\n" +
                               "nohup /data/local/tmp/z6x_udp_relay \\\n" +
                               "  -listen 0.0.0.0:8554 \\\n" +
                               "  -target 127.0.0.1:8555 \\\n" +
                               "  -fec-ratio 10 > /data/local/tmp/udp_relay.log 2>&1 &\n\n" +
                               "# 2. 查看数据包接收与纠错恢复统计\n" +
                               "cat /data/local/tmp/udp_relay.log",
                        ExpectedOutput = "[FEC] Listening on 0.0.0.0:8554, targeting 127.0.0.1:8555 (Redundancy: 10%)\n[FEC] Recovered 48 lost packets out of 10,200 total packets (Zero retransmission)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "UDP 接收缓冲区大小调优：Android 默认的 `rmem_default` 较小，接收高码率 4K 投屏流可能因内核队列满丢包，启动前需通过 `sysctl -w net.core.rmem_max=4194304` 扩大套接字缓冲。",
                    "FEC 冗余比例平衡：冗余比例设置过高（如 > 30%）会反噬 Wi-Fi 空口带宽，家庭局域网推荐设定在 5%~10% 区间。"
                ]
            }
        ]
    };
}
