using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class MicroProcessSupervisorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "micro-process-supervisor",
        Title = "19. 自研服务编排与热重载管理器：SIGHUP 信号与依赖拓扑（Z6X RustSupervisor）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的嵌入式微型进程守护与编排总线，负责一键纳管极米上所有 Go/Rust 原生二进制的生命周期、依赖拓扑与热重载，自身内存仅 1.5MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "随着部署在极米上的原生服务（WebDAV、Aria2、DoH、看门狗、同步网关）逐渐增多，靠手写 shell 脚本管理存在诸多痛点：进程之间存在启动先后依赖（如必须先挂载 U 盘并启动网络隧道，才能启动文件同步）、某个子进程挂掉后无法自动分级重启、修改配置必须杀掉整个脚本重新开机。\n\n" +
                       "使用 Rust 自研的微型服务编排器后，单文件替代 supervisord/systemd。一个 `services.toml` 统一配置所有进程的依赖顺序与自动重启策略，支持发送 `SIGHUP` 信号热重载配置无需断流，整个编排器自身物理内存常驻仅 1.5MB。",
                BulletPoints =
                [
                    "全纳管极米所有自研进程：单个编排总线统一管理各服务的启停与健康状态。",
                    "有向无环图（DAG）依赖启动：精准控制各子服务的启动依赖顺序，避免网络未通就抢先报错。",
                    "支持无缝热重载（Hot Reload）：修改配置无需中断其他正在运行的无辜服务。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `tokio::process` + POSIX 信号处理（单文件体积约 800KB）。",
                    "什么是孤儿进程与僵尸进程回收（Zombie Reaping）？当后台子进程退出时，若父进程未调用 `waitpid()` 读取退出码，该进程会变成僵尸进程驻留进程表占用 PID。RustSupervisor 作为守护树顶层，自动通过异步循环执行 `waitpid` 清理僵尸进程，保持系统进程表绝对整洁。",
                    "SIGHUP 热重载机制：监听 `tokio::signal::unix::signal(SignalKind::hangup())`。收到信号时重新解析 `services.toml`，仅重启修改过配置的模块，未变动的服务进程保持运行零中断。",
                    "【参考开源项目】supervisord（Python 经典进程管理，参考其控制模型）；s6 / runit（Linux 嵌入式极简 init 进程设计）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动编排总线并测试服务纳管：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动服务编排器并纳管子进程",
                        Code = "# 1. 编写微型编排配置文件 services.toml\n" +
                               "cat << 'EOF' > /data/local/tmp/services.toml\n" +
                               "[[program]]\n" +
                               "name = \"hub_service\"\n" +
                               "command = \"/data/local/tmp/z6x_hub\"\n" +
                               "autostart = true\n" +
                               "autorestart = true\n" +
                               "EOF\n\n" +
                               "# 2. 启动编排总线\n" +
                               "nohup /data/local/tmp/z6x_supervisor -c /data/local/tmp/services.toml > /data/local/tmp/supervisor.log 2>&1 &\n\n" +
                               "# 3. 查看纳管服务实时运行状态\n" +
                               "/data/local/tmp/z6x_supervisor status",
                        ExpectedOutput = "[Supervisor] Managing 1 program:\n  hub_service    RUNNING    pid 14201, uptime 0:02:15"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "进程编排排坑指南：",
                BulletPoints =
                [
                    "问题 1：子进程崩溃频繁触发频繁重启（Crash Loop）。设置指数退避惩罚机制（Backoff）：若子进程在启动后 5 秒内连续崩溃超过 3 次，将其状态置为 `FATAL` 暂停拉起，并写入错误日志，防止 CPU 陷入死循环抢占。",
                    "问题 2：优雅退出超时。退出时优先发送 `SIGTERM` 信号给所有子进程并等待 3 秒，若超时未退出再强行发送 `SIGKILL` 确保彻底关闭。"
                ]
            }
        ]
    };
}
