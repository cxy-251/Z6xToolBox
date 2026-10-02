import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.serialization.json)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
                implementation(libs.dadb)
            }
        }
    }
}

// 版本号统一来自仓库根目录的 VERSION 文件（hub、z6x-tools 与发布流程也读它）
val appVersion = rootProject.file("VERSION").readText().trim()

compose.desktop {
    application {
        mainClass = "z6x.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Z6xToolBox"
            packageVersion = appVersion
            description = "极米 Z6X Pro 与手机的实践手册和工具箱"
            vendor = "cxy-251"
            linux {
                packageName = "z6xtoolbox"
                menuGroup = "Utility"
            }
            windows {
                menu = true
                shortcut = true
                perUserInstall = true // 不需要管理员权限
                upgradeUuid = "6f0d2a43-6c5e-4f3b-9a11-2d7c8e5b9a01" // 固定不变，新版本才能覆盖安装旧版本
            }
            macOS {
                // jpackage 要求 macOS 上版本号的第一位大于 0：0.x.y 在应用内部标为 1.x.y，文件名仍用 0.x.y
                packageVersion = appVersion.replaceFirst(Regex("^0\\."), "1.")
                bundleID = "io.github.cxy251.z6xtoolbox"
            }
        }
    }
}

// ./run.sh 时以项目根目录为工作目录，内容里的相对路径（如 scripts/）才能找到
tasks.withType<JavaExec>().configureEach {
    workingDir = rootDir
}

// 把桌面版的运行时 classpath 写进 build/desktop.classpath，./run.sh 用它直接 `java -cp` 启动程序，
// 不再借 Gradle 的 run 任务运行（那样窗口开着期间会一直占着一个 Gradle 守护进程）。
val desktopMain = kotlin.targets.getByName("desktop").compilations.getByName("main")
val desktopClasspath = files(desktopMain.output.allOutputs, desktopMain.runtimeDependencyFiles ?: files())
val desktopClasspathFile = rootProject.layout.buildDirectory.file("desktop.classpath")
tasks.register("writeDesktopClasspath") {
    // 先拷到局部变量：配置缓存不允许 doLast 里引用脚本顶层的对象
    val cp = desktopClasspath
    val out = desktopClasspathFile
    dependsOn(desktopMain.compileAllTaskName)
    inputs.files(cp)
    outputs.file(out)
    doLast { out.get().asFile.writeText(cp.joinToString(":") { it.absolutePath }) }
}
