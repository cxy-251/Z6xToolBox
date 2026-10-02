package z6x.content.stack

import z6x.framework.Host
import z6x.framework.module

val GradleBasics = module("gradle-basics", "Gradle：构建工具与 wrapper") {
    keywords = "gradlew · wrapper · libs.versions.toml · 任务 · 守护进程"
    overview = """
        Gradle 负责将源代码构建为可运行的程序：下载依赖、调用 Kotlin 编译器、打包和运行，相当于 .NET 中的 `dotnet build` / `dotnet run` 加上 NuGet。
    """
    verified("2026-10-01")

    why("基本概念") {
        text("""
            • **wrapper（./gradlew）**：项目自带的小脚本。首次运行时按 `gradle/wrapper/gradle-wrapper.properties` 中指定的版本下载 Gradle，因此所有人使用的 Gradle 版本一致，电脑上无需预先安装 Gradle。gradlew 和 gradle/wrapper/ 需要提交到 git。
            • **构建脚本**：`settings.gradle.kts`（项目包含哪些模块）、`build.gradle.kts`（每个模块如何构建）。`.kts` 表示用 Kotlin 编写的脚本。
            • **版本目录**：`gradle/libs.versions.toml` 集中记录所有依赖和插件的版本，构建脚本中用 `libs.xxx` 引用。升级版本时只需修改这一个文件。
            • **任务（task）**：Gradle 的每个动作都是一个任务，例如 `compileKotlinDesktop`（编译桌面版）、`run`（运行）。`:composeApp:run` 表示 composeApp 模块的 run 任务。
            • **守护进程（Daemon）**：Gradle 首次运行后会在后台保留一个 Java 进程，下次构建时直接复用，因此第二次构建快得多。本项目将其空闲超时设为 15 分钟，见「问题记录：后台 Java 进程内存持续增长」。
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
                此处的 Kotlin 2.4.10 是 **Gradle 内置的**，用于执行构建脚本；本项目代码使用的 Kotlin 版本是 libs.versions.toml 中指定的 2.4.20，两者不必相同。
                需要先设置 JAVA_HOME（`./run.sh` 会自动设置），否则 gradlew 找不到 Java。
            """
        }
        change("只编译不运行（检查代码是否有错）", "./gradlew :composeApp:compileKotlinDesktop", Host.Deck) {
            note = "首次约需 6 分钟（下载依赖），之后为数十秒。`-q` 表示安静模式，只输出错误。"
        }
        change("通过 Gradle 运行", "./gradlew :composeApp:run --args=\"--check\"", Host.Deck) {
            note = "`--args` 将参数传给程序。日常请使用 `./run.sh`：它只用 Gradle 编译，再由 java 直接启动程序，不会占用 Gradle 守护进程（原因见「问题记录：后台 Java 进程内存持续增长」）。"
        }
        change("停止后台守护进程", "./gradlew --stop", Host.Deck) {
            note = "在更换 JDK、删除临时 Gradle 或构建出现异常错误时使用。"
        }
        read("依赖缓存占用的空间", "du -sh ~/.gradle", Host.Deck) {
            varies = true
            captured("2026-10-01", "444M\t/home/deck/.gradle")
            note = "下载的 Gradle 本体和所有依赖都在此目录中，删除后会重新下载。"
        }
    }

    lesson("遇到过的问题") {
        text("""
            • 用临时下载的 Gradle 生成 wrapper 后将其删除，再运行 ./gradlew 时报 `NoSuchFileException: /tmp/gradle-9.8.0/lib/…`：临时 Gradle 留下的守护进程仍在运行并被复用。执行 `./gradlew --stop` 即可解决。
            • 用 `pkill -f GradleDaemon` 结束守护进程时，执行该命令的 shell 也被结束了（其命令行中同样包含 GradleDaemon）。应改用 `./gradlew --stop`。
            • 程序在 Gradle 中运行时，工作目录默认为模块目录（composeApp/），使用相对路径时找不到项目根目录下的文件。因此在 composeApp/build.gradle.kts 中将 run 任务的 workingDir 设为 rootDir。
        """)
    }

    related("install-jdk", "kotlin-in-project", "project-layout")
}

val KotlinInProject = module("kotlin-in-project", "Kotlin：本项目用到的语法") {
    keywords = "data class · sealed · enum · 扩展 · DSL · 协程 · 字符串模板"
    overview = """
        不介绍完整语法，只介绍本项目代码中实际用到、阅读代码时会遇到的 Kotlin 写法，并注明所在文件，同时与 C# 对照。
    """
    verified("2026-10-01")

    story("类型") {
        text("""
            • **data class**（framework/Model.kt 中的 `Step`）：自动生成 equals、hashCode、toString、copy，相当于 C# 的 record。
            • **带属性的 enum class**（Model.kt 中的 `Risk(val label: String)`）：每个枚举值都可以携带数据，功能比 C# 的 enum 更强。
            • **sealed interface**（Model.kt 中的 `Item`）：只能有文件中列出的几种实现（Text、Cmd、Facts、Claim）。用 `when` 判断时编译器掌握全部情况，遗漏任何一种都会报错（见 ui/ModuleView.kt 中的 SectionCard）。
            • **object**（ui/Theme.kt 中的 `Palette`、content/Content.kt 中的 `Content`）：单例，相当于 C# 的 static class。
            • **可空类型**：`String?` 可以为 null，`String` 则不可以。`step.verdict?.let { … }` 表示仅在非 null 时执行（ModuleView.kt）。
        """)
    }

    story("函数") {
        text("""
            • **扩展属性 / 函数**（Theme.kt 中的 `val Risk.color`）：为已有类型增加成员，无需修改其源码。C# 也有扩展方法，但没有扩展属性。
            • **带接收者的 lambda**（framework/Dsl.kt）：`module("id", "标题") { keywords = "…"; story { … } }`。花括号中的代码以 ModuleBuilder 为 this 执行，因此可以直接使用其属性和方法。整个内容 DSL 即基于此实现。
            • **具名参数与默认值**：`Step(title, command, risk, host, note = "…")`，不需要的参数可以省略。
            • **函数引用**：`onOpen = state::open`（ui/AppShell.kt），相当于 C# 的方法组。
            • **use { }**（desktopMain/Main.kt）：使用完毕后自动关闭，相当于 C# 的 using。
            • **@Serializable**（device/Hub.kt 中的 `HubHealth`）：kotlinx.serialization 的编译器插件自动生成 JSON 解析代码；字段名不同时用 `@SerialName` 指定，相当于 C# 的 `[JsonPropertyName]`。
        """)
    }

    story("字符串") {
        text("""
            • **字符串模板**：`"${'$'}{step.title}"`、`"${'$'}name"` 可直接嵌入变量。
            • **原始字符串**：用三个双引号括起，可以跨行且无需转义，配合 `.trimIndent()` 去除公共缩进。内容中的多行文字均采用这种写法。
            • **在字符串中书写字面的美元符号**：原始字符串中需写作 `${'$'}{'${'$'}'}`；普通字符串中写作 `\${'$'}`。命令中含有 shell 变量（`${'$'}PATH`）时经常需要处理这一点，写错会导致编译失败，或被当作 Kotlin 变量。
        """)
    }

    story("协程（异步）") {
        text("""
            • `scope.launch { … }`（ui/DevicePanel.kt）：启动一个协程，在后台读取设备信息，界面不会卡顿，相当于 C# 的 `Task.Run` 加 async。
            • `withContext(Dispatchers.IO) { … }`（Main.kt 中的 DadbShell）：切换到 IO 线程池执行阻塞的网络操作。
            • `suspend fun`：可挂起的函数，只能在协程中调用，相当于 C# 的 async 方法。
        """)
    }

    related("compose-in-project", "gradle-basics", "project-layout")
}

val ComposeInProject = module("compose-in-project", "Compose：界面的编写方式") {
    keywords = "@Composable · remember · mutableStateOf · 重组 · Modifier · 多平台"
    overview = """
        Compose 用函数描述界面的外观，状态改变时重新调用函数以刷新界面（重组）。这与 Avalonia 的 XAML 加数据绑定是完全不同的思路：没有 XAML，也没有 ViewModel 的属性通知，界面本身就是 Kotlin 代码。
    """
    verified("2026-10-01")

    story("核心概念") {
        text("""
            1. @Composable 函数：`fun ModuleView(module: Module, …)`（ui/ModuleView.kt）。它不返回控件，而是在调用时生成界面元素。
            2. 状态：`var address by remember { mutableStateOf("…") }`（ui/DevicePanel.kt）。`mutableStateOf` 创建可观察的值，读取它的 Composable 会被记录下来，值一旦改变，这些函数便自动重新执行。`remember` 使值在多次执行之间得以保留。
            3. 重组：无需手动通知界面刷新。相比之下，Avalonia 需要实现 INotifyPropertyChanged，并在属性 setter 中发送通知。
            4. 应用级状态：ui/AppState.kt 将当前专区、当前模块、是否显示设备页和提示信息放在一个类中，属性通过 `by mutableStateOf` 委托，由整个界面共享。
        """)
    }

    story("布局与修饰") {
        text("""
            • 布局：`Column`（纵向排列）、`Row`（横向排列）、`Box`（叠放）、`LazyColumn`（长列表只渲染可见部分，左侧目录即采用它）、`FlowRow`（空间不足时自动换行，命令上的标签采用它）。
            • Modifier 链：`Modifier.fillMaxWidth().background(…).padding(…)`，**顺序有意义**：先 background 后 padding 时，背景包含内边距；顺序相反则不包含。
            • `key(module.id) { … }`（ModuleView.kt）：切换模块时整块重建，滚动位置回到顶部。
            • `LaunchedEffect(state.toast) { delay(2000); … }`（AppShell.kt）：toast 内容一旦改变即启动一个协程，2 秒后将其清空。
            • `drawBehind { drawRect(…) }`（旧记录卡片左侧的彩色竖线）：直接在内容下层绘制图形。
        """)
    }

    story("多平台结构") {
        text("""
            • `commonMain/`：所有平台共用的代码，包括内容、框架和界面。今后制作安卓版时，这部分可原样复用。
            • `desktopMain/`：仅属于桌面版的代码，包括窗口入口（Main.kt）、通过 dadb 连接设备、AWT 剪贴板以及命令行工具（Tools.kt）。
            • 与平台相关的能力通过参数传入共用代码：`App(state, shell, hub, onCopy)`，其中 `shell` 是 DeviceShell 接口（桌面版传入 DadbShell），`hub` 是 HubControl 接口（桌面版传入 DesktopHub，用 JDK 的 HttpClient 查询状态、运行部署脚本）。
        """)
    }

    related("kotlin-in-project", "project-layout")
}

val GitInProject = module("git-in-project", "Git 在本项目中的用法") {
    keywords = "commit · tag · baseline · log · show · diff · worktree"
    overview = """
        本仓库用 git 记录每一次改动。最初的 Avalonia（C#）版本已从目录中删除，但在 git 历史中打有标签 `avalonia-baseline`，可随时取回查看。
    """
    verified("2026-10-01")

    steps("常用命令") {
        read("提交历史", "git log --oneline | head -5", Host.Deck) {
            varies = true
            note = "每行对应一次提交：前面是编号（哈希的前几位），后面是说明。"
        }
        read("标签", "git tag", Host.Deck) {
            captured("2026-10-01", "avalonia-baseline")
            note = "标签是为某次提交起的永久名称。avalonia-baseline 指向最初的 C# 版本：文件虽已从目录中删除，但仍保留在历史中，标签指向的内容永远不变。"
        }
        read("列出旧版本中某个目录的文件", "git ls-tree --name-only avalonia-baseline:Z6xToolBox.App/Content/Modules/PreAdb/", Host.Deck) {
            captured("2026-10-01", """
                01_HdmiDisplayData.cs
                02_UsbApk1BypassData.cs
                03_LanSharingFlowData.cs
                04_AppCompatibilityData.cs
                05_SshHardwareAuditData.cs
                06_SshUninstallFailureData.cs
                07_DeveloperModeBlocksData.cs
                08_ForceAdbdActivationData.cs
            """)
            note = "文件虽已不在目录中，仍可从标签中列出。"
        }
        read("查看旧版本中的某个文件", "git show avalonia-baseline:run.sh", Host.Deck) {
            note = "格式为 `版本:路径`，无需切换版本即可直接输出内容。notes/gen.py 即以这种方式读取旧提案原文并核对引用。"
        }
        read("某个文件与旧版本的差异", "git diff avalonia-baseline -- run.sh", Host.Deck) {
            varies = true
            note = "以 `-` 开头的行是旧版有而现在没有的内容；以 `+` 开头的是新增内容。"
        }
        change("将旧版本完整检出到相邻目录", "git worktree add ../Z6x-old avalonia-baseline", Host.Deck) {
            note = "两个版本并排存放，可同时打开对比。使用完毕后执行 `git worktree remove ../Z6x-old`。"
        }
    }

    consequences("本仓库的约定") {
        text("""
            • 旧的 Avalonia 代码（Z6xToolBox.App、Z6xToolBox.Desktop）中有用的内容已全部吸收进新版，目录已删除（2026-10-01）；需要对照时从标签 `avalonia-baseline` 中获取。
            • 代理节点、Wi-Fi 信息和私钥不得进入仓库（.gitignore 已排除 nodes.txt、clash.yaml、*.apk、*.apk1 等）。
            • 每次提交前运行 `./run.sh --check`；涉及设备的改动还需运行 `./run.sh --try-read 模块id`。
        """)
    }

    related("project-layout", "gradle-basics")
}

val ProjectLayout = module("project-layout", "项目结构与日常操作") {
    keywords = "目录结构 · run.sh · 新增模块 · --check · --try-read"
    overview = """
        仓库的内容、运行方法、新增内容的步骤，以及修改后的检查方法。
    """
    verified("2026-10-01")

    story("目录") {
        facts(
            "composeApp/src/commonMain/kotlin/z6x/framework" to "内容模型（Model.kt）和写内容用的 DSL（Dsl.kt）",
            "…/z6x/ui" to "界面：外壳、模块页、富文本、配色、设备面板",
            "…/z6x/content" to "全部内容；Content.kt 为目录，records / inspect / manual / custom / proposals / stack 分别存放各专区的内容",
            "composeApp/src/desktopMain" to "桌面入口 Main.kt、命令行工具 Tools.kt",
            "scripts/" to "一键精简与恢复脚本",
            "hub/" to "z6x-hub（Go 常驻服务）的源码、配置示例与部署脚本 deploy.sh，见「规格：z6x-hub」",
            "dev/go-server/" to "Go 测试服务的源码",
            "notes/" to "环境搭建记录、提案审核数据与生成脚本",
        )
    }

    steps("日常操作") {
        change("打开工具箱", "./run.sh", Host.Deck)
        change("内容静态检查", "./run.sh --check", Host.Deck) {
            note = "检查风险标注、加粗与代码标记是否成对、旧记录是否写有结论、是否包含代理链接等。"
        }
        change("在实机上试运行只读命令", "./run.sh --try-read force-adb", Host.Deck) {
            note = "不指定模块 id 时运行全部。ADB 步骤发送给投影仪，Deck 步骤在本机执行，SSH 步骤通过 `ssh z6x` 执行（无法连接时整体跳过）。与实测记录不一致的结果标注为 △。"
        }
        change("在命令行中查询设备", "./run.sh --probe", Host.Deck)
        change("启动后直接打开设备页", "./run.sh --device", Host.Deck)
        change("查询 z6x-hub 状态", "./run.sh --hub", Host.Deck) {
            note = "与「📡 设备」页的 hub 区域使用同一段代码。所有模块正常时退出码为 0。"
        }
        change("编译并部署 z6x-hub", "./run.sh --hub-deploy", Host.Deck) {
            note = "与设备页的「部署并启动」按钮相同，实际执行的是 hub/deploy.sh。"
        }
        change("命令行版一键体检", "./run.sh --health", Host.Deck) {
            note = "与工具箱「📡 设备」页中的体检为同一组检查。必检项异常时退出码为 1，可在脚本中使用。"
        }
    }

    story("新增一篇内容") {
        text("""
            1. 在 content 下对应专区的文件中编写 `module("唯一id", "标题") { … }`（可参照现有模块）。
            2. 在 content/Content.kt 中对应分类的 listOf(…) 里加入该模块。
            3. 命令须先在设备上实际运行，再用 `captured("日期", "…")` 原样记录输出；未运行过的命令不写输出。
            4. 来自旧记录的说法用 `claim(原文, Verdict.…, 实测结果)` 记录，不得删除。
            5. 依次运行 `./run.sh --check` 和 `./run.sh --try-read 新模块id`，均通过后再提交。
        """)
    }

    related("gradle-basics", "git-in-project", "kotlin-in-project")
}

val JavaMemory = module("java-memory", "问题记录：后台 Java 进程内存持续增长") {
    keywords = "Gradle 守护进程 · Kotlin 编译守护进程 · -Xmx · idletimeout · jps"
    overview = """
        使用一段时间后，Deck 后台的 Java 进程合计占用 2~3GB 内存，且持续增长。原因并非工具箱代码存在内存泄漏，而是**本项目的构建和启动方式存在缺陷**：每次运行都可能多留下一个常驻的 Gradle 进程，而每个进程最多可占用 2GB。该问题已修复（提交 2f6aed6）。
    """
    verified("2026-10-01")

    why("相关概念") {
        text("""
            • **Gradle 守护进程（GradleDaemon）**：`./gradlew` 本身很轻量，实际执行构建的是一个常驻后台的 Java 进程。它在编译完成后不退出，留待下次复用，从而加快后续编译。默认空闲 **3 小时**后才退出。
            • **Kotlin 编译守护进程（KotlinCompileDaemon）**：编译 Kotlin 时，Gradle 还会另外启动一个常驻进程，同样在编译完成后不退出。
            • **-Xmx 是 Java 堆的上限**：Java 进程按需向系统申请内存，垃圾回收后一般**不会**立即将内存归还系统，因此进程的内存占用会停留在使用过的最高点附近，看起来「只增不减」。
        """)
    }

    story("原配置导致内存增长的原因") {
        facts(
            "gradle.properties" to "Gradle 守护进程和 Kotlin 守护进程均设为 `-Xmx2g`：每个最多可占用 2GB",
            "run.sh" to "原为 `./gradlew :composeApp:run`：工具箱窗口在 Gradle 守护进程内运行，**窗口不关闭，该守护进程就一直处于「忙碌」状态**",
            "忙碌的守护进程无法复用" to "此时再执行任何 `./gradlew`（编译、--check 或再开一个窗口），Gradle 发现已有守护进程正忙，便会**另起一个**。运行几次就会多出几个",
            "空闲 3 小时后才退出" to "关闭窗口后，这些守护进程还需空闲 3 小时才会退出，在此之前一直占用内存",
            "结果" to "多个 Gradle 守护进程、Kotlin 守护进程加上工具箱本身，合计约 3GB",
        )
        text("因此这属于**项目缺陷**：工具箱代码本身没有泄漏，但启动方式导致守护进程堆积，这是最初编写 run.sh 和 gradle.properties 时未考虑到的。")
    }

    steps("排查内存占用") {
        read("列出所有 Java 进程", "jps -l", Host.Deck) {
            note = "jps 是 JDK 自带的命令，可以清楚地区分 GradleDaemon、KotlinCompileDaemon 和 z6x.MainKt（工具箱本身）。"
        }
        read("看每个 Java 进程实际占的内存", "ps -C java -o pid,rss,etime,args --sort=-rss | cut -c1-150", Host.Deck) {
            note = "RSS 为实际占用的物理内存（单位 KB），etime 为运行时长。运行时间很长且不是工具箱窗口的进程，即为闲置的守护进程。"
        }
        change("让 Gradle 守护进程退出", "./gradlew --stop", Host.Deck) {
            note = "只停止当前 Gradle 版本的守护进程。Kotlin 守护进程空闲一段时间后会自行退出；如需立即释放，可用 jps 查出进程号后执行 `kill 进程号`。"
        }
    }

    steps("修复（已提交）") {
        change("gradle.properties：降低上限、缩短空闲时间", """
            org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8
            org.gradle.daemon.idletimeout=900000
            kotlin.daemon.jvmargs=-Xmx1g
        """, Host.Deck) {
            note = "两个守护进程最多各占用 1GB（足以编译本项目）；Gradle 守护进程空闲 15 分钟（900000 毫秒）后即退出。"
        }
        change("run.sh：Gradle 只负责编译，窗口由 java 直接启动", """
            ./gradlew -q :composeApp:writeDesktopClasspath || exit 1
            exec java -Xmx512m -cp "$(cat build/desktop.classpath)" z6x.MainKt "$@"
        """, Host.Deck) {
            note = "writeDesktopClasspath 是在 composeApp/build.gradle.kts 中新增的任务：编译后将运行所需的全部 jar 和 class 目录写入 build/desktop.classpath。Gradle 完成这一步即进入空闲状态，下次 `./gradlew` 可直接复用，不会另起新进程。工具箱本身是一个独立的 java 进程，堆上限为 512MB，关闭窗口即退出。"
        }
    }

    verify("修复后的效果") {
        facts(
            "清理后" to "可用内存恢复到约 7.6GB（Deck 共 14GB）",
            "常驻的进程" to "最多一个 Gradle 守护进程和一个 Kotlin 守护进程（各不超过 1GB），以及打开着的工具箱窗口",
            "实测（连续两次 ./run.sh --check）" to "两次使用的是同一个 Gradle 守护进程（进程号不变），未另起新进程；Gradle 守护进程约 430MB，Kotlin 守护进程约 730MB",
            "不使用时" to "15 分钟后 Gradle 守护进程自动退出，关闭窗口后工具箱进程也随之退出",
        )
    }

    lesson("经验") {
        text("""
            • 发现 Java 内存占用较大时，先用 `jps -l` 分清是哪个进程，不要直接认定为「内存泄漏」。真正的泄漏是**同一个进程**的占用随时间持续上涨；本次则是**进程数量**在增加。
            • Gradle 的 run 任务适合偶尔使用；需要反复打开的桌面程序，应让 Gradle 只负责编译，由程序单独启动。
            • 应按进程号结束进程。不要使用 `pkill -f GradleDaemon`：它按整条命令行匹配，可能连正在执行该命令的 shell 本身也一并结束（本项目中确实发生过）。
        """)
    }

    related("gradle-basics", "project-layout", "install-jdk")
}
