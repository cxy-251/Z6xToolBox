using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class WebDavFileHubServiceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "webdav-file-hub-service",
        Title = "1. 局域网大容量存储中枢：Go 静态 WebDAV / 文件直链服务",
        Group = "Go原生服务",
        Summary = "基于 Go 纯静态编译的 WebDAV 服务，将 47GB 空闲闪存与外接 100GB+ U 盘挂载为局域网微盘，支持多设备免客户端读写。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "家庭内经常需要在 Steam Deck、手机与 PC 之间互相传文件（游戏安装包、截图、视频等）。通常做法是开微信/QQ传输、买 NAS、或者用物理 U 盘拔来拔去。\n\n" +
                       "极米 Z6X Pro 常年插电连 Wi-Fi，机身自带 47GB 空闲存储，后置还有 USB 接口可常插 100GB+ 的闲置 U 盘。通过部署该服务，投影仪直接变成一台静音微型网络网盘，任何设备打开系统自带的文件管理器就能直接像本地硬盘一样拷贝、剪切和播放，不需要额外买硬件。",
                BulletPoints =
                [
                    "解决大文件互传麻烦：Steam Deck 游戏安装包或视频直接拖进投影仪盘符，不用插拔 U 盘。",
                    "利用闲置大容量空间：激活机身 47GB 闪存与外接 U 盘，不浪费硬件资源。",
                    "零客户端依赖：Windows 资源管理器、Mac Finder、Steam Deck Dolphin 文件管理器均自带 WebDAV 支持，无需安装任何第三方 App。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准扩展库 `golang.org/x/net/webdav`。纯静态链接（CGO_ENABLED=0），生成单一二进制文件（约 6MB），无需任何动态依赖库，放入 Android 即可直接运行。",
                    "什么是 WebDAV（RFC 4918）？普通 HTTP 协议通常只能「下载」网页和文件；WebDAV 是基于 HTTP 的扩展协议，增加了 PROPFIND（读取目录树）、MKCOL（新建文件夹）、MOVE/COPY（改名与移动）、PUT（上传）等标准接口，操作系统能将其识别为标准网络盘符。",
                    "什么是 HTTP Range 切片传输？读取大视频时，客户端不需一次性将几百兆文件全部下载到内存，而是发送带有 `Range: bytes=0-1048575` 头的请求，实现按需分块加载和毫秒级快进。",
                    "为什么比 Python / Node 方案更合适？Python/Node 需要在设备上安装几百兆的运行时和几千个依赖文件，常驻内存 100MB+；而 Go 静态单文件直接由 Linux 内核调度，空闲内存仅占约 15MB，读写时 < 30MB。"
                ]
            },
            new ContentSection
            {
                Heading = "交叉编译与部署命令",
                Text = "在 PC 端完成编译并推送到投影仪后台运行：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "Go WebDAV 交叉编译与后台启动",
                        Code = "# 1. PC 端单指令编译为 ARM64 静态二进制\n" +
                               "CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags=\"-s -w\" -o z6x_webdav main.go\n\n" +
                               "# 2. 推送至设备并赋予执行权限\n" +
                               "adb push z6x_webdav /data/local/tmp/\n" +
                               "adb shell chmod 755 /data/local/tmp/z6x_webdav\n\n" +
                               "# 3. 后台守护启动（挂载 /storage 路径，监听 8085 端口）\n" +
                               "adb shell \"nohup /data/local/tmp/z6x_webdav -port 8085 -dir /storage > /data/local/tmp/webdav.log 2>&1 &\"",
                        ExpectedOutput = "# 服务启动，监听 0.0.0.0:8085，挂载根路径 /storage"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "实机调试时可能遇到的常见故障与解决办法：",
                BulletPoints =
                [
                    "问题 1：U 盘重新拔插后路径失效。解决：Android 会根据 U 盘 UUID 挂载在 `/storage/XXXX-XXXX/`。若重新格式化或换 U 盘，挂载路径变动，服务启动参数建议直接挂载上一级的 `/storage`，即可自动兼容所有新插入的设备。",
                    "问题 2：端口绑定失败报错 bind: permission denied。解决：当前为非 Root（shell 用户），Linux 内核禁止非特权进程绑定 1024 以下端口（如 80、443），必须指定 1024 以上端口（如 8085）。",
                    "问题 3：局域网无法连通。解决：确认 PC 与投影仪处于同一个路由器子网（检查 IP `192.168.0.x`）；如开启了访客网络或 AP 隔离需在路由器后台关闭。",
                    "问题 4：大文件上传中断。解决：Android TV 系统默认的 lowmemorykiller（LMK）机制在内存吃紧时可能回收后台。启动 Go 服务时建议加上环境变量 `GOGC=50` 提高垃圾回收频率，将堆内存峰值压在 25MB 以内。"
                ]
            }
        ]
    };
}
