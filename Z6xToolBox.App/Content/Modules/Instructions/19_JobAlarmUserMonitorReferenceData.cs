using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class JobAlarmUserMonitorReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "job-alarm-user-monitor-reference",
        Title = "19. 定时任务、唤醒闹钟、多用户与文件监控（jobscheduler / alarm / users / inotify）",
        Group = "设备接入",
        Summary = "讲解 JobScheduler 后台调度任务排查、AlarmManager 系统闹钟诊断、多用户环境及 inotify 目录变动监听。",
        Sections =
        [
            new ContentSection
            {
                Heading = "后台定时任务调度排查（JobScheduler）",
                Text = "排查哪些极米或第三方应用在后台注册了延迟或周期性执行的 Job（需 ADB shell 权限）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看系统当前待执行与正在执行的后台 Job 列表",
                        Code = "dumpsys jobscheduler | grep -iE \"JOB #|pkg=\" | head -n 6",
                        ExpectedOutput =
                            "JOB #u0a68/1: com.xgimi.doubtservice/.UploadJobService   # 极米数据上报后台任务\n" +
                            "  Periodic: interval=+1d0h0m0s                           # 每 24 小时周期触发一次"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "系统闹钟与唤醒定时器（AlarmManager）",
                Text = "排查定期唤醒 CPU 执行扫描的闹钟事件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看排名前列的高频唤醒闹钟（排查后台偷跑）",
                        Code = "dumpsys alarm | grep -E \"Top Alarms:|com.xgimi\" | head -n 5",
                        ExpectedOutput =
                            "+12m30s ... u0a68:com.xgimi.doubtservice   # 该服务频繁设定 RTC 唤醒定时器\n" +
                            "+45m00s ... u0a10:com.xgimi.home"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Android 多用户系统结构（pm list users）",
                Text = "确认系统当前的用户隔离架构：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前系统所有用户与运行状态",
                        Code = "pm list users",
                        ExpectedOutput = "UserInfo{0:Owner:13} running   # 用户 ID 为 0，Owner 主用户，单用户电视架构"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "目录与文件变动实时监听（inotify）",
                Text = "在终端中监控特定目录是否有新文件生成或配置被修改：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "实时监听 /data/local/tmp 目录下的写入与创建事件",
                        Code = "toybox inotifyd - /data/local/tmp 2>/dev/null",
                        ExpectedOutput = "w /data/local/tmp/app.apk   # 捕获到文件写入（Write）事件"
                    }
                ]
            }
        ]
    };
}
