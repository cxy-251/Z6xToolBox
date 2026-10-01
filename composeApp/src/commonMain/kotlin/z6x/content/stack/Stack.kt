package z6x.content.stack

import z6x.framework.Host
import z6x.framework.module

val GradleBasics = module("gradle-basics", "Gradle：构建工具与 wrapper") {
    keywords = "gradlew · wrapper · libs.versions.toml · 任务 · 守护进程"
    overview = """
        Gradle 负责「把源代码变成能运行的程序」：下载依赖、调用 Kotlin 编译器、打包、运行。相当于 .NET 里的 `dotnet build` / `dotnet run` 加 NuGet。
    """
    verified("2026-10-01")

    why("几个概念") {
        text("""
            • **wrapper（./gradlew）**：项目自带的小脚本。第一次运行时按 `gradle/wrapper/gradle-wrapper.properties` 里写的版本下载 Gradle，所以每个人用的 Gradle 版本都一样，电脑上不用预先装 Gradle。gradlew、gradle/wrapper/ 要提交进 git。
            • **构建脚本**：`settings.gradle.kts`（项目里有哪些模块）、`build.gradle.kts`（每个模块怎么构建）。`.kts` 表示用 Kotlin 写的脚本。
            • **版本目录**：`gradle/libs.versions.toml` 集中写所有依赖和插件的版本，构建脚本里用 `libs.xxx` 引用。升级版本只改这一个文件。
            • **任务（task）**：Gradle 的每个动作都是一个任务，比如 `compileKotlinDesktop`（编译桌面版）、`run`（运行）。`:composeApp:run` 表示 composeApp 模块的 run 任务。
            • **守护进程（Daemon）**：Gradle 第一次运行后在后台留一个 Java 进程，下次构建直接复用，所以第二次快很多。
        """)
    }

    steps("常用命令") {
        read("版本信息", "./gradlew --version | grep -E '^Gradle|^Kotlin|^Launcher'", Host.Deck) {
            captured("2026-10-01", """
                Gradle 9.8.0
                Kotlin:        2.4.10
                Launcher JVM:  21.0.12.1 (Eclipse Adoptium 21.0.12.1+1-LTS)
            """)
            note = """
                这里的 Kotlin 2.4.10 是 **Gradle 自己内置的**，用来执行构建脚本；我们项目代码用的 Kotlin 版本是 libs.versions.toml 里写的 2.4.20。两者不必一样。
                要先设好 JAVA_HOME（`./run.sh` 会自动设），否则 gradlew 找不到 Java。
            """
        }
        change("只编译不运行（检查代码有没有错）", "./gradlew :composeApp:compileKotlinDesktop", Host.Deck) {
            note = "第一次 6 分钟左右（下载依赖），之后几十秒。`-q` 安静模式只输出错误。"
        }
        change("运行", "./gradlew :composeApp:run --args=\"--check\"", Host.Deck) {
            note = "`--args` 把参数传给程序。日常用 `./run.sh --check` 就是这条的简写。"
        }
        change("停掉后台守护进程", "./gradlew --stop", Host.Deck) {
            note = "换了 JDK、删了临时 Gradle、构建出现奇怪错误时用。"
        }
        read("依赖缓存占了多少空间", "du -sh ~/.gradle", Host.Deck) {
            varies = true
            captured("2026-10-01", "444M\t/home/deck/.gradle")
            note = "下载的 Gradle 本体和所有依赖都在这里。删掉会重新下载。"
        }
    }

    lesson("踩过的坑") {
        text("""
            • 用临时下载的 Gradle 生成 wrapper 后把它删了，再运行 ./gradlew 报 `NoSuchFileException: /tmp/gradle-9.8.0/lib/…`：临时 Gradle 留下的守护进程还活着被复用了。`./gradlew --stop` 解决。
            • 想用 `pkill -f GradleDaemon` 杀守护进程，结果把执行这条命令的 shell 自己也杀了（它的命令行里也有 GradleDaemon 这个词）。用 `./gradlew --stop`。
            • 程序在 Gradle 里运行时，工作目录默认是模块目录（composeApp/），相对路径找不到项目根目录的文件。在 composeApp/build.gradle.kts 里把 run 任务的 workingDir 设成了 rootDir。
        """)
    }

    related("install-jdk", "kotlin-in-project", "project-layout")
}

val KotlinInProject = module("kotlin-in-project", "Kotlin：这个项目里用到的写法") {
    keywords = "data class · sealed · enum · 扩展 · DSL · 协程 · 字符串模板"
    overview = """
        不讲完整语法，只讲这个项目代码里实际用到、看代码时会碰到的 Kotlin 写法，每个都指出在哪个文件。和 C# 对照着看。
    """
    verified("2026-10-01")

    story("类型") {
        text("""
            • **data class**（framework/Model.kt 的 `Step`）：自动生成 equals、hashCode、toString、copy。相当于 C# 的 record。
            • **enum class 带属性**（Model.kt 的 `Risk(val label: String)`）：每个枚举值带数据，比 C# 的 enum 能力强。
            • **sealed interface**（Model.kt 的 `Item`）：只能有文件里列出的几种实现（Text、Cmd、Facts、Claim）。`when` 判断时编译器知道所有情况，漏写一种就报错（见 ui/ModuleView.kt 的 SectionCard）。
            • **object**（ui/Theme.kt 的 `Palette`、content/Content.kt 的 `Content`）：单例，相当于 C# 的 static class。
            • **可空类型**：`String?` 可以是 null，`String` 不能。`step.verdict?.let { … }`：不是 null 时才执行（ModuleView.kt）。
        """)
    }

    story("函数") {
        text("""
            • **扩展属性 / 函数**（Theme.kt 的 `val Risk.color`）：给已有的类型「加」成员，不用改它的源码。C# 也有扩展方法，但没有扩展属性。
            • **带接收者的 lambda**（framework/Dsl.kt）：`module("id", "标题") { keywords = "…"; story { … } }`。花括号里的代码以 ModuleBuilder 为 this 执行，所以能直接写它的属性和方法。整个内容 DSL 就是靠这个实现的。
            • **具名参数与默认值**：`Step(title, command, risk, host, note = "…")`，不用的参数可以省略。
            • **函数引用**：`onOpen = state::open`（ui/AppShell.kt），相当于 C# 的方法组。
            • **use { }**（desktopMain/Main.kt）：用完自动关闭，相当于 C# 的 using。
        """)
    }

    story("字符串") {
        text("""
            • **字符串模板**：`"${'$'}{step.title}"`、`"${'$'}name"` 直接嵌入变量。
            • **原始字符串**：三个双引号包起来，可以跨行、不用转义，配合 `.trimIndent()` 去掉公共缩进。内容里的多行文字都这么写。
            • **在字符串里写字面的美元符号**：原始字符串里要写成 `${'$'}{'${'$'}'}`；普通字符串里写 `\${'$'}`。命令里有 shell 变量（`${'$'}PATH`）时经常要处理这个，写错会编译失败或被当成 Kotlin 变量。
        """)
    }

    story("协程（异步）") {
        text("""
            • `scope.launch { … }`（ui/DevicePanel.kt）：启动一个协程，在后台读设备信息，界面不卡。相当于 C# 的 `Task.Run` + async。
            • `withContext(Dispatchers.IO) { … }`（Main.kt 的 DadbShell）：切到 IO 线程池执行阻塞的网络操作。
            • `suspend fun`：可以挂起的函数，只能在协程里调用。相当于 C# 的 async 方法。
        """)
    }

    related("compose-in-project", "gradle-basics", "project-layout")
}

val ComposeInProject = module("compose-in-project", "Compose：界面是怎么写出来的") {
    keywords = "@Composable · remember · mutableStateOf · 重组 · Modifier · 多平台"
    overview = """
        Compose 用函数描述界面「长什么样」，状态变了就重新调用函数刷新界面（重组）。和 Avalonia 的 XAML + 数据绑定是完全不同的思路：没有 XAML、没有 ViewModel 的属性通知，界面就是 Kotlin 代码。
    """
    verified("2026-10-01")

    story("核心概念") {
        text("""
            1. @Composable 函数：`fun ModuleView(module: Module, …)`（ui/ModuleView.kt）。它不返回控件，而是在调用时「发出」界面元素。
            2. 状态：`var address by remember { mutableStateOf("…") }`（ui/DevicePanel.kt）。`mutableStateOf` 创建可观察的值，读它的 Composable 会被记下来，值一改，这些函数自动重新执行。`remember` 让值在重新执行之间保留下来。
            3. 重组：不需要手动通知界面刷新。对比 Avalonia：那边要实现 INotifyPropertyChanged，在属性 setter 里发通知。
            4. 应用级状态：ui/AppState.kt 把当前专区、当前模块、搜索词放在一个类里，属性用 `by mutableStateOf` 委托，整个界面共享。
        """)
    }

    story("布局与修饰") {
        text("""
            • 布局：`Column`（竖排）、`Row`（横排）、`Box`（叠放）、`LazyColumn`（长列表只渲染看得见的部分，左侧目录用的就是它）、`FlowRow`（放不下自动换行，命令上的标签用它）。
            • Modifier 链：`Modifier.fillMaxWidth().background(…).padding(…)`，**顺序有意义**：先 background 再 padding，背景包含内边距；反过来则不包含。
            • `key(module.id) { … }`（ModuleView.kt）：切换模块时整块重建，滚动位置回到顶部。
            • `LaunchedEffect(state.toast) { delay(2000); … }`（AppShell.kt）：toast 内容一变就启动一个协程，2 秒后清空。
            • `drawBehind { drawRect(…) }`（旧记录卡片左边的彩色竖线）：直接在内容下面画图形。
        """)
    }

    story("多平台结构") {
        text("""
            • `commonMain/`：所有平台共用的代码——内容、框架、界面。以后做安卓版，这部分原样复用。
            • `desktopMain/`：只属于桌面版的代码——窗口入口（Main.kt）、用 dadb 连设备、AWT 剪贴板、命令行工具（Tools.kt）。
            • 平台相关的能力通过参数传进共用代码：`App(state, shell, onCopy)`，`shell` 是 DeviceShell 接口，桌面版传 DadbShell。
        """)
    }

    related("kotlin-in-project", "project-layout")
}

val GitInProject = module("git-in-project", "Git：这个项目怎么用它") {
    keywords = "commit · tag · baseline · log · show · diff · worktree"
    overview = """
        这个仓库用 git 记录每一步改动。agy 的旧版本打了标签 `avalonia-baseline`，永久保留，随时可以对照。
    """
    verified("2026-10-01")

    steps("常用命令") {
        read("提交历史", "git log --oneline | head -5", Host.Deck) {
            varies = true
            note = "每行一次提交：前面是编号（哈希的前几位），后面是说明。"
        }
        read("标签", "git tag", Host.Deck) {
            captured("2026-10-01", "avalonia-baseline")
            note = "标签是给某次提交起的永久名字。avalonia-baseline 指向 agy 的原始版本，之后怎么改它都不会变。"
        }
        read("看旧版本里的某个文件", "git show avalonia-baseline:run.sh", Host.Deck) {
            note = "`版本:路径`。不用切换版本，直接把内容打印出来。"
        }
        read("某个文件和旧版本的差别", "git diff avalonia-baseline -- run.sh", Host.Deck) {
            varies = true
            note = "`-` 开头的行是旧版有、现在没有的；`+` 开头是新加的。"
        }
        change("把旧版本完整检出到旁边的目录", "git worktree add ../Z6x-old avalonia-baseline", Host.Deck) {
            note = "两个版本并排放，可以同时打开对比。用完 `git worktree remove ../Z6x-old`。"
        }
    }

    consequences("这个仓库的约定") {
        text("""
            • 旧的 Avalonia 代码（Z6xToolBox.App、Z6xToolBox.Desktop）永久保留不动，用来对比。
            • 代理节点、Wi-Fi 信息、私钥不进仓库（.gitignore 排除了 nodes.txt、clash.yaml、*.apk、*.apk1 等）。
            • 每次提交前跑 `./run.sh --check`，涉及设备的改动再跑 `./run.sh --try-read 模块id`。
        """)
    }

    related("project-layout", "gradle-basics")
}

val ProjectLayout = module("project-layout", "这个项目的结构与日常操作") {
    keywords = "目录结构 · run.sh · 新增模块 · --check · --try-read"
    overview = """
        仓库里有什么、怎么运行、怎么加一篇内容、改完怎么检查。
    """
    verified("2026-10-01")

    story("目录") {
        facts(
            "composeApp/src/commonMain/kotlin/z6x/framework" to "内容模型（Model.kt）和写内容用的 DSL（Dsl.kt）",
            "…/z6x/ui" to "界面：外壳、模块页、富文本、配色、设备面板",
            "…/z6x/content" to "全部内容；Content.kt 是目录，records / inspect / manual / custom / proposals / stack 各是一个专区的内容",
            "composeApp/src/desktopMain" to "桌面入口 Main.kt、命令行工具 Tools.kt",
            "scripts/" to "一键精简与恢复脚本",
            "dev/go-server/" to "Go 测试服务的源码",
            "notes/" to "环境搭建记录、提案审核结论表与生成脚本",
            "Z6xToolBox.App / .Desktop" to "agy 的旧版 Avalonia 代码，保留不动",
        )
    }

    steps("日常操作") {
        change("打开工具箱", "./run.sh", Host.Deck)
        change("内容静态检查", "./run.sh --check", Host.Deck) {
            note = "检查风险标记、加粗和代码标记是否成对、旧记录有没有写结论、有没有代理链接等。"
        }
        change("在实机上试跑只读命令", "./run.sh --try-read force-adb", Host.Deck) {
            note = "不写模块 id 就跑全部。ADB 步骤发给投影仪，Deck 步骤在本机，SSH 步骤走 `ssh z6x`（连不上会整体跳过）。和实测记录不一样的会标 △。"
        }
        change("命令行查设备", "./run.sh --probe", Host.Deck)
        change("命令行版一键体检", "./run.sh --health", Host.Deck) {
            note = "和工具箱「📡 设备」页里的体检是同一组检查。必检项有异常时退出码为 1，可以放进脚本里用。"
        }
        change("打开旧版", "./run-avalonia.sh", Host.Deck)
    }

    story("新增一篇内容") {
        text("""
            1. 在 content 下对应专区的文件里写一个 `module("唯一id", "标题") { … }`（照着现有的抄）。
            2. 在 content/Content.kt 对应分类的 listOf(…) 里加上它。
            3. 命令先在设备上真实跑一遍，把输出用 `captured("日期", "…")` 原样记下来；没跑过的不写输出。
            4. 来自旧记录的说法用 `claim(原文, Verdict.…, 实测结果)` 写，不要删。
            5. `./run.sh --check`，再 `./run.sh --try-read 新模块id`，都通过后提交。
        """)
    }

    related("gradle-basics", "git-in-project", "kotlin-in-project")
}
