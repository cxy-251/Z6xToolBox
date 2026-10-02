package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val Emergency = module("emergency", "应急恢复手册") {
    keywords = "ADB 连不上 · 遥控器失灵 · 定制被还原 · 恢复出厂 · 重启后"
    overview = """
        汇总各篇中已验证的恢复手段：先判断故障类型，再按步骤恢复。每种恢复手段都已于 2026-10-01 实际使用或核实。
        2026-10-02 更新：SimpleSSHD 已卸载，恢复 ADB 的后路改由投影仪上的 Termux 承担，hub 与 keymap 开机自动启动（见「开机自动启动 hub 与 keymap」）。下文保留原先使用 SimpleSSHD 的做法作为记录。
    """
    verified("2026-10-02")

    story("情况 1：ADB 无法连接") {
        text("按顺序排查，前一步恢复正常即可停止：")
        read("投影仪是否在线", "ping -c 3 192.168.0.109", Host.Deck) {
            manual = true
            note = "不通：投影仪可能已关机（睡眠），或路由器为其分配了新 IP。IP 可在路由器后台查询，或在电视上打开 Termux 执行 `/system/bin/ip -4 addr show wlan0`。（原先在 SimpleSSHD 界面顶部查看，该应用已于 2026-10-02 卸载。）"
        }
        change("重启 Deck 端的 adb 后重新连接", "adb kill-server && adb connect 192.168.0.109:5555", Host.Deck)
        read("5555 端口是否开放", "timeout 3 bash -c 'echo > /dev/tcp/192.168.0.109/5555' && echo 开 || echo 关", Host.Deck) {
            manual = true
            note = "网络可达但端口关闭：说明 adbd 未运行。注意：adbd 一旦停止，开机自动运行也会失效（见「核查：ADB 开机自动运行的原因」）。"
        }
        change("重启投影仪", "", Host.Tv) {
            note = "最简单的办法：Termux:Boot 的开机脚本每次先执行 `setprop ctl.start adbd`，adbd 被停止过也会重新启动。开机约 70 秒后 ADB、hub、keymap 都会恢复。"
        }
        change("不重启：在 Termux 中启动 adbd", "/system/bin/setprop ctl.start adbd", Host.Tv) {
            note = "在电视上打开 Termux，用遥控器输入这条命令（Termux 的 PATH 不含系统命令，需写完整路径）。原理与当初在 SimpleSSHD 中打开 ADB 相同，见「通过 SSH 启动网络 ADB」。"
        }
        text("""
            **原先的做法（SimpleSSHD，2026-10-02 已卸载）**：在电视上打开 SimpleSSHD 点击 Start，执行 `ssh z6x 'setprop ctl.start adbd'`。现在 `ssh z6x` 指向 hub 内置的 SSH（端口 8022），它由 hub 启动、依赖 ADB，ADB 失效时不能用来恢复 ADB。
            **如果 Termux 也不可用**（例如被卸载）：只能用遥控器在电视上操作，通过 U 盘重新安装 Termux（见「U 盘安装应用」），在其中执行上面的命令。
        """)
    }

    story("情况 2：遥控器失灵") {
        change("用 ADB 代替遥控器", "adb shell input keyevent 3", Host.Deck) {
            note = "3 为主页、4 为返回、19~22 为方向键、23 为确认。详见「查看当前焦点窗口」。"
        }
        change("重启蓝牙使遥控器重新连接", "cmd bluetooth_manager disable && sleep 2 && cmd bluetooth_manager enable", Host.Adb) {
            note = "遥控器会断开数秒，随后自动重新连接。"
        }
        text("更换输入法后遥控器迟滞，见「输入法：LeanKeyboard 替换失败，恢复搜狗」：恢复使用搜狗。")
    }

    story("情况 3：定制被还原（广告、官方桌面再次出现）") {
        read("检查停用和卸载的数量", "pm list packages -d | wc -l; pm list packages -u | grep -vxF \"\$(pm list packages)\"", Host.Adb) {
            manual = true
            note = "正常应为 29 个停用，加上 home、stream.video 两个卸载。数量减少说明已被还原（通常由系统升级导致）。"
        }
        change("重新应用定制", "./scripts/z6x_debloat_apply.sh 192.168.0.109:5555", Host.Deck) {
            note = "在项目根目录执行，可重复运行。见「一键精简与恢复脚本」。系统升级后，还需先确认 ADB 能否连接、SELinux 是否仍为 Permissive。"
        }
    }

    story("情况 4：想回到原厂状态") {
        change("撤销全部定制", "./scripts/z6x_debloat_restore.sh 192.168.0.109:5555", Host.Deck) {
            note = "重新安装官方桌面和影视推荐，启用所有已停用的组件。ADB、hub 与 Termux 不受影响。"
        }
    }

    story("情况 5：已恢复出厂设置") {
        text("""
            恢复出厂设置会清除所有内容：第三方应用、定制、ADB 自动运行（persist 属性）以及 /data/local/tmp 中的程序。按以下顺序重新操作：
            1. 通过 U 盘安装 Termux（改扩展名为 .apk1，见「U 盘安装应用」）。
            2. 在 Termux 中用遥控器输入 `/system/bin/setprop ctl.start adbd` 启动 ADB（前提：SELinux 仍为 Permissive）。
            3. 执行 `./scripts/z6x_debloat_apply.sh` 重新精简，用 `adb install` 重新安装 Projectivy、Clash、Kodi、Termux:Boot 等。
            4. 部署 hub 与 z6x-tools：`./hub/deploy.sh projector`、`./tools/build.sh deploy projector`、`./tools/keymap.sh projector --push-config`，再按「开机自动启动 hub 与 keymap」配置 Termux。
            （最初是先装 TV Bro 和 SimpleSSHD，在 SimpleSSHD 中登记公钥后执行 `ssh z6x 'setprop ctl.start adbd'`，见「SimpleSSHD 公钥登录」；SimpleSSHD 已于 2026-10-02 卸载。）
        """)
    }

    story("情况 6：投影仪重启之后") {
        text("""
            注意：电源菜单中的「关机」**并非**重启，而是睡眠，开机后一切原样恢复，无需任何操作（见「核查：「关屏」与「关机」的实际行为」）。以下针对选择「重启」或断电之后的情况：
        """)
        text("""
            • ADB、hub（含 SSH 8022）、keymap：开机约 70 秒后全部自动可用，无需处理（2026-10-02 实测，见「开机自动启动 hub 与 keymap」）。
            • 原先需要在电视上打开 SimpleSSHD 点 Start、在 Deck 上执行 `./hub/deploy.sh` 重新启动 hub，现已不需要。
            • 已停用的 3 个常驻组件（hilink 等）仍会启动，属于已知问题，见「停用清单」。
        """)
        change("重新启动 Go 测试服务", "adb shell 'nohup /data/local/tmp/z6x_go_server > /data/local/tmp/go_server.log 2>&1 &'", Host.Deck)
    }

    story("情况 7：时间错误、HTTPS 报证书错误") {
        text("本机没有硬件时钟，断电后需要联网对时。先确认 Wi-Fi 已连接，稍候即会自动对时；也可参考「后台任务、唤醒、时间与其他」中的手动设置方法。")
    }

    consequences("需要备份的内容") {
        text("""
            • **Deck 上的 `~/.ssh/z6x_ecdsa`**（SSH 私钥）：hub 内置 SSH 只接受它（原先用于 SimpleSSHD）。丢失后可在 hub 配置中更换公钥，不影响 ADB。
            • **Deck 上的 `~/.ssh/phone_ed25519`**：手机 Termux 的 SSH 私钥。
            • **`hub/devices/*.yaml`**：各设备的 hub 配置与 token（不入库，只在 Deck 上）。
            • **本项目仓库**：脚本、知识库和 Go 源码均在其中，git 保留完整历史。
            • **shared 目录中的安装包**：恢复出厂设置后重新安装时需要。
            • 投影仪本身没有需要备份的数据：定制可通过脚本重新完成。
        """)
    }

    related("force-adb", "adb-autostart", "ssh-key-login", "debloat-scripts", "security")
}

val Security = module("security", "安全检查：对局域网开放的服务") {
    keywords = "ADB 免授权 · SSH 公钥 · Clash 7890 · 节点信息 · 端口清单"
    overview = """
        为便于调试，这台投影仪在局域网中开放了若干服务。在家庭 Wi-Fi 中风险不大，但应清楚开放了哪些服务、谁能访问以及如何关闭。
    """
    verified("2026-10-01")

    story("开放的服务") {
        facts(
            "ADB 5555" to "**免授权**（ro.adb.secure=0）。同一 Wi-Fi 下的任何设备只要安装 adb 即可获得 shell 权限：安装卸载应用、查看文件、模拟按键。风险最高",
            "SSH 8022（hub 内置）" to "shell 身份，只接受配置中列出的公钥（Deck 的 ECDSA 公钥），不支持密码；只在局域网地址上监听。原先的 SimpleSSHD（2222）已于 2026-10-02 卸载",
            "z6x-hub 8090 / 8091" to "需要 token；投影仪开启了 trust_local，投影仪本机的浏览器免 token（保存配置除外）",
            "Clash 7890 / 7891" to "监听所有地址，**从 Deck 可以直接连接**（实测）：局域网内的其他设备都能使用这台投影仪的代理节点和流量",
            "Go 测试服务 8088" to "只返回版本信息，无害；z6x-hub 的规格要求使用 token 鉴权",
            "系统组件端口" to "8080、7100、1458 等属于系统组件（uid 1000），见「核查：端口的所属进程」",
        )
    }

    steps("收紧措施") {
        change("关闭 Clash 的局域网访问", "", Host.Tv) {
            note = "在 Clash Meta 的设置中找到「允许局域网连接」（allow-lan）一类的选项并关闭（选项的确切名称未核对）。投影仪自身的 VPN 不受影响。**此项未擅自修改**，请按需决定。"
        }
        danger("不使用 ADB 时将其停止", "setprop ctl.stop adbd", Host.Ssh) {
            note = """
                **务必考虑后果**：停止后，极米补丁会同时关闭开机自动运行，再次使用时必须从 SSH 重新执行 `setprop ctl.start adbd`。
                在 ADB 中执行会立即断开当前连接，因此应从 SSH 执行。仅在投影仪需要接入不可信网络时才值得这样做。
            """
        }
        text("""
            • 路由器的「访客网络」与主网络隔离：访客连接访客 Wi-Fi 后，无法访问投影仪的这些端口。
            • 将投影仪带到他人家中或公共网络时，先停止 ADB（上述命令），回家后再启动。
        """)
    }

    consequences("不应泄露的信息") {
        text("""
            • shared 目录中的 `index.html`、`nodes.txt`、`clash.yaml` 包含代理节点信息。用 `python3 -m http.server` 共享 shared 目录时，**局域网内任何人都能打开**，用完应立即关闭。这些文件不在本仓库中，也不应复制进来。
            • 截图和日志中可能包含 IP、Wi-Fi 名称、MAC 地址和一次性密码，分享前应先检查。
            • `~/.ssh/z6x_ecdsa` 是登录投影仪的密钥，不要上传，也不要发送给他人。
        """)
    }

    related("emergency", "clash-proxy", "port-owner", "force-adb")
}
