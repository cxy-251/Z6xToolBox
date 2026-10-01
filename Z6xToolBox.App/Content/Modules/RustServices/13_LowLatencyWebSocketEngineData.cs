using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LowLatencyWebSocketEngineData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "low-latency-websocket-engine",
        Title = "13. 自研低延迟 WebSocket 广播引擎：无锁并发与多端大屏同步（Z6X RustWsHub）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 自研的事件驱动 WebSocket 消息扇出中枢，专为家庭多设备同时监听极米状态设计，在多长连接下依然保持低于 2MB 的极简物理内存。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "当家庭中多台手机、网页仪表盘、Home Assistant、以及投影大屏前端同时监听极米的系统指标（温度、播放进度、通知弹窗、网络状态）时，传统轮询方式会造成大量的重复 HTTP 握手；若使用 Go 维护长连接，每个连接的 Goroutine 与读写缓冲堆栈会累积占用几十兆内存。\n\n" +
                       "使用 Rust 自研轻量 WebSocket 广播中枢后，基于单线程异步 Reactor 模型，一份消息瞬间零拷贝扇出给局域网所有在线客户端，端到端延迟低于 2 毫秒，且并发维持 50 个长连接时物理内存常驻依然稳定在 2MB 以内。",
                BulletPoints =
                [
                    "毫秒级多端状态同步：极米硬件或播放状态变动，所有手机与大屏瞬间同步。",
                    "告别高频轮询开销：事件驱动被动推流，彻底消除无用 HTTP 请求。",
                    "极限低连接内存：基于异步状态机，单长连接内存占用仅需几 KB。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `tokio-tungstenite`（轻量无外部依赖的纯 Rust WebSocket 库）+ `tokio::sync::broadcast` 通道（单文件体积约 900KB）。",
                    "什么是广播无锁扇出（Broadcast Channel）？Go 常用带锁的 map 遍历每个连接发送，存在锁争抢与慢客户端阻塞问题；Rust 的广播通道采用无锁环形队列，所有订阅者并发读取同一份内存引用，慢连接落后时自动跳帧，绝不阻塞主消息源。",
                    "单线程异步 Reactor（Mio/Epoll）：在单 OS 线程内通过单个 epoll 句柄管理所有 TCP 套接字，无线程切换损耗。",
                    "【参考开源项目】tungstenite-rs（Rust 生态性能最标杆的 WebSocket 协议解析库）；centrifugo（现代实时消息服务器广播范式参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动广播引擎并在多客户端验证推流：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 WebSocket 广播服务与客户端订阅",
                        Code = "# 1. 启动轻量 WebSocket 广播引擎（监听 8091 端口）\n" +
                               "nohup /data/local/tmp/z6x_wshub -port 8091 > /data/local/tmp/wshub.log 2>&1 &\n\n" +
                               "# 2. PC 终端模拟向引擎广播一条状态消息\n" +
                               "curl -X POST http://192.168.0.109:8091/publish \\\n" +
                               "  -d '{\"event\":\"volume_change\",\"level\":65}'",
                        ExpectedOutput = "{\"status\":\"broadcasted\",\"clients\":3}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "WebSocket 广播排坑指南：",
                BulletPoints =
                [
                    "问题 1：半开连接（Half-Open Socket）堆积。手机息屏或 Wi-Fi 偶发断开未发 FIN 包时连接挂死。服务端必须开启心跳机制（每 30 秒发送 Ping 帧，若 3 次未收到 Pong 即刻关闭连接回收句柄）。",
                    "问题 2：广播背压（Backpressure）。慢客户端读取不及时会导致通道溢出，需设置 `broadcast::channel(16)` 定额缓冲区，超量自动丢弃旧消息保证实时性。"
                ]
            }
        ]
    };
}
