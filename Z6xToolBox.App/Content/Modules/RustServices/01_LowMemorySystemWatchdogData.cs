using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LowMemorySystemWatchdogData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "low-memory-system-watchdog",
        Title = "1. 自研极限低内存系统看门狗：RAII 资源管理与 1MB 免杀守护（Z6X RustWatchdog）",
        Group = "Rust原生服务",
        Summary = "基于 Rust 无 GC 特性自研的微型系统看门狗，常驻物理内存仅约 1MB，负责全天候监控 Go 服务集群存活并在系统内存紧张时保持自身免疫免杀。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "如果用 Go 或 Python 编写看门狗进程，守护进程自身通常就要吃掉 5MB ~ 15MB 物理内存；当电视前台播放 4K 超高清电影或启动大型游戏导致系统内存（MemAvailable）瞬间跌破警戒线时，连看门狗本身都会被 Android TV 的 LMK（低内存查杀）机制一同杀死，失去守护自愈能力。\n\n" +
                       "使用 Rust 自研看门狗后，得益于无运行时与 RAII（资源获取即初始化）机制，程序剥离调试符号后体积仅约 600KB，常驻物理内存（VmRSS）压低至 1MB 左右。系统将其视作轻量级系统守护任务，在极端低内存环境下依然保持免杀，全天候守护后台其他业务进程。",
                BulletPoints =
                [
                    "极限低内存常驻：物理内存常驻仅 1MB ~ 1.5MB，比 Go 守护进程节省 70% 以上内存。",
                    "无 GC 零 CPU 尖峰：休眠时 CPU 占用恒定为 0.0%，唤醒检测仅耗时数十微秒。",
                    "自动拉起崩溃微服务：周期性轮询 Go 集群状态，发现进程异常退出即刻自动重启。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + 标准库 `std`（单二进制交叉编译目标 `aarch64-unknown-linux-musl`）。",
                    "为什么 Rust 能做到 1MB 内存？Go 进程启动必须装载 runtime 调度器、GC 分代扫描堆栈；Rust 完全没有 GC，内存由编译器在编译期推导出释放时机（RAII），栈内存随函数退出即刻归还，堆上几乎零多余内存开销。",
                    "如何精准探测目标进程？通过 Linux 系统调用 `kill(pid, 0)`。向目标 PID 发送信号 0 并不真正中断进程，仅由内核校验该 PID 是否存在并具备访问权，单次探测开销在微秒级别，无需通过慢速的 `ps` 或文本解析。",
                    "【参考开源项目】daemontools（经典 UNIX 守护工具设计思想）；tini（Docker 极简轻量 Init 进程设计参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "PC 端使用 Rust 交叉编译后推送启动：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "Rust 交叉编译与后台启动看门狗",
                        Code = "# 1. PC 交叉编译 ARM64 musl 静态二进制（开启 LTO 与 strip 优化）\n" +
                               "cargo build --target aarch64-unknown-linux-musl --release\n\n" +
                               "# 2. 推送至极米并启动看门狗\n" +
                               "adb push target/aarch64-unknown-linux-musl/release/z6x_watchdog /data/local/tmp/\n" +
                               "adb shell chmod 755 /data/local/tmp/z6x_watchdog\n" +
                               "adb shell \"nohup /data/local/tmp/z6x_watchdog -interval 5 > /data/local/tmp/watchdog.log 2>&1 &\"\n\n" +
                               "# 3. 查看实测物理内存占用（VmRSS）\n" +
                               "adb shell \"cat /proc/$(pidof z6x_watchdog)/status | grep -E 'Name|VmRSS'\"",
                        ExpectedOutput = "Name:   z6x_watchdog\nVmRSS:      1124 kB"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "Rust 看门狗排坑指南：",
                BulletPoints =
                [
                    "问题 1：musl-gcc 静态交叉编译链接报错。原因与解决：在非 musl 环境下编译时若缺少 `musl-tools`，需在 Host 端安装 `musl-tools` 或直接使用 `cross` 容器化交叉编译工具（`cross build --target aarch64-unknown-linux-musl`）。",
                    "问题 2：进程 ID 循环复用误判。Linux PID 耗尽后会循环分配。探测目标时除校验 `kill(pid, 0)` 外，需额外核对 `/proc/<PID>/cmdline` 的实际二进制名，避免误判其他新启动的应用。"
                ]
            }
        ]
    };
}
