using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class ProcessOomWatchdogPolicyData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "process-oom-watchdog-policy",
        Title = "9. 进程生命周期与 OOM 防杀机制：LMK 白名单与自愈配置",
        Group = "开发环境",
        Summary = "深入解析 Android TV 系统的低内存查杀（LMK）机制，提供调优进程优先级（oom_score_adj）与编写自愈看门狗脚本的完整方案，保障自研服务长期存活。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "自研服务推送到极米后台运行后，往往在开机前几个小时一切正常；但当用户在电视前台打开爱奇艺/B站播放 4K 高码率视频，或启动大型 3D 电视游戏时，系统内存瞬间吃紧，后台的微服务常常被静默直接杀死并消失，甚至连错误日志都来不及输出。\n\n" +
                       "这是 Android TV 底层的 Low Memory Killer（LMK）主动内存回收机制在生效。通过调整自研进程的内核 OOM 优先级，并配合本地轻量自愈守护看门狗，可防止原生微服务被误杀，并在异常退出后 3 秒内自动拉起复活。",
                BulletPoints =
                [
                    "防止服务被前台大应用挤死：让内核将自研服务标记为关键守护任务，避免优先被杀。",
                    "自动检测与 3 秒自愈重启：崩溃或被杀后看门狗自动拉起，保障 7x24 小时服务连续性。",
                    "异常退出日志现场保存：被杀时记录最后一刻的内存与系统日志，便于排查归因。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Linux 内核 OOM Killer 机制 + Android LMKD 驱动 + Shell 自愈看门狗（Watchdog）。",
                    "什么是 `oom_score_adj`（内存杀伐权重分）？Linux 内核为每个进程打分（取值范围 -1000 到 1000）。分值越高越先被杀；-1000 代表完全免疫 OOM 查杀。普通 Android 前台 App 分值为 0，后台缓存 App 为 900+。非 Root shell 虽不能设为 -1000，但可将其调整为比普通后台 App 更低的优先保护级别。",
                    "什么是自愈看门狗脚本？一个几十行代码的轻量 Shell 死循环守护进程。通过 `pidof` 或 `kill -0 <PID>` 周期探测目标进程，一旦发现进程消失立即输出告警并重新执行启动命令。",
                    "【参考开源项目】Android lmkd（Android 低内存守护程序源码与查杀阶梯策略）；supervisord（经典进程看门狗设计范式）。"
                ]
            },
            new ContentSection
            {
                Heading = "防杀配置与自愈看门狗脚本",
                Text = "实机配置进程保护与自愈脚本：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "编写自愈看门狗守护脚本 watchdog.sh",
                        Code = "cat << 'EOF' > /data/local/tmp/watchdog.sh\n" +
                               "#!/bin/sh\n" +
                               "TARGET_BIN=\"/data/local/tmp/z6x_hub\"\n" +
                               "LOG_FILE=\"/data/local/tmp/watchdog.log\"\n\n" +
                               "while true; do\n" +
                               "  if ! pidof z6x_hub > /dev/null; then\n" +
                               "    echo \"[$(date '+%Y-%m-%d %H:%M:%S')] Service died. Restarting...\" >> $LOG_FILE\n" +
                               "    nohup $TARGET_BIN > /data/local/tmp/hub.log 2>&1 &\n" +
                               "    sleep 2\n" +
                               "    PID=$(pidof z6x_hub)\n" +
                               "    echo \"-500\" > /proc/$PID/oom_score_adj 2>/dev/null || true\n" +
                               "  fi\n" +
                               "  sleep 10\n" +
                               "done\n" +
                               "EOF\n" +
                               "chmod 755 /data/local/tmp/watchdog.sh && nohup /data/local/tmp/watchdog.sh > /dev/null 2>&1 &",
                        ExpectedOutput = "# 看门狗启动，每隔 10 秒巡检一次，进程消失自动拉起并调优权重"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "进程保活排坑指南：",
                BulletPoints =
                [
                    "问题 1：写入 `/proc/<PID>/oom_score_adj` 报错 Permission denied。原因与解决：Android 内核出于安全限制，仅允许进程自身调整自身的 score 分值，或由其父进程降级。方案是在看门狗拉起子进程的同一 shell 线程中趁其诞生即刻写入，或在 Go/Rust 代码初始化阶段直接由自身进程执行写入操作。",
                    "问题 2：看门狗自身被杀死。看门狗使用纯 Shell 运行，物理内存常驻仅 800KB，处于系统查杀优先级的最底层，只要内存不低于 50MB 绝不会被触发查杀。"
                ]
            }
        ]
    };
}
