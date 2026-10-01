using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ZeroCopyRingBufferIpcData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zero-copy-ring-buffer-ipc",
        Title = "44. 自研跨进程无锁共享内存环形缓冲区 IPC：单生产者单消费者管道（Z6X RustShmRing）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的超高速跨进程环形缓冲区，基于 Linux 共享内存与无锁原子指针，实现音频与遥测数据零拷贝传输，常驻内存仅 500KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米系统内，当底层 Rust 音频采集或日志探针需要将大量原始流数据（如 48kHz PCM 音频、高频硬件采样）传输给上层 Go 业务服务时，使用本地 Unix Domain Socket 或 TCP 回环接口仍需经过两次内核态内存拷贝，且存在 Socket 缓冲区溢出丢帧风险。\n\n" +
                       "使用 Rust 自研的跨进程无锁环形队列，通过在共享内存文件上建立单生产者单消费者（SPSC）内存模型。两端直接通过内存指针读写数据，利用原子变量同步头部与尾部偏移量，完全免除系统调用与拷贝开销，吞吐可达数 GB/s，常驻内存仅约 500KB。",
                BulletPoints =
                [
                    "完全免除系统调用：数据写入与读取无需触发进入内核态的 syscall。",
                    "单生产者单消费者无锁设计：基于内存屏障与原子指针，读写两端互不阻塞。",
                    "纳秒级传输延迟：跨进程数据传递耗时降至数十纳秒，避免音频爆音卡顿。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `memmap2` + `core::sync::atomic::{AtomicUsize, Ordering}`。",
                    "环形缓冲区回绕设计：缓冲区大小取 2 的幂次方（如 1MB），利用位与运算 `offset & (CAPACITY - 1)` 替代慢速的取模运算，加速指针回绕。",
                    "ARM64 内存屏障保障：写入数据后使用 `Release` 语义更新写指针，读取端使用 `Acquire` 语义读取指针，确保多核 CPU 间数据可见性。",
                    "【参考开源项目】disruptor（LMAX 经典无锁环形队列）；rtrb（Rust 专为实时音频设计的无锁环形缓冲库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "创建跨进程无锁队列并执行吞吐压测：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "创建共享内存队列并测试零拷贝吞吐",
                        Code = "# 1. 初始化 1MB 容量的跨进程共享环形队列\n" +
                               "/data/local/tmp/z6x_shm_ring init \\\n" +
                               "  -file /data/local/tmp/audio_stream.ring \\\n" +
                               "  -size 1048576\n\n" +
                               "# 2. 执行跨进程无锁吞吐压测（发送 1000 万条 64 字节帧）\n" +
                               "/data/local/tmp/z6x_shm_ring bench -file /data/local/tmp/audio_stream.ring",
                        ExpectedOutput = "[RingBuffer] Mapped 1048576 bytes from /data/local/tmp/audio_stream.ring\n[Bench] Transferred 640.0 MB across processes\n[Stat] Elapsed: 182ms, Throughput: 3516 MB/s, Avg Latency: 18ns/msg [PASS]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "读者进程崩溃后写者挂死防范：若读者意外退出，环形缓冲区写满后写者可能会持续空转自旋，工具内置自旋 1000 次后降级为 `futex_wait` 或检查对方 PID 存活性以避免 CPU 跑满。",
                    "文件锁权限保护：共享内存文件需设置 `0600` 权限，防止其他低特权第三方应用窥探音频原始流。"
                ]
            }
        ]
    };
}
