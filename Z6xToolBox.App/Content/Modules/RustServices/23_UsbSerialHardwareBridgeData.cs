using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class UsbSerialHardwareBridgeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "usb-serial-hardware-bridge",
        Title = "23. 自研硬件串口与 USB 虚拟串口通信守护器：mio 非阻塞硬件总线（Z6X RustSerialHub）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的非阻塞 USB/TTL 串口通信网关，利用 mio 与 termios C-ABI 对接单片机与传感器，常驻内存仅 800KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "部分极客用户会通过极米 USB 口外接 USB-to-TTL（CH340/CP2102/FT232）转接板，连接单片机（ESP32、Arduino）、红外发射器、智能窗帘电机或物理继电器。在 Android 环境下，常规 Java USB 串口驱动（如 usb-serial-for-android）需要依赖 Activity 界面且垃圾回收开销不可控。\n\n" +
                       "使用 Rust 自研的串口守护服务，直接打开内核暴露的 `/dev/ttyUSB0` 或 `/dev/ttyACM0` 字符设备节点，基于 Linux `termios` 结构体配置波特率与数据位，并将其注册到 mio 事件循环中。实现微秒级非阻塞字节流转发，常驻内存仅约 800KB。",
                BulletPoints =
                [
                    "直接字符设备访问：无需安装 Java 复杂层，纯 C-ABI 裸调系统底层串口描述符。",
                    "事件驱动非阻塞读写：空闲时不占 CPU 周期，接收到字节后微秒级触发回调。",
                    "自动断线重连：支持热插拔探测，USB 转接器重新插入后自动重新打开设备句柄。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `mio-serial` + `nix::sys::termios` + 无锁环形输入缓冲。",
                    "Termios 纯静态配置：通过标准 POSIX 系统调用设置 `B115200`、8 数据位、无校验位、1 停止位，绕过 Android 上层框架层拦截。",
                    "串口与网络桥接：内置轻量 UDP/Unix Socket 转接端，上层业务只需向本地端口发送 JSON，即可自动转为串口二进制帧下发。",
                    "【参考开源项目】serialport-rs（跨平台 Rust 串口库）；mio-serial（基于 mio 的异步非阻塞串口扩展）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动串口网关并发送控制指令测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动串口与 UDP 桥接守护进程",
                        Code = "# 1. 启动串口监听服务（绑定 115200 波特率，UDP 转发至 9099 端口）\n" +
                               "nohup /data/local/tmp/z6x_serial \\\n" +
                               "  -device /dev/ttyUSB0 \\\n" +
                               "  -baud 115200 \\\n" +
                               "  -udp-port 9099 > /data/local/tmp/serial.log 2>&1 &\n\n" +
                               "# 2. 向本地 UDP 发送开灯十六进制指令帧\n" +
                               "echo -ne \"\\xA5\\x5A\\x01\\x01\\x00\" | nc -u -w1 127.0.0.1 9099\n\n" +
                               "# 3. 查看日志回显\n" +
                               "cat /data/local/tmp/serial.log",
                        ExpectedOutput = "[Serial] /dev/ttyUSB0 opened at 115200 8N1\n[Serial] Forwarded 5 bytes to MCU\n[Serial] MCU ACK received: 0xA5 0x5A 0x01 0x00"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "设备节点读写权限：某些原厂固件默认 `/dev/ttyUSB0` 权限为 0660 且归属 root/dialout，在非 root 的 shell 下启动前需执行 `chmod 666 /dev/ttyUSB0`。",
                    "USB 供电不足导致串口掉线：外接耗电较大的多路继电器或大功率雷达传感器时，必须使用带有独立供电的 USB Hub，避免极米 USB 口瞬间过流保护断电。"
                ]
            }
        ]
    };
}
