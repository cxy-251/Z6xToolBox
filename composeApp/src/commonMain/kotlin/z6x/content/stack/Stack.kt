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
        这个仓库用 git 记录每一步改动。最初的 Avalonia（C#）版本已经从目录里删掉，但它在 git 历史里打了标签 `avalonia-baseline`，随时可以取回来看。
    """
    verified("2026-10-01")

    steps("常用命令") {
        read("提交历史", "git log --oneline | head -5", Host.Deck) {
            varies = true
            note = "每行一次提交：前面是编号（哈希的前几位），后面是说明。"
        }
        read("标签", "git tag", Host.Deck) {
            captured("2026-10-01", "avalonia-baseline")
            note = "标签是给某次提交起的永久名字。avalonia-baseline 指向最初的 C# 版本：文件从目录里删掉了，历史里还在，标签指向的内容永远不变。"
        }
        read("列出旧版本里某个目录的文件", "git ls-tree --name-only avalonia-baseline:Z6xToolBox.App/Content/Modules/PreAdb/", Host.Deck) {
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
            note = "文件已经不在目录里了，也能从标签里列出来。"
        }
        read("看旧版本里的某个文件", "git show avalonia-baseline:run.sh", Host.Deck) {
            note = "`版本:路径`。不用切换版本，直接把内容打印出来。notes/gen.py 就是这样读取旧提案原文、核对引用的。"
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
            • 旧的 Avalonia 代码（Z6xToolBox.App、Z6xToolBox.Desktop）有用的内容已全部吸收进新版，目录已删除（2026-10-01）；需要对照时从标签 `avalonia-baseline` 里取。
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

val JavaMemory = module("java-memory", "案例：后台 Java 进程越来越占内存") {
    keywords = "Gradle 守护进程 · Kotlin 编译守护进程 · -Xmx · idletimeout · jps"
    overview = """
        用了一段时间后，Deck 后台的 Java 进程合计占了 2~3GB 内存，而且越用越多。原因不是工具箱代码里的内存泄漏，而是**本项目的构建和启动方式有缺陷**：每次运行都可能多留下一个常驻的 Gradle 进程，每个都允许用到 2GB。已修复（提交 2f6aed6）。
    """
    verified("2026-10-01")

    why("先弄懂三件事") {
        text("""
            • **Gradle 守护进程（GradleDaemon）**：`./gradlew` 本身很轻，真正干活的是一个常驻后台的 Java 进程。它编译完不退出，留着给下次用，下次编译就快很多。默认空闲 **3 小时**才退出。
            • **Kotlin 编译守护进程（KotlinCompileDaemon）**：编译 Kotlin 时 Gradle 还会另起一个常驻进程，同样编译完不退出。
            • **-Xmx 是 Java 堆的上限**：Java 进程用到多少就向系统要多少，垃圾回收后一般**不会**马上把内存还给系统，所以进程占用的内存会停在用过的最高点附近，看上去像「只涨不降」。
        """)
    }

    story("原来的配置为什么会越来越多") {
        facts(
            "gradle.properties" to "Gradle 守护进程和 Kotlin 守护进程都设成 `-Xmx2g`：每个最多可以占 2GB",
            "run.sh" to "原来是 `./gradlew :composeApp:run`：工具箱窗口在 Gradle 守护进程里面运行，**窗口不关，这个守护进程就一直「忙」**",
            "忙的守护进程不能复用" to "这时再执行任何 `./gradlew`（编译、--check、再开一个窗口），Gradle 发现已有的守护进程在忙，就**新起一个**。开几次就多几个",
            "空闲 3 小时才退出" to "关掉窗口后，这些守护进程还要空闲 3 小时才退出，在这之前一直占着内存",
            "结果" to "几个 Gradle 守护进程 + Kotlin 守护进程 + 工具箱本身，合计约 3GB",
        )
        text("所以是**项目缺陷**：工具箱代码本身没有泄漏，但启动方式让守护进程堆积，这是当初写 run.sh 和 gradle.properties 时没考虑到的。")
    }

    steps("怎么查是谁在占内存") {
        read("列出所有 Java 进程", "jps -l", Host.Deck) {
            note = "jps 是 JDK 自带的命令。GradleDaemon、KotlinCompileDaemon、z6x.MainKt（工具箱本身）分别是什么，一眼就能看出来。"
        }
        read("看每个 Java 进程实际占的内存", "ps -C java -o pid,rss,etime,args --sort=-rss | cut -c1-150", Host.Deck) {
            note = "RSS 是实际占用的物理内存（单位 KB），etime 是运行了多久。运行了很久、又不是工具箱窗口的，就是闲置的守护进程。"
        }
        change("让 Gradle 守护进程退出", "./gradlew --stop", Host.Deck) {
            note = "只停当前 Gradle 版本的守护进程。Kotlin 守护进程空闲一段时间后会自己退出；急着释放的话，用 jps 查到进程号后 `kill 进程号`。"
        }
    }

    steps("修复（已提交）") {
        change("gradle.properties：降低上限、缩短空闲时间", """
            org.gradle.jvmargs=-Xmx1g -Dfile.encoding=UTF-8
            org.gradle.daemon.idletimeout=900000
            kotlin.daemon.jvmargs=-Xmx1g
        """, Host.Deck) {
            note = "两个守护进程最多各 1GB（编译这个项目足够）；Gradle 守护进程空闲 15 分钟（900000 毫秒）就退出。"
        }
        change("run.sh：Gradle 只负责编译，窗口由 java 直接启动", """
            ./gradlew -q :composeApp:writeDesktopClasspath || exit 1
            exec java -Xmx512m -cp "$(cat build/desktop.classpath)" z6x.MainKt "$@"
        """, Host.Deck) {
            note = "writeDesktopClasspath 是 composeApp/build.gradle.kts 里加的任务：编译后把运行需要的所有 jar 和 class 目录写进 build/desktop.classpath。Gradle 做完这一步就空闲了，下次 `./gradlew` 能直接复用它，不会再新起。工具箱自己是一个独立的 java 进程，堆上限 512MB，关窗口就退出。"
        }
    }

    verify("修复后的效果") {
        facts(
            "清理后" to "可用内存回到 7.6GB 左右（Deck 共 14GB）",
            "常驻的进程" to "最多一个 Gradle 守护进程、一个 Kotlin 守护进程（各 ≤1GB），外加开着的工具箱窗口",
            "实测（连续两次 ./run.sh --check）" to "两次用的是同一个 Gradle 守护进程（进程号不变），没有新起；Gradle 守护进程约 430MB、Kotlin 守护进程约 730MB",
            "不用时" to "15 分钟后 Gradle 守护进程自动退出，关掉窗口工具箱进程也退出",
        )
    }

    lesson("经验") {
        text("""
            • 看到 Java 占内存大，先用 `jps -l` 分清是哪个进程，不要直接认定是「内存泄漏」。真正的泄漏是**同一个进程**的占用随时间不断上涨；这次是**进程数量**在增加。
            • Gradle 的 run 任务适合偶尔跑一下；需要反复打开的桌面程序，让 Gradle 只管编译，程序自己单独启动。
            • 清理进程时按进程号 kill。不要用 `pkill -f GradleDaemon`：它按整条命令行匹配，可能把正在执行这条命令的 shell 自己也杀掉（这个项目里真的发生过）。
        """)
    }

    related("gradle-basics", "project-layout", "install-jdk")
}
