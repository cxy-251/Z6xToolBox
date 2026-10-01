using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class MemoryMappedConfigStoreData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "memory-mapped-config-store",
        Title = "33. 自研跨进程共享内存 mmap 配置同步总线：无锁原子变量状态共享（Z6X RustShmConfig）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的无锁共享内存配置与状态总线，基于 Linux tmpfs/ashmem 内存映射实现纳秒级跨进程标志位同步，常驻内存仅 400KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米后台部署的多个服务（Go 业务服务、Rust 底层看门狗、Shell 自动化脚本）之间，经常需要共享全局状态标志（例如：是否处于勿扰模式、是否静音、当前亮度挡位、是否暂停后台下载等）。传统做法是写入文件或者通过 HTTP API 互相查询，存在磁盘损耗和毫秒级网络调用开销。\n\n" +
                       "使用 Rust 自研的共享内存同步总线，通过在内存文件系统 `/dev/shm`（或 Android 的 `/data/local/tmp/shm_state`）创建固定大小的内存映射文件，内部采用原子变量进行数据排布。多个进程以 mmap 方式挂载，状态修改在纳秒级跨进程生效，常驻内存仅约 400KB。",
                BulletPoints =
                [
                    "纳秒级状态同步：直接对物理内存原子指令操作，读写延迟 < 50ns。",
                    "零磁盘寿命损耗：基于 tmpfs 纯内存文件系统，绝不产生物理 flash 擦写。",
                    "多语言兼容互通：基于标准 C 内存布局，Go、C、Rust 与 Python 均可无缝读取。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `memmap2` + `core::sync::atomic`（无锁原子指令序列）。",
                    "C-ABI 内存对其结构体：通过 `#[repr(C, align(64))]` 避免 CPU 缓存行伪共享，保障高频并发读写性能。",
                    "轻量命令行读写接口：二进制自身既可作为共享内存初始化服务，又可作为单行 CLI 工具供脚本调用。",
                    "【参考开源项目】ipc-channel（Rust 原生跨进程通信抽象）；redis（键值存储，本模块为其最小化无守护进程形态）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "初始化共享内存区并测试跨进程快速写入与读取：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "初始化 mmap 状态区并执行跨进程原子读写",
                        Code = "# 1. 初始化 4KB 共享内存状态文件\n" +
                               "/data/local/tmp/z6x_shm init -file /data/local/tmp/z6x_state.bin\n\n" +
                               "# 2. 进程 A 写入勿扰模式开关标志位\n" +
                               "/data/local/tmp/z6x_shm set -file /data/local/tmp/z6x_state.bin -key dnd_mode -val 1\n\n" +
                               "# 3. 进程 B 快速读取该标志位\n" +
                               "/data/local/tmp/z6x_shm get -file /data/local/tmp/z6x_state.bin -key dnd_mode",
                        ExpectedOutput = "[SHM] Initialized 4096 bytes at /data/local/tmp/z6x_state.bin\n[SHM] Set key 'dnd_mode' = 1 (atomic write in 38ns)\n1"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "Android tmpfs 挂载点差异：原生 Linux 通常有 `/dev/shm`，但在 Android 上可能未挂载或权限受限，推荐统一建立在 `/data/local/tmp/` 目录下并通过 `ftruncate` 设定文件大小。",
                    "跨进程内存重排防护：原子操作务必使用 `Ordering::SeqCst` 或 `Ordering::AcqRel`，防止 ARM64 弱内存模型下多核 CPU 指令重排导致的状态脏读。"
                ]
            }
        ]
    };
}
