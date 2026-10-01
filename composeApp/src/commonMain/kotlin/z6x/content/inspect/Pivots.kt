package z6x.content.inspect

import z6x.framework.module

val Pivots = module("pivots", "换方案记录：一条路不通时") {
    keywords = "排查思路 · 换方案 · 经验总结"
    overview = """
        折腾过程中每一次"试了不行 → 弄清为什么 → 换一条路"，都汇总在这一页。详细经过在各自的模块里，点页面底部的相关模块跳过去看。
    """
    verified("2026-10-01")

    story("接入设备") {
        text("""
            1. 开 ADB 调试：遥控器组合键没反应 → 设置里没有开发者选项（整个被换掉了）→ 工厂模式里也没有开关 → **不找开关，直接看底层属性**，发现端口和免授权早就配好了，一条 setprop 拉起 adbd。
            - 转折点：问自己"这个功能本来应该在哪"，一层层往下找。（找开发者模式入口 → 强开网络 ADB）
            2. 删预装应用：SSH 里执行 pm 被拒 → 弄清是 uid 权限问题，换个 SSH 工具也没用 → **目标改成拿到 shell 身份**，也就是 ADB。（SSH 能查不能改）
            3. 装 App：文件管理器拦截 .apk → **改后缀绕过界面，让系统安装器读文件内容**。（U 盘装 App）
            4. 输入长文本：遥控器按不过来 → **换个通道**，局域网网页 + 浏览器复制粘贴。（局域网传文字）
            5. 配代理：v2rayNG 闪退 → **不纠缠，直接换 Clash Meta**，目标是能用代理，不是修好某个 App。旧记录的闪退解释已证伪，原因不再追查。（App 安装顺序与兼容）
            6. 64 位 App 装不上 → 弄清系统只有 32 位运行库 → **下载前先用 unzip -l 查架构**。（App 安装顺序与兼容）
            7. 换桌面：Projectivy 里开无障碍的按钮没反应 → `resolve-activity` 查到系统根本没有无障碍设置页 → **绕过界面用 ADB 命令开启** → 后来更进一步，**把官方桌面卸载**，Projectivy 成了唯一桌面，连无障碍服务都不需要了。（换掉官方桌面）
            8. 应用商店：Aurora 闪退 → Aptoide 能用但很多 App 缺 Google 服务 → **放弃商店**，自己下载 + ADB 安装。（找个应用商店）
            9. 输入法：换 LeanKeyboard 后遥控器变卡 → **退回搜狗**。（输入法）
            10. Deck 接投影仪黑屏 → 改成 1080p@60 就好 → 照抄的接口名 HDMI-A-1 在 Deck 上根本不存在 → **先 kscreen-doctor -o 查清名字**。（Deck 接投影仪黑屏）
        """)
    }

    story("核实旧记录") {
        text("""
            1. 型号：`ro.product.model` 只写了 XGIMI TV → **不盯着一个属性**，getprop 全量搜 → 型号藏在蓝牙名里。芯片也是平台代号、产品名、/proc/cpuinfo 交叉验证才确定。（案例：查出真实型号和芯片）
            2. 测 SSH 的停用权限：拿不存在的包测，系统先报"包不存在"，测不到权限 → **改成对已经停用的应用再停用一次**：真实存在、又不会改变任何状态。（SSH 能查不能改）
            3. 测卸载权限：同样测不出，又找不到无害的做法 → **不测了，如实写"无法安全验证"**，不编结果。（SSH 能查不能改）
            4. 证明 SELinux 放行：去日志里找记录，没有，全量搜索还超时 → **换成做一个能直接看到结果的实验**：App 身份写一个 debug 属性，写进去了就是证据。（强开网络 ADB）
            5. 实验留下了痕迹：setprop 不能删属性，清空后名字还在 → 被自动试跑发现，记录下来，重启自然消失。（强开网络 ADB）
            6. 代理：旧记录说 VPN 模式必崩，只能用端口代理 → 截图里看到一个不寻常的 IP 172.19.0.1 → 顺藤摸瓜查到 tun0 → **Clash 其实一直以 VPN 模式在跑**，旧结论被推翻。（代理：Clash Meta）
            7. 查端口归属：`netstat -p` 在 shell 下看不到进程 → **改读 /proc/net/tcp 里的 uid** → 设备上的 awk 没有 strtonum → **把换算搬到 Deck 上**。（案例：这个端口是谁开的）
            8. 一键脚本：恢复脚本里官方桌面入口名写错，但 `|| true` 把错误吞了，一直没发现 → 用 `dumpsys package` 查出正确入口，并在脚本结尾**加结果核对**。（一键精简与恢复脚本）
            9. 服务保活：原提案担心服务被内存回收杀掉，要写看门狗、调 oom 分 → 先查现状，Go 服务的 oom 分已经是 -1000（从 adbd 继承）→ **问题本来不存在**，真正的问题是重启。（服务保活）
            10. 抓包：提案要部署 tcpdump → 系统自带了，但 shell 没有抓包权限 → **换到 Deck 那一端抓**，或在服务里记日志。（在投影仪上抓包）
            11. Go 服务源码丢了 → 按它的实际输出重写等价源码，用原来的编译命令验证能编出同样大小的程序（哈希不同，如实标注）。（Go 服务）
            12. 读内核日志：`dmesg` 在 shell 下 Operation not permitted → 发现 logcat 有个 kernel 缓冲区 → **改用 `logcat -b kernel`**（注意不一定是最新的）。（日志与崩溃）
            13. 批量验证命令时，脚本里的函数叫 `r`，结果全部报 `fc: history functions not available` → 原来 mksh 里 `r` 是内置别名（重复上一条命令）→ **改名**。教训：别用一个字母当函数名。
            14. 想测「能不能结束系统进程」又不想真的结束它 → **用 `kill -0`**：只检查权限，不发信号。（进程、内存与信号）
        """)
    }

    story("SSH 免密登录") {
        text("""
            1. 一次性密码太麻烦 → 改用公钥登录。
            2. 先用 ed25519 密钥 → 登录被拒 → `ssh -v` 看到服务端是 dropbear 2019.78，不支持 ed25519 → **换成 ECDSA**。
            3. 更糟的是密码登录也关了，被锁在门外，ADB 又没权限改 SimpleSSHD 的私有文件 → **退一步：pm clear 清掉它的数据，回到初始状态重来**。
            4. 想看电视屏幕上 SimpleSSHD 的状态，不用走过去 → `adb exec-out screencap` 截图。
            （SSH 免密登录 SimpleSSHD、给投影仪截图）
        """)
    }

    story("搭建这个工具箱") {
        text("""
            1. 换框架：Tauri、Wails 需要系统的 webkit2gtk，Flutter 需要 clang、cmake 和 GTK 头文件，SteamOS 系统分区只读装不了 → **选不依赖系统库的 Compose Multiplatform**（自带 Skia 渲染，JDK 放家目录就行）。
            2. Gradle：删掉临时 Gradle 后报找不到文件 → 原因是它启动的后台守护进程还活着被复用了 → `./gradlew --stop` 停掉再跑。
            3. 想用 `pkill -f GradleDaemon` 杀守护进程，结果把执行这条命令的 shell 自己也杀了（命令行里含有这个词）→ 改用 `./gradlew --stop`。
            4. 写内容时凭印象补了一行 `java -version` 的输出 → 实际跑一遍才发现少了一行 → **所有实测输出必须真跑**，`--try-read` 会自动对比。
            5. 把权限实验命令标成了"只读" → `--check` 指出它们其实是修改操作（只是预期被拒）→ 按真实风险标注。
        """)
    }

    lesson("共同的规律") {
        text("""
            • **先弄清为什么不行**，再换方案。原因不同，该换的方向完全不同（换工具？换权限？换通道？）。
            • **看版本、看身份、看前提**：dropbear 版本、uid、SELinux 模式，这些"背景条件"决定了什么方案可行。
            • **实验要安全**：选不存在的目标，或已经处于目标状态的对象；动手前后都用另一个通道确认状态。
            • **验证不了就说验证不了**，不要编一个看起来合理的结果。
            • **留退路**：做可能把自己锁在外面的操作前，先想好怎么恢复。
        """)
    }

    related(
        "find-adb-entry", "force-adb", "ssh-permission-wall", "usb-apk1", "lan-share",
        "app-install-order", "deck-hdmi", "find-real-model", "ssh-key-login", "tv-screencap", "install-jdk",
        "projectivy-launcher", "app-store-pivot", "input-method", "clash-proxy", "port-owner", "debloat-scripts",
        "oom-watchdog", "packet-capture", "go-server", "logs-crash", "process-memory",
    )
}
