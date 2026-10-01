using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class HighFrequencyInputInjectorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "high-frequency-input-injector",
        Title = "18. 自研高频事件注入与微秒级按键模拟器：C-ABI 直写事件（Z6X RustInputInjector）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的硬件级按键模拟器，绕过 Android 慢速的 app_process Java 虚拟机调用，直接向底层事件流直写二进制 input_event，实现微秒级高频连击与平滑手势。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Android 系统自带的 `input keyevent` 或 `input swipe` 命令底层是基于 Java 的 `app_process` 运行的。每次执行命令，系统都要重新启动一次完整的 Java 虚拟机环境，单次调用耗时长达 200ms ~ 400ms，根本无法实现快速连续按键（如长按音量连续递增）或高刷新率平滑滑动。\n\n" +
                       "使用 Rust 自研底层事件注入器后，通过 C-ABI 直接打开系统输入设备文件并向内核写入标准的 `input_event` 二进制流。单次按键或手势注入耗时缩短至 50 微秒以内，支持 100Hz 甚至更高频率的顺滑滚动与游戏手柄连击模拟。",
                BulletPoints =
                [
                    "耗时缩短 4000 倍：从 Java 版的 300 毫秒降低至 50 微秒以内。",
                    "支持高频平滑滚动：以 60fps/120fps 帧率平滑注入触摸轨迹，消除生硬跳跃卡顿。",
                    "长按与连击宏无感触发：毫秒级精准控制按键按下（Down）与弹起（Up）之间的时间差。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + Linux C-ABI（`libc` crate 直调 `write()` 系统调用，单文件体积约 600KB）。",
                    "为什么 Android 原生 input 那么慢？`input` 命令是一个位于 `/system/bin/input` 的 shell 脚本，每次调用都执行 `app_process /system/bin com.android.commands.input.Input`，启动 JVM、装载 Android Framework 类库耗尽了 99% 的时间；Rust 直接以原生二进制执行，零启动开销。",
                    "底层二进制写入流程：通过 POSIX `open()` 打开已有遥控器设备节点（如 `/dev/input/event2`），构造 `struct input_event { time, type: EV_KEY, code: 115, value: 1 }` 写入，紧接着写入 `EV_SYN` 同步事件，内核立即完成事件上报。",
                    "【参考开源项目】uinput-rs（Rust 用户态输入设备创建库）；scrcpy（参考其注入事件协议实现）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台测试微秒级按键注入：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行高速连击与微秒级按键注入",
                        Code = "# 1. 模拟音量键快速连续增加 5 次（总耗时 < 10ms）\n" +
                               "/data/local/tmp/z6x_injector -dev /dev/input/event2 -key KEY_VOLUMEUP -repeat 5 -delay-ms 2\n\n" +
                               "# 2. 查看单次注入耗时基准\n" +
                               "/data/local/tmp/z6x_injector -bench",
                        ExpectedOutput = "[Injector] 100 events dispatched in 4.2ms (Avg: 42μs per event)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "事件注入排坑指南：",
                BulletPoints =
                [
                    "问题 1：写入节点报错 Permission Denied。确保当前用户属于 `input` 组（ADB shell 默认具备），若特定节点不可写，可退而求其次写入虚拟键盘节点（`/dev/uinput`）。",
                    "问题 2：忘记发送 EV_SYN 导致按键卡死。Linux 输入子系统规定每个事件包后必须紧随一个 `EV_SYN (SYN_REPORT)` 报文，否则系统会一直等待事件结束，导致按键呈现“一直被按住”的死锁状态。"
                ]
            }
        ]
    };
}
