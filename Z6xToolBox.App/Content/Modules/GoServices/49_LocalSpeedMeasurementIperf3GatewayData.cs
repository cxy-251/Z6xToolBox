using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LocalSpeedMeasurementIperf3GatewayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "local-speed-measurement-iperf3-gateway",
        Title = "49. 自研局域网 iperf3 协议兼容测速服务端：无线空口吞吐压测（Z6X IperfNode）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的 iperf3 测速协议服务端，免装 Linux C 语言二进制即可配合电脑/手机 iperf3 客户端测试真实 Wi-Fi 吞吐，常驻内存仅 11MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "排查极米投影仪 4K 蓝光播放卡顿或串流掉帧时，必须先测出局域网 Wi-Fi 芯片到路由器的真实物理吞吐量。但在 Android 终端上交叉编译并安装官方 iperf3 经常遇到动态链接库缺失与权限报错。\n\n" +
                       "使用 Go 自研的轻量 iperf3 服务端，完整实现 iperf3 的 JSON 控制流握手与 TCP/UDP 打流测速协议。监听标准 5201 端口，电脑或手机直接运行 `iperf3 -c <极米IP>` 即可测试上下行带宽、丢包率与重传次数，常驻物理内存约 11MB。",
                BulletPoints =
                [
                    "标准客户端原生兼容：支持电脑、手机各类官方 iperf3 客户端直接连入测速。",
                    "免安装 C 动态库：纯 Go 原生实现打流协程，单二进制直接运行。",
                    "多流并发测速：支持多线程并发压测，测出 Wi-Fi 芯片瞬时最大吞吐极限。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net` 原生套接字 + iperf3 状态机协议解析器（单二进制体积仅 12MB）。",
                    "零堆分配循环读取：在打流测试阶段，复用预分配的 128KB 字节切片缓冲区，通过 `io.Copy(io.Discard, conn)` 丢弃垃圾数据，避免触发频繁 GC。",
                    "高精计时统计：基于 `time.Now()` 纳秒级单调时钟，每秒汇总各子协程吞吐量并回传客户端格式化结果。",
                    "【参考开源项目】iperf3（C 语言官方吞吐压测工具）；go-iperf（Go 语言协议绑定参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 iperf3 服务并在电脑端发起测速：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 iperf3 测速服务端并执行客户端打流",
                        Code = "# 1. 启动轻量 iperf3 服务（监听标准端口 5201）\n" +
                               "nohup /data/local/tmp/z6x_iperf -port 5201 > /data/local/tmp/iperf.log 2>&1 &\n\n" +
                               "# 2. PC 端运行标准 iperf3 发起 10 秒多线程压测\n" +
                               "iperf3 -c 192.168.1.100 -P 4 -t 10",
                        ExpectedOutput = "[SUM]   0.00-10.00  sec   982 MBytes   824 Mbits/sec                  receiver\n[IperfNode] Test finished: 824.2 Mbps achieved on wlan0"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "TCP 窗口与套接字缓冲：为测满千兆局域网速率，服务需通过 `conn.SetReadBuffer(4194304)` 扩大读取缓冲，防止内核层 TCP 拥塞退避。",
                    "单次测速高 CPU 占用：打流期间占用较多 CPU，测速结束后所有打流协程必须立即退出归还算力。"
                ]
            }
        ]
    };
}
