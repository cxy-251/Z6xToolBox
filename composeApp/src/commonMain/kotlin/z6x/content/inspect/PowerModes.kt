package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val PowerModes = module("power-modes", "核查：「关屏」与「关机」的实际行为") {
    keywords = "mWakefulness · /proc/uptime · suspend · mem_sleep · 关机是睡眠"
    overview = """
        极米的电源菜单有关屏、关机、重启、定时关机。想知道哪种状态下后台服务还在，从 Deck 每隔几秒检查一次网络、ADB 端口、系统唤醒状态和运行时长，实测得出：**关屏时系统照常运行；关机其实是睡眠**。
    """
    verified("2026-10-01")

    why("用来判断的几个指标") {
        text("""
            • **mWakefulness**（`dumpsys power`）：安卓的唤醒状态。Awake 醒着；Asleep 休眠；Dozing 打盹。
            • **/proc/uptime** 第一个数：开机以来经过的秒数，**包括睡眠的时间**。真关机重启后从 0 开始；睡眠醒来则接着累加。
            • 网络通不通、5555 端口开不开：从 Deck 上看投影仪还在不在线。
        """)
    }

    steps("从 Deck 上盯着看") {
        read("每 3 秒记录一次变化", """
            prev=""; while true; do
              p=${'$'}(ping -c1 -W1 192.168.0.109 >/dev/null 2>&1 && echo 通 || echo 断)
              a=${'$'}(timeout 2 bash -c 'echo > /dev/tcp/192.168.0.109/5555' 2>/dev/null && echo ADB开 || echo ADB关)
              w=""; [ "${'$'}a" = "ADB开" ] && w=${'$'}(timeout 5 adb shell 'dumpsys power | grep -m1 -o "mWakefulness=[A-Za-z]*"; cut -d" " -f1 /proc/uptime' | tr '\n' ' ')
              key="网络${'$'}p | ${'$'}a | ${'$'}{w%% *}"   # 比较时不含 uptime（它每秒都在变）
              [ "${'$'}key" != "${'$'}prev" ] && echo "${'$'}(date +%T) 网络${'$'}p | ${'$'}a | ${'$'}w"; prev="${'$'}key"; sleep 3
            done
        """, Host.Deck) {
            manual = true
            note = "一直运行，只在网络、端口、唤醒状态变化时打印一行（带上当时的 uptime），Ctrl+C 停止。`${'$'}{w%% *}` 去掉 uptime 只留唤醒状态。`/dev/tcp/IP/端口` 是 bash 测端口通不通的写法。"
        }
    }

    story("实验 1：关屏") {
        facts(
            "21:46" to "选「关屏」。光机关闭，但音乐继续播放",
            "关屏后立刻查" to "mWakefulness=Awake；`dumpsys window` 里 mScreenOnFully=true；显示状态 ON、亮度策略 BRIGHT",
            "之后 30 分钟" to "网络、ADB、SSH、Go 服务**全程在线，没有任何变化**",
        )
        text("**结论：**「关屏」只是极米把光机关了，安卓系统根本不知道，照常运行。后台服务在这个状态下一直可用。")
    }

    story("实验 2：关机") {
        facts(
            "22:47:28 关机前" to "网络通 | ADB开 | Awake | uptime 6382 秒",
            "22:47:36 选「关机」" to "网络通 | ADB开 | **Asleep** | uptime 6388 秒",
            "22:47:42（约 14 秒后）" to "**网络断 | ADB关**",
            "22:49:13 按开机键" to "网络通 | ADB开 | Awake | uptime **6487 秒**",
        )
        read("醒来后：进程还在吗", "pidof z6x_go_server", Host.Adb) {
            varies = true
            captured("2026-10-01", "9089")
            note = "和关机前是**同一个进程号**，服务没有重启，醒来后直接继续工作。"
        }
        read("内核用的睡眠方式", "cat /sys/power/mem_sleep", Host.Adb) {
            captured("2026-10-01", "s2idle [deep]")
            note = "方括号里是当前选用的：`deep` = 挂起到内存（Suspend-to-RAM）：CPU 断电，内存保持供电，所有进程冻结在原地。"
        }
        read("系统记录的睡眠原因", "dumpsys power | grep -m1 mLastSleepReason", Host.Adb) {
            varies = true
            captured("2026-10-01", "  mLastSleepReason=display_groups_turned_off")
        }
        text("""
            **结论：「关机」其实是睡眠。** uptime 接着关机前的数往上加（多出的 99 秒正好是关机到开机的时间），进程号不变，内核没有重启。
            关机后约 14 秒挂起，网络断开，期间投影仪对局域网完全不可见；按开机键后原样恢复。
        """)
    }

    verify("这对我们意味着什么") {
        facts(
            "关屏" to "系统照常运行。想让文件共享、遥控网页等服务 24 小时可用，看完投影就选「关屏」",
            "关机" to "睡眠：服务暂停、对外不可达；开机后自动恢复，**不需要**重新启动任何东西",
            "重启 / 拔电源" to "真正的重启：自己部署的服务要重新启动（ADB 会自动起来）",
        )
    }

    lesson("经验") {
        text("""
            • 「关机」这个词不可信，要看证据：uptime 是否归零、进程号是否改变。
            • 用一个只在状态变化时输出的循环盯着看，比隔一会儿手动查一次可靠，能拿到精确到秒的时间线。
        """)
    }

    related("go-server", "emergency", "spec-hub", "proc-metrics")
}
