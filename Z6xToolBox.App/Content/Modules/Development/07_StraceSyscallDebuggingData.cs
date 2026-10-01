using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class StraceSyscallDebuggingData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "strace-syscall-debugging",
        Title = "7. 系统调用排错工具箱：静态 strace 部署与报错排查",
        Group = "开发环境",
        Summary = "部署纯静态 ARM64 版 strace 诊断工具，直接追踪二进制进程发起的系统调用，秒级排查外部程序闪退、权限拒绝与死锁瓶颈。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在 PC 上交叉编译的程序（Go、Rust、C 等）推送到极米运行后，常常发生意料之外的故障：例如启动瞬间直接闪退、终端冷酷地抛出 `Segmentation fault`、`Permission denied`，或者进程卡在某个环节死锁不输出任何日志。\n\n" +
                       "在没有完整源码调试环境的极米上，部署静态 `strace` 后，无需重新修改代码打印日志，一条命令即可透视目标程序与 Linux 内核交互的每一个动作（尝试打开了什么文件、调用了什么网络 socket、哪个系统调用返回了错误码 -1），将排错定位时间压缩到秒级。",
                BulletPoints =
                [
                    "透视黑盒程序崩溃原因：一眼定位引发崩溃或闪退的最后一笔底层指令。",
                    "精准定位权限与缺文件问题：精准查明具体是哪个目录不可写或缺少动态库。",
                    "零源码侵入排查死锁：快速看清程序卡在哪一个 `epoll_wait` 或 `futex` 阻塞调用上。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：C 语言编写、由 musl-gcc 静态交叉编译的 ARM64 `strace` 二进制（单文件约 1.5MB）。",
                    "什么是系统调用（Syscall）拦截？用户态程序要与硬件交互（读写磁盘、发网络包、申请内存），必须向内核发起系统调用。strace 利用 Linux 内核的 `ptrace(PTRACE_SYSCALL)` 机制在每次内核调用入口与出口处设置断点，打印函数名（如 `openat`）、实参、返回值以及 `errno`（如 `EACCES` 权限拒绝、`ENOENT` 文件不存在）。",
                    "非 Root 权限边界：在非 Root 状态下，UID 2000 的 strace 只能挂载并追踪由自身启动的子进程，无法跨用户追踪 Android 系统服务（`system_server`）。",
                    "【参考开源项目】strace 官方源码库（Linux 领域最核心的系统诊断利器）。"
                ]
            },
            new ContentSection
            {
                Heading = "常用排错诊断命令集",
                Text = "常用高频排错场景实战指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "典型排错与追踪实战指令",
                        Code = "# 1. 过滤追踪所有文件打开动作（排查为什么找不到配置文件）\n" +
                               "/data/local/tmp/bin/strace -e trace=openat,open,access /data/local/tmp/my_app\n\n" +
                               "# 2. 过滤追踪网络与端口绑定（排查为什么端口监听失败）\n" +
                               "/data/local/tmp/bin/strace -e trace=network /data/local/tmp/my_app\n\n" +
                               "# 3. 统计程序系统调用的耗时占比（定位性能热点与慢 I/O）\n" +
                               "/data/local/tmp/bin/strace -c /data/local/tmp/my_app",
                        ExpectedOutput = "openat(AT_FDCWD, \"/data/local/tmp/config.yaml\", O_RDONLY) = -1 ENOENT (No such file or directory)\n# 一眼看出是 config.yaml 路径不存在"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "strace 诊断排坑指南：",
                BulletPoints =
                [
                    "问题 1：报错 ptrace: Operation not permitted。原因与解决：Android 内核开启了 `yama` 安全限制。当尝试通过 `strace -p <PID>` 挂载一个已经运行的后台进程时容易被拦截。建议在启动程序时直接用 strace 包装：`strace /data/local/tmp/app`，由父子进程直接继承追踪权限。",
                    "问题 2：日志量过大刷屏。生产排错时务必使用 `-e trace=...` 限定过滤范围，或加上 `-o /data/local/tmp/trace.log` 将跟踪记录写入文件分析。"
                ]
            }
        ]
    };
}
