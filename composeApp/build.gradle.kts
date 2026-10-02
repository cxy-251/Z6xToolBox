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

compose.desktop {
    application {
        mainClass = "z6x.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "Z6xToolBox"
            packageVersion = "0.2.0"
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
