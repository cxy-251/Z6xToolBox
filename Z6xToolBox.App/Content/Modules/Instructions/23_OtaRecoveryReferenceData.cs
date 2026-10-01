using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class OtaRecoveryReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "ota-recovery-reference",
        Title = "23. OTA更新机制与Recovery系统（update_engine / recovery / bootctl）",
        Group = "设备接入",
        Summary = "讲解 Android 12 A/B 无缝固件升级客户端状态查询、Recovery 升级日志与引导参数。",
        Sections =
        [
            new ContentSection
            {
                Heading = "A/B 无缝升级客户端状态（update_engine_client）",
                Text = "查询后台是否正在静默下载或合并极米官方推送的系统 OTA 固件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 update_engine 守护进程的当前执行阶段",
                        Code = "update_engine_client --status",
                        ExpectedOutput =
                            "CURRENT_OP=UPDATE_STATUS_IDLE   # 当前状态处于空闲（未在下载或刷写后台分区）\n" +
                            "LAST_CHECKED_TIME=1695801200   # 最近一次向极米服务器检查固件更新的时间戳\n" +
                            "NEW_VERSION=0.0.0.0             # 暂无已排队的新版本固件"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "引导槽位与回滚状态（ro.boot.slot_suffix / bootctl）",
                Text = "确认系统当前运行的槽位以及引导标记状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询当前分区槽位及是否成功引导",
                        Code = "getprop ro.boot.slot_suffix && bootctl is-slot-marked-successful 0 2>/dev/null",
                        ExpectedOutput =
                            "_a   # 当前为 A 分区引导\n" +
                            "1    # 1 代表槽位启动成功，不会触发 Watchdog 自动回滚到 Slot B"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Recovery 日志排查（/cache/recovery/）",
                Text = "排查历史 OTA 升级失败或系统崩溃回滚记录：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "读取最近一次刷机或升级安装日志末尾",
                        Code = "tail -n 15 /cache/recovery/last_log 2>/dev/null || cat /metadata/ota/last_status 2>/dev/null || echo \"No recovery log\"",
                        ExpectedOutput = "No recovery log   # 出厂后未进入独立 Recovery 分区刷机"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`update_engine_client`：需要系统级权限（AID_SYSTEM 或 AID_SHELL），SSH 下执行会报权限不足（Permission denied）。",
                    "`/cache/recovery/`：该目录属于 `system:cache`，模式为 770，普通应用（SSH）无权读取其下任何文件，ADB 可正常查看。"
                ]
            }
        ]
    };
}
