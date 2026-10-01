using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class SimdFastFileChecksumData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "simd-fast-file-checksum",
        Title = "4. 自研大文件高速哈希校验与去重工具：ARM64 NEON 指令集加速（Z6X RustHash）",
        Group = "Rust原生服务",
        Summary = "利用 Rust 对底层硬件指令集的零成本控制能力，调用 ARM64 NEON 向量指令集做 xxHash3 极速哈希计算与 mmap 内存映射，秒级完成几百 GB U 盘媒体去重。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接的 100GB+ U 盘或移动硬盘中，往往混杂着从手机备份、电脑下载的不同版本的电影、视频素材和游戏安装包，存在大量同名异位或异名同内容的重复文件，白白浪费数十 GB 的宝贵闪存空间。\n\n" +
                       "若在极米上用常规脚本或 Go 计算 SHA-256 哈希，因 CPU 单核性能较弱且频繁发生内存拷贝，扫描几百 GB 文件通常需要几个小时且内存占用大幅波动。使用 Rust 编写的哈希去重工具直接调用联发科芯片的 ARM64 NEON 硬件向量指令，配合零拷贝内存映射（mmap），将几百 GB 文件的查重耗时缩短 80% 以上。",
                BulletPoints =
                [
                    "硬件级向量指令加速：利用 ARM64 NEON 寄存器一次性并发计算 128 位数据块哈希。",
                    "零拷贝内存映射（mmap）：文件数据绕过用户态缓冲区，直通内核页缓存（PageCache）。",
                    "安全幂等去重：精准比对文件真实内容哈希，自动识别重复文件并生成清理建议清单。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `xxhash-rust`（开启 `xxh3` 与 `neon` 特性）+ `memmap2` crate（单文件体积约 800KB）。",
                    "什么是 ARM64 NEON 向量指令集？MediaTek MT9669 的 4 个 Cortex-A73 核心内部均集成了 NEON 向量处理单元。Rust 编译器在开启目标特性时，能将循环哈希运算直接编译为单指令多数据流（SIMD）汇编指令，单核哈希吞吐即可超过 2GB/s，瓶颈仅取决于 U 盘接口的物理读取上限。",
                    "什么是 mmap 零拷贝？传统 `read()` 系统调用需要将数据从内核缓存拷贝到用户空间数组；`memmap2` 将 U 盘文件直接映射到进程的虚拟地址空间，利用 Linux 内核自动换页，零额外物理内存堆分配。",
                    "【参考开源项目】xxHash（由 Yann Collet 设计的极速非加密哈希算法标准）；czkawka（Rust 编写的著名跨平台文件查重清理工具）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台执行 U 盘高速查重扫描：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行 U 盘大文件秒级查重分析",
                        Code = "# 1. 扫描外接 U 盘目录并输出重复文件报告\n" +
                               "/data/local/tmp/z6x_rust_hash \\\n" +
                               "  -dir /storage/XXXX-XXXX/Videos \\\n" +
                               "  -min-size 50M \\\n" +
                               "  -out /data/local/tmp/duplicates.json\n\n" +
                               "# 2. 查看查重报告中的重复浪费空间统计\n" +
                               "cat /data/local/tmp/duplicates.json | jq .summary",
                        ExpectedOutput = "{\n  \"scanned_files\": 1240,\n  \"duplicate_groups\": 18,\n  \"wasted_space\": \"14.6 GB\",\n  \"elapsed_seconds\": 38.2\n}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "NEON 向量计算排坑指南：",
                BulletPoints =
                [
                    "问题 1：U 盘随机 I/O 成为唯一瓶颈。USB 2.0/3.0 接口在大文件读取时受物理带宽限制。Rust 工具设计时采用「两级哈希策略」：先比对文件字节大小，相同者仅读取头部 4KB 快速哈希过滤，只有前两步完全一致的候选者才执行全文件 NEON 计算，规避 95% 的无效 I/O。",
                    "问题 2：mmap 遇到 U 盘中途被拔出触发 SIGBUS 信号导致崩溃。Rust 中需使用 `mmap` 的安全封装并在读写处捕获总线异常错误。"
                ]
            }
        ]
    };
}
