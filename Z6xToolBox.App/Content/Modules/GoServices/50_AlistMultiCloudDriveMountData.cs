using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class AlistMultiCloudDriveMountData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "alist-multi-cloud-drive-mount",
        Title = "50. 自研轻量云盘多存储挂载与聚合网关：网盘转本地 WebDAV（Z6X CloudMount）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型云盘聚合挂载服务，把阿里云盘/百度网盘等主流网盘转为本地 WebDAV 目录供电视播放器直接点播，常驻内存仅 20MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端很多第三方播放器（如 Kodi、Nova Video Player）不支持直接扫码登录各类商业网盘，而官方 AList 原版二进制功能较重（包含数十种国外小众网盘驱动与前端管理界面），常驻内存达 60MB~80MB。\n\n" +
                       "使用 Go 自研的轻量网盘挂载网关，只保留国内主流网盘（阿里云盘、夸克、百度网盘、115）的核心 OpenToken 刷新与直链解析逻辑。对外统一暴露一个标准的非加密 WebDAV 服务（如 `:5244`），电视播放器添加该 WebDAV 即可直接串流播放原画视频，常驻物理内存约 20MB。",
                BulletPoints =
                [
                    "网盘转标准 WebDAV：电视播放器无需适配各类私有网盘 SDK，无缝直接点播。",
                    "轻量裁剪设计：剥离庞大前端静态文件与不必要的国外网盘依赖，内存压降 60% 以上。",
                    "直链 302 重定向播放：视频流直接由播放器与网盘 CDN 建立连接，不走极米中转节省本地带宽。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http` + 纯 Go WebDAV 处理器 + Token 自动持久化存储。",
                    "HTTP 302 临时重定向：播放器请求文件时，服务调用网盘 OpenAPI 获取带签名的 CDN 下载直链并返回 302 Redirect，让播放器直连下载，极米自身 CPU 占用几乎为 0。",
                    "自动刷新 Token 协程：后台协程在 AccessToken 过期前半小时自动刷新并更新 SQLite 凭证表，避免播放中途突然鉴权失效。",
                    "【参考开源项目】alist（流行的多存储文件列表程序）；rclone（多云存储同步工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动轻量网盘挂载服务并测试直链重定向：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动网盘聚合挂载服务并请求视频直链",
                        Code = "# 1. 启动轻量网盘挂载服务（监听 5244 端口）\n" +
                               "nohup /data/local/tmp/z6x_cloudmount \\\n" +
                               "  -port 5244 \\\n" +
                               "  -config /data/local/tmp/cloud.yaml > /data/local/tmp/cloud.log 2>&1 &\n\n" +
                               "# 2. 使用 curl 请求挂载目录中的视频文件，检查是否返回 302 播放直链\n" +
                               "curl -I http://127.0.0.1:5244/dav/aliyun/Movie.mp4",
                        ExpectedOutput = "HTTP/1.1 302 Found\nLocation: https://cn-beijing-data.alicloud.com/download/Movie.mp4?sign=...\n[CloudMount] Redirected client directly to cloud CDN (Bandwidth bypassed)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "网盘 API 频控保护：禁止高频递归扫描整个网盘目录，播放器刮削建议关闭全盘探测，仅按需拉取一级目录，防止触发网盘接口封禁。",
                    "Referer 与 User-Agent 校验：部分网盘对 CDN 直链校验严格，需在配置中支持注入伪装请求头以防止播放器直连时报错 403 Forbidden。"
                ]
            }
        ]
    };
}
