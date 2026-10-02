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
import z6x.content.phone.GamePorts
import z6x.content.phone.PhoneAdb
import z6x.content.phone.PhoneDebloat
import z6x.content.phone.PhoneFacts
import z6x.content.phone.PhoneHub
import z6x.content.phone.PhoneParams
import z6x.content.phone.PhoneTermux
import z6x.content.phone.PhoneTransfer
import z6x.content.manual.BackgroundCmds
import z6x.content.manual.DumpsysSettings
import z6x.content.manual.HardwareCmds
import z6x.content.manual.LogsCrash
import z6x.content.manual.NetworkCmds
import z6x.content.manual.PermModel
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
            "records", "📖", "实践记录",
            "从接入投影仪到接管系统的完整过程：每一步的尝试、失败原因与最终方案。",
            listOf(
                Category("设备接入", "🔌", "外接显示、U 盘安装应用、局域网传输与兼容性", listOf(DeckHdmi, UsbApk1, LanShare, AppInstallOrder)),
                Category(
                    "获取权限", "🔑", "从 SSH 到网络 ADB",
                    listOf(SshProbe, SshPermissionWall, FindAdbEntry, ForceAdb, AdbAutostart, SshKeyLogin),
                ),
                Category(
                    "系统定制", "🧹", "精简预装、替换桌面、输入法、代理与投屏",
                    listOf(
                        DebloatMethod, DebloatList, SystemPackages, DebloatScripts, ProjectivyLauncher,
                        InputMethodPivot, AppStorePivot, ClashProxy, ScreenCast,
                    ),
                ),
                Category("开发环境", "⚙️", "在投影仪上运行自编译程序", listOf(NativeExec, Busybox, GoServer, ProcMetrics)),
                Category("应急与安全", "🛟", "故障恢复与局域网暴露面", listOf(Emergency, Security)),
            ),
        ),
        Scope(
            "inspect", "🔍", "设备查询",
            "通过 ADB 查询这台投影仪的硬件、系统与权限：原理、命令手册与核查案例。",
            listOf(
                Category("原理", "🧭", "理解命令为什么能执行或被拒绝", listOf(PermModel)),
                Category("查看工具", "📶", "截图与焦点窗口", listOf(TvScreencap, FocusWindow)),
                Category(
                    "命令手册", "📚", "按主题整理的常用命令，均在本机实测",
                    listOf(
                        AdbBasics, PmAm, PropsInit, DumpsysSettings, ProcessMemory, StoragePartitions,
                        NetworkCmds, LogsCrash, SelinuxCmds, HardwareCmds, BackgroundCmds,
                    ),
                ),
                Category("核查案例", "🧪", "对旧记录中的说法逐项实测", listOf(FindRealModel, PortOwner, PowerModes)),
                Category("经验", "💡", "方案不通时如何调整", listOf(Pivots)),
            ),
        ),
        Scope(
            "phone", "📱", "手机",
            "Redmi Note 12 Turbo：按开发设备改造，并作为 hub 的资源库。设备信息、环境配置、精简与实测记录。",
            listOf(
                Category("设备信息", "📋", "型号、系统、安全状态与常用查询命令", listOf(PhoneFacts, PhoneParams)),
                Category("环境配置", "⚙️", "无线调试、Termux 与 SSH、hub 的运行方式、大量文件传输", listOf(PhoneAdb, PhoneTermux, PhoneHub, PhoneTransfer)),
                Category("系统精简", "🧹", "两个空间分别移除预装，替换文件管理、相册与浏览器", listOf(PhoneDebloat)),
                Category("游戏移植原理", "🎮", "以本机安装的游戏为样本，分析电脑游戏如何做成手机游戏", listOf(GamePorts)),
            ),
        ),
        Scope(
            "proposals", "💡", "提案",
            "尚未实施的方案，均附实机可行性审核：可行、可选或不可行。",
            listOf(
                Category("规格", "📐", "z6x-hub（Go，hub/）与 z6x-tools（Rust，tools/），均在本项目内实现", listOf(HubSpec, ToolsSpec)),
                Category(
                    "小项目审核", "🗂", "109 个提案：采纳、暂缓或不可行的依据",
                    listOf(ReviewSummary, ReviewFiles, ReviewMedia, ReviewNetwork, ReviewControl, ReviewSystem),
                ),
                Category(
                    "开发辅助", "⚙️", "改善投影仪上开发体验的提案",
                    listOf(EnvProfile, StraceDebug, DropbearShell, OomWatchdog, PacketCapture),
                ),
            ),
        ),
        Scope(
            "stack", "🛠", "本项目技术栈",
            "工具箱本身使用的 JDK、Gradle、Kotlin、Compose 与 Git：作用、安装与用法。",
            listOf(
                Category("环境搭建", "📦", "在 Steam Deck 上准备开发环境", listOf(InstallJdk, GradleBasics)),
                Category("代码解读", "📘", "本项目用到的 Kotlin 与 Compose 写法", listOf(KotlinInProject, ComposeInProject)),
                Category("项目维护", "🧭", "目录结构、运行检查、Git 与问题记录", listOf(ProjectLayout, GitInProject, JavaMemory)),
            ),
        ),
    )
}
