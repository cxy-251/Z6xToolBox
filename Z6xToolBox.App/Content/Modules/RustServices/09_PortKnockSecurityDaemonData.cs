using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class PortKnockSecurityDaemonData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "port-knock-security-daemon",
        Title = "9. 自研快速端口敲门与非特权端口隐蔽守护器：原始套接字序列认证（Z6X RustKnock）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的轻量端口敲门与动态访问控制守护器，默认关闭高危管理端口，收到特定端口敲击序列后动态放行 IP，常驻物理内存仅约 500KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米投影仪上开启 SSH、调试终端或 WebDAV 之后，如果设备接入了合租网络、宿舍局域网或带有访客 Wi-Fi 的环境，开放的端口极易受到内网扫描和自动化爆破尝试。\n\n" +
                       "使用 Rust 自研的端口敲门服务，电视后台平时不开放任何管理端口（在外部探测呈现关闭或超时状态）。当管理员需要在电脑或手机上访问极米时，只需向指定的一组闭合端口发送特定顺序的 UDP/TCP 包，Rust 守护器在内核层检测到有效序列后，动态将该来源 IP 加入放行名单，开启管理端口连接通道。常驻内存仅约 500KB。",
                BulletPoints =
                [
                    "隐藏后台管理端口：所有管理端口默认处于静默状态，防端口扫描器探测。",
                    "轻量序列认证：基于预设敲门序列（如 UDP 7001 -> TCP 8002 -> UDP 9003）触发认证。",
                    "自动超时重锁：放行一定时长（如 1 小时）后自动关闭通道，避免残留暴露面。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `pcap`/`socket2` + 纯非阻塞状态机（二进制仅 600KB）。",
                    "非特权端口拦截设计：在 Android 11 非 root 环境下，无法直接修改 iptables，RustKnock 通过在用户态充当轻量代理转接层，仅当验证成功后才建立向目标本地服务（如 127.0.0.1:2222）的数据透传转发。",
                    "滑动窗口时序比对：记录来源 IP 的敲门时间戳，超时（如超过 5 秒）或顺序错误立即重置状态，防暴力枚举。",
                    "【参考开源项目】knockd（经典 Linux 端口敲门守护服务）；rust-pcap（Rust 原生网络包捕获库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动敲门守护并在 PC 端触发敲门解锁：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Rust 端口敲门与客户端序列解锁",
                        Code = "# 1. 启动 Rust 敲门守护器（代理本地 SSH 2222 端口，监听外部 22222）\n" +
                               "nohup /data/local/tmp/z6x_knock \\\n" +
                               "  -target 127.0.0.1:2222 \\\n" +
                               "  -bind 0.0.0.0:22222 \\\n" +
                               "  -sequence \"7001/udp,8002/tcp,9003/udp\" \\\n" +
                               "  -timeout 3600 > /data/local/tmp/knock.log 2>&1 &\n\n" +
                               "# 2. PC 端发送敲门触发序列\n" +
                               "nc -z -u 192.168.1.100 7001 && nc -z 192.168.1.100 8002 && nc -z -u 192.168.1.100 9003\n\n" +
                               "# 3. 敲门成功后即刻连接 SSH\n" +
                               "ssh -p 22222 deck@192.168.1.100",
                        ExpectedOutput = "[Knock] Sequence matched from 192.168.1.50, opening port 22222 for 3600s\nConnection established."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "非 root 权限端口限制：非 root 用户只能绑定 1024 以上非特权端口，对外暴露敲门和代理端口需选择如 22222 而非 22。",
                    "Wi-Fi 休眠丢包排查：Android 默认可能在息屏后进入低功耗 Wi-Fi 省电模式导致 UDP 敲门丢包，可在调试前保持屏幕点亮或执行 `dumpsys deviceidle whitelist +com.android.shell`。"
                ]
            }
        ]
    };
}
