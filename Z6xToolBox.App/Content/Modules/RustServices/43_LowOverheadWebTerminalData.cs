using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LowOverheadWebTerminalData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "low-overhead-web-terminal",
        Title = "43. 自研零动态库 Web 控制台网关：纯 Rust WebSocket 与 PTY 终端（Z6X RustWebTerm）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的轻量 Web 终端网关，静态内嵌 HTML/JS 终端与 WebSocket PTY 桥接，无需外部 Node/Python 依赖，常驻内存仅 1.8MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "如果想在手机浏览器或 Steam Deck 掌机上无需打开 SSH 客户端直接调试极米后台，传统的 ttyd 或 web-shell 往往依赖 libwebsockets、openssl 以及外部静态文件目录，编译繁琐且常驻内存超过 15MB。\n\n" +
                       "使用 Rust 自研的 Web 终端，基于纯静态编译将 xterm.js 压缩代码直接打包进可执行文件。单二进制内嵌微型 HTTP 服务器、WebSocket 协议处理器与 Linux PTY 伪终端桥接器。打开浏览器即可获得带有 ANSI 颜色支持的完整交互终端，常驻物理内存仅约 1.8MB。",
                BulletPoints =
                [
                    "单二进制自包含：HTML/JS 前端与后端 PTY 桥接打包为单个文件，零外部资源依赖。",
                    "轻量资源开销：常驻内存仅 1.8MB，远低于 Node/Python 实现的 Web 控制台。",
                    "全平台浏览器兼容：手机、平板、电脑均可直接通过浏览器输入命令调试极米。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `tungstenite`（纯 Rust 轻量 WebSocket 库）+ `nix::pty` + 宏内嵌静态资源。",
                    "双向无缓冲流传输：WebSocket 收到按键字符即刻直写 PTY master 描述符，PTY 输出由 epoll 事件驱动分片封装为二进制帧广播回前端。",
                    "Basic Auth 简易鉴权：支持在启动命令行中指定用户名与密码参数，防止局域网未经授权的非法访问。",
                    "【参考开源项目】ttyd（C 语言经典 Web 终端工具，本模块为其内存安全纯 Rust 替代）；xterm.js（前端终端渲染标准组件）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 Web 终端网关并在浏览器访问：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Web 控制台并测试交互连接",
                        Code = "# 1. 启动轻量 Web 终端服务（绑定 7681 端口，默认启动 /bin/sh）\n" +
                               "nohup /data/local/tmp/z6x_webterm \\\n" +
                               "  -port 7681 \\\n" +
                               "  -auth admin:secret123 \\\n" +
                               "  -cmd /bin/sh > /data/local/tmp/webterm.log 2>&1 &\n\n" +
                               "# 2. 查看监听状态\n" +
                               "cat /data/local/tmp/webterm.log",
                        ExpectedOutput = "[WebTerm] Listening on http://0.0.0.0:7681\n[Auth] Basic auth enabled for user 'admin'\n[Session] Accepted WebSocket handshake from 192.168.1.50 -> Forked PTY (PID 18402)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "Android 终端控制码支持：部分 Android 内置 sh 缺少 Tab 补全支持，建议启动参数传入 `/data/local/tmp/bin/busybox sh` 获得完整的 readline 体验。",
                    "移动端软键盘遮挡：手机端访问时软键盘弹出可能顶起视口，前端内嵌脚本已默认绑定 `window.visualViewport` 动态调整终端行高。"
                ]
            }
        ]
    };
}
