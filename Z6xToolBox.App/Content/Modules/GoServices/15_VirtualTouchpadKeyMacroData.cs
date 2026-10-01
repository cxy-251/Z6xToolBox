using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class VirtualTouchpadKeyMacroData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "virtual-touchpad-key-macro",
        Title = "15. 自研手机虚拟触控板与按键宏中继：WebSocket 手势与按键映射（Z6X RemoteBridge）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的 WebSocket 遥控与触控板中继服务，手机扫码即可化身触控板控制大屏鼠标指针，并支持一键触发预设按键宏指令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米原装遥控器仅有方向键与确定键，缺少空鼠/鼠标指针功能。在投影仪上运行某些移植自平板或手机的 App（如部分文件管理器、专业设置软件、第三方播放器）时，许多按钮无法用遥控器焦点选中，操作极为困难；此外，切换特定输入源或深层系统设置需要按多步按键。\n\n" +
                       "自研该服务后，极米在后台提供一个 WebSocket 页面。手机扫码打开即可化身为大屏的平滑虚拟触控板（支持单指滑动移动鼠标、双指轻触滚动页面）；同时面板支持自定义宏按键，实现一键执行多步跳转组合操作。",
                BulletPoints =
                [
                    "解决大屏非焦点软件操作难题：手机触摸屏变身为高精度大屏触控板，轻松点击任意位置。",
                    "一键执行多步按键宏：一键完成「设置 -> 信号源 -> HDMI」或自定义连招操作。",
                    "零 App 安装与蓝牙配对：基于 Web 技术，任何连接家庭 Wi-Fi 的手机扫码即用。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `gorilla/websocket` + Android 本地 `input` 事件派发（单文件体积约 5MB）。",
                    "什么是 WebSocket 低延迟长连接？相比传统 HTTP 请求每次都要重新握手，WebSocket 在手机与极米之间保持一条双向全双工通道。手机手指滑动的微小偏移量（dx, dy）能以低于 10 毫秒的超低延迟实时推送至极米端。",
                    "虚拟指针与事件模拟：后端接收到手势偏移后，通过换算大屏分辨率（1920x1080），在本地调用 Linux shell `input swipe` 或注入鼠标指针事件；按键宏则是在 Go 协程中以精准毫秒级间隔顺序执行多个 `input keyevent` 代码。",
                    "【参考开源项目】scrcpy（参考其轻量控制协议与按键/触摸坐标映射规范）；gorilla/websocket（Go 语言中最经典的低延迟长连接网络库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台启动遥控中继并在手机端打开控制面板：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "后台启动虚拟触控中继服务",
                        Code = "# 1. 启动遥控中继守护进程（监听 8095 端口）\n" +
                               "nohup /data/local/tmp/z6x_remotebridge -port 8095 > /data/local/tmp/remote.log 2>&1 &\n\n" +
                               "# 2. PC 终端模拟通过 HTTP 触发一键切换 HDMI 宏操作\n" +
                               "curl -X POST http://192.168.0.109:8095/api/macro?action=switch_hdmi",
                        ExpectedOutput = "{\"status\":\"executed\",\"macro\":\"switch_hdmi\",\"steps\":2}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "触控中继运维排坑：",
                BulletPoints =
                [
                    "问题 1：手指滑动时指令堆积导致鼠标延迟。原因与解决：手机触摸屏每秒产生数百次 `touchmove` 事件，高频触发会导致极米底层 input 命令队列堵塞。前端必须做 16ms（约 60 帧）节流防抖处理，只向 WebSocket 发送累计增量。",
                    "问题 2：后台连接断开。手机锁屏或切换应用时 WebSocket 会断连。前端页面需加入心跳 Ping/Pong 检测并在页面重新聚焦时自动重连。"
                ]
            }
        ]
    };
}
