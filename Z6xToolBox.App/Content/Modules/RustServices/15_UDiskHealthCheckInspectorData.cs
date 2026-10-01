using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class UDiskHealthCheckInspectorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "udisk-health-check-inspector",
        Title = "15. 自研 U 盘坏道与介质健康度检测探针：直接 I/O 与延迟抖动分析（Z6X RustDiskDoctor）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 自研的存储介质检测探针，基于 O_DIRECT 绕过操作系统缓存直接探测 U 盘扇区读写延迟与隐形坏块，提前预防数据损坏。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "外接的闲置大容量 U 盘或移动硬盘在长期高频写入（如离线下载、日志写入、录制存储）后，闪存颗粒极易发生坏块损耗或写放大，导致视频播放花屏、文件写入卡死甚至文件系统强行挂载为只读。\n\n" +
                       "使用 Rust 自研的存储健康探针后，工具利用直接 I/O（`O_DIRECT`）绕开 Android 系统的 PageCache 缓存，真实探测底层物理扇区的读取延迟分布；一旦发现单扇区读取延迟超过 500ms（潜在坏块征兆），提前生成告警报告，避免关键存档与媒体彻底丢失。",
                BulletPoints =
                [
                    "真实物理介质探测：绕过操作系统内存缓存，直测 U 盘颗粒真实读写响应。",
                    "隐形坏块与慢扇区预警：在文件彻底损坏前捕获 I/O 抖动异常。",
                    "零损耗只读扫描模式：提供安全的非破坏性只读体检模式，完全不影响盘内现有数据。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + Linux 底层系统调用 `libc::O_DIRECT` + 对齐内存分配（单文件体积约 650KB）。",
                    "什么是 O_DIRECT（直接 I/O）？普通读写会先经过 Linux PageCache 内存缓冲，即便底层扇区已损坏，只要缓存在内存里，读取依然返回成功；`O_DIRECT` 强制内核直接向 USB 控制器发起 SCSI/UAS 物理读指令，直面硬件真实状态。",
                    "扇区对齐缓冲：直接 I/O 强制要求内存缓冲区地址与大小必须为扇区大小（512 字节或 4096 字节）的整数倍。Rust 通过 `std::alloc::alloc_zeroed` 申请严格对齐的内存切片，保证无内核兼容性故障。",
                    "【参考开源项目】badblocks（Linux 经典坏道检测工具规范）；fio（高性能存储基准评估工具参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台对指定 U 盘分区执行健康度快速扫描：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行 U 盘快速健康巡检",
                        Code = "# 1. 对外接 U 盘执行 1GB 范围的只读延迟抽样检测\n" +
                               "/data/local/tmp/z6x_disk_doctor \\\n" +
                               "  -target /dev/block/vold/public:8,1 \\\n" +
                               "  -sample-mb 1024 -out /data/local/tmp/disk_health.json\n\n" +
                               "# 2. 查看介质健康度与延迟分布\n" +
                               "cat /data/local/tmp/disk_health.json | jq .",
                        ExpectedOutput = "{\n  \"status\": \"healthy\",\n  \"avg_read_latency_ms\": 2.8,\n  \"slow_sectors\": 0,\n  \"bad_blocks\": 0\n}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "存储健康检测排坑指南：",
                BulletPoints =
                [
                    "问题 1：非 Root 权限无法打开块设备 `/dev/block/vold/*`。解决：非 Root shell 可将检测目标直接设为 U 盘文件系统上的一个测试文件（如 `/storage/XXXX-XXXX/.test_block`），通过对该文件进行 `O_DIRECT` 读写完成介质吞吐与延迟健康评估。",
                    "问题 2：低端 U 盘不支持 O_DIRECT。自动回退为大文件多次强行 `fsync` 方式测速。"
                ]
            }
        ]
    };
}
