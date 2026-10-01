using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LanSpeedLatencyProbeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lan-speed-latency-probe",
        Title = "14. 自研内网测速与网络抖动探针：HTML5 测速与延迟监控（Z6X SpeedProbe）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量局域网测速与延迟监控探针，提供零 Flash/零 Java 依赖的 HTML5 测速页面，并在后台持续监测网络抖动与丢包率。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在 Steam Deck 串流游戏（Steam Link / Moonlight）或投屏看 4K 电影时，若出现偶发卡顿或掉帧，用户往往无法快速定位瓶颈所在：究竟是房间 Wi-Fi 信号衰减、家庭路由器负荷过高，还是外网网络波动。\n\n" +
                       "在常驻且连入 5G Wi-Fi 的极米上部署自研测速探针后，手机或 Deck 打开网页即可直接测试与极米之间的真实内网吞吐带宽；同时探针在后台自动记录局域网与公网的 Ping 延迟与丢包率曲线，卡顿原因一目了然。",
                BulletPoints =
                [
                    "精准排查投屏与串流卡顿：秒级测出当前设备与投影仪之间的实际 Wi-Fi 传输带宽。",
                    "纯 HTML5 零依赖：任何带有现代浏览器的设备均可即点即测，无需安装测速 App。",
                    "后台网络抖动趋势监控：记录每小时的网络延迟波动与丢包率，定位网络隐患。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 内存伪随机数据流生成器 + 周期性 Ping 统计器（单文件体积约 4MB）。",
                    "什么是内存伪随机下行流？测速时为避免磁盘 I/O 成为性能瓶颈，Go 后端在内存中预先生成一个 1MB 的字节切片模板。客户端测速请求到达时，服务端直接通过网络套接字高频循环推送该内存块，测出的是纯粹的 Wi-Fi/以太网真实物理吞吐。",
                    "非 Root 环境下的 Ping 实现：由于 Linux 限制普通非特权用户创建原始套接字（Raw Socket），探针通过调用系统自带的 `/system/bin/ping` 命令（或向特定 UDP 端口发包）获取 RTT 往返时延与丢包统计。",
                    "【参考开源项目】librespeed/speedtest-go（参考其内存数据流生成与测速算法逻辑）；ping_exporter（参考其针对网络时延时序数据的采集设计）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动测速探针服务：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动内网测速与时延探针守护进程",
                        Code = "# 1. 启动测速服务（监听 8094 端口）\n" +
                               "nohup /data/local/tmp/z6x_speedprobe -port 8094 > /data/local/tmp/speed.log 2>&1 &\n\n" +
                               "# 2. 本地验证测速接口吞吐响应\n" +
                               "curl -s http://192.168.0.109:8094/ping",
                        ExpectedOutput = "pong"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "测速探针开发与运行调优：",
                BulletPoints =
                [
                    "问题 1：千兆测速时 CPU 占满导致测速结果失真。原因与解决：在 Go 中频繁向客户端写小切片会引发大量的系统调用上下文切换。优化方案是在 `net/http` 中将缓冲区调大至 64KB 以上，减少系统调用次数，MT9669 芯片单核即可轻松跑满 800Mbps+ 的局域网 Wi-Fi 吞吐。",
                    "问题 2：后台 Ping 进程残留。探针调用外部 ping 命令时务必配置带有超时的 `exec.CommandContext`，防止僵尸进程驻留。"
                ]
            }
        ]
    };
}
