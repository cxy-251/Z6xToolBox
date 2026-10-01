package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val PowerModes = module("power-modes", "核查：「关屏」与「关机」的实际行为") {
    keywords = "mWakefulness · /proc/uptime · suspend · mem_sleep · 关机是睡眠"
    overview = """
        极米的电源菜单提供关屏、关机、重启和定时关机。为确定在哪种状态下后台服务仍可用，从 Deck 每隔数秒检查一次网络、ADB 端口、系统唤醒状态和运行时长，实测结论为：**关屏时系统照常运行；关机实际上是睡眠**。
    """
    verified("2026-10-01")

    why("用来判断的几个指标") {
        text("""
            • **mWakefulness**（`dumpsys power`）：安卓的唤醒状态。Awake 为唤醒，Asleep 为休眠，Dozing 为低功耗待机。
            • **/proc/uptime** 的第一个数：开机以来经过的秒数，**包含睡眠时间**。真正关机重启后从 0 开始；从睡眠中唤醒则继续累加。
            • 网络是否可达、5555 端口是否开放：从 Deck 判断投影仪是否在线。
        """)
    }

    steps("从 Deck 持续监测") {
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
            note = "持续运行，仅在网络、端口或唤醒状态变化时输出一行（附带当时的 uptime），按 Ctrl+C 停止。`${'$'}{w%% *}` 去掉 uptime，只保留唤醒状态。`/dev/tcp/IP/端口` 是 bash 测试端口是否可达的写法。"
        }
    }

    story("实验 1：关屏") {
        facts(
            "21:46" to "选择「关屏」。光机关闭，但音乐继续播放",
            "关屏后立即查询" to "mWakefulness=Awake；`dumpsys window` 中 mScreenOnFully=true；显示状态为 ON，亮度策略为 BRIGHT",
            "此后 30 分钟" to "网络、ADB、SSH 和 Go 服务**全程在线，没有任何变化**",
        )
        text("**结论：**「关屏」只是极米关闭了光机，安卓系统并未感知，照常运行。后台服务在此状态下始终可用。")
    }

    story("实验 2：关机") {
        facts(
            "22:47:28 关机前" to "网络通 | ADB开 | Awake | uptime 6382 秒",
            "22:47:36 选择「关机」" to "网络通 | ADB开 | **Asleep** | uptime 6388 秒",
            "22:47:42（约 14 秒后）" to "**网络断 | ADB关**",
            "22:49:13 按开机键" to "网络通 | ADB开 | Awake | uptime **6487 秒**",
        )
        read("唤醒后进程是否仍在", "pidof z6x_go_server", Host.Adb) {
            varies = true
            captured("2026-10-01", "9089")
            note = "与关机前是**同一个进程号**，服务没有重启，唤醒后直接继续工作。"
        }
        read("内核采用的睡眠方式", "cat /sys/power/mem_sleep", Host.Adb) {
            captured("2026-10-01", "s2idle [deep]")
            note = "方括号内为当前选用的方式：`deep` 即挂起到内存（Suspend-to-RAM），CPU 断电，内存保持供电，所有进程冻结在原状态。"
        }
        read("系统记录的睡眠原因", "dumpsys power | grep -m1 mLastSleepReason", Host.Adb) {
            varies = true
            captured("2026-10-01", "  mLastSleepReason=display_groups_turned_off")
        }
        text("""
            **结论：「关机」实际上是睡眠。** uptime 在关机前的数值上继续累加（多出的 99 秒正是从关机到开机的时间），进程号不变，内核没有重启。
            关机约 14 秒后系统挂起，网络断开，其间投影仪在局域网中完全不可见；按开机键后原样恢复。
        """)
    }

    verify("实际影响") {
        facts(
            "关屏" to "系统照常运行。若希望文件共享、网页遥控等服务全天可用，观看结束后应选择「关屏」",
            "关机" to "睡眠：服务暂停，外部无法访问；开机后自动恢复，**无需**重新启动任何程序",
            "重启 / 断电" to "真正的重启：自行部署的服务需要重新启动（ADB 会自动运行）",
        )
    }

    lesson("经验") {
        text("""
            • 不能仅凭「关机」这个名称判断，要看证据：uptime 是否归零、进程号是否改变。
            • 用只在状态变化时输出的循环持续监测，比间隔手动查询更可靠，能得到精确到秒的时间线。
        """)
    }

    related("go-server", "emergency", "spec-hub", "proc-metrics")
}
