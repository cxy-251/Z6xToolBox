package z6x.content.phone

import z6x.framework.Host
import z6x.framework.module

val GamePorts = module("game-ports", "电脑游戏如何做成手机游戏") {
    keywords = "Unity IL2CPP · GameMaker · LÖVE · Adobe AIR · 模拟器 · 原生引擎 · Java"
    overview = """
        以手机上已安装的游戏为样本，查看安装目录中的原生库与安装包内容，判断各自的移植方式。电脑游戏与手机游戏能否共用一份代码，取决于它使用的引擎。
    """
    verified("2026-10-02")

    steps("判断方法") {
        read("列出某个游戏的原生库", "ls \$(dirname \$(pm path com.xd.humanfallflat | head -1 | cut -d: -f2))/lib/arm64", Host.PhoneAdb) {
            varies = true
            note = "原生库的名称通常直接指明引擎：`libunity.so` + `libil2cpp.so` 为 Unity，`libyoyo.so` 为 GameMaker，`liblove.so` 为 LÖVE。没有原生库则多为 Java 或 Kotlin 编写。"
        }
        read("查看安装包中的资源目录", "unzip -l base.apk | awk 'NR>3{print \$4}' | awk -F/ '{print \$1\"/\"\$2}' | sort | uniq -c | sort -rn | head", Host.Deck) {
            manual = true
            note = "先用 `adb pull` 取出安装包（`pm path <包名>` 给出位置）。"
        }
    }

    verify("样本结果（2026-10-02 实测）") {
        facts(
            "空洞骑士、人类一败涂地、ICEY、Bridge Constructor、反叛公司、辐射避难所、Strike Buster" to "Unity（libunity.so + libil2cpp.so）。辐射避难所另有 libxlua.so，即用 Lua 编写可热更新的部分逻辑",
            "Crashlands" to "GameMaker（libyoyo.so，YoYo 运行时）",
            "王国保卫战：起源" to "LÖVE（liblove.so），游戏逻辑为 Lua 脚本",
            "Red Sun" to "Adobe AIR（包名以 air. 开头，libCore.so 为 AIR 运行时），游戏为 ActionScript 编写",
            "SNK 的街机游戏（android001）" to "模拟器（libEmulator.so）：直接运行原版街机程序，而非重新编写",
            "瘟疫公司" to "自研 C++ 引擎（libPlagueIncAndroidNative.so）；另打包了多套广告 SDK（AppLovin、穿山甲等）",
            "铁锈战争" to "无原生库，纯 Java；单位定义（assets/units，410 个文件）和地图均为数据文件，便于制作模组。它最初是手机游戏，后来才推出电脑版",
            "Brotato（本机这个包名）" to "Unity。电脑原版使用 Godot，两者不是同一份代码，属于重新实现的版本",
            "体积" to "Unity 游戏普遍较大：辐射避难所 1.7GB，人类一败涂地 1.1GB，空洞骑士 0.65～1GB；铁锈战争仅 25MB",
        )
    }

    why("几种移植方式的原理") {
        text("""
            1. **跨平台引擎直接导出**（最常见）：Unity、GameMaker、LÖVE、Godot 等引擎为每个平台提供运行时，游戏本身（场景、脚本、资源）基本不变，开发者在引擎中选择「导出为安卓」。
               Unity 的 IL2CPP 会把 C# 编译成的中间语言再转换为 C++，用安卓 NDK 编译成 arm64 原生库（libil2cpp.so），因此运行效率接近原生程序。
            2. **模拟器**：为原平台（街机、家用机）编写的程序，连同一个模拟该硬件的程序一起打包，代码完全不改。
            3. **自研原生引擎**：用 C/C++ 编写的引擎，借助安卓 NDK 为 arm64 重新编译；平台相关的部分（窗口、输入、声音、文件）需要单独适配。
            4. **Java 引擎**：Java 程序在电脑上运行于 JVM，在安卓上运行于 ART，同一份逻辑代码可在两边共用，只替换图形和输入层（铁锈战争即为此类，方向是从手机到电脑）。
            5. **重新实现**：用另一种引擎照原作重新制作，代码与原版无关。
        """)
    }

    consequences("移植时真正要做的工作") {
        text("""
            引擎解决了「能运行」，以下问题仍需针对手机处理：
            • **操作**：鼠标键盘改为触屏，例如虚拟摇杆、点按代替悬停、放大可点击区域；
            • **界面**：小屏幕上的字号、按钮布局和刘海、圆角的避让；
            • **性能与发热**：降低分辨率与特效、限制帧率，控制内存（安卓会结束占用过多内存的后台应用）；
            • **存档与生命周期**：手机应用随时可能被切到后台或被结束，需要及时保存；
            • **商业模式**：手机版常改为免费加广告或内购，例如瘟疫公司中的多套广告 SDK。
            另外，部分游戏官方并未发布安卓版，网上流传的安装包来自第三方修改，安全性无法保证。
        """)
    }
    related("phone-facts")
}
