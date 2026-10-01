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
import z6x.content.inspect.FindRealModel
import z6x.content.inspect.PortOwner
import z6x.content.inspect.Pivots
import z6x.content.inspect.SshKeyLogin
import z6x.content.inspect.TvScreencap
import z6x.content.records.AppInstallOrder
import z6x.content.records.DeckHdmi
import z6x.content.records.FindAdbEntry
import z6x.content.records.ForceAdb
import z6x.content.records.LanShare
import z6x.content.records.SshPermissionWall
import z6x.content.records.SshProbe
import z6x.content.records.UsbApk1
import z6x.content.stack.InstallJdk
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
            ),
        ),
        Scope(
            "inspect", "🔍", "学会查设备",
            "用 adb 查这台投影仪的硬件、系统和权限。每页都是一次真实的核查过程。",
            listOf(
                Category("连接设备", "📶", "ADB、SSH、截图：先连上，才能查", listOf(SshKeyLogin, TvScreencap)),
                Category("核查案例", "🧪", "旧文档里的说法，实机上查一遍", listOf(FindRealModel, PortOwner)),
                Category("经验", "💡", "一条路不通时怎么换", listOf(Pivots)),
            ),
        ),
        Scope(
            "stack", "🛠", "本项目技术栈",
            "这个工具箱本身用到的 JDK、Gradle、Kotlin、Compose 和 Git：是什么、怎么装、怎么用。",
            listOf(
                Category("环境搭建", "📦", "在 Steam Deck 上准备开发环境", listOf(InstallJdk)),
            ),
        ),
    )
}
