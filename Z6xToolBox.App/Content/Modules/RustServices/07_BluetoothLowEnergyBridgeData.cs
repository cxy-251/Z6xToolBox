using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class BluetoothLowEnergyBridgeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "bluetooth-low-energy-bridge",
        Title = "7. 自研低功耗蓝牙与原始串口数据中继：HCI 套接字直读与零拷贝过滤（Z6X RustBleBridge）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 自研的硬件层蓝牙 BLE 原始广播中继服务，直接监听 Linux HCI Socket，无感抓取家庭 BLE 传感器报文并低开销中转。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "家庭中常有各种低功耗蓝牙（BLE）温湿度传感器、小米门窗传感器或人体移动探头。由于距离路由器或主网关较远，边缘房间的信号经常盲区失联。\n\n" +
                       "极米 Z6X Pro 配备了 Wi-Fi/BT 组合芯片（MT7921）且长期待机在客厅中心位置。自研该轻量 BLE 中继后，Rust 守护进程直接在底层以只读模式监听蓝牙广播包，捕获周围传感器的温度、电量与状态数据，并通过局域网 UDP 零开销中转给 Home Assistant，将极米转化为客厅蓝牙网关。",
                BulletPoints =
                [
                    "扩展蓝牙覆盖盲区：利用客厅核心位置的极米音响/投影仪充当全天候蓝牙中继网关。",
                    "免配对被动监听：只读监听 BLE 广播（Advertising Packets），无需主动连接握手，零额外耗电。",
                    "低开销常驻：物理内存占用仅 1.5MB 左右，CPU 占用低于 0.1%。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `btleplug` / 纯 Linux 原始套接字 `AF_BLUETOOTH`（单二进制体积约 900KB）。",
                    "什么是 Linux 原始蓝牙套接字（HCI Socket）？普通 Android 软件访问蓝牙必须通过复杂笨重的 `BluetoothAdapter` Java 框架与系统后台守护交互；Rust 在 Linux 下直接通过 `socket(AF_BLUETOOTH, SOCK_RAW, BTPROTO_HCI)` 创建原始通道，以极低的内核指令开销获取原始空口蓝牙广播帧。",
                    "数据报文二进制解析：利用 Rust 强大的模式匹配（Pattern Matching）直接解包 Xiaomi/BTHome 协议的数据负载（Payload），提取摄氏度与湿度浮点数，全程零多余内存拷贝。",
                    "【参考开源项目】Theengs Gateway（蓝牙网关标准解码模型参考）；btleplug（跨平台 Rust 异步蓝牙库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动蓝牙广播监听中继：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 BLE 广播监听中继服务",
                        Code = "# 1. 启动 Rust 蓝牙监听中继（指定广播转发目标至 Home Assistant）\n" +
                               "nohup /data/local/tmp/z6x_ble_bridge \\\n" +
                               "  -target \"192.168.0.200:1883\" \\\n" +
                               "  -filter \"BTHome\" > /data/local/tmp/ble.log 2>&1 &\n\n" +
                               "# 2. 查看实时抓取到的蓝牙传感器广播流\n" +
                               "tail -f /data/local/tmp/ble.log",
                        ExpectedOutput = "[BLE] Decoded: TempSensor_LivingRoom -> Temp: 23.4°C, Humidity: 55%"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "蓝牙底层监听排坑指南：",
                BulletPoints =
                [
                    "问题 1：RF 射频冲突导致遥控器卡顿。注意：在早前排障中已发现高频扫描 Wi-Fi/BT 会抢占 MT7921 射频通道。Rust 服务必须采用「被动监听（Passive Scan）」模式，绝不可开启主动轮询（Active Scan），确保蓝牙遥控器连接稳定不丢包。",
                    "问题 2：Android 权限限制。确保当前运行用户为 shell（属于 `net_bt_admin` 与 `net_bt` 组，GID 3001/3002），具备打开 HCI Socket 的合法权限。"
                ]
            }
        ]
    };
}
