using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class WebpThumbnailGeneratorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "webp-thumbnail-generator",
        Title = "47. 自研海报墙与相册 WebP 高速缩略图生成器：内存缓存分发（Z6X ThumbGen）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型图片缩略图生成与 WebP 转换服务，流式缩放 U 盘相册与海报墙大图并提供本地缓存，常驻内存仅 18MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端自带的图片浏览器或海报墙在打开装有数千张数码相机原图（单张 10MB~20MB）的 U 盘时，由于一次性解码多张超大分辨率 JPEG/PNG 图片，会导致系统内存瞬时打满，出现严重的掉帧或界面卡死崩溃。\n\n" +
                       "使用 Go 自研的轻量缩略图服务，对外暴露动态图片获取接口（如 `/thumb?path=/...&w=300&h=450`）。服务端采用双三次插值（Bicubic）算法就地缩放并压缩为高效的 WebP 格式（体积通常仅为原图的 3%），配合内存与磁盘二级缓存，保证电视大屏滑动流畅不掉帧，常驻物理内存约 18MB。",
                BulletPoints =
                [
                    "大图秒变轻量缩略图：按需实时生成指定尺寸图片，节约 95% 以上传输带宽与渲染内存。",
                    "转码为高效 WebP 格式：体积比普通 JPEG 缩减 30%，解码速度大幅加快。",
                    "两级缓存防重复计算：已生成的缩略图自动缓存至机身快速存储，二次加载仅需 2ms。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `disintegration/imaging`（纯 Go 快速图片处理）+ `chai2010/webp`（纯安全 Go 封装）。",
                    "单帧限制与协程并发池：将图片解码与缩放的并发工作协程严格限制为 2，防止并发上传原图时将 4 核 CPU 算力占满导致遥控器卡顿。",
                    "LRU 内存缓存设计：在内存中保留最近访问的 200 张小缩略图，其余沉淀至 U 盘隐藏的 `.cache` 目录，兼顾命中速度与低内存。",
                    "【参考开源项目】imagor（Go 语言高性能图片处理服务器）；bimg（基于 libvips 的缩放参考，本模块为其免动态库精简版）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动缩略图服务并请求指定尺寸图片：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动图片缩略图动态生成服务",
                        Code = "# 1. 启动服务（监听 8092 端口，缓存目录设置在本地 tmpfs）\n" +
                               "nohup /data/local/tmp/z6x_thumb \\\n" +
                               "  -port 8092 \\\n" +
                               "  -cache /data/local/tmp/thumb_cache \\\n" +
                               "  -max-cache-mb 50 > /data/local/tmp/thumb.log 2>&1 &\n\n" +
                               "# 2. 发送请求将 15MB 原始相机海报转为 300x450 WebP\n" +
                               "curl -s \"http://192.168.1.100:8092/thumb?path=/mnt/media_rw/USB_DISK/Photos/RAW_001.jpg&w=300&h=450\" -o /data/local/tmp/thumb.webp\n\n" +
                               "# 3. 查看转换前后体积比对\n" +
                               "ls -lh /data/local/tmp/thumb.webp",
                        ExpectedOutput = "-rw-r--r-- 1 shell shell 18K 10月 1日 16:42 thumb.webp\n[Thumb] Processed 15.4 MB JPEG -> 18.2 kB WebP in 38ms (Cache: SAVED)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "EXIF 旋转元数据矫正：数码照片经常带有 EXIF Orientation 属性，解码时需先调用 `imaging.AutoOrientation` 纠正偏角，防止生成的缩略图横置倒置。",
                    "大图解码内存防击穿（OOM Bomb）：限制单张图片最大原始像素不得超过 4000x4000，防止畸形超大图片瞬间申请过量内存触发系统 LMK。"
                ]
            }
        ]
    };
}
