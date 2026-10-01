using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class Aria2HeadlessDownloadNodeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "aria2-headless-download-node",
        Title = "2. 脱机离线下载节点：静态 ARM64 aria2c + JSON-RPC 调度",
        Group = "Go原生服务",
        Summary = "部署静态编译的 ARM64 aria2c 客户端，开启 JSON-RPC 接口配合外部 Web UI，利用外接 U 盘实现夜间静音脱机下载。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "平时从网络下载大体积资源（几十 GB 的 4K 电影、Steam 游戏备份、几百 GB 短视频素材包）通常需要电脑或 Steam Deck 整夜开机挂机，风扇发热且耗电。\n\n" +
                       "极米 Z6X Pro 具备常插电、连接 5G Wi-Fi、且在关闭光机（息屏）时芯片功耗仅几瓦且零风扇噪音的物理优势。将大容量 U 盘插在投影仪上，部署脱机下载服务后，电脑或手机只需把下载链接或种子往控制网页一扔，投影仪便会在夜间自动下载到 U 盘中，下完后局域网各设备直接通过共享读取。",
                BulletPoints =
                [
                    "告别电脑整夜挂机：省电、静音、不占用工作机带宽。",
                    "自动落盘大容量 U 盘：直接存入外置存储，不挤占系统机身空间。",
                    "随时随地远程推送：在公司用手机看到想看的视频，发送链接给家里的极米，回家即下好。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：静态编译 ARM64 `aria2c` 二进制（单文件约 5MB，无依赖）+ JSON-RPC 通信协议 + AriaNg 前端界面。",
                    "什么是 aria2c？纯 C++ 开发的高性能轻量多线程下载器，支持 HTTP/HTTPS、FTP、BitTorrent 磁力链接和 Metalink 协议，支持把单个大文件切分成数十个分块同时并发拉取。",
                    "什么是 JSON-RPC？一种轻量级的远程指令协议。aria2c 在后台静默运行并监听 6800 端口，手机端无需登录，直接通过 HTTP POST 发送一段 JSON 文本（如 `{\"method\": \"aria2.addUri\", ...}`），即可跨网络增删下载任务或读取进度。",
                    "为什么不用现成的 TV 版下载 App（如迅雷 TV / 百度网盘）？商业 TV 客户端体积臃肿（常超过 100MB）、附带大量开屏广告与推广、强制要求界面在前台展示，且占用 150MB+ 内存；而纯静态 aria2c 无界面、零多余开销，物理内存常驻仅 15MB~30MB。"
                ]
            },
            new ContentSection
            {
                Heading = "核心配置文件与启动指令",
                Text = "编写精简配置文件并启动守护进程：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "生成配置文件并后台常驻运行",
                        Code = "# 1. 写入精简下载配置（存储路径指向 U 盘，开启 RPC 监听）\n" +
                               "cat << 'EOF' > /data/local/tmp/aria2.conf\n" +
                               "dir=/storage/emulated/0/Download\n" +
                               "enable-rpc=true\n" +
                               "rpc-listen-all=true\n" +
                               "rpc-listen-port=6800\n" +
                               "rpc-secret=z6x_token\n" +
                               "max-concurrent-downloads=3\n" +
                               "max-connection-per-server=8\n" +
                               "split=8\n" +
                               "file-allocation=trunc\n" +
                               "EOF\n\n" +
                               "# 2. 后台启动守护进程\n" +
                               "nohup /data/local/tmp/bin/aria2c --conf-path=/data/local/tmp/aria2.conf > /data/local/tmp/aria2.log 2>&1 &",
                        ExpectedOutput = "# aria2c 在后台运行，监听端口 6800"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "脱机下载常见问题与处理经验：",
                BulletPoints =
                [
                    "问题 1：单个文件超过 4GB 时下载报错（File size exceeds limit）。原因与解决：U 盘出厂默认为 FAT32 格式，单文件上限为 4GB。建议将 U 盘在电脑上格式化为 exFAT 或 NTFS，Android 12 内核自带 exFAT 驱动，可无上限写入大文件。",
                    "问题 2：下载磁力链接无速度或搜不到 Peers。原因与解决：Android 内部缺少公网映射且 DHT 初始节点为空。需在配置文件中添加 `bt-tracker` 活跃服务器列表，或在手机端先通过 AriaNg 上传带有 metadata 的 `.torrent` 种子文件。",
                    "问题 3：磁盘预分配导致卡死。原因与解决：默认 `file-allocation=prealloc` 会在大文件开始前先在 U 盘写满 0 占位，低速 U 盘会导致 I/O 假死甚至触发 LMK 杀死进程。务必在配置中指定 `file-allocation=trunc`（快速截断分配，秒建文件）。",
                    "问题 4：关闭投影仪画面后下载停止。原因与解决：系统息屏待机可能触发 Wi-Fi 芯片节能睡眠。需配合执行模块 11 中的网络防休眠配置（`settings put global wifi_sleep_policy 2`）。"
                ]
            }
        ]
    };
}
