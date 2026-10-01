using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class ComicEbookReaderServerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "comic-ebook-reader-server",
        Title = "13. 自研漫画与电子书阅览中枢：U 盘流式解压与大屏翻页（Z6X Reader Hub）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的轻量漫画与电子书阅读服务，对 U 盘内的 CBZ/ZIP/EPUB 压缩包进行流式随机读取，内嵌 HTML5 阅读器供手机或电视大屏翻页。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "用户在 U 盘中存放了几十到几百 GB 的漫画资源包（CBZ、CBR、ZIP 格式）或技术文档电子书（EPUB、PDF）。若要在手机或平板上看，每次都要通过数据线拷进设备再解压几十个压缩包，过程繁琐且迅速占满手机存储空间。\n\n" +
                       "自研该阅览中枢后，极米在后台直接流式读取插入的 U 盘压缩包；前端提供内嵌的纯静态触控/遥控翻页网页。手机或平板扫码即在浏览器中无感翻页阅读，阅读进度自动保存在极米本地，无需在移动端解压任何文件。",
                BulletPoints =
                [
                    "免解压直读压缩包：直接读取 `.zip` 和 `.cbz` 内的图片流，不浪费极米闪存做中间解压。",
                    "多端进度实时同步：手机看一半，换平板或投影大屏打开自动续读。",
                    "完全释放手机空间：数百 GB 漫画全在投影 U 盘上，手机零空间占用。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `archive/zip` + 单页 Web 阅读器（单二进制体积约 6MB）。",
                    "什么是流式随机解压（Random Access Zip）？ZIP 文件的目录索引（Central Directory）存放在压缩包末尾。Go 后端通过 `zip.NewReader` 先读取尾部几十 KB 的目录树建立文件索引，当客户端翻到第 15 页时，仅对那一张图片进行单文件流式解压并输出给前端，不需要解压整个几百兆的压缩包。",
                    "什么是前端内嵌单页阅读器？将基于 Canvas 的翻页组件通过 `embed.FS` 打包入 Go 二进制中，支持触屏左右滑动、预加载下一页图片以及全屏双页排版模式。",
                    "【参考开源项目】Kavita（参考其对 CBZ/ZIP 漫画分卷的元数据组织模型，剔除其庞大的 C# 运行时）；go-epub（参考其提取 EPUB 内部 HTML 与图片资源的流式处理方式）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台启动阅读中枢服务并在手机浏览器打开：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动漫画与电子书阅览服务",
                        Code = "# 1. 启动漫画服务（挂载 U 盘漫画目录，监听 8093 端口）\n" +
                               "nohup /data/local/tmp/z6x_reader \\\n" +
                               "  -library /storage/XXXX-XXXX/Comics \\\n" +
                               "  -port 8093 > /data/local/tmp/reader.log 2>&1 &\n\n" +
                               "# 2. PC 验证 API 目录返回\n" +
                               "curl -s http://192.168.0.109:8093/api/books | jq .",
                        ExpectedOutput = "[\n  {\"id\":\"vol1\",\"title\":\"Volume 01.cbz\",\"pages\":186}\n]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "漫画阅读服务常见问题：",
                BulletPoints =
                [
                    "问题 1：ZIP 包内文件名乱码。原因与解决：早期 Windows 制作的压缩包文件名多为 GBK 编码，标准库默认按 UTF-8 解码会导致图片名乱码。Go 代码需加入编码自动嗅探（如引入 `golang.org/x/text/encoding/simplifiedchinese`）确保兼容。",
                    "问题 2：高频翻页造成内存震荡。原因与解决：解压图片时的临时字节缓冲区应使用 `sync.Pool` 循环复用，避免高频翻页触发 GC 停顿，保持内存稳定在 20MB 以内。"
                ]
            }
        ]
    };
}
