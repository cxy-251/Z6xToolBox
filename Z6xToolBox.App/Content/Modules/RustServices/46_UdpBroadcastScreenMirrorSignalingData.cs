using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class UdpBroadcastScreenMirrorSignalingData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "udp-broadcast-screen-mirror-signaling",
        Title = "46. 自研多屏协同与低延迟投屏组播信令中继：UDP 多播状态同步（Z6X RustCastSignal）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的多屏协同微秒级信令中继服务，基于 UDP 局域网组播实现手机、Steam Deck 与极米投影仪之间的状态秒同步，常驻内存仅 800KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在多设备协作（如手机作为遥控器、平板涂鸦同步投影、Deck 投屏快速切源）场景下，传统基于 HTTP REST API 或 WebSocket 长连接的方案由于握手慢、心跳包繁重，容易产生 200ms 以上的响应迟滞，且多设备并存时需维护复杂连接树。\n\n" +
                       "使用 Rust 自研的组播信令中继，通过局域网 UDP 组播（Multicast）通道分发紧凑的 16 字节信令帧（设备 ID、操作码、时间戳、校验码）。设备加入局域网即可零配置收发状态，单次状态分发耗时 < 2ms，常驻内存仅约 800KB。",
                BulletPoints =
                [
                    "毫秒级信令分发：多播广播一次，局域网所有协同终端在 2ms 内同时响应。",
                    "零连接维持负担：无需维护 TCP 连接池，节点随时上线或下线，架构去中心化。",
                    "紧凑定长二进制帧：避免 JSON 序列化堆开销，网络带宽消耗微乎其微。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `socket2` + 纯栈内存定长协议解析（单二进制体积仅 620KB）。",
                    "组播 TTL 与环回控制：配置 `IP_MULTICAST_TTL = 1` 限制在家庭局域网内不外溢，开启 `IP_MULTICAST_LOOP = true` 支持本机多进程同步接收。",
                    "轻量序列号与去重表：内置滑动窗口去重位图，自动忽略网络中由于 Wi-Fi 重发产生的重复组播数据帧。",
                    "【参考开源项目】raknet（轻量低延迟 UDP 协议库）；open-airplay（开源 AirPlay 协议实现中的信令发现模块）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动组播信令节点并广播测试控制帧：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 UDP 组播信令同步服务",
                        Code = "# 1. 启动信令节点加入组播组 239.255.42.99:9998\n" +
                               "nohup /data/local/tmp/z6x_signal \\\n" +
                               "  -group 239.255.42.99 \\\n" +
                               "  -port 9998 > /data/local/tmp/signal.log 2>&1 &\n\n" +
                               "# 2. 本地发送切源信令帧（模拟手机触发）\n" +
                               "/data/local/tmp/z6x_signal send -opcode 0x0A -param 1\n\n" +
                               "# 3. 查看组播接收回执\n" +
                               "cat /data/local/tmp/signal.log",
                        ExpectedOutput = "[Signal] Joined multicast group 239.255.42.99:9998 on wlan0\n[Signal] Recv opcode 0x0A (Switch HDMI 1) from 192.168.1.100 (latency: 1.4ms)\n[Dispatch] Action triggered in 120us"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "双频 Wi-Fi 跨频段组播转发：某些双频合一路由器在 2.4G 与 5G 之间组播转发可能存在阻断，测试时建议所有协同设备统一连接至 5G Wi-Fi 频段。",
                    "UDP 丢包容忍设计：由于 UDP 无保障交付，关键性控制信令（如切换信号源）内部需连续发送 3 次冗余帧并由去重表自动去重。"
                ]
            }
        ]
    };
}
