using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ZstdStreamCompressorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zstd-stream-compressor",
        Title = "11. 自研 U 盘大容量 Zstd 实时流式压缩工具：ARM64 向量优化与极速归档（Z6X RustZstd）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 包装的 Zstandard 流式压缩工具，针对 ARM64 指令优化，以极低 CPU 占用与零中间文件写入实现几百 GB 媒体与日志压缩。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接 U 盘中存放的大量视频分段、历史日志与游戏存档占用大量磁盘空间。使用传统 `gzip` 或 `tar.gz` 压缩时，因算法单线程且内存吞吐低，在 4 核 A73 电视芯片上压缩一个 10GB 目录常常需要几十分钟，导致 CPU 满载发热。\n\n" +
                       "使用 Rust 自研流式压缩工具后，利用 Zstandard（zstd）高吞吐特征配合多核流水线，在保持同等压缩率的前提下，压缩速度提升 3 到 5 倍，且直接以流式写入 U 盘，不产生任何临时垃圾文件，内存恒定在 3MB 左右。",
                BulletPoints =
                [
                    "压缩速度提升数倍：相比传统 gzip，解压缩速度接近物理磁盘读取上限（> 200MB/s）。",
                    "流式边读边压：输入流直连输出文件，零磁盘临时缓存开销。",
                    "可调压缩等级与 CPU 平衡：支持 1-19 级弹性调控，日常使用 Level 3 保持极低 CPU 占用。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `zstd` crate（开启静态 C 绑定与 aarch64 汇编加速）+ `rayon` 并发流（单文件体积约 850KB）。",
                    "什么是 Zstandard（zstd）算法？Facebook 开发的高性能现代压缩标准。结合了有限状态熵（FSE）编码与哈希链查找，解压时几乎只涉及数组查表，非常契合 ARM64 弱 CPU 架构的运算特性。",
                    "零拷贝缓冲区流式穿透：在 Rust 中通过实现 `std::io::Write` trait，数据分块（Chunk 64KB）逐段流入压缩状态机并直接写入目标 U 盘句柄，杜绝跨堆拷贝。",
                    "【参考开源项目】zstd 官方实现；tar-rs（纯 Rust 实现的归档处理库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台对大容量目录执行高速压缩测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行大目录 zstd 高速压缩",
                        Code = "# 1. 采用 Level 3 极速压缩模式归档 U 盘日志与存档\n" +
                               "/data/local/tmp/z6x_zstd -c -lvl 3 \\\n" +
                               "  -src /storage/XXXX-XXXX/Logs \\\n" +
                               "  -out /storage/XXXX-XXXX/backup_logs.tar.zst\n\n" +
                               "# 2. 查看压缩耗时与压缩比\n" +
                               "ls -lh /storage/XXXX-XXXX/backup_logs.tar.zst",
                        ExpectedOutput = "-rw-r--r-- 1 shell shell 142M 10月 1日 15:55 backup_logs.tar.zst\n[Zstd] Ratio: 3.8x, Elapsed: 14.2s"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "流式压缩排坑指南：",
                BulletPoints =
                [
                    "问题 1：高压缩等级（Level 15+）导致内存爆炸。zstd 高等级需要分配几十兆字典窗口（Window Log）。在极米电视上务必将压缩级别限制在 1 至 5 之间，既能获得极高压缩速度，又将内存锁定在 3MB 以内。",
                    "问题 2：低速 U 盘写入堵塞。开启异步双缓冲队列，写入与压缩分离，防止 I/O 阻塞计算线程。"
                ]
            }
        ]
    };
}
