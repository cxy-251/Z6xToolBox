using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class WebTerminalDirectAccessData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "web-terminal-direct-access",
        Title = "20. 自研网页直连终端：WebSocket PTY 桥接与免 ADB 管理（Z6X WebShell）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的网页轻量终端服务，基于 WebSocket 与 PTY 伪终端桥接 Android 原生 Shell，手机或平板浏览器即可直接执行 Linux 运维命令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "日常对极米投影仪执行运维操作、查看日志、启停微服务时，必须每次都打开电脑、连接 ADB 终端才能敲命令，操作链路长且不便。\n\n" +
                       "自研该网页终端后，极米在后台常驻一个 WebShell 服务。任何连接家庭 Wi-Fi 的手机、平板或 Steam Deck，直接打开浏览器（如 `http://192.168.0.109:7681`）即可直接进入极米的 Linux 终端界面，随时随地免电脑维护设备。",
                BulletPoints =
                [
                    "完全脱离电脑与 ADB：手机掏出浏览器即可直连操作命令行。",
                    "完整的终端交互体验：内嵌 xterm.js 前端，支持 Tab 自动补全、Ctrl+C 中断、方向键翻历史命令。",
                    "安全的局域网认证：内置可选的访问口令验证，防止局域网内其他设备误操作。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `creack/pty`（Linux 伪终端驱动）+ `gorilla/websocket` + 内嵌 xterm.js 静态前端（单文件体积约 6MB）。",
                    "什么是 PTY（伪终端）桥接？普通的标准输入输出重定向无法支持交互式命令（如 `top`、`vi`、Tab 补全）。`creack/pty` 在 Linux 内核中创建一个虚拟终端从设备（`/dev/pts/X`）并附加到 `/system/bin/sh`，将终端转义字符通过 WebSocket 双向流式转发给前端 xterm.js 渲染。",
                    "单二进制全栈交付：前端 xterm.js 的 HTML/JS 资产全部内嵌进 Go 二进制中，免去在极米文件系统部署前端文件的麻烦。",
                    "【参考开源项目】gotty（参考其 WebSocket 与 PTY 驱动核心连接通道）；ttyd（C 语言经典网页终端交互范式）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动网页终端守护进程：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 WebShell 并在浏览器中连接",
                        Code = "# 1. 启动网页终端守护进程（监听 7681 端口，启动 /system/bin/sh）\n" +
                               "nohup /data/local/tmp/z6x_webshell \\\n" +
                               "  -port 7681 \\\n" +
                               "  -cmd /system/bin/sh > /data/local/tmp/webshell.log 2>&1 &\n\n" +
                               "# 2. PC 验证端口连通性\n" +
                               "curl -s http://192.168.0.109:7681/ | head -n 10",
                        ExpectedOutput = "<!DOCTYPE html>\n<html>\n<head><title>Z6X WebShell</title>"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "网页终端运维排坑：",
                BulletPoints =
                [
                    "问题 1：打开终端提示 open /dev/ptmx: permission denied。原因与解决：Android 的 SELinux 策略在某些域下禁止普通用户打开伪终端主设备。ADB shell 用户拥有完整的 `devpts` 挂载权限，需确保二进制由 UID 2000（shell）启动运行。",
                    "问题 2：终端窗口尺寸不匹配导致字符换行错乱。前端在页面大小改变时向 WebSocket 发送 JSON（如 `{\"type\":\"resize\",\"cols\":80,\"rows\":24}`），Go 后端调用 `pty.Setsize` 动态重置内核终端尺寸。"
                ]
            }
        ]
    };
}
