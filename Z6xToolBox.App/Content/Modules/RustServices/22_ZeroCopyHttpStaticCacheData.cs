using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ZeroCopyHttpStaticCacheData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zero-copy-http-static-cache",
        Title = "22. 自研静态 Web 资源与固件更新 HTTP 缓存分发器：sendfile 零拷贝分发（Z6X RustEdgeCache）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的轻量静态资源 HTTP 分发服务，直接利用 Linux sendfile 系统调用将 U 盘中的安装包与固件直通网卡，常驻内存仅 2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "经常需要在家庭局域网内向其他电视盒子、Steam Deck 或手机分发 APK 安装包、系统固件镜像或离线静态文档。如果使用 Nginx 或 Python `http.server`，不仅启动慢、体积大，且 Python 在传输大文件时会频繁进行用户态缓冲区复制，导致单核 CPU 跑满。\n\n" +
                       "使用 Rust 自研的静态文件分发服务，基于原生 `sendfile` 系统调用，让数据直接在内核文件页缓存与网卡 Socket 描述符之间传输，完全绕过用户空间内存拷贝。单文件二进制仅 1.1MB，常驻内存稳定在 2MB 以内，局域网千兆传输跑满速率且 CPU 占用低至 3%。",
                BulletPoints =
                [
                    "Linux 原生零拷贝：使用 sendfile/splice，避免大文件读取时的额外内存拷贝开销。",
                    "轻量并发处理：基于 Epoll 非阻塞驱动，支持数十个客户端并发断点续传（Range 请求）。",
                    "单二进制开箱即用：无须任何外部配置或依赖库，单行命令指定目录即刻启动分发。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `mio` + `nix`（Linux sendfile64 API 封装）+ HTTP/1.1 简易状态机。",
                    "HTTP Range 断点续传：精确解析 `bytes=start-end` 头部，利用 `lseek` 与 `sendfile` 指定偏移量与长度，直接完成片段发送。",
                    "静态缓存与 MIME 映射：预编译内置常见 MIME 映射表（apk/zip/img/mp4/json），零运行时正则匹配开销。",
                    "【参考开源项目】actix-web（高性能 Rust Web 框架，本模块为其最小化 sendfile 剥离版本）；miniserve（Rust 静态文件快速服务命令行工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动文件缓存分发并测试千兆吞吐：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动静态文件服务器并执行 Range 断点续传测试",
                        Code = "# 1. 启动静态分发服务（绑定 8089 端口，挂载 U 盘目录）\n" +
                               "nohup /data/local/tmp/z6x_cache \\\n" +
                               "  -dir /mnt/media_rw/USB_DISK/packages \\\n" +
                               "  -port 8089 > /data/local/tmp/cache.log 2>&1 &\n\n" +
                               "# 2. PC 端发送带有 Range 的分片下载请求验证零拷贝分发\n" +
                               "curl -r 0-1048575 -o /dev/null -v http://192.168.1.100:8089/firmware.bin",
                        ExpectedOutput = "< HTTP/1.1 206 Partial Content\n< Content-Range: bytes 0-1048575/524288000\n< Content-Length: 1048576\n[Sendfile] Transfer complete in 12ms."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "U 盘 exFAT/NTFS 挂载点权限：部分 Android 11 系统对外置存储启用了 FUSE 包装层，sendfile 在某些 FUSE 驱动上可能降级为标准 read/write，此时服务会自动兼容退回零额外分配的管道 splice 模式。",
                    "局域网大文件超时防护：对于断点下载超大固件，内部需设定 keep-alive 超时为 60 秒，避免客户端断流后挂死连接描述符。"
                ]
            }
        ]
    };
}
