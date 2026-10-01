using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LinuxCgroupResourceJailData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "linux-cgroup-resource-jail",
        Title = "48. 自研 Linux Cgroups 轻量资源隔离沙箱：非特权子进程配额管理（Z6X RustJail）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的嵌入式进程资源隔离沙箱，基于 Linux cgroups v1/v2 限制后台子进程 CPU 配额与最大内存，常驻内存仅 600KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米后台运行 WebDAV、Aria2 或视频转码服务时，一旦发生偶发内存泄漏或多线程占满 CPU，会导致前台投影画面剧烈卡顿，严重时触发系统 LMK 连带杀掉核心前台播放器。\n\n" +
                       "使用 Rust 自研的微型沙箱隔离器，直接通过挂载的 Linux cgroups 控制节点（`/sys/fs/cgroup/cpu`、`memory`），创建专属控制组。通过向 `memory.limit_in_bytes` 和 `cpu.cfs_quota_us` 写入参数，对纳管的后台进程严格设定 CPU 使用率上限（如最高 30%）与物理内存硬顶（如最高 100MB），超出立即就地熔断，常驻内存仅约 600KB。",
                BulletPoints =
                [
                    "硬件资源硬配额隔离：防止后台任务失控抢占前台观影 CPU 算力与内存。",
                    "子进程防膨胀熔断：精确将子进程内存限制在阈值内，超出直接局部终止，不伤及系统。",
                    "轻量资源守护：无需启动 Docker 等重型容器运行时，仅用 Linux 原生内核特性。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `nix` + cgroups 文件接口读写 + POSIX fork/execve 包装器（单二进制体积仅 590KB）。",
                    "CFS 调度器周期配额算法：配置 `cpu.cfs_period_us = 100000`（100ms），配额 `cpu.cfs_quota_us = 30000`，使子进程每 100ms 内最多占用 30ms CPU 时间，严格限制在 30% 单核负载。",
                    "内存 OOM 计数捕获：监听 `memory.oom_control` 事件描述符，在子进程达到内存上限触发 OOM Killer 前发出告警日志。",
                    "【参考开源项目】cgroups-rs（Rust 原生 cgroups 管理库）；systemd-nspawn（轻量命名空间容器）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动被配额限制的子进程并验证资源锁定：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "在沙箱中限制进程 CPU 与内存上限",
                        Code = "# 1. 在沙箱配额保护下启动后台任务（限制 CPU 25%，内存最高 80MB）\n" +
                               "/data/local/tmp/z6x_jail \\\n" +
                               "  -name bg_worker \\\n" +
                               "  -cpu-percent 25 \\\n" +
                               "  -mem-max 80M \\\n" +
                               "  -- /data/local/tmp/aria2c --enable-rpc\n\n" +
                               "# 2. 查看 cgroups 状态与资源使用量\n" +
                               "cat /sys/fs/cgroup/memory/z6x_jail/bg_worker/memory.usage_in_bytes",
                        ExpectedOutput = "[Jail] Created cgroup node: /sys/fs/cgroup/memory/z6x_jail/bg_worker\n[Jail] Set CPU quota to 25000/100000 us (25%)\n[Jail] Set memory limit to 83886080 bytes (80MB)\n[Jail] Spawned child PID 19280 in cgroup"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "非 root 权限 cgroups 目录写权限：Android 系统默认将 `/sys/fs/cgroup` 归属于 root。在普通 shell 下，需利用系统已预先向 shell 放权的子目录（如 `/dev/cpuctl` 或 `/sys/fs/cgroup/tasks`）进行分组控制，若无写权限则自动退回采用 POSIX `setpriority(PRIO_PROCESS)` 降低调度优先级（nice 值到 19）。",
                    "cgroups v1 与 v2 挂载兼容：工具会自动探测当前内核是采用 hybrid 还是 unified 层次结构，根据路径自动适配配置语法。"
                ]
            }
        ]
    };
}
