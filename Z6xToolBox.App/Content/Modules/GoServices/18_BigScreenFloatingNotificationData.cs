using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class BigScreenFloatingNotificationData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "big-screen-floating-notification",
        Title = "18. 自研大屏悬浮通知中心：Webhook 监听与原生 Toast 派发（Z6X NotifyHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型通知网关，接收 Home Assistant、手机或自动化服务的 HTTP Webhook，在投影屏幕右上角无感悬浮弹出半透明通知小贴条。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "关灯沉浸看电影或大屏串流打游戏时，用户通常将手机放在一旁充电，极易错过门铃按响、洗衣机洗完、离线下载完成或家庭服务器告警等重要通知。\n\n" +
                       "自研通知中继后，极米在后台监听 HTTP Webhook。任何家庭智能设备只要发一条简单的 POST 请求，极米屏幕右上角便会浮现一个半透明的通知胶囊展示 5 秒后自动隐去，既不错过关键事件，又完全不打断当前正在全屏播放的电影画面。",
                BulletPoints =
                [
                    "不打断当前全屏画面：以半透明轻量悬浮方式贴在右上角，5 秒后无感淡出。",
                    "广泛的智能家居兼容：支持 Home Assistant、Node-RED、Bark 格式以及简易 curl 调用。",
                    "纯本地私密流动：通知仅在家庭 Wi-Fi 内网传输，零公网上传泄露风险。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net/http` + Android 本地全局 Toast / 广播注入调度（单文件体积约 4MB）。",
                    "大屏如何无侵入弹出通知？方案 A：通过 Go 服务调用系统底层的 `cmd notification post` 命令注入标准 Android 通知；方案 B：通过极简的前端 WebView 浮窗（基于 `android.view.WindowManager` 的悬浮窗图层）以半透明 CSS 动画渲染。",
                    "什么是通用 Webhook 适配？Go 服务提供统一的 `/api/notify?title=...&message=...` 端点，同时兼容 Gotify 与 Bark 的标准 JSON 数据结构，无需修改发送端现有的自动化格式。",
                    "【参考开源项目】gotify-server（参考其轻量通知数据模型设计，剔除其复杂的 Web 前端与鉴权系统，专为局域网裁剪）；notify-send（Linux 原生桌面通知工具交互规范）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动通知网关并模拟发送大屏通知：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动通知服务并推送大屏浮动消息",
                        Code = "# 1. 启动通知中心守护进程（监听 8098 端口）\n" +
                               "nohup /data/local/tmp/z6x_notify -port 8098 > /data/local/tmp/notify.log 2>&1 &\n\n" +
                               "# 2. PC 模拟发送一条大屏弹窗通知\n" +
                               "curl -X POST http://192.168.0.109:8098/api/notify \\\n" +
                               "  -d \"title=门铃提醒&message=前门检测到有人按门铃&duration=5\"",
                        ExpectedOutput = "{\"status\":\"delivered\",\"id\":\"msg_1001\"}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "通知中继排坑指南：",
                BulletPoints =
                [
                    "问题 1：全屏播放视频时通知被遮挡。原因与解决：使用系统标准 Toast 时，某些处于独占全屏（Immersive Mode）的第三方播放器会强制覆盖所有普通图层。需通过悬浮窗顶层权限（`TYPE_APPLICATION_OVERLAY`）或注入广播方式确保处于最高 Z-Index 图层。",
                    "问题 2：高频通知刷屏。后端应设置 3 秒防抖节流队列，同一发送方连续推多条时自动合并为数字摘要，避免影响观影。"
                ]
            }
        ]
    };
}
