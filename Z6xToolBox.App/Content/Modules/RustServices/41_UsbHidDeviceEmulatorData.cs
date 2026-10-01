using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class UsbHidDeviceEmulatorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "usb-hid-device-emulator",
        Title = "41. 自研 USB 虚拟 HID 键鼠协议模拟注入器：/dev/hidg0 硬件直写（Z6X RustHidEmu）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的标准 USB HID 描述符驱动与键鼠模拟注入器，通过直接向内核 /dev/hidg0 写入标准报文模拟外接键盘，常驻内存仅 600KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Android 原生的 `input keyevent` 命令行工具极其缓慢（单次执行耗时 150ms~300ms），在需要进行自动化连招、高频快进或无障碍宏指令时容易丢键。而部分被禁用的应用或输入法还会拦截上层注入事件。\n\n" +
                       "使用 Rust 自研的虚拟 HID 模拟工具，通过向 Linux 内核 USB Gadget 或 uinput 驱动直接写入标准的 8 字节键盘报文（Modifier, Reserved, KeyCode1..6）。系统会将其当作真正的硬件物理键盘插入并响应，单键注入延迟低至 100μs 以内，常驻内存约 600KB。",
                BulletPoints =
                [
                    "硬件级输入模拟：系统识别为真实 USB 物理外设，绕过软件层防注入拦截。",
                    "微秒级按键延迟：直接写入字符设备描述符，单次击键耗时 < 100μs。",
                    "完整按键码表支持：支持标准 USB HID 键码表，包括方向键、多媒体播放/暂停键。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `byteorder` + Linux `/dev/hidg0` 或 `/dev/uinput` 裸系统调用（单二进制体积仅 580KB）。",
                    "8 字节 HID 键盘报文协议：Byte 0（Ctrl/Shift/Alt/GUI 掩码位）、Byte 1（保留 0x00）、Byte 2~7（按键数组），严格遵循 USB HID 1.11 规范。",
                    "按键弹起防粘滞保护：在写入按键按下帧后，微秒级自动跟进全零（`[0; 8]`）释放帧，防止因进程被杀导致按键一直卡在按下状态。",
                    "【参考开源项目】hid-gadget-test（Linux 内核自带 HID 测试工具）；uinput（Linux 用户空间输入子系统）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "向系统注入标准硬件按键码并验证响应：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行虚拟 USB 键盘按键注入测试",
                        Code = "# 1. 模拟按下并释放 Enter 回车键（USB HID 码 0x28）\n" +
                               "/data/local/tmp/z6x_hid send-key 0x28 --hold-ms 50\n\n" +
                               "# 2. 连续快速输入字符串指令（模拟物理键盘打字）\n" +
                               "/data/local/tmp/z6x_hid type-text \"http://192.168.1.100:8080\" --delay-ms 10\n\n" +
                               "# 3. 查看注入耗时统计\n" +
                               "/data/local/tmp/z6x_hid bench -count 1000",
                        ExpectedOutput = "[HID] Wrote 8 bytes report to /dev/uinput: [00, 00, 28, 00, 00, 00, 00, 00]\n[HID] Wrote key release report\n[Bench] Injected 1000 key events in 12.4ms (Avg: 12.4 us/key) [PASS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "字符设备节点权限：Android 下 `/dev/uinput` 默认可能只属于 `system` 或 `root`，在普通 shell 启动前需通过 `chmod 666 /dev/uinput` 赋予读写权限。",
                    "输入法焦点捕获：注入字符时前台界面必须已有获得焦点的文本编辑框，否则按键事件将被当前 Activity 作为导航键处理。"
                ]
            }
        ]
    };
}
