using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class SftpServerStorageGatewayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "sftp-server-storage-gateway",
        Title = "35. 自研纯 Go SFTP 文件传输服务端：安全加密文件通道（Z6X SftpNode）",
        Group = "Go原生服务",
        Summary = "纯 Go 实现的轻量 SFTP 子系统服务，免安装 OpenSSH 守护即可通过 FileZilla/WinSCP 安全管理极米 U 盘，常驻内存仅 14MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "如果使用普通的 FTP 服务传输文件，账号密码与数据在局域网内均为明文传输，且极米上移植 vsftpd 容易因为非 root 无法绑定特权端口和 PAM 认证缺失而失败。\n\n" +
                       "使用 Go 自研的独立 SFTP 服务端，基于纯 Go 原生 SSH 库实现 SSHv2 握手与 SFTP v3 协议子系统。独立监听高位端口（如 `:2022`），支持基于公钥免密登录或密码认证，局域网电脑直接使用 FileZilla、WinSCP 或 VS Code 远程连接管理 U 盘文件，常驻物理内存约 14MB。",
                BulletPoints =
                [
                    "全链路加密安全传输：SSHv2 协议层加密，防明文流量抓包窃听。",
                    "零 OpenSSH 依赖：纯 Go 原生实现，免除 openssh-server 与复杂的 pam 依赖。",
                    "支持权限与目录禁锢（Chroot）：可限定客户端仅能访问外置 U 盘路径，保护机身内部关键目录。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `golang.org/x/crypto/ssh` + `pkg/sftp`（纯 Go 核心子系统实现）。",
                    "单进程独立服务：无需依赖系统的 `/etc/passwd`，用户凭证与公钥完全由 YAML 配置文件管理，适配 Android 无标准用户数据库的痛点。",
                    "流式文件管道：文件读取与写入直接映射底层 `os.File`，借助标准并发协程实现多文件并发多线程加速传输。",
                    "【参考开源项目】pkg/sftp（Go 生态最主流的 SFTP 服务器实现库）；sftpgo（功能完备的 SFTP/WebDAV 网关参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 SFTP 服务并使用 sftp 命令行测试登录与文件上传：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 SFTP 服务端并测试传输",
                        Code = "# 1. 启动轻量 SFTP 服务（监听 2022 端口，根目录映射 U 盘）\n" +
                               "nohup /data/local/tmp/z6x_sftp \\\n" +
                               "  -port 2022 \\\n" +
                               "  -user media -pass \"media123\" \\\n" +
                               "  -root /mnt/media_rw/USB_DISK > /data/local/tmp/sftp.log 2>&1 &\n\n" +
                               "# 2. PC 端使用标准 sftp 客户端连接测试\n" +
                               "sftp -P 2022 media@192.168.1.100 <<< $'ls\\nexit'",
                        ExpectedOutput = "[SFTP] Listening on :2022 (Host key auto-generated ED25519)\n[Auth] User 'media' authenticated successfully from 192.168.1.50\nConnected to 192.168.1.100.\nsftp> ls\nMovies   Music   Photos   Downloads"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "HostKey 主机公钥持久化：初次启动自动生成的 ED25519 主机私钥必须保存到 `/data/local/tmp/id_ed25519`，避免重启服务后客户端提示主机密钥改变警告（Host key verification failed）。",
                    "连接并发数限制：为防止 WinSCP 打开多线程传输拉起过多并发协程耗尽内存，需限制每个客户端最大并发 SFTP 通道数为 4。"
                ]
            }
        ]
    };
}
