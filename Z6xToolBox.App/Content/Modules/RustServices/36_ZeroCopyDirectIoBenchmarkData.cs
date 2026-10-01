using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ZeroCopyDirectIoBenchmarkData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zero-copy-direct-io-benchmark",
        Title = "36. 自研底层存储吞吐与随机读写 IOPS 评测工具：O_DIRECT 裸盘基准（Z6X RustIoBench）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的嵌入式存储基准测试工具，通过 O_DIRECT 绕过 Linux 页缓存测量 U 盘与 eMMC 读写性能，常驻内存仅 800KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接 U 盘或移动硬盘在极米投影仪上播放 4K 蓝光原盘（码率 > 80Mbps）时，有时会出现声音继续播放但画面定格的假死现象。使用常规的 `dd` 测试无法反映真实情况，因为 Linux 页缓存会虚标瞬时速率，无法测出小文件随机读写 IOPS 与持续写入掉速缺陷。\n\n" +
                       "使用 Rust 自研的存储测试工具，通过 `O_DIRECT` 与 `O_SYNC` 标志位打开文件描述符，直接对硬件介质执行对齐的内存块读写。能测量 4K 随机读写 IOPS、64K 顺序吞吐与 I/O 延迟尾部抖动，常驻物理内存仅约 800KB。",
                BulletPoints =
                [
                    "绕过系统页缓存干扰：采用 O_DIRECT 测量物理介质的真实裸盘吞吐与延迟。",
                    "4K 随机 IOPS 测量：评估存储介质在多进程并发读写时的抗拥堵能力。",
                    "轻量开箱即用：体积仅 700KB，无需在 Android 设备上安装复杂的 fio 工具。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `libc`（Linux posix_memalign 内存对齐分配）+ `std::time::Instant` 精密测量。",
                    "扇区边界内存对齐：由于 O_DIRECT 要求用户态缓冲区地址与文件偏移必须是 512 或 4096 字节的整数倍，使用 Rust 分配器精准构造对齐内存块。",
                    "I/O 尾部延迟分布：不仅输出平均速度，还统计 P95/P99 慢 I/O 耗时，排查 U 盘坏块导致的瞬间卡顿卡死。",
                    "【参考开源项目】fio（Linux 经典 I/O 压测工具）；bonnie++（硬盘与文件系统性能评测套件）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "执行裸盘读写性能与随机 IOPS 压测：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行 O_DIRECT 存储基准压测",
                        Code = "# 1. 针对外接 U 盘执行 4K 随机读取与 64M 顺序写入压测\n" +
                               "/data/local/tmp/z6x_io_bench \\\n" +
                               "  -target /mnt/media_rw/USB_DISK/bench.tmp \\\n" +
                               "  -direct \\\n" +
                               "  -bs 4k \\\n" +
                               "  -size 64M\n\n" +
                               "# 2. 查看评测指标与延迟分布\n" +
                               "cat /data/local/tmp/bench.tmp.result",
                        ExpectedOutput = "[IoBench] Mode: O_DIRECT (Bypass Page Cache), Block Size: 4096 B\n[Sequential Write] Speed: 32.4 MB/s\n[Random Read 4K] IOPS: 840, Avg Latency: 1.18ms, P99 Latency: 18.2ms [PASS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "FAT32 文件系统对 O_DIRECT 的支持：部分 Android 内核挂载 FAT32/vfat 时不支持 `O_DIRECT`（会返回 `EINVAL`），此时测试工具需自动退回为开启 `O_SYNC` 配合 `posix_fadvise(POSIX_FADV_DONTNEED)` 模拟直写效果。",
                    "Flash 磨损防护：基准测试应限制单次写入文件大小在 64MB~128MB 以内，避免频繁在电视机身 eMMC 跑全盘压测损伤闪存寿命。"
                ]
            }
        ]
    };
}
