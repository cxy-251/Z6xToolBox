package z6x.content.stack

import z6x.framework.Host
import z6x.framework.module

val InstallJdk = module("install-jdk", "在 Deck 上安装 JDK") {
    keywords = "JDK 21 · Temurin · JAVA_HOME · ~/Applications"
    overview = """
        Kotlin 编译器和 Gradle 都运行在 Java 虚拟机上，所以第一步是装 JDK（Java 开发工具包）。
        SteamOS 系统分区只读，不用 pacman，直接下载官方压缩包解压到 `~/Applications`。
    """
    verified("2026-10-01")

    why {
        text("""
            • **JDK** = Java 运行时（JVM）+ 编译器等开发工具。Kotlin 编译出来的也是 JVM 字节码。
            • 选 **21** 是因为它是长期支持版（LTS），Gradle 和 Kotlin 都完整支持。
            • **Temurin** 是 Eclipse Adoptium 社区构建的免费 OpenJDK，和 Oracle JDK 功能相同、没有授权问题。
            • **和投影仪的安卓版本无关**：JDK 只是在 Deck 上编译用的工具，不会装到投影仪上。
        """)
    }

    steps {
        read("确认 CPU 架构", "uname -m", Host.Deck) {
            captured("2026-10-01", "x86_64")
            note = "决定下载哪个架构的包。Steam Deck 是 x86_64（也叫 x64、amd64）。"
        }
        read("确认剩余空间", "df -h ~", Host.Deck) {
            note = "JDK 解压后约 350M，加上 Gradle 和依赖缓存，首次构建至少要留 1G。"
        }
        read("向 Adoptium 查询最新的 JDK 21 下载地址",
            "curl -s \"https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux\" | grep -oE '\"(link|release_name)\": ?\"[^\"]+' | head -2",
            Host.Deck) {
            note = "直接问官方接口，不靠记忆里的版本号。返回的 JSON 里 link 是下载地址，release_name 是版本。"
        }
        change("下载并解压到 ~/Applications", """
            cd ~/Applications
            curl -L -o /tmp/jdk21.tar.gz "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
            tar xzf /tmp/jdk21.tar.gz && rm /tmp/jdk21.tar.gz
        """, Host.Deck) {
            note = "地址换成上一步查到的 link。`-L` 跟随 GitHub 的跳转；`tar xzf` = 解压（x）gzip（z）文件（f）。"
            outcome = "生成目录 ~/Applications/jdk-21.0.12.1+1，约 346M。"
        }
        change("建一个固定入口", "ln -sfn jdk-21.0.12.1+1 ~/Applications/jdk", Host.Deck) {
            note = "软链接 `~/Applications/jdk` 指向具体版本。以后升级 JDK 只改这个链接，脚本和环境变量都不用动。"
        }
        read("检查安装结果", "~/Applications/jdk/bin/java -version", Host.Deck) {
            captured("2026-10-01", """
                openjdk version "21.0.12.1" 2026-08-18 LTS
                OpenJDK Runtime Environment Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS)
                OpenJDK 64-Bit Server VM Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS, mixed mode, sharing)
            """)
        }
    }

    consequences {
        text("""
            • 只是解压了文件，不改系统，删除 `~/Applications/jdk*` 即可卸载。
            • 没有加进全局 PATH。项目的 `./run.sh` 自己设置 `JAVA_HOME`，避免影响其他程序。
            • 想在任何终端都能用 java，可以在 `~/.bashrc` 末尾加：`export JAVA_HOME=~/Applications/jdk` 和 `export PATH="${'$'}JAVA_HOME/bin:${'$'}PATH"`。
        """)
    }
}
