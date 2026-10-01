import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
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
