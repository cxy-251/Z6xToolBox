using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class GalleryHubPhotoServiceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "gallery-hub-photo-service",
        Title = "8. 自研大屏相册服务：Go 缩略图缓存与瀑布流展示（Z6X Gallery Hub）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的大容量 U 盘相册服务，生成 WebP 缩略图并缓存，内嵌 HTML5 瀑布流供电视浏览器全屏流畅浏览。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "手机与相机备份在 100GB+ U 盘中的高清照片常有数万张。使用电视自带相册浏览时，因需一次性解码数百万像素的原始大图，系统频繁发生长达数秒的假死、切图卡顿甚至 OOM（内存溢出）闪退。\n\n" +
                       "自研该轻量相册服务后，Go 后端在后台利用单协程预先为大图生成轻量的 WebP 缩略图并做本地持久化缓存；电视自带浏览器全屏打开内嵌的瀑布流网页，遥控器左右平滑浏览，彻底解决大图扫描假死问题。",
                BulletPoints =
                [
                    "告别电视相册扫描崩溃：缩略图机制规避了电视直接解码几十 MB 单张原生大图的内存压力。",
                    "按时间线自动聚合：解析 EXIF 拍摄时间，自动按年/月归档呈现。",
                    "遥控器大屏沉浸看图：单页 Web 布局针对 1080p 电视比例优化，左右方向键平滑切换。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net/http` + 纯 Go 图像处理库（如 `disintegration/imaging`，无 CGO 依赖）。",
                    "什么是 WebP 动态缩略图缓存？当浏览器请求相册时，后端只返回 400px 宽度的 WebP 格式预览图（体积仅约 20KB），并缓存到 `/data/local/tmp/thumbs/` 目录。再次浏览直接从缓存读取，毫秒级加载。",
                    "什么是静态资源内嵌（`embed.FS`）？Go 语言的 `//go:embed` 指令可将前端 HTML/CSS/JS 代码在编译时直接打包成二进制的一部分，无需在投影仪上单独维护网页静态文件目录。",
                    "【参考开源项目】FileBrowser（参考其前端单文件内嵌打包方式与目录遍历树逻辑）；PhotoPrism（参考其 EXIF 时间线索引思路，剔除其沉重的人工智能模型，保留纯目录逻辑）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台启动相册服务并打开电视浏览器：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "后台启动自研相册服务并拉起前台浏览",
                        Code = "# 1. 启动相册服务（指定 U 盘照片目录与缩略图缓存路径）\n" +
                               "nohup /data/local/tmp/z6x_gallery \\\n" +
                               "  -photos /storage/XXXX-XXXX/Photos \\\n" +
                               "  -cache /data/local/tmp/thumbs \\\n" +
                               "  -port 8087 > /data/local/tmp/gallery.log 2>&1 &\n\n" +
                               "# 2. 极米前台拉起系统浏览器进入相册大屏\n" +
                               "am start -a android.intent.action.VIEW -d \"http://127.0.0.1:8087/\"",
                        ExpectedOutput = "# 服务启动并监听 8087 端口，前台展示 WebP 瀑布流大屏相册"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "自研相册常见问题与性能调优：",
                BulletPoints =
                [
                    "问题 1：生成缩略图瞬间 CPU 飙高导致投影卡顿。原因与解决：若并发对多个大图做尺寸缩放，会瞬间占满 4 核 A73。后端务必采用单 Worker 队列设计（单协程顺序处理），并设置 `runtime.GOMAXPROCS(2)` 限制最多占用两个 CPU 核心。",
                    "问题 2：缩略图缓存撑满内置闪存。原因与解决：数万张照片的缩略图可能占用 1GB~2GB 空间。缩略图缓存目录应配置在外接 U 盘自身（如 `/storage/XXXX-XXXX/.cache_thumbs/`），避免占用本机的 `/data` 空间。"
                ]
            }
        ]
    };
}
