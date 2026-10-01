package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val FocusWindow = module("focus-window", "查看当前焦点窗口") {
    keywords = "dumpsys window · mCurrentFocus · input keyevent"
    overview = """
        屏幕上出现来源不明的窗口（广告或提示）时，需要确定它属于哪个应用；或者需要确认按 Home 键后回到了哪个桌面。用 `dumpsys window` 即可查明，再用 `input keyevent` 远程按键。
    """
    verified("2026-10-01")

    steps("查看") {
        read("当前焦点窗口和前台应用", "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'", Host.Adb) {
            varies = true
            captured("2026-10-01", """
                  mCurrentFocus=Window{440e237 u0 org.galexander.sshd/org.galexander.sshd.SimpleSSHDTV}
                  mFocusedApp=ActivityRecord{d14f18a u0 org.galexander.sshd/.SimpleSSHDTV t615}
            """)
            note = """
                **mCurrentFocus**：当前接收按键的窗口，可能是弹窗或浮层。
                **mFocusedApp**：前台应用的界面（Activity）。
                两者不同时，说明有浮层覆盖在应用之上。格式为 `包名/界面类名`，包名即表明其所属应用。
                以上是截图时的结果：屏幕上打开的是 SimpleSSHD。
            """
        }
        change("配合截图使用", "adb exec-out screencap -p > tv.png", Host.Deck) {
            note = "先截图看清内容，再用上一条命令查明归属。见「投影仪截图」。"
        }
    }

    steps("远程模拟遥控器") {
        change("返回键", "input keyevent 4", Host.Adb) {
            note = "关闭浮层或退出当前界面。4 即 KEYCODE_BACK。"
        }
        change("Home 键", "input keyevent 3", Host.Adb) {
            note = "回到桌面。3 即 KEYCODE_HOME。按下后再查询一次焦点，即可确认回到的桌面。"
        }
        change("方向键与确认", "input keyevent 19   # 上；20 下、21 左、22 右、23 确认", Host.Adb) {
            note = "`#` 之后为注释。完整键值表见 Android 官方文档中的 KeyEvent。"
        }
    }

    consequences {
        text("""
            • `input keyevent` 等同于实际按下遥控器，会改变屏幕状态。有人观看时应避免打断。
            • 部分浮层不是 Activity（如系统音量条），此时 mFocusedApp 不会变化，只需查看 mCurrentFocus。
        """)
    }

    related("tv-screencap", "projectivy-launcher")
}
