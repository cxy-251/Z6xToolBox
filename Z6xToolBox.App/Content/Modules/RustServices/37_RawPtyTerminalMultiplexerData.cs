using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class RawPtyTerminalMultiplexerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "raw-pty-terminal-multiplexer",
        Title = "37. 自研微型 PTY 虚拟终端会话保持与复用器：POSIX 伪终端托管（Z6X RustMicroTmux）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的嵌入式 PTY 终端会话托管器，免安装 tmux/screen 即可保持 SSH 后台会话与重新挂载，常驻内存仅 900KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在通过 SSH 登录极米调试或编译程序时，如果网络中断或关闭 SSH 窗口，当前正在执行的长耗时任务（如离线校验、格式转换、长时间下载）会被 SIGHUP 信号强行终止。但在 Android 终端上交叉编译并安装复杂的 tmux 或 screen 较为繁琐，且依赖 ncurses 与动态库。\n\n" +
                       "使用 Rust 自研的微型终端复用守护器，直接调用 Linux 底层 `posix_openpt`、`grantpt` 与 `unlockpt` 系统调用创建伪终端 Master/Slave。将子进程的标准输入输出接入环形内存缓冲区，网络断开后会话在后台继续运行，重连后可重新 attach 恢复控制，常驻物理内存仅约 900KB。",
                BulletPoints =
                [
                    "SSH 断线不杀任务：Master 守护子进程，避免 SIGHUP 杀掉长耗时运维作业。",
                    "零动态库依赖：纯静态 Rust 编译，免除 ncurses、libevent 等依赖。",
                    "会话回显缓冲：保留最近 1000 行历史输出，重新连接后自动重绘输出界面。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `nix::pty` + Unix Domain Socket（UDS 本地套接字挂载协议）。",
                    "PTY 信号与窗口大小同步：准确转发 `SIGWINCH` 信号，在本地客户端调整窗口大小时，动态更新 PTY 行列参数（TIOCSWINSZ）。",
                    "与 tmux 资源对比：tmux 常驻需 15MB 且安装依赖较多；RustMicroTmux 二进制仅 800KB，常驻内存稳定在 900KB。",
                    "【参考开源项目】abduco/dvtm（C 语言极简终端会话管理工具）；portable-pty（Rust 跨平台伪终端库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 PTY 会话并在断开后重新接入验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "创建 PTY 守护会话并在断开后重新挂载",
                        Code = "# 1. 在电视端创建名为 download_job 的持久终端会话\n" +
                               "/data/local/tmp/z6x_session -c download_job /bin/sh\n\n" +
                               "# 2. 在会话内部启动长耗时任务，按 Ctrl+\\ 随时脱离（Detach）会话\n" +
                               "# [detached from download_job]\n\n" +
                               "# 3. 重新连入 SSH 后，重新挂载（Attach）回原会话\n" +
                               "/data/local/tmp/z6x_session -a download_job",
                        ExpectedOutput = "[Session] Attached to 'download_job' (PID 14210, UDS: /data/local/tmp/download_job.sock)\nOutput history replayed (48 lines)\n$ _"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "/dev/pts 挂载属性检查：Android 系统某些版本挂载 `/dev/pts` 时使用了特殊的 mode 参数，若打开报错可在 shell 中确认 `mount | grep pts` 是否挂载成功。",
                    "终端字符编码支持：投影仪系统的 shell 默认可能缺少 UTF-8 语言包支持，启动前需设置 `export LANG=C.UTF-8` 保证中文与控制字符不乱码。"
                ]
            }
        ]
    };
}
