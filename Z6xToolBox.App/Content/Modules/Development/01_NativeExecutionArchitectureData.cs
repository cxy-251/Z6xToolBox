using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class NativeExecutionArchitectureData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "native-execution-architecture",
        Title = "1. 原生执行边界与非 Root 运行机制",
        Group = "开发环境",
        Summary = "分析 Android 12 Linux 内核对静态 ELF 二进制的直接执行支持，排查 /data/local/tmp 挂载属性与非 Root 权限边界。",
        Sections =
        [
            new ContentSection
            {
                Heading = "文件系统挂载与执行权限验证",
                Text = "Android 为防止恶意应用在数据分区执行脚本，多数应用私有目录挂载或配置了限制。通过对 /data 分区挂载属性排查确认，极米 Z6X Pro 的 /data 分区未添加 noexec 挂载标志：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 /data 分区挂载参数与 tmp 目录权限",
                        Code = "mount | grep ' /data '\nls -ld /data/local/tmp",
                        ExpectedOutput =
                            "/dev/block/platform/bootdevice/by-name/userdata on /data type ext4 (rw,seclabel,nosuid,nodev,noatime,discard,noauto_da_alloc,data=ordered)\n" +
                            "drwxrwx--x 4 shell shell 4096 ... /data/local/tmp"
                    }
                ],
                BulletPoints =
                [
                    "未设置 noexec：/data 分区仅声明了 nosuid 与 nodev，并未启用 noexec，允许内核装载执行 ELF 文件。",
                    "Shell 权限直写：ADB 连接的用户身份为 shell（UID 2000, GID 2000），对 /data/local/tmp 拥有完整读、写、执行（rwx）权限。"
                ]
            },
            new ContentSection
            {
                Heading = "轻量原生方案与容器方案对比",
                Text = "对比三种不同运行架构的资源占用与执行效率：",
                BulletPoints =
                [
                    "轻量原生架构（采用方案）：PC 端交叉编译静态二进制，直接放入 /data/local/tmp 运行。无虚拟机、无解释器、无模拟层，物理内存占用小于 10MB，对机顶盒/投影仪微弱算力零额外开销。",
                    "Termux 套件方案：提供完整的包管理（apt/pkg）和 Bionic libc 编译环境，约占 200MB 存储，但部分包缺乏电视版 ARM64 支持，仍需通过 ADB 转发操控。",
                    "Debian / PRoot 容器方案：通过 ptrace 拦截所有底层系统调用以模拟 root/chroot 环境，系统调用开销大（I/O 速度下降 30%-60%），常驻内存 150MB-300MB，不适合 3.5GB 内存且运行 Android TV 的投影仪。"
                ]
            },
            new ContentSection
            {
                Heading = "网络端口与 SELinux 约束边界",
                Text = "非 Root 状态下原生进程的系统调用与网络监听规则：",
                BulletPoints =
                [
                    "端口监听边界：Linux 标准内核限制非 root 用户不可绑定 < 1024 特权端口。开发的原生服务需绑定 1024 以上非特权端口（如 8080, 8088, 9090）。",
                    "SELinux 规则：ADB shell 运行于 u:r:shell:s0 域，允许访问 /proc、/sys、网络 socket 绑定与连接，但禁止修改系统底层属性（如 setprop 核心配置）或读取应用私有加密数据。"
                ]
            }
        ]
    };
}
