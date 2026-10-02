package z6x.content.custom

import z6x.framework.Host
import z6x.framework.module

/*
 * 遥控器按键重映射：原理、排查过程与使用方法。2026-10-02 实测，用户已确认短按、长按均有效。
 */
val RemoteKeys = module("remote-keys", "遥控器按键重映射：影视快捷键与调焦旁键") {
    keywords = "2116～2121 · XgimiWindowManager · XRM · WindowManager 日志 · keymap · 短按 / 长按"
    overview = """
        极米遥控器上的四个影视快捷键（酷喵、云视听极光、奇异果、芒果）和调焦键两侧的两个键（壁纸键与另一个键），在精简系统后都不再有用。
        现在由 z6x-tools 的 keymap 守护进程识别它们的短按与长按，执行自定义的动作；动作在 hub 的「遥控器按键」页面设置，共 12 项。
    """
    verified("2026-10-02")

    why("这些键是怎么传进系统的") {
        facts(
            "按键码" to "极米自定义的安卓按键码：酷喵 2118、云视听极光 2119、奇异果 2120、芒果 2121（另有哔哩哔哩 2126，本机遥控器上没有），壁纸键 2116，调焦键另一侧的键 2117",
            "不经过输入设备节点" to "遥控器在系统中是两个输入设备（XGIMI RC Keyboard、XGIMI RC Consumer Control），用 getevent 同时录制这两个节点，按这些键时什么也录不到（只出现过一次无关的 KEY_FILE 抬起）",
            "原始 HID 通道读不到" to "遥控器的原始数据设备 /dev/hidraw2（XGIMI RC）权限为 system:audio 0660，shell 不在 audio 组，无法读取；推测这条通道还承载语音按键",
            "极米的拦截" to "极米改过的窗口管理（XgimiWindowManagerDomestic.interceptKeyBeforeDispatch）在按键分发给应用之前拦截 2116、2118～2121，交给 XRM 服务（com.xgimi.xrmservice）处理：XRM 按对照表 {2118=youkutv, 2119=tencenttv, 2120=aiqiyitv, 2121=mangotv, 2126=bilibilitv} 打开对应应用。这些应用未安装，壁纸功能也已在精简时停用，因此原本按下后什么也不发生。2117 不被拦截，交给前台应用，应用也不处理",
            "没有可改的设置" to "系统设置（system、secure、global）中没有按键与应用的对照项，XRM 是系统内置应用，对照表写在其内部",
        )
    }

    why("keymap 如何识别短按与长按") {
        text("""
            系统的 WindowManager 在日志中完整记录每个按键的过程（shell 在 log 组，可以读取日志）：
            • 按下：`interceptKeyTi keyCode=2118 down=true repeatCount=0`
            • 按住：约 0.4 秒后每 50ms 一条，`repeatCount` 依次为 1、2、3……
            • 松开：`interceptKeyTi keyCode=2118 down=false`
            keymap 以 `logcat -v brief -T 1 WindowManager:D *:S` 持续读取新日志：按住达到 600ms 即执行长按动作；在此之前松开则在松开时执行短按动作。
            最初读取的是 `XgimiWindowManager: do action keycode … keyevent down`，它只在按下时出现一次，无法区分长按，后改为读取 WindowManager 的完整过程（2116、2118～2121 两者都有，2117 只有 WindowManager 的记录）。
        """)
        text("""
            **为什么不用「独占遥控器再转发」的方式**：普通键可以独占输入设备（EVIOCGRAB）后由虚拟键盘转发，但这几个键根本不经过输入设备节点，只能从日志识别。由于极米拦截后什么也不做，读取日志的方式不会与原有功能冲突。
            **普通键**（方向、确认、返回、菜单等）keymap 仍从输入设备节点读取，只支持长按；不独占设备，原有功能不受影响（长按的键松开时仍会产生它原本的效果）。按用户要求，只保留短按与长按，不支持双击（容易误触）。
        """)
    }

    steps("使用") {
        change("设置动作", "", Host.Remote) {
            note = "在 hub 首页进入「遥控器按键」：每个键的短按、长按可选「打开应用」（自动列出可从桌面启动的应用，占位入口已过滤）、「任务管理」、「按键」或「自定义命令」。保存后 keymap 在 2 秒内退出，由 z6x run 1 秒后重启并读入新配置。"
        }
        change("部署与启动", "./tools/build.sh deploy projector && ./tools/keymap.sh projector", Host.Deck) {
            note = "配置以投影仪上的 /data/local/tmp/z6x-tools/keymap.conf 为准：已存在时只拉回本机，不覆盖网页上的修改；需要推送本机配置时加 --push-config。投影仪重启后需重新执行（与 hub 相同）。"
        }
        read("查看运行状态", "./tools/keymap.sh projector status", Host.Deck) { varies = true }
    }

    consequences("限制") {
        text("""
            • 依赖系统日志：若系统更新后 WindowManager 不再输出这些调试日志，或日志格式改变，需要相应调整。
            • 需要 shell 身份运行（读取日志与输入设备），因此由 ADB 启动；投影仪重启后需从 Deck 重新启动。
            • 从按下到执行动作有日志传递的延迟，实测感觉不到。
        """)
    }
    related("spec-tools", "spec-hub", "debloat-method")
}
