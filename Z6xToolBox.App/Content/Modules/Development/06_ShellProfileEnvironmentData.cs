using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class ShellProfileEnvironmentData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "shell-profile-environment",
        Title = "6. Shell 运行环境持久化与 Profile 注入：PATH 自动挂载与命令别名",
        Group = "开发环境",
        Summary = "定制并注入持久化的环境初始化脚本，解决每次登录终端需手动配置 PATH 的问题，自动激活 Busybox 396 命令集与高效运维别名。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Android 原生系统的 `sh`（基于 mksh）在每次建立连接后，仅提供极简的默认环境变量（`PATH=/system/bin`）。这导致用户部署好的 396 个 Busybox 工具（如 `wget`、`vi`、`awk`、`nc`）以及自研服务每次都要手动敲 `export PATH=/data/local/tmp/bin:$PATH` 才能识别。\n\n" +
                       "通过编写并持久化环境变量注入脚本，进入终端时自动激活完整 Linux 工具链、配置彩色友好的命令行提示符（Prompt），并固化常用运维别名，使极米终端体验完全对齐标准 Linux 发行版。",
                BulletPoints =
                [
                    "告别每次手动 export PATH：登录即刻全局识别所有自研与 Busybox 命令。",
                    "常用复杂指令别名化：如 `ll`（详细文件列表）、`meminfo`（精准内存统计）、`ports`（监听端口查看）。",
                    "定制安全环境变量：预设 `TMPDIR=/data/local/tmp` 与 `GOGC=50`，保护系统稳定。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：mksh（MirBSD Korn Shell，Android 系统标配轻量 Shell）脚本规范。",
                    "什么是 Shell 初始化时序（Profile/RC）？标准 Linux 下 bash 会读取 `/etc/profile` 或 `~/.bashrc`；但 Android 根分区为只读，用户主目录处于临时沙盒。方案是在 `/data/local/tmp/env.sh` 中固化配置，并在 Dropbear SSH 启动参数或 ADB 交互脚本中自动 source 引入。",
                    "高频别名设计：将容易敲错或参数复杂的 Linux 内核取证命令包装为 2 到 4 个字母的短命令（如 `ps_rss` 一键按内存占用从大到小排列所有进程）。",
                    "【参考开源项目】Termux packages（参考其在非 Root 目录下引导完整 PATH 与环境变量的环境文件结构）。"
                ]
            },
            new ContentSection
            {
                Heading = "环境脚本创建与生效验证",
                Text = "在极米本地生成初始化脚本并测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "生成环境初始化脚本 env.sh",
                        Code = "cat << 'EOF' > /data/local/tmp/env.sh\n" +
                               "export PATH=/data/local/tmp/bin:/data/local/tmp:$PATH\n" +
                               "export HOME=/data/local/tmp\n" +
                               "export TMPDIR=/data/local/tmp\n" +
                               "export PS1='[z6x-linux:\\w]\\$ '\n" +
                               "alias ll='ls -la'\n" +
                               "alias ports='netstat -tuln'\n" +
                               "alias meminfo='cat /proc/meminfo | grep -E \"MemTotal|MemFree|MemAvailable\"'\n" +
                               "alias ps_mem='ps -eo pid,user,vsz,rss,comm --sort=-rss | head -n 15'\n" +
                               "EOF\n\n" +
                               "# 验证脚本直接 source 加载\n" +
                               "source /data/local/tmp/env.sh && which wget && ll",
                        ExpectedOutput = "/data/local/tmp/bin/wget\ntotal ..."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "Shell 环境注入排坑指南：",
                BulletPoints =
                [
                    "问题 1：通过 ADB 进入时不自动执行脚本。原因与解决：`adb shell` 命令默认以非登录（non-login）交互模式启动，不加载任何配置文件。方案是在电脑或 Deck 端配置一条快捷别名：`alias z6x=\"adb shell -t 'source /data/local/tmp/env.sh; sh'\"`，一键直连带环境的终端。",
                    "问题 2：换行符兼容。脚本必须确保为 Unix (LF) 换行符，若在 Windows 下编辑引入 CRLF 会导致 mksh 报错 `syntax error: unexpected word`。"
                ]
            }
        ]
    };
}
