package z6x.content

import z6x.content.custom.AppStorePivot
import z6x.content.custom.ClashProxy
import z6x.content.custom.DebloatList
import z6x.content.custom.DebloatMethod
import z6x.content.custom.DebloatScripts
import z6x.content.custom.InputMethodPivot
import z6x.content.custom.ProjectivyLauncher
import z6x.content.custom.ScreenCast
import z6x.content.custom.SystemPackages
import z6x.content.inspect.AdbAutostart
import z6x.content.inspect.FindRealModel
import z6x.content.manual.AdbBasics
import z6x.content.manual.BackgroundCmds
import z6x.content.manual.DumpsysSettings
import z6x.content.manual.HardwareCmds
import z6x.content.manual.LogsCrash
import z6x.content.manual.NetworkCmds
import z6x.content.manual.PmAm
import z6x.content.manual.ProcessMemory
import z6x.content.manual.PropsInit
import z6x.content.manual.SelinuxCmds
import z6x.content.manual.StoragePartitions
import z6x.content.inspect.FocusWindow
import z6x.content.inspect.PortOwner
import z6x.content.inspect.Pivots
import z6x.content.inspect.PowerModes
import z6x.content.inspect.SshKeyLogin
import z6x.content.inspect.TvScreencap
import z6x.content.proposals.DropbearShell
import z6x.content.proposals.HubSpec
import z6x.content.proposals.ReviewControl
import z6x.content.proposals.ReviewFiles
import z6x.content.proposals.ReviewMedia
import z6x.content.proposals.ReviewNetwork
import z6x.content.proposals.ReviewSummary
import z6x.content.proposals.ReviewSystem
import z6x.content.proposals.ToolsSpec
import z6x.content.proposals.EnvProfile
import z6x.content.proposals.OomWatchdog
import z6x.content.proposals.PacketCapture
import z6x.content.proposals.StraceDebug
import z6x.content.records.AppInstallOrder
import z6x.content.records.Busybox
import z6x.content.records.GoServer
import z6x.content.records.NativeExec
import z6x.content.records.ProcMetrics
import z6x.content.records.DeckHdmi
import z6x.content.records.Emergency
import z6x.content.records.Security
import z6x.content.records.FindAdbEntry
import z6x.content.records.ForceAdb
import z6x.content.records.LanShare
import z6x.content.records.SshPermissionWall
import z6x.content.records.SshProbe
import z6x.content.records.UsbApk1
import z6x.content.stack.ComposeInProject
import z6x.content.stack.GitInProject
import z6x.content.stack.GradleBasics
import z6x.content.stack.InstallJdk
import z6x.content.stack.JavaMemory
import z6x.content.stack.KotlinInProject
import z6x.content.stack.ProjectLayout
import z6x.framework.Category
import z6x.framework.Scope

/**
 * 全部内容的目录：专区 → 分类 → 模块。新增模块：写一个 module(...)，再在这里对应分类加一行。
 * 修改后运行 `./run.sh --check` 做静态检查，`./run.sh --try-read` 在实机上试跑只读命令。
 */
object Content {
    val scopes = listOf(
        Scope(
            "records", "📖", "折腾记录",
            "从拿到投影仪到接管系统的真实经过：每一步尝试了什么、为什么失败、最后怎么解决。",
            listOf(
                Category(
                    "设备接入", "🔌", "从 U 盘装 App、SSH 到强开 ADB",
                    listOf(DeckHdmi, UsbApk1, LanShare, AppInstallOrder, SshProbe, SshPermissionWall, FindAdbEntry, ForceAdb),
                ),
                Category(
                    "深度定制", "🧹", "有了 ADB 之后：精简预装、换桌面、代理、脚本",
                    listOf(
                        DebloatMethod, DebloatList, ProjectivyLauncher, ClashProxy, AppStorePivot,
                        InputMethodPivot, ScreenCast, SystemPackages, DebloatScripts,
                    ),
                ),
                Category("开发环境", "⚙️", "在投影仪上跑自己编译的程序", listOf(NativeExec, Busybox, GoServer, ProcMetrics)),
                Category("应急与安全", "🛟", "出事了怎么恢复；对局域网开放了什么", listOf(Emergency, Security)),
            ),
        ),
        Scope(
            "inspect", "🔍", "学会查设备",
            "用 adb 查这台投影仪的硬件、系统和权限。每页都是一次真实的核查过程。",
            listOf(
                Category("连接与查看", "📶", "ADB、SSH、截图、焦点窗口：先连上，才能查", listOf(SshKeyLogin, TvScreencap, FocusWindow)),
                Category("核查案例", "🧪", "旧文档里的说法，实机上查一遍", listOf(FindRealModel, PortOwner, AdbAutostart, PowerModes)),
                Category(
                    "命令手册", "📚", "按主题整理的常用命令，全部在这台投影仪上跑过",
                    listOf(
                        AdbBasics, PmAm, PropsInit, DumpsysSettings, ProcessMemory, StoragePartitions,
                        NetworkCmds, LogsCrash, SelinuxCmds, HardwareCmds, BackgroundCmds,
                    ),
                ),
                Category("经验", "💡", "一条路不通时怎么换", listOf(Pivots)),
            ),
        ),
        Scope(
            "proposals", "💡", "提案",
            "想过但还没做的方案。每篇都附实机可行性审核：可行、可选还是做不到。",
            listOf(
                Category("规格", "📐", "交给实现者的两份规格：Go 常驻服务、Rust 命令集", listOf(HubSpec, ToolsSpec)),
                Category(
                    "agy 的 109 个小项目", "🗂", "逐篇审核：纳入哪个项目、第几期，或为什么不做",
                    listOf(ReviewSummary, ReviewFiles, ReviewMedia, ReviewNetwork, ReviewControl, ReviewSystem),
                ),
                Category(
                    "开发环境", "⚙️", "让投影仪上的开发更顺手",
                    listOf(EnvProfile, StraceDebug, DropbearShell, OomWatchdog, PacketCapture),
                ),
            ),
        ),
        Scope(
            "stack", "🛠", "本项目技术栈",
            "这个工具箱本身用到的 JDK、Gradle、Kotlin、Compose 和 Git：是什么、怎么装、怎么用。",
            listOf(
                Category("环境搭建", "📦", "在 Steam Deck 上准备开发环境", listOf(InstallJdk, GradleBasics)),
                Category("读懂代码", "📘", "这个项目里用到的 Kotlin 和 Compose 写法", listOf(KotlinInProject, ComposeInProject)),
                Category("项目日常", "🧭", "目录结构、运行与检查、Git", listOf(ProjectLayout, GitInProject, JavaMemory)),
            ),
        ),
    )
}
