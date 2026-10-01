using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class S3CompatibleMinioLiteStorageData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "s3-compatible-minio-lite-storage",
        Title = "26. 自研嵌入式 S3 兼容对象存储网关：U 盘存储标准化 API 暴露（Z6X S3Lite）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型 S3 兼容对象存储服务，将极米外接 U 盘转为轻量 S3 桶端点，供局域网设备按标准协议备份与存取，常驻内存仅 18MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "现代备份软件（如 Restic、Kopia、Duplicati）以及手机同步应用越来越倾向于使用标准 AWS S3 兼容协议来存储文件。但官方 MinIO 二进制体积庞大（常超 100MB）且常驻内存高达 200MB 以上，无法在 3.5GB 内存的投影仪上长期挂载。\n\n" +
                       "使用 Go 自研的微型 S3 网关，实现 S3 核心子集（PutObject、GetObject、ListObjectsV2、DeleteObject）。将外接 U 盘目录映射为 Bucket 桶，局域网内的电脑和手机可通过标准 S3 客户端无缝上传与拉取备份，常驻物理内存约 18MB。",
                BulletPoints =
                [
                    "兼容主流备份工具：无缝对接 Restic、Kopia 与各类支持 S3 API 的软件。",
                    "轻量资源开销：单二进制体积仅 14MB，常驻内存仅为官方 MinIO 的十分之一。",
                    "直接映射物理文件系统：U 盘中的对象即为普通文件，可直接拔下在电脑正常读取。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http` 原生路由器 + AWS Signature v4 签名校验器（单二进制无外部 CGO 依赖）。",
                    "流式直通磁盘：PutObject 请求使用 `io.Copy` 将 HTTP 请求体直接流式写入 U 盘临时文件，避免将整个对象加载到内存堆中。",
                    "签名验证优化：仅针对头部与凭证计算 SHA256 HMAC 摘要，签名通过即放行数据流，降低四核 ARM 处理器验签开销。",
                    "【参考开源项目】minio（标准对象存储参考）；s3proxy（轻量 S3 协议代理网关设计）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 S3 网关并通过 AWS CLI 测试文件上传：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 S3 兼容存储网关并执行对象上传测试",
                        Code = "# 1. 启动轻量 S3 服务（监听 9000 端口，指定存储根目录与 AccessKey）\n" +
                               "nohup /data/local/tmp/z6x_s3 \\\n" +
                               "  -port 9000 \\\n" +
                               "  -dir /mnt/media_rw/USB_DISK/s3_storage \\\n" +
                               "  -access-key admin \\\n" +
                               "  -secret-key secret123 > /data/local/tmp/s3.log 2>&1 &\n\n" +
                               "# 2. PC 端使用 aws-cli 执行桶创建与文件上传\n" +
                               "aws --endpoint-url http://192.168.1.100:9000 s3 mb s3://mybackup\n" +
                               "aws --endpoint-url http://192.168.1.100:9000 s3 cp test.iso s3://mybackup/\n\n" +
                               "# 3. 查看服务端写入状态\n" +
                               "tail -n 10 /data/local/tmp/s3.log",
                        ExpectedOutput = "[S3] Listening on :9000 (Bucket Root: /mnt/media_rw/USB_DISK/s3_storage)\n[S3] PUT /mybackup/test.iso -> 200 OK (Transferred 450 MB in 5.2s, Speed: 86.5 MB/s)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "大对象分片上传（Multipart Upload）：针对超过 5GB 的大文件，服务需支持简易的分片合并逻辑，分片临时文件需存放在同一个物理分区以避免跨分区重命名失败。",
                    "文件系统名称长度限制：exFAT 文件系统对路径深度与文件名有最大 255 字符限制，深层命名空间对象需做散列映射防护。"
                ]
            }
        ]
    };
}
