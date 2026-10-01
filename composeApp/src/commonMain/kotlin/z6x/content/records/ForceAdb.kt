package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val ForceAdb = module("force-adb", "通过 SSH 启动网络 ADB") {
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
        text("翻系统属性时发现三处关键配置：")
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

    story("事后验证：普通 App 真的能设置系统属性吗") {
        text("""
            上面说"能成功是因为 Permissive"，这是推理。2026-10-01 配好 SSH 免密登录后，用实验验证了一次。
            **第一个办法：** 在 SSH 里再执行一次 `setprop ctl.start adbd`（adbd 已经在运行，init 会忽略这个请求，没有副作用），然后去日志里找 SELinux "违规但放行"的记录。
        """)
        change("SSH 里再发一次启动请求", "setprop ctl.start adbd; echo exit=${'$'}?", Host.Ssh) {
            captured("2026-10-01", "exit=0")
            note = "`${'$'}?` 是上一条命令的退出码，0 表示成功。adbd 的进程号前后没变，说明 init 确实忽略了这次请求。"
        }
        text("""
            日志里没找到任何记录：这台机器的 init 不记录控制消息，`logcat -b all` 全量搜索还因为缓冲区太大超时了。**此路不通。**
            **换方案：** 不找日志，直接做一个能看到结果的实验。写一个无害的调试属性 `debug.z6x.test`。正常（Enforcing）的安卓上，普通 App 没有权限写 debug 类属性；如果这里能写进去，就证明 SELinux 没在拦。
        """)
        change("App 身份写一个调试属性", "setprop debug.z6x.test 1; echo exit=${'$'}?; getprop debug.z6x.test", Host.Ssh) {
            captured("2026-10-01", """
                exit=0
                1
            """)
            note = """
                写进去了。实验后用 `setprop debug.z6x.test ""` 清空。
                **意外发现：** setprop 不能删除属性，只能把值清空。之后 `getprop` 列出全部属性时仍有一行 `[debug.z6x.test]: []`，要到重启才消失（debug.* 不持久化；2026-10-01 重启后确认已消失）。
                「核查：真实型号与芯片」里用 `grep z6x` 搜属性时就多出了这一行，被 `--try-read` 发现了。
            """
            outcome = "证实：在这台机器上，普通 App 可以设置本该被 SELinux 拦下的属性。"
        }
    }

    consequences {
        text("""
            • **重启后 adbd 会自动运行**（2026-10-01 实测：重启后 `ro.boottime.adbd` 约 6.6 秒，ADB 直接能连）。原因是极米启动配置里的一段补丁，见「核查：ADB 开机自动运行的原因」。
            • 但只要 adbd 被停掉一次，这个"自启"就会失效，要从 SSH 再强开。所以 SimpleSSHD **不要卸载**。
            • `ro.adb.secure=0` 意味着同一局域网里**任何人**都能连上并获得 shell 权限，不要把投影仪放在不可信的网络里。
            • 系统 OTA 升级可能把 SELinux 改回 Enforcing，这条路就会失效。深度定制里停用 OTA 也有这个考虑。
        """)
    }

    audit("旧记录核对（2026-10-01）") {
        claim("排查系统底层属性时，发现极米系统已经将底层网络 ADB 的配置预埋好了：service.adb.tcp.port 属性值预设为 5555；ro.adb.secure 属性值为 0（免除了弹出 RSA 密钥授权对话框的步骤）；只需在 SimpleSSHD shell 中向系统发送属性指令启动 adbd 守护进程，即可直接对外开放 5555 调试端口。", Verdict.Confirmed,
            "两个属性值都对；App 身份确实能设置这类属性（debug 属性实验）。旧记录**没提** SELinux 是 Permissive，这才是能成功的前提。唯一没重现的是「adbd 没运行时把它拉起来」那一刻：现在 adbd 在运行，为此关掉它会断开 ADB，不值得。")
        change("旧版：触发系统启动 adbd 的另一种写法", "setprop service.adb.tcp.port 5555 && setprop ctl.start adbd", Host.Ssh) {
            verdict = Verdict.Unverified
            note = "前半句多余：service.adb.tcp.port 本来就是 5555。效果和 `setprop ctl.start adbd` 相同。"
        }
        change("旧版：极米特定私有属性", "setprop xgimi.remoteDebug.on 1", Host.Ssh) {
            verdict = Verdict.Unverified
            note = "这个属性存在，当前值是 `false`。ADB 不是靠它开的（按你的记录，是在 SSH 里执行命令拉起的）。设成 1 会发生什么没有测试；它可能和极米远程调试应用 com.xgimi.remote 有关。"
        }
        claim("调试接口：网络 ADB 端口 5555（默认开放且无需授权指纹）（硬件摸底记录）；需要在 SSH 里强开（强开 ADB 记录）", Verdict.Disproved,
            "两处记录互相矛盾。准确说法：端口和免授权是**预设**的，adbd 默认**不运行**，要手动拉起。")
    }

    related("find-real-model")
}
