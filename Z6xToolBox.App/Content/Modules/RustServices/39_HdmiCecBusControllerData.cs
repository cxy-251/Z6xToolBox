using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class HdmiCecBusControllerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "hdmi-cec-bus-controller",
        Title = "39. 自研 HDMI-CEC 协议监听与设备联动控制器：/dev/cec0 字符设备直连（Z6X RustCecHub）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的 HDMI-CEC 消费电子控制总线守护器，通过裸调 /dev/cec0 字符设备实现开机联动功放音响与待机休眠，常驻内存仅 700KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米投影仪通常通过 HDMI ARC/eARC 连接外置音响、家庭影院或游戏主机。原生 Android 的 HdmiControlService 逻辑复杂且经常出现关机后音响不关、按遥控器音量键无法同步调整功放音量的偶发兼容性故障。\n\n" +
                       "使用 Rust 自研的轻量 CEC 守护器，直接打开内核字符设备节点 `/dev/cec0`，通过 Linux 标准 CEC ioctl 指令（`CEC_RECEIVE` / `CEC_TRANSMIT`）收发物理帧。实现毫秒级捕获开机、待机、输入源切换及音量联动指令，常驻物理内存仅约 700KB。",
                BulletPoints =
                [
                    "底层字符设备直连：绕过 Android HdmiControl 框架层，直接与内核 CEC 驱动交互。",
                    "双向状态同步：监听外部功放广播，或主动发送 `User Control Pressed` 同步音量与静音。",
                    "低功耗事件等待：利用 poll/epoll 挂起线程，总线无消息时 CPU 占用 0.0%。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `nix::sys::ioctl` + Linux CEC API 纯结构体封装（单二进制体积仅 600KB）。",
                    "逻辑地址声明（CEC Logical Address）：正确申请 Playback Device（地址 4 或 8）或 Tuner 逻辑地址，保障符合 HDMI 1.4b/2.0 标准拓扑。",
                    "操作码（Opcode）快速解析：在固定栈数组中匹配 `0x36`（Standby）、`0x44`（User Control）、`0x7A`（Report Audio Status），零堆内存分配。",
                    "【参考开源项目】cec-ctl（Linux 官方 v4l-utils 中的 CEC 调试工具）；libcec（Pulse-Eight 跨平台 CEC 驱动库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 CEC 监听服务并主动发送音响待机指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 CEC 总线守护并发送待机同步指令",
                        Code = "# 1. 启动 CEC 监听服务并配置为 Playback 设备\n" +
                               "nohup /data/local/tmp/z6x_cec \\\n" +
                               "  -device /dev/cec0 \\\n" +
                               "  -logical-address 4 > /data/local/tmp/cec.log 2>&1 &\n\n" +
                               "# 2. 主动向外部音频功放（地址 5）发送休眠待机广播帧\n" +
                               "/data/local/tmp/z6x_cec send -to 5 -opcode 0x36\n\n" +
                               "# 3. 查看 CEC 驱动响应回执\n" +
                               "cat /data/local/tmp/cec.log",
                        ExpectedOutput = "[CEC] /dev/cec0 opened, Driver: mt9669_cec, Caps: 0x0000001e\n[CEC] Transmitted Frame: 45:36 (Playback -> Audio System: Standby) [ACK]\n[CEC] Received Frame: 5F:72:01 (Audio System -> Broadcast: System Audio Mode On)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "系统服务冲突释放：Android 系统自带的 `hdmi_control` 服务可能会以非共享模式锁定 CEC 描述符，必要时可在 shell 中通过 `setprop persist.sys.hdmi.control 0` 暂时释放原厂占用。",
                    "HDMI 线缆 pin 13 连通性：廉价或质量较差的 HDMI 线可能未连接第 13 号 CEC 物理引脚，排查无信号时需先确认硬件线缆导通。"
                ]
            }
        ]
    };
}
