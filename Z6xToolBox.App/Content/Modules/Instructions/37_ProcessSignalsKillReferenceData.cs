using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class ProcessSignalsKillReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "process-signals-kill-reference",
        Title = "37. 进程信号传递与退出状态分析（kill / kill -3 / pkill）",
        Group = "设备接入",
        Summary = "实测 toybox POSIX 信号列表、向进程发送 SIGQUIT（kill -3）触发 ANR 转储与进程清理。",
        Sections =
        [
            new ContentSection
            {
                Heading = "toybox POSIX 信号列表实测（kill -l）",
                Text = "实测终端支持的标准与实时信号映射：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出系统支持的信号代号与名称",
                        Code = "kill -l",
                        ExpectedOutput =
                            " 1 HUP Hangup\n" +
                            " 2 INT Interrupt\n" +
                            " 3 QUIT Quit          # 常用于触发 JVM/ART 打印线程调用栈\n" +
                            " 9 KILL Killed        # 强制无条件杀死进程（内核直接回收，不走清理钩子）\n" +
                            "15 TERM Terminated    # 优雅退出信号\n" +
                            "19 STOP Stopped       # 暂停进程调度"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "触发 ANR 堆栈转储（kill -3）",
                Text = "用于排查某个应用假死或主线程死锁原因：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "向卡顿的目标进程发送 SIGQUIT 信号",
                        Code = "kill -3 $(pidof com.xgimi.dueros) 2>/dev/null",
                        ExpectedOutput = "# ART 虚拟机收到信号后会将全线程 Backtrace 写入 /data/anr/traces.txt"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`kill`：Linux DAC 机制规定普通进程只能向同 UID 进程发送信号。SSH（UID 10068）向非本应用发送信号直接报 `Operation not permitted`；ADB（UID 2000）可向几乎所有应用级进程（UID 10000+）发送信号。"
                ]
            }
        ]
    };
}
