package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val FocusWindow = module("focus-window", "查看当前焦点窗口") {
    keywords = "dumpsys window · mCurrentFocus · input keyevent"
    overview = """
        屏幕上弹出一个不认识的窗口（广告？提示？），想知道它属于哪个 App；或者想确认按 Home 后到底回到了哪个桌面。用 `dumpsys window` 一查便知，再用 `input keyevent` 远程按键。
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
                **mCurrentFocus**：现在接收按键的窗口，可能是弹窗、浮层。
                **mFocusedApp**：前台应用的界面（Activity）。
                两者不一样时，说明有个浮层盖在应用上面。格式是 `包名/界面类名`，包名就告诉你它属于谁。
                上面是截图那会儿的结果：屏幕上开着 SimpleSSHD。
            """
        }
        change("配合截图一起看", "adb exec-out screencap -p > tv.png", Host.Deck) {
            note = "先截图看清是什么，再用上一条查它是谁。见「给投影仪截图」。"
        }
    }

    steps("远程按遥控器") {
        change("返回键", "input keyevent 4", Host.Adb) {
            note = "关掉浮层、退出当前界面。4 = KEYCODE_BACK。"
        }
        change("Home 键", "input keyevent 3", Host.Adb) {
            note = "回桌面。3 = KEYCODE_HOME。按完再查一次焦点，就能确认回到的是哪个桌面。"
        }
        change("方向键与确认", "input keyevent 19   # 上；20 下、21 左、22 右、23 确认", Host.Adb) {
            note = "`#` 后面是注释。完整键值表搜索 Android KeyEvent。"
        }
    }

    consequences {
        text("""
            • `input keyevent` 等同于真的按了遥控器，会改变屏幕上的状态。投影仪有人在看时注意别打断。
            • 有些浮层不是 Activity（比如系统音量条），mFocusedApp 不会变，只看 mCurrentFocus。
        """)
    }

    related("tv-screencap", "projectivy-launcher")
}
