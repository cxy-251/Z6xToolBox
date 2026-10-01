package z6x.content.records

import z6x.framework.Host
import z6x.framework.module

val Emergency = module("emergency", "应急手册：出事了怎么恢复") {
    keywords = "ADB 连不上 · 遥控器失灵 · 定制被还原 · 恢复出厂 · 重启后"
    overview = """
        把各篇里验证过的退路串成一页：先看是什么情况，再按步骤恢复。每条恢复手段都在 2026-10-01 实际用过或核实过。
    """
    verified("2026-10-01")

    story("情况 1：ADB 连不上") {
        text("按顺序排查，前一步通了就不用往下走：")
        read("投影仪还在网上吗", "ping -c 3 192.168.0.109", Host.Deck) {
            manual = true
            note = "不通：投影仪可能关机了，或者路由器给它换了 IP。IP 可以在电视上打开 SimpleSSHD 看（界面顶部列出所有 IP），或在路由器后台查。"
        }
        change("重启 Deck 端的 adb 再连", "adb kill-server && adb connect 192.168.0.109:5555", Host.Deck)
        read("5555 端口开着吗", "timeout 3 bash -c 'echo > /dev/tcp/192.168.0.109/5555' && echo 开 || echo 关", Host.Deck) {
            manual = true
            note = "网络通但端口关：说明 adbd 没在运行。注意：adbd 一旦停过，开机自启也会失效（见「案例：ADB 为什么开机就自动运行」）。"
        }
        change("从 SSH 重新拉起 adbd", "ssh z6x 'setprop ctl.start adbd'", Host.Deck) {
            note = "先在电视上打开 SimpleSSHD 点 Start。拉起后极米的补丁会把开机自启也恢复。原理见「在 SSH 里强开网络 ADB」。"
        }
        text("""
            **如果 SSH 也登不上：**
            • 报 `Connection refused`：SimpleSSHD 没点 Start。
            • 报 `Permission denied (publickey)`：Deck 上的私钥 `~/.ssh/z6x_ecdsa` 丢了或换了。SimpleSSHD 现在只接受这把钥匙（密码登录已自动关闭）。清掉 SimpleSSHD 的数据可以恢复密码登录，但这需要 ADB——**所以要备份这把私钥**（见文末）。
            • 两条路都断了：只能用遥控器在电视上操作，用 U 盘重装 SimpleSSHD（见「U 盘装 App」），重新登记公钥、重新强开 ADB。
        """)
    }

    story("情况 2：遥控器失灵") {
        change("用 ADB 代替遥控器", "adb shell input keyevent 3", Host.Deck) {
            note = "3 主页、4 返回、19~22 方向、23 确认。详见「屏幕上现在是谁」。"
        }
        change("重启蓝牙让遥控器重连", "cmd bluetooth_manager disable && sleep 2 && cmd bluetooth_manager enable", Host.Adb) {
            note = "遥控器会断开几秒，然后自动重连。"
        }
        text("遥控器换过输入法后变卡，见「输入法」：退回搜狗。")
    }

    story("情况 3：定制被还原（广告、官方桌面又回来了）") {
        read("看停用和卸载的数量还对不对", "pm list packages -d | wc -l; pm list packages -u | grep -vxF \"\$(pm list packages)\"", Host.Adb) {
            manual = true
            note = "正常是 29 个停用 + home、stream.video 两个卸载。少了说明被还原过（多半是系统升级）。"
        }
        change("重新应用定制", "./scripts/z6x_debloat_apply.sh 192.168.0.109:5555", Host.Deck) {
            note = "在项目根目录执行，可以重复运行。见「一键精简与恢复脚本」。系统升级后还要先确认 ADB 能连、SELinux 是否还是 Permissive。"
        }
    }

    story("情况 4：想回到原厂状态") {
        change("撤销全部定制", "./scripts/z6x_debloat_restore.sh 192.168.0.109:5555", Host.Deck) {
            note = "装回官方桌面和影视推荐、启用所有停用的组件。ADB 和 SSH 不受影响。"
        }
    }

    story("情况 5：恢复了出厂设置") {
        text("""
            恢复出厂会清掉一切：第三方 App、定制、ADB 自启（persist 属性）、/data/local/tmp 下的程序。按当初的顺序重来：
            1. U 盘装 TV Bro、SimpleSSHD（.apk1 改名法，见「U 盘装 App」）。
            2. SimpleSSHD 点 Start，用一次性密码登录，登记 Deck 的公钥（见「SSH 免密登录 SimpleSSHD」）。
            3. `ssh z6x 'setprop ctl.start adbd'` 强开 ADB（前提：SELinux 仍是 Permissive）。
            4. `./scripts/z6x_debloat_apply.sh` 重新精简，`adb install` 装回 Projectivy、Clash 等。
            5. 重新部署 BusyBox、Go 服务。
        """)
    }

    story("情况 6：投影仪重启以后") {
        text("""
            • ADB：自动可用，不用管（约开机 1 分钟后）。
            • SSH：在电视上打开 SimpleSSHD 点 Start。
            • 自己部署的服务（Go 测试服务、以后的 z6x-hub）：要重新从 ADB 启动。
            • 停用的 3 个常驻组件（hilink 等）会照样启动，属于已知问题，见「停用清单」。
        """)
        change("重新启动 Go 测试服务", "adb shell 'nohup /data/local/tmp/z6x_go_server > /data/local/tmp/go_server.log 2>&1 &'", Host.Deck)
    }

    story("情况 7：时间不对、HTTPS 报证书错误") {
        text("这台没有硬件时钟，断电后要联网对时。先确认 Wi-Fi 连着，等一会儿自动对时；或看「后台、唤醒、时间与其他」里的手动设置。")
    }

    consequences("该备份的东西") {
        text("""
            • **Deck 上的 `~/.ssh/z6x_ecdsa`**（SSH 私钥）：丢了就登不上 SimpleSSHD。
            • **本项目仓库**：脚本、知识库、Go 源码都在这里，git 有完整历史。
            • **shared 目录里的安装包**：恢复出厂后重装要用。
            • 投影仪本身没有需要备份的数据：定制可以用脚本重做。
        """)
    }

    related("force-adb", "adb-autostart", "ssh-key-login", "debloat-scripts", "security")
}

val Security = module("security", "安全：这台投影仪对局域网开放了什么") {
    keywords = "ADB 免授权 · SSH 公钥 · Clash 7890 · 节点信息 · 端口清单"
    overview = """
        为了方便折腾，这台投影仪在局域网里开了几扇门。在自己家的 Wi-Fi 里问题不大，但要知道门在哪、谁能进、怎么关。
    """
    verified("2026-10-01")

    story("开着的门") {
        facts(
            "ADB 5555" to "**免授权**（ro.adb.secure=0）。同一 Wi-Fi 下任何设备装个 adb 就能拿到 shell 权限：装卸应用、看文件、模拟按键。风险最高",
            "SSH 2222" to "只接受 Deck 那把 ECDSA 公钥，密码登录已自动关闭。较安全，前提是私钥不外泄",
            "Clash 7890 / 7891" to "监听所有地址，**从 Deck 能直接连上**（实测）：局域网里的其他设备都能借用你的代理节点和流量",
            "Go 测试服务 8088" to "只返回版本信息，无害；以后的 z6x-hub 规格要求带 token 鉴权",
            "系统组件端口" to "8080、7100、1458 等属于系统组件（uid 1000），见「案例：这个端口是谁开的」",
        )
    }

    steps("可以怎么收紧") {
        change("关掉 Clash 的局域网访问", "", Host.Tv) {
            note = "在 Clash Meta 的设置里找到类似「允许局域网连接」（allow-lan）的选项关掉（选项的确切名称没有核对）。投影仪自己的 VPN 不受影响。**没有替你改**，按需决定。"
        }
        danger("不用 ADB 时停掉它", "setprop ctl.stop adbd", Host.Ssh) {
            note = """
                **后果要想清楚**：停掉后极米补丁会把开机自启也关掉，再要用必须从 SSH 重新 `setprop ctl.start adbd`。
                在 ADB 里执行会立刻断开当前连接，所以写的是从 SSH 执行。只有投影仪要接入不信任的网络时才值得这么做。
            """
        }
        text("""
            • 路由器的「访客网络」和主网络隔离：客人连访客 Wi-Fi，就碰不到投影仪的这些端口。
            • 把投影仪带去别人家或公共网络时，先停掉 ADB（上面的命令），回家再开。
        """)
    }

    consequences("别泄露的东西") {
        text("""
            • shared 目录里的 `index.html`、`nodes.txt`、`clash.yaml` 含代理节点信息。用 `python3 -m http.server` 共享 shared 目录时，**局域网里谁都能打开**，用完立刻关。这些文件不在本仓库里，也不要拷进来。
            • 截图、日志里可能有 IP、Wi-Fi 名、MAC 地址、一次性密码，分享前先看一眼。
            • `~/.ssh/z6x_ecdsa` 是登录投影仪的钥匙，不要上传、不要发给别人。
        """)
    }

    related("emergency", "clash-proxy", "port-owner", "force-adb")
}
