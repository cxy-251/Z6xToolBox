package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val FindAdbEntry = module("find-adb-entry", "找开发者模式入口：全部碰壁") {
    keywords = "开发者选项 · 工厂模式 · minitvfactory · Activity"
    overview = """
        普通安卓设备在"关于本机"里连点版本号就能打开开发者选项，再开 ADB 调试。这台投影仪上试了三条路都走不通，最后才转向「在 SSH 里强开网络 ADB」。
    """
    partial("2026-10-01")

    story("尝试 1：遥控器按键组合") {
        change("网上流传的各种组合键", "", Host.Tv) {
            note = """
                • 在"关于"页面连按确定键 5~7 次；
                • 方向键"左、确认、右"之类的组合，长按或快速连按；
                • 长按遥控器上的设置键。
            """
            outcome = "全部没有反应。"
        }
        text("这些「偏方」多半来自老版本固件或别的机型，没有可靠来源。**不行就换思路：** 既然界面上找不到，就用命令行看看系统里到底有没有开发者选项。")
    }

    story("尝试 2：看看系统设置里有没有开发者选项") {
        read("找设置相关的包", "pm list packages | grep -iE 'setting|developer'", Host.Ssh) {
            captured("2026-10-01", """
                package:com.android.settings.intelligence
                package:com.android.providers.settings
                package:com.android.newsettings
            """)
            note = """
                原生安卓的设置是 `com.android.settings`，电视版是 `com.android.tv.settings`，这两个**都没有**。
                极米换成了自己的 `com.android.newsettings`，里面没有"开发者选项"这一项。
                另外两个不是设置界面：settings.intelligence 是设置搜索，providers.settings 是存储设置值的数据库。
            """
        }
        text("**此路不通：** 原生开发者选项整个被拿掉了。**换思路：** 厂商一般有工厂/工程模式，生产线调试时要用，说不定藏着调试开关。")
    }

    story("尝试 3：工厂模式") {
        read("找工厂模式的启动界面", "cmd package resolve-activity --brief -a android.intent.action.MAIN com.xgimi.minitvfactory", Host.Adb) {
            captured("2026-10-01", """
                priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
                com.xgimi.minitvfactory/.ui.MainFactoryMenuActivity
            """)
            note = """
                `resolve-activity` 问系统"这个包响应 MAIN（启动）意图的是哪个界面"，`--brief` 只给出结论。
                最后一行就是入口：`包名/.界面类名`。拿到它就能用 Activity Launcher 或 `am start -n` 打开。
            """
        }
        text("""
            用 Activity Launcher 打开了工厂菜单，两页工程选项（老化、光机参数、色温、按键测试、网络测试等）翻遍了，没有 ADB 或网络调试开关。**此路也不通。**
        """)
    }

    story("后来发现：其实有个「远程调试」应用") {
        read("在所有可启动的界面里搜 debug", "pm query-activities --brief -a android.intent.action.MAIN | grep -iE 'developer|debug|remote'", Host.Adb) {
            captured("2026-10-01", "    com.xgimi.remote/.RemoteActivity")
        }
        read("它是哪个安装包", "pm list packages -f com.xgimi.remote", Host.Adb) {
            captured("2026-10-01", "package:/vendor/app/XgimiRemoteDebug/XgimiRemoteDebug.apk=com.xgimi.remote")
            note = "`-f` 同时显示安装包路径。文件名就叫 **XgimiRemoteDebug**（极米远程调试），装在 vendor 分区。"
        }
        text("""
            旧记录说"全局检索只找到 WebView DevTools"，实测并非如此：有一个极米自己的远程调试应用 `com.xgimi.remote`（版本 2.0.1），带启动界面，还能接收开关 WebRTC 的广播。
            它和属性 `xgimi.remoteDebug.on`（当前为 false）可能有关，像是官方远程协助工具。**没有打开验证**：不清楚它会不会联网上报或改变调试状态，等有需要时再研究。
        """)
    }

    lesson("经验") {
        text("""
            • 界面上找不到，就用命令行列出系统里实际有什么：`pm list packages`、`pm query-activities`、`resolve-activity`。
            • 一条路走不通时，问自己"这个功能**本来**应该在哪"：开发者选项在设置里 → 设置被换掉了 → 厂商调试一般在工厂模式 → 工厂模式也没有 → 不找开关了，直接看底层属性（这一步成功了）。
            • 输出要看全。不加 `--brief` 时，搜 debug 命中的是安装包路径里的 XgimiRemoteDebug.apk，组件名 RemoteActivity 里并没有 debug，所以这里多加了 remote 关键词。旧记录说"只找到 WebView DevTools"，和实测对不上。
        """)
    }

    related("force-adb", "ssh-permission-wall")
}
