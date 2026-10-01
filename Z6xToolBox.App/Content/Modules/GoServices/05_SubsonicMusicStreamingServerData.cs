using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class SubsonicMusicStreamingServerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "subsonic-music-streaming-server",
        Title = "5. 全天候低功耗音乐串流中枢：静态 Navidrome 原生部署",
        Group = "Go原生服务",
        Summary = "在极米后台部署静态编译的 Navidrome 音乐服务器，挂载 U 盘音乐库，对外提供 Subsonic 协议串流。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "许多用户在 U 盘或移动硬盘中存有几十到几百 GB 的高品质无损音乐（FLAC、APE、WAV、MP3）。若放在电脑上，想听歌必须保持电脑持续开机；若拷入手机，会迅速占满手机存储空间。\n\n" +
                       "将音乐 U 盘插在常年接电的极米投影仪上，部署轻量 Navidrome 音乐服务器后，投影仪直接化身为家庭专属的云音乐中枢。即使投影仪处于息屏关机（待机）状态，手机、平板或 Steam Deck 也可随时随地通过音乐 App 无线点播播放家中的无损曲库。",
                BulletPoints =
                [
                    "释放移动设备存储：几百 GB 音乐全放在投影仪 U 盘中，手机无需本地下载歌曲。",
                    "全天候随时听歌：投影仪待机芯片功耗仅几瓦，24 小时随时点播，免除开电脑的麻烦与噪音。",
                    "广泛的客户端支持：支持 iOS、Android、Linux 桌面端所有主流第三方 Subsonic 音乐播放器。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 纯静态编译版 Navidrome + 嵌入式 SQLite 数据库（单二进制体积约 25MB）。",
                    "什么是 Subsonic / OpenSubsonic 协议？一种专门针对个人音频串流制定的开放网络协议。只要服务端实现了该标准，前端客户端（如「音流」、Symfonium、Amplefin 等）输入 `http://192.168.0.109:4533` 和账号密码后，即可自动同步歌曲名、歌手、专辑封面、歌词与音轨流。",
                    "为什么不用 Plex / Emby / Jellyfin？Plex 和 Jellyfin 是重度全功能多媒体套件，依赖完整 .NET/Java/C++ 动态栈，常驻内存高达 300MB ~ 600MB，投影仪 3.5GB 内存无法长期承受；Navidrome 专精于音乐，空闲常驻内存仅 35MB ~ 50MB，对系统性能几乎零负担。"
                ]
            },
            new ContentSection
            {
                Heading = "部署配置与启动命令",
                Text = "推送二进制与环境变量配置后在后台常驻运行：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "后台启动 Navidrome 音乐服务（关闭服务端转码）",
                        Code = "# 1. 推送静态编译的 Navidrome 至设备\n" +
                               "adb push navidrome /data/local/tmp/ && \\\n" +
                               "adb shell chmod 755 /data/local/tmp/navidrome\n\n" +
                               "# 2. 启动服务（指定音乐库路径、禁用服务端 CPU 转码，原样串流）\n" +
                               "adb shell \"ND_MUSICFOLDER=/storage/XXXX-XXXX/Music \\\n" +
                               "ND_DATAFOLDER=/data/local/tmp/navidrome_data \\\n" +
                               "ND_PORT=4533 \\\n" +
                               "ND_TRANSCODING_ENABLED=false \\\n" +
                               "nohup /data/local/tmp/navidrome > /data/local/tmp/navidrome.log 2>&1 &\"",
                        ExpectedOutput = "# 服务启动，监听 0.0.0.0:4533，Web 管理面板已就绪"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "音乐服务常见故障与处理经验：",
                BulletPoints =
                [
                    "问题 1：播放高规格无损音频时 CPU 飙升导致卡顿。原因与解决：默认配置下 Navidrome 可能会尝试调用 ffmpeg 将 FLAC 转码为 MP3 传输，投影仪 CPU 算力有限会导致卡顿。务必设置 `ND_TRANSCODING_ENABLED=false` 彻底关闭服务端转码，让 Steam Deck 或手机本地解码原始音频。",
                    "问题 2：插拔 U 盘后扫描报错。原因与解决：Android 挂载路径按卷标命名，若更换 U 盘需确认 `ND_MUSICFOLDER` 的路径与实际 `/storage/` 下的目录一致。",
                    "问题 3：息屏后客户端播放一两首歌后断连。原因与解决：投影仪进入深度休眠断开了 Wi-Fi。需配合模块 11 开启 `wifi_sleep_policy 2`，保证息屏待机状态下局域网网络连接畅通。"
                ]
            }
        ]
    };
}
