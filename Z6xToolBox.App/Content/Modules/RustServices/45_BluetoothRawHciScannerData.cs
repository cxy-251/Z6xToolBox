using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class BluetoothRawHciScannerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "bluetooth-raw-hci-scanner",
        Title = "45. 自研蓝牙 HCI 原始套接字高速扫描探针：内核协议栈抓包（Z6X RustHciSniff）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的蓝牙底层 HCI 原始数据包捕获工具，直接通过 AF_BLUETOOTH 套接字抓取遥控器按键与外设广播包，常驻内存仅 900KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米原装遥控器或蓝牙手柄出现按键失灵、延迟严重或自动断连时，Android 上层的 BluetoothGatt 回调经过了 Bluetooth.apk（Java）和 Fluoride/BlueDroid 协议栈，日志存在大量丢包且无法看清物理层链路控制帧。\n\n" +
                       "使用 Rust 自研的 HCI 探针，直接在 Linux 内核层创建 `socket(AF_BLUETOOTH, SOCK_RAW, BTPROTO_HCI)` 描述符，对蓝牙芯片与主机之间的串口/USB 通信执行捕获。微秒级解析 ACL 数据包、HCI 命令回执与连接状态事件，常驻内存仅约 900KB。",
                BulletPoints =
                [
                    "底层蓝牙协议帧捕获：直接读取 HCI 事件包与 ACL 数据流，排查物理层丢包断连。",
                    "低开销被动监听：无主动扫描发射功率，不影响当前正常连接的蓝牙遥控器使用。",
                    "轻量单二进制：无需依赖 Android 重型蓝牙诊断工具或 hcidump。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `libc`（Linux AF_BLUETOOTH 扩展支持）+ 纯栈内存 HCI 报文解构器。",
                    "HCI 过滤器配置：通过 `setsockopt(SOL_HCI, HCI_FILTER)` 精准过滤目标外设的 MAC 地址与事件类型（如 Disconnection Complete），避免日志刷屏。",
                    "PCAP 格式实时转储：支持将抓取的 HCI 帧直接封装为标准 Wireshark 可读取的 `.pcap` 格式落盘，便于电脑端离线分析。",
                    "【参考开源项目】bluez（Linux 官方蓝牙协议栈，本模块为其最小化轻量 HCI 抓包器）；btsnoop（Android 蓝牙侦听规范）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动蓝牙 HCI 捕获并转储 PCAP 文件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动蓝牙 HCI 原始套接字监听",
                        Code = "# 1. 启动轻量 HCI 抓包探针并保存抓包文件\n" +
                               "nohup /data/local/tmp/z6x_hcisniff \\\n" +
                               "  -adapter hci0 \\\n" +
                               "  -out /data/local/tmp/bluetooth.pcap > /data/local/tmp/hci.log 2>&1 &\n\n" +
                               "# 2. 查看当前蓝牙交互事件\n" +
                               "tail -n 10 /data/local/tmp/hci.log",
                        ExpectedOutput = "[HCI] Bound to hci0 (Device index: 0, Bus: UART)\n[Event] 0x05 (Disconnection Complete): Handle 0x0041, Reason: Connection Timeout (0x08)\n[HCI] Written 24 frames to /data/local/tmp/bluetooth.pcap"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "SELinux 对 AF_BLUETOOTH 的限制：部分 Android 系统 SELinux 策略阻止普通 shell 用户建立蓝牙 raw socket，若报错 `EACCES` 需确认进程所在上下文具有 net_admin 或 bluetooth 组权限。",
                    "UART 串口波特率防溢出：极米内置蓝牙芯片多通过串口（ttyS*）与 SoC 互联，抓包时避免向控制台高频打印字符串导致串口 FIFO 溢出丢帧。"
                ]
            }
        ]
    };
}
