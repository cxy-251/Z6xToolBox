package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val ForceAdb = module("force-adb", "在 SSH 里强开网络 ADB") {
    keywords = "setprop · ctl.start adbd · ro.adb.secure · SELinux"
    overview = """
        系统里找不到开发者选项，SimpleSSHD 又只有普通 App 权限，卸不掉也停不了预装应用。
        最后发现系统底层已经预设好了网络 ADB：在 SSH 里执行一条 setprop 拉起 adbd，就拿到了 shell 身份。
    """
    partial("2026-10-01")

    story("为什么非要 ADB 不可") {
        text("""
            SimpleSSHD 是一个普通 App，SSH 登录进去的 shell 继承它的身份 **uid 10068**。
            查东西没问题（getprop、/proc、df 对所有 App 开放），但 `pm uninstall`、`pm disable-user`、跨应用 `am start` 都要求调用者是 **shell（uid 2000）** 或 root，普通 App 一律被拒。
            ADB 的 shell 正是 uid 2000，所以目标变成：想办法让 adbd 跑起来。
        """)
    }

    story("关键发现：底层早就配好了") {
        text("翻系统属性时发现三处关键配置（属性值与身份无关，用哪种 shell 读结果都一样）：")
        read("网络调试端口", "getprop service.adb.tcp.port", Host.Ssh) {
            captured("2026-10-01", "5555")
            note = "adbd 启动后会监听这个 TCP 端口。"
        }
        read("是否要求授权", "getprop ro.adb.secure", Host.Ssh) {
            captured("2026-10-01", "0")
            note = "0 表示连接时**不弹 RSA 指纹授权框**。普通手机是 1，第一次连接要在屏幕上点允许。"
        }
        read("SELinux 模式", "getenforce", Host.Ssh) {
            captured("2026-10-01", "Permissive")
            note = """
                **这是能成功的真正前提。** `ctl.start` 这类控制属性由 SELinux 策略决定谁能设置，正常（Enforcing）系统上普通 App 无权启动系统服务。
                这台机器是 **Permissive（宽容模式）**：违规操作只记日志、不拦截，所以普通 App 也能拉起 adbd。
            """
        }
    }

    steps("强开 adbd") {
        change("让 init 启动 adbd 服务", "setprop ctl.start adbd", Host.Ssh) {
            note = "`ctl.start` 是 init 的控制属性：写入服务名，init 就启动这个服务。adbd 读取 service.adb.tcp.port，开始监听 5555。"
            outcome = "没有任何输出。adbd 在后台启动，局域网内可以用 adb 连接了。"
        }
        read("在 Deck 上连接", "adb connect 192.168.0.109:5555", Host.Deck) {
            captured("2026-10-01", "already connected to 192.168.0.109:5555")
            note = "IP 换成投影仪自己的地址（电视的网络设置里能看到，或在 SSH 里 `ip -4 addr show wlan0`）。上面是已经连着时的输出；第一次连接显示 connected to …"
        }
        read("确认拿到的身份", "id", Host.Adb) {
            captured("2026-10-01", "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid) context=u:r:shell:s0")
            note = "uid=2000(shell) 就是目标身份。后面的组决定还能碰哪些设备节点：例如有 **input**（能读遥控器按键事件），没有 audio、graphics。"
        }
    }

    consequences {
        text("""
            • 断电重启后 adbd 不一定还在：需要时再从 SSH 执行一次 `setprop ctl.start adbd`。所以 SimpleSSHD **不要卸载**。
            • `ro.adb.secure=0` 意味着同一局域网里**任何人**都能连上并获得 shell 权限，不要把投影仪放在不可信的网络里。
            • 系统 OTA 升级可能把 SELinux 改回 Enforcing，这条路就会失效。深度定制里停用 OTA 也有这个考虑。
        """)
    }

    lesson("核对旧记录时发现的问题") {
        text("""
            • 旧版写的另一种办法 `setprop xgimi.remoteDebug.on 1` 没有依据：这个属性在实机上的值是 `false`，ADB 并不是靠它开的，已删除。
            • 旧版第 5 篇说 ADB "默认开放无需授权"，第 8 篇又说要强开，两者矛盾。准确说法是：端口和免授权是**预设好的**，但 adbd 默认**不运行**，要手动拉起。
            • 旧版没提 SELinux。不知道 Permissive 这个前提，就解释不了为什么普通 App 能启动系统服务。
            • 本页的"部分核实"：属性值和身份都在 2026-10-01 核对过；`setprop ctl.start adbd` 那一步发生在当时，现在 adbd 已经在运行，没有重新演示。
        """)
    }

    related("find-real-model")
}
