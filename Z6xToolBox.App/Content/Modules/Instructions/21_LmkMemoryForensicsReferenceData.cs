using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class LmkMemoryForensicsReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lmk-memory-forensics-reference",
        Title = "21. 内存深度分析与低内存杀手（meminfo --oom / smaps / drop_caches）",
        Group = "设备接入",
        Summary = "讲解 Android 进程 OOM 分级评定、单进程物理内存细节（PSS/RSS/Dirty）与内存释放。",
        Sections =
        [
            new ContentSection
            {
                Heading = "OOM 调度分组与优先级分层（dumpsys meminfo --oom）",
                Text = "按系统保活优先级从高到低排列所有进程，确认哪些进程在低内存时会被 lmkd 优先终结：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看按 OOM ADJ 分级的内存进程列表",
                        Code = "dumpsys meminfo --oom | head -n 25",
                        ExpectedOutput =
                            "OOM adjustments:\n" +
                            "  Native:   # 系统原生守护进程，最低 OOM 评分，永远不被杀死\n" +
                            "    75,410K: surfaceflinger (pid 248)\n" +
                            "    32,150K: audioserver (pid 380)\n" +
                            "  System:   # Android Framework 核心服务\n" +
                            "    142,300K: system (pid 785)\n" +
                            "  Persistent:   # 驻留常驻进程（包括极米遥控与按键捕获服务）\n" +
                            "    45,200K: com.xgimi.deviceservice (pid 1120)\n" +
                            "  Foreground:   # 当前屏幕可见的前台活动进程\n" +
                            "    85,120K: com.android.tv.settings (pid 2340)\n" +
                            "  Cached:   # 后台缓存进程，内存吃紧时最先被 lmkd 回收杀掉"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "精准排查进程内存泄漏（smaps_rollup）",
                Text = "在 Android 12 上快速读取单个进程的真实物理内存占用，无需解析冗长的 smaps：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "获取指定 PID 的物理内存消耗（以桌面或核心服务为例）",
                        Code = "cat /proc/$(pidof system_server)/smaps_rollup | head -n 8",
                        ExpectedOutput =
                            "Rss:              158204 kB   # 驻留内存（物理 RAM 总占用，包含共享库）\n" +
                            "Pss:              112450 kB   # 比例共享内存（该进程独占加均摊后的真实物理占用）\n" +
                            "Pss_Dirty:         98400 kB   # 进程实际修改写入的脏页内存\n" +
                            "Private_Dirty:     92120 kB   # 进程独占且不可释放的物理内存（排查内存泄漏的核心指标）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "系统缓存手动释放与页面回收（drop_caches）",
                Text = "用于在大型模拟器或 4K 播放器启动前释放 Linux 内核文件页与目录树缓存：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "清理 PageCache、dentries 和 inodes",
                        Code = "echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || echo \"Permission denied (Requires Root)\"",
                        ExpectedOutput = "Permission denied (Requires Root)   # 极米 Android 12 内核中 drop_caches 仅 root 可写，ADB(shell) 无法执行"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`dumpsys meminfo --oom`：SSH 下执行会报安全限制（无 DUMP 权限）；ADB（UID 2000）拥有完整 DUMP 权限，能打印全系统 OOM 列表。",
                    "`cat /proc/<pid>/smaps_rollup`：SSH 只能读取自己进程沙盒内的 `/proc/self/smaps_rollup`；ADB 可以读取全系统任意非 root-isolated 进程的内存分布。"
                ]
            }
        ]
    };
}
