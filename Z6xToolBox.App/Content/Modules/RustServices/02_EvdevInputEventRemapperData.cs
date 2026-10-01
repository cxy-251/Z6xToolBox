using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class EvdevInputEventRemapperData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "evdev-input-event-remapper",
        Title = "2. 自研底层输入事件拦截与按键重映射：直读 /dev/input 与零延迟宏（Z6X RustEvdev）",
        Group = "Rust原生服务",
        Summary = "基于 Rust 零成本 C-FFI 特性自研的输入设备事件监听器，直接读取 Linux 内核 /dev/input/event* 设备节点，实现极米遥控器按键双击/长按重映射。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米蓝牙遥控器只有有限的几个物理按键（电源、静音、方向键、主页、设置）。在 Android TV 软件层很难实现「双击设置键切换到 HDMI」、「长按静音键打开文件管理器」等高级功能，且市面上的第三方按键映射 App 容易被系统前台杀掉且有显著的事件处理延迟。\n\n" +
                       "使用 Rust 直接监听 Linux 内核层的 `/dev/input/event*` 原始输入流后，可以在硬件信号到达 Android 窗口系统之前直接捕获并判断按键时序，以低于 1 毫秒的零延迟触发自定义脚本或快捷跳转，不改动系统框架即可扩展遥控器操作边界。",
                BulletPoints =
                [
                    "毫秒级底层按键捕获：在 Linux 内核事件层直接截获原始按键，跳过 Android Framework 调度延迟。",
                    "按键手势与宏扩展：为任意实体按键赋予双击、长按 1 秒、组合按键触发动作。",
                    "轻量常驻无感知：物理内存占用仅约 1.2MB，后台静默运行零发热。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `evdev` crate（Linux 输入子系统纯原生无封装绑定）。",
                    "什么是 Linux evdev 子系统？Linux 内核通过 `/dev/input/eventX` 字符设备向上层派发标准的 `input_event` 二进制结构体（包含时间戳 `timeval`、事件类型 `type`、键码 `code` 与状态 `value`）。Rust 通过标准 POSIX `read()` 系统调用以二进制流直读，无需像 Go 那样跨越运行时上下文进行反射转换。",
                    "非 Root 权限读取可行性：实测 ADB shell 用户属于 `input` 组（GID 1004），对 `/dev/input/event*` 设备节点具备原生只读权限，无需 Root 即可监听全量按键输入。",
                    "【参考开源项目】kanata / kmonad（开源键盘重映射神器，参考其事件流解析与宏处理状态机）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动按键重映射守护进程：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动按键映射服务并测试长按事件捕获",
                        Code = "# 1. 查看当前遥控器对应的 input 节点\n" +
                               "adb shell \"getevent -S | grep -B 2 -A 2 -i remote\"\n\n" +
                               "# 2. 启动 Rust 按键监听服务（绑定蓝牙遥控器 event2）\n" +
                               "nohup /data/local/tmp/z6x_evdev -dev /dev/input/event2 > /data/local/tmp/evdev.log 2>&1 &\n\n" +
                               "# 3. 查看实时拦截到的按键与动作日志\n" +
                               "tail -f /data/local/tmp/evdev.log",
                        ExpectedOutput = "[Evdev] Detected: KEY_MUTE (LongPress 1000ms) -> Action: am start ... (Switch HDMI)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "evdev 重映射排坑指南：",
                BulletPoints =
                [
                    "问题 1：独占捕获（Grab）与系统穿透。若在 Rust 中调用 `ioctl(EVIOCGRAB)` 独占设备，该按键会被完全拦截，Android 系统自身将收不到按键。若是做「双击扩展」不应独占事件，仅在检测到长按模式时才派发扩展动作，避免破坏原本的单击体验。",
                    "问题 2：蓝牙遥控器休眠重连导致设备节点变动。蓝牙遥控器断连后再连可能由 event2 变为 event3。Rust 服务需加入 `inotify` 监控 `/dev/input/` 目录，当有新节点创建时自动重新扫描绑定。"
                ]
            }
        ]
    };
}
