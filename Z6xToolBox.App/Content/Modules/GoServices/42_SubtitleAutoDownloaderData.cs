using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class SubtitleAutoDownloaderData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "subtitle-auto-downloader",
        Title = "42. 自研本地影视字幕自动检索与哈希匹配器：射手与 SubHD 接口（Z6X SubSync）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的影视字幕自动检索工具，通过提取视频特征哈希自动从射手网与 SubHD 下载中文字幕并重命名归档，常驻内存仅 12MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接 U 盘或网络下载的原盘电影经常缺少中文字幕，或者自带的英文字幕无法在电视遥控器上轻松切换。如果每次都去电脑上找字幕、解压、重命名再拷贝回 U 盘，操作链条极其繁琐。\n\n" +
                       "使用 Go 自研的字幕自动匹配服务，监控 U 盘媒体目录。当检测到新增无中文字幕的视频文件时，仅读取文件头尾特定偏移字节计算射手特征哈希（Shooter Hash），并发向字幕 API 发起精准检索。自动下载最佳匹配的 `.zh.srt` 或 `.zh.ass` 放到同一目录下，常驻物理内存约 12MB。",
                BulletPoints =
                [
                    "特征哈希精准命中：无需精确文件名，直接比对视频特定帧二进制特征，匹配度高。",
                    "自动下载与重命名：下载完成后自动与视频文件同名保存，播放器即开即加载。",
                    "多字幕源并发重试：聚合射手网、SubHD、Assrt 等开放接口，单源失败自动切换。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http` + 视频文件分段流式读取（无外部 C 动态库绑定）。",
                    "Shooter Hash 算法实现：对视频文件前 4KB、中段 4KB、后 4KB 以及特定采样点计算 MD5 校验和，无需读取几个 GB 的全文件，单片哈希耗时 < 5ms。",
                    "自动字符集转换：下载的字幕若是 GBK/BIG5 编码，服务使用 `golang.org/x/text/encoding` 在内存中转为 UTF-8，彻底解决电视播放器乱码问题。",
                    "【参考开源项目】ChineseSubFinder（自动化中文字幕下载工具）；subliminal（跨平台字幕检索核心逻辑参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动字幕自动下载服务并测试单文件匹配：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行单个视频字幕哈希匹配与下载",
                        Code = "# 1. 针对 U 盘指定视频执行字幕自动抓取\n" +
                               "/data/local/tmp/z6x_subsync \\\n" +
                               "  -file /mnt/media_rw/USB_DISK/Movies/Oppenheimer.2023.mkv \\\n" +
                               "  -lang chn \\\n" +
                               "  -format srt\n\n" +
                               "# 2. 查看同目录下是否生成对应字幕\n" +
                               "ls -lh /mnt/media_rw/USB_DISK/Movies/Oppenheimer.2023*",
                        ExpectedOutput = "[SubSync] Calculated Shooter Hash: 4a2b91... (Time: 3ms)\n[API] Matched 2 subtitles from Shooter/SubHD\n[Download] Saved UTF-8 subtitle: Oppenheimer.2023.zh.srt (78 kB) [SUCCESS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "字幕 API 接口鉴权与频控：部分公开字幕接口有每分钟并发限制，程序内置了每次调用间隔 1 秒的限速，避免批量拉取时被接口封禁 IP。",
                    "只读介质写入失败保护：若 U 盘由于异常拔插被挂载为只读（Read-Only），服务会自动将字幕回退保存至机身本地缓存目录并通过大屏弹出提示。"
                ]
            }
        ]
    };
}
