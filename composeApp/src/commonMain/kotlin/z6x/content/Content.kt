package z6x.content

import z6x.content.inspect.FindRealModel
import z6x.content.records.ForceAdb
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
                Category("设备接入", "🔌", "从 U 盘装 App、SSH 到强开 ADB", listOf(ForceAdb)),
            ),
        ),
        Scope(
            "inspect", "🔍", "学会查设备",
            "用 adb 查这台投影仪的硬件、系统和权限。每页都是一次真实的核查过程。",
            listOf(
                Category("核查案例", "🧪", "旧文档里的说法，实机上查一遍", listOf(FindRealModel)),
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
