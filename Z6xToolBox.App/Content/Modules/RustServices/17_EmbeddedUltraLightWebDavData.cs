using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class EmbeddedUltraLightWebDavData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "embedded-ultra-light-webdav",
        Title = "17. 自研微型嵌入式 WebDAV 与文件直链引擎：纯 Rust 实现（Z6X RustWebDav）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 打造的超轻量 WebDAV 与 HTTP 静态直链服务，单文件仅 1.1MB，常驻内存低于 2.5MB，适合在极端内存受限环境下替代 Go WebDAV 服务。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Go 版本的 WebDAV 服务功能齐全，但由于自带 runtime 调度器与 GC 堆栈，常驻内存需要 15MB ~ 25MB。当极米投影仪前台正在运行极度消耗内存的 4K 播放器或高负荷任务时，任何多占用的内存都有可能加速触发系统 LMK 查杀。\n\n" +
                       "使用 Rust 自研超轻量 WebDAV 引擎后，基于底层异步框架实现 RFC 4918 核心方法（PROPFIND、GET、PUT、MKCOL、DELETE），编译产物仅 1.1MB，常驻物理内存死死锁定在 **2.0MB ~ 2.5MB** 之间，以近乎零成本的硬件开销提供全天候文件共享。",
                BulletPoints =
                [
                    "比 Go 版本再节省 80% 内存：物理内存常驻仅 2MB 出头，极端低内存环境稳定存活。",
                    "单二进制体积仅 1.1MB：剥离调试符号后体积仅为 Go 版本的五分之一。",
                    "完整标准协议支持：支持 Windows 网络驱动器挂载、Steam Deck Dolphin 与手机无感读写。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `hyper`（极速 HTTP 底座）+ `tokio`（单文件体积约 1.1MB）。",
                    "流式处理大文件上传下载：在处理数 GB 大文件写入时，Rust 借助异步流（`Stream`）将网络套接字数据直接写入 `tokio::fs::File`，读写操作完全不在用户空间堆上分配大块缓存，内存曲线平滑如直线。",
                    "XML 目录树快速格式化：通过只读字符串切片构建 WebDAV PROPFIND XML 应答，规避复杂的 DOM 树解析与频繁内存申请。",
                    "【参考开源项目】webdav-handler-rs（纯 Rust 官方标准的 WebDAV 处理器库）；miniserve（Rust 著名轻量文件共享工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动 Rust 微型 WebDAV 服务：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动微型 WebDAV 服务并挂载 U 盘",
                        Code = "# 1. 启动 Rust WebDAV 服务（监听 8085 端口，挂载 U 盘路径）\n" +
                               "nohup /data/local/tmp/z6x_rust_webdav \\\n" +
                               "  -dir /storage/XXXX-XXXX \\\n" +
                               "  -port 8085 > /data/local/tmp/webdav_rust.log 2>&1 &\n\n" +
                               "# 2. 查看实测物理内存占用（VmRSS）\n" +
                               "adb shell \"cat /proc/$(pidof z6x_rust_webdav)/status | grep VmRSS\"",
                        ExpectedOutput = "VmRSS:      2148 kB"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "微型 WebDAV 排坑指南：",
                BulletPoints =
                [
                    "问题 1：Windows 挂载时提示「文件夹无效」。原因与解决：Windows WebDAV 客户端在连接时会先发送一个根路径 `OPTIONS` 请求探测特性支持。Rust 路由中必须显式在响应头返回 `DAV: 1, 2` 及 `MS-Author-Via: DAV`，方可被 Windows 正常挂载为盘符。",
                    "问题 2：大文件 Range 请求。务必实现 `bytes=start-end` 请求头解析并调用 `seek`，确保视频点播进度条平滑快进。"
                ]
            }
        ]
    };
}
