package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val ProjectorAutostart = module("projector-autostart", "开机自动启动 hub 与 keymap（本机 ADB）") {
    keywords = "Termux:Boot · adb connect 127.0.0.1:5555 · ro.adb.secure=0 · z6x-boot.sh"
    overview = """
        hub 与 keymap 必须以 shell 身份运行（读取按键、日志，模拟按键），而 shell 身份的程序只能从 ADB 启动；没有 root，就没有让它们开机自启的入口，原来每次投影仪重启后都要从 Deck 重新启动。
        现在由投影仪自己完成：开机时 Termux:Boot 运行脚本，用 Termux 自带的 adb 连接投影仪自己的 ADB（127.0.0.1:5555），再以 shell 身份启动 hub 与 keymap。思路与 Shizuku、LADB 相同。
    """
    partial("2026-10-02")

    why("为什么可行") {
        facts(
            "ADB 开机即开" to "投影仪开机后 adbd 自动在 5555 端口监听（见「开机自动开启 ADB」）",
            "不需要授权" to "投影仪的 ro.adb.secure 为 0：ADB 不验证客户端密钥，Termux 连接时不会弹出「允许 USB 调试」提示（这也是 Deck 最初能直接连上的原因）。投影仪无法从界面打开开发者选项，也就不需要它",
            "身份" to "实测 Termux 经 adb shell 执行 id 得到 uid=2000(shell)，与从 Deck 连接相同",
            "风险" to "Termux 因此拥有获得 shell 权限的途径；ro.adb.secure 为 0 本身已意味着局域网内任何人都能连接 5555，这不是新增的风险",
        )
    }

    steps("组成") {
        change("设备端启动脚本", "adb -s 192.168.0.109:5555 shell sh /data/local/tmp/z6x-boot.sh", Host.Deck) {
            note = "仓库中的 `hub/adb-boot.sh`，由 `hub/deploy.sh` 推送到 /data/local/tmp/z6x-boot.sh。hub 或 keymap 未运行时启动，已在运行的不重复启动，记录写入 /data/local/tmp/z6x-boot.log。Deck 上也可以手动执行。"
        }
        change("Termux 的开机脚本", "~/.termux/boot/z6x-start.sh", Host.Remote) {
            note = "等待 20 秒（adbd 与网络就绪），然后最多尝试 6 次：adb connect 127.0.0.1:5555，adb shell sh /data/local/tmp/z6x-boot.sh。Termux:Boot 需在安装后手动打开一次，安卓才允许它接收开机广播。"
        }
    }

    story("安装 adb 时遇到的问题（2026-10-02）") {
        text("""
            1. **Termux 下载不了软件包**：镜像站的域名被解析为 28.0.0.28，这是投影仪上 Clash 的虚拟地址段（fake-ip），HTTPS 握手中断，改用 HTTP 也连接失败。按约定不修改 Clash 的设置，改为不让投影仪上网下载：由 Deck 从清华镜像下载 arm 架构的 android-tools 及其依赖（共 11 个 .deb，约 7MB），推送到共享存储，在 Termux 中用 dpkg 安装。
            2. **文件名中的冒号和加号**：`fmt_1:11.2.0-1_arm.deb`、`libprotobuf_2:35.1_arm.deb`、`libc++_30_arm.deb` 推送到 /sdcard 后无法被正确安装（libc++ 在共享存储中根本找不到）。改名为 fmt.deb、libprotobuf.deb、libcxx.deb 后正常。
            3. **库版本不匹配**：缺少 libc++ 时 adb 报「cannot locate symbol _ZNSt6__ndk113__hash_memoryEPKvj」，装上 libc++ 30 后正常（adb 1.0.41）。
            4. Termux 的私有目录从 ADB 读不到（发布版不可调试），排查时让 Termux 把命令输出写到 /sdcard，再从 Deck 读取。
        """)
    }

    verify("待验证") {
        text("重启投影仪后，hub 与 keymap 是否自动运行（查看 /data/local/tmp/z6x-boot.log）。")
    }
    related("adb-autostart", "spec-hub", "remote-keys")
}
