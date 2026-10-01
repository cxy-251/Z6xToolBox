package z6x.content.records

import z6x.framework.Host
import z6x.framework.Verdict
import z6x.framework.module

val ForceAdb = module("force-adb", "通过 SSH 启动网络 ADB") {
    keywords = "setprop · ctl.start adbd · ro.adb.secure · SELinux"
    overview = """
        系统中没有开发者选项，而 SimpleSSHD 只有普通应用权限，无法卸载或停用预装应用。
        最终发现系统底层已预设网络 ADB：在 SSH 中执行一条 setprop 命令启动 adbd，即可获得 shell 身份。
    """
    partial("2026-10-01")

    story("为什么必须使用 ADB") {
        text("""
            SimpleSSHD 是普通应用，通过 SSH 登录后得到的 shell 继承其身份 **uid 10068**。
            查询不受影响（getprop、/proc、df 对所有应用开放），但 `pm uninstall`、`pm disable-user`、跨应用 `am start` 都要求调用者拥有相应的系统权限。**shell（uid 2000）** 预先被授予了这些权限，普通应用则没有，因此一律被拒绝。
            ADB 的 shell 正是 uid 2000，于是目标变为：设法让 adbd 运行起来。
        """)
    }

    story("关键发现：底层已预先配置") {
        text("检查系统属性时发现三处关键配置：")
        read("网络调试端口", "getprop service.adb.tcp.port", Host.Ssh) {
            captured("2026-10-01", "5555")
            note = "adbd 启动后会监听这个 TCP 端口。"
        }
        read("是否要求授权", "getprop ro.adb.secure", Host.Ssh) {
            captured("2026-10-01", "0")
            note = "0 表示连接时**不弹出 RSA 指纹授权框**。普通手机为 1，首次连接时需要在屏幕上点击允许。"
        }
        read("SELinux 模式", "getenforce", Host.Ssh) {
            captured("2026-10-01", "Permissive")
            note = """
                **这是方法得以成功的真正前提。** `ctl.start` 这类控制属性由 SELinux 策略决定谁可以设置；在正常（Enforcing）系统上，普通应用无权启动系统服务。
                这台机器处于 **Permissive（宽容模式）**：违规操作只记录日志而不拦截，因此普通应用也能启动 adbd。
            """
        }
    }

    steps("启动 adbd") {
        change("让 init 启动 adbd 服务", "setprop ctl.start adbd", Host.Ssh) {
            note = "`ctl.start` 是 init 的控制属性：写入服务名后，init 即启动该服务。adbd 读取 service.adb.tcp.port，开始监听 5555 端口。"
            outcome = "命令无输出。adbd 在后台启动，局域网内即可用 adb 连接。"
        }
        read("在 Deck 上连接", "adb connect 192.168.0.109:5555", Host.Deck) {
            captured("2026-10-01", "already connected to 192.168.0.109:5555")
            note = "将 IP 替换为投影仪的实际地址（可在电视的网络设置中查看，或在 SSH 中执行 `ip -4 addr show wlan0`）。上方为已连接时的输出；首次连接时显示 connected to …"
        }
        read("确认获得的身份", "id", Host.Adb) {
            captured("2026-10-01", "uid=2000(shell) gid=2000(shell) groups=2000(shell),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),1078(ext_data_rw),1079(ext_obb_rw),3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid) context=u:r:shell:s0")
            note = "uid=2000(shell) 即目标身份。其后的用户组决定可以访问哪些设备节点：例如包含 **input**（可读取遥控器按键事件），不包含 audio、graphics。"
        }
    }

    story("事后验证：普通应用能否设置系统属性") {
        text("""
            上文所说「因 Permissive 而成功」属于推理。2026-10-01 配置好 SSH 公钥登录后，通过实验进行了验证。
            **第一种方法：** 在 SSH 中再次执行 `setprop ctl.start adbd`（adbd 已在运行，init 会忽略该请求，无副作用），然后在日志中查找 SELinux「违规但放行」的记录。
        """)
        change("在 SSH 中再次发送启动请求", "setprop ctl.start adbd; echo exit=${'$'}?", Host.Ssh) {
            captured("2026-10-01", "exit=0")
            note = "`${'$'}?` 是上一条命令的退出码，0 表示成功。adbd 的进程号前后未变，说明 init 确实忽略了该请求。"
        }
        text("""
            日志中没有找到任何记录：这台机器的 init 不记录控制消息，而 `logcat -b all` 全量搜索又因缓冲区过大而超时。**此方法无效。**
            **调整方案：** 不再查日志，改为设计一个结果可直接观察的实验：写入一个无害的调试属性 `debug.z6x.test`。在正常（Enforcing）的安卓系统上，普通应用无权写入 debug 类属性；若此处能够写入，即证明 SELinux 没有拦截。
        """)
        change("以应用身份写入调试属性", "setprop debug.z6x.test 1; echo exit=${'$'}?; getprop debug.z6x.test", Host.Ssh) {
            captured("2026-10-01", """
                exit=0
                1
            """)
            note = """
                写入成功。实验后用 `setprop debug.z6x.test ""` 清空。
                **意外发现：** setprop 无法删除属性，只能清空其值。此后 `getprop` 列出全部属性时仍有一行 `[debug.z6x.test]: []`，直到重启才消失（debug.* 不会持久化；2026-10-01 重启后确认已消失）。
                「核查：真实型号与芯片」中用 `grep z6x` 搜索属性时多出的正是这一行，由 `--try-read` 发现。
            """
            outcome = "证实：在这台机器上，普通应用可以设置本应被 SELinux 拦截的属性。"
        }
    }

    consequences {
        text("""
            • **重启后 adbd 会自动运行**（2026-10-01 实测：重启后 `ro.boottime.adbd` 约为 6.6 秒，ADB 可直接连接）。原因是极米启动配置中的一段补丁，见「核查：ADB 开机自动运行的原因」。
            • 但只要 adbd 被停止一次，自动运行即失效，需要再从 SSH 启动。因此**不要卸载** SimpleSSHD。
            • `ro.adb.secure=0` 意味着同一局域网内的**任何人**都能连接并获得 shell 权限，不要将投影仪置于不可信的网络中。
            • 系统 OTA 升级可能将 SELinux 改回 Enforcing，届时此方法将失效。「系统定制」中停用 OTA 也有这一考虑。
        """)
    }

    audit("旧记录核对（2026-10-01）") {
        claim("排查系统底层属性时，发现极米系统已经将底层网络 ADB 的配置预埋好了：service.adb.tcp.port 属性值预设为 5555；ro.adb.secure 属性值为 0（免除了弹出 RSA 密钥授权对话框的步骤）；只需在 SimpleSSHD shell 中向系统发送属性指令启动 adbd 守护进程，即可直接对外开放 5555 调试端口。", Verdict.Confirmed,
            "两个属性值均正确；应用身份确实能设置这类属性（见 debug 属性实验）。旧记录**未提及** SELinux 处于 Permissive，而这才是成功的前提。唯一未重现的是「在 adbd 未运行时将其启动」这一步：adbd 当前正在运行，为此停止它会断开 ADB，得不偿失。")
        change("旧版：触发系统启动 adbd 的另一种写法", "setprop service.adb.tcp.port 5555 && setprop ctl.start adbd", Host.Ssh) {
            verdict = Verdict.Unverified
            note = "前半句多余：service.adb.tcp.port 本来就是 5555。效果与 `setprop ctl.start adbd` 相同。"
        }
        change("旧版：极米特定私有属性", "setprop xgimi.remoteDebug.on 1", Host.Ssh) {
            verdict = Verdict.Unverified
            note = "该属性存在，当前值为 `false`。ADB 并非通过它开启（按当时的记录，是在 SSH 中执行命令启动的）。设为 1 的效果未测试；它可能与极米远程调试应用 com.xgimi.remote 有关。"
        }
        claim("调试接口：网络 ADB 端口 5555（默认开放且无需授权指纹）（硬件摸底记录）；需要在 SSH 里强开（强开 ADB 记录）", Verdict.Disproved,
            "两处记录互相矛盾。准确的说法是：端口和免授权是**预设**的，但 adbd 默认**不运行**，需要手动启动。")
    }

    related("find-real-model")
}
