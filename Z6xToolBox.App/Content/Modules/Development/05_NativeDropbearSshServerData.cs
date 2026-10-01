using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class NativeDropbearSshServerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "native-dropbear-ssh-server",
        Title = "5. 原生 SSH 守护进程与免密登录：静态 Dropbear 部署与 SCP 文件通道",
        Group = "开发环境",
        Summary = "部署纯静态 ARM64 版 Dropbear SSH 守护进程，以 UID 2000 shell 身份运行于非特权端口，支持公钥免密登录与 SCP 产物推送，替代 60MB 的受限应用。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "此前通过 Android 软件商店安装的 SimpleSSHD 应用运行在 Android 沙盒权限下（用户身份为 `u0_a68`），物理内存占用高达 60MB，且无法自由读写和执行 `/data/local/tmp` 目录下的开发二进制；同时日常运维必须开电脑连 ADB 终端，操作繁琐。\n\n" +
                       "部署原生静态 Dropbear 后，SSH 守护进程直接以系统 `shell` 用户（UID 2000）身份常驻后台，内存占用降至 2MB。Steam Deck 或 PC 终端可直接使用标准 `ssh` 命令免密登录，且外部编译产物可通过 `scp` 命令一键直传极米，无需依赖 ADB 数据线或网络 ADB 握手。",
                BulletPoints =
                [
                    "获取完整开发执行权：UID 2000 原生运行，对 `/data/local/tmp` 拥有完整 rwx 权限。",
                    "极致压降内存：从应用版的 60MB 内存直降到 2MB ~ 3MB。",
                    "打通一键部署传输通道：支持标准 SCP/SFTP 协议，编译产物一条命令直达极米开跑。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：C 语言编写、经由 musl-libc 纯静态交叉编译的 ARM64 Dropbear 二进制（单文件约 1.8MB）。",
                    "为什么选 Dropbear 而不是 OpenSSH？OpenSSH 依赖 glibc/PAM 鉴权和大量复杂的配置文件，移植体积超 20MB；Dropbear 是专为嵌入式 Linux 设计的超轻量 SSH 服务，支持单文件运行、ED25519/RSA 公钥认证，资源消耗仅为 OpenSSH 的五分之一。",
                    "非特权端口与 HostKey 准备：非 Root 用户不可监听标准 TCP 22 端口，故绑定 `:2222`；密钥对由 `dropbearkey -t ed25519` 本地生成保存在 `/data/local/tmp/.ssh/` 中。",
                    "【参考开源项目】Dropbear SSH（经典嵌入式 SSH 服务器实现）；Busybox 内置工具链。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "生成主机密钥并在极米后台启动 SSH 服务：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "生成密钥并启动 Dropbear 守护进程",
                        Code = "# 1. 建立配置目录并生成 ED25519 主机私钥\n" +
                               "mkdir -p /data/local/tmp/.ssh && \\\n" +
                               "/data/local/tmp/bin/dropbearkey -t ed25519 -f /data/local/tmp/.ssh/host_key_ed25519\n\n" +
                               "# 2. 写入 Steam Deck 的公钥实现免密认证\n" +
                               "echo \"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5... deck@steamdeck\" >> /data/local/tmp/.ssh/authorized_keys && \\\n" +
                               "chmod 600 /data/local/tmp/.ssh/authorized_keys\n\n" +
                               "# 3. 后台启动 Dropbear 监听 2222 端口（禁用密码登录，仅公钥）\n" +
                               "nohup /data/local/tmp/bin/dropbear -r /data/local/tmp/.ssh/host_key_ed25519 -p 2222 -s -g > /data/local/tmp/dropbear.log 2>&1 &",
                        ExpectedOutput = "# Dropbear 在后台启动，监听 0.0.0.0:2222"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "Dropbear 运维排坑指南：",
                BulletPoints =
                [
                    "问题 1：公钥登录提示 Permission Denied。原因与解决：SSH 强制校验目录权限。若 `/data/local/tmp/.ssh` 权限为 777 会触发安全防护拒登。务必执行 `chmod 700 /data/local/tmp/.ssh` 及 `chmod 600 /data/local/tmp/.ssh/authorized_keys`。",
                    "问题 2：登录后默认 Shell 路径错误。Android 系统的默认 Shell 位于 `/system/bin/sh`，启动参数需确认指定 shell 映射或在环境变量中指定 `SHELL=/system/bin/sh`。"
                ]
            }
        ]
    };
}
