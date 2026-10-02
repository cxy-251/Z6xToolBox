package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val SshKeyLogin = module("ssh-key-login", "SimpleSSHD 公钥登录（历史记录）") {
    keywords = "ssh-keygen · authorized_keys · dropbear · ~/.ssh/config"
    overview = """
        **历史记录：SimpleSSHD 已于 2026-10-02 卸载**，SSH 改由 hub 内置（端口 8022，shell 身份，见「以 shell 身份运行 SSH 服务」），恢复 ADB 的后路改由 Termux 承担。以下内容保留当时的做法与结论。
        SimpleSSHD 默认每次登录都需要输入一次性密码，操作繁琐，也无法让脚本自动登录。
        将 Deck 的公钥登记到 SimpleSSHD 后，执行 `ssh z6x` 即可直接登录。过程中曾因密钥类型不受支持而无法登录。
    """
    verified("2026-10-01")

    why {
        text("""
            • **公钥登录**：Deck 生成一对密钥，私钥保留在 Deck 上，公钥交给服务端并登记在 `authorized_keys` 文件中。登录时服务端用公钥验证 Deck 确实持有私钥，无需密码。
            • SimpleSSHD 内部运行的是 **dropbear**（一个小型 SSH 服务端）。`authorized_keys` 位于其私有目录中，只有它自己（uid 10068）可以写入。
            • **该文件一旦存在，SimpleSSHD 即关闭一次性密码登录**，只接受公钥。
        """)
    }

    steps("生成并登记密钥") {
        change("生成一对 ECDSA 密钥", "ssh-keygen -t ecdsa -b 256 -f ~/.ssh/z6x_ecdsa -N \"\" -C \"deck->z6x simplesshd\"", Host.Deck) {
            note = """
                `-t ecdsa -b 256` 指定密钥类型和长度；`-f` 指定文件名（为这台设备单独使用一把密钥，与其他密钥分开）；`-N ""` 不设口令；`-C` 为备注。
                **为何不用更常见的 ed25519：** 见下文「问题：ed25519 密钥不受支持」。
            """
            outcome = "生成 ~/.ssh/z6x_ecdsa（私钥，切勿外传）和 ~/.ssh/z6x_ecdsa.pub（公钥）。"
        }
        change("为投影仪设置别名", """
            cat >> ~/.ssh/config <<'EOF'

            Host z6x
                HostName 192.168.0.109
                Port 2222
                IdentityFile ~/.ssh/z6x_ecdsa
                IdentitiesOnly yes
            EOF
        """, Host.Deck) {
            note = """
                此后 `ssh z6x` 即等同于「用这把密钥登录 192.168.0.109 的 2222 端口」。
                `IdentitiesOnly yes` 表示只使用指定的密钥，而不逐一尝试其他密钥（尝试次数过多时服务端会断开连接）。
                `<<'EOF'` 是 here-document：把直到 EOF 的若干行原样写入文件；EOF 带引号时，其中的 ~ 和 ${'$'} 不会被展开。
            """
        }
        change("用一次性密码登录一次并登记公钥", "ssh -o StrictHostKeyChecking=accept-new z6x 'cat >> /data/user/0/org.galexander.sshd/files/authorized_keys && chmod 600 /data/user/0/org.galexander.sshd/files/authorized_keys && echo OK' < ~/.ssh/z6x_ecdsa.pub", Host.Deck) {
            note = """
                先在电视上打开 SimpleSSHD 并点击 Start。提示 password 时，输入 SimpleSSHD 界面上显示的一次性密码。
                `< ~/.ssh/z6x_ecdsa.pub` 将公钥内容送入远端命令的标准输入，`cat >>` 将其追加到 authorized_keys。
                单引号中的命令在**电视上**执行；需要 `chmod 600`，是因为 dropbear 拒绝使用其他用户可写的 authorized_keys。
                `StrictHostKeyChecking=accept-new` 表示首次连接时自动记录服务端指纹，不再询问。
                此步骤需要输入密码，须在 Konsole 中手动执行。
            """
            outcome = "输出 OK。此后 SimpleSSHD 只接受这把密钥。"
        }
        read("验证公钥登录", "ssh -o BatchMode=yes z6x id", Host.Deck) {
            captured("2026-10-01", "uid=10068(u0_a68) gid=10068(u0_a68) groups=10068(u0_a68),3003(inet),9997(everybody),20068(u0_a68_cache),50068(all_a68) context=u:r:untrusted_app_27:s0:c68,c256,c512,c768")
            note = """
                `BatchMode=yes` 禁止任何交互提示：密钥无效时直接失败，而不会停在密码输入处。脚本中的自动登录都应加上此选项。
                输出表明 SSH 中的身份是 **uid 10068**，SELinux 域为 **untrusted_app**，即普通第三方应用。与 ADB 的 uid 2000（shell）的对比见「SSH 的权限边界」。
            """
        }
    }

    story("问题：ed25519 密钥不受支持，导致无法登录") {
        text("""
            最初使用的是 `ssh-keygen -t ed25519`（目前最常推荐的类型）。公钥登记成功后，登录却被拒绝。
            用 `ssh -v` 查看握手过程，找到了原因：
        """)
        read("查看握手细节", "ssh -v -o BatchMode=yes z6x true 2>&1 | grep -iE 'remote software|offering|can continue|denied'", Host.Deck) {
            manual = true
            note = "`-v` 输出调试信息，`2>&1` 将其从标准错误合并到标准输出，以便 grep 过滤。以下是当时使用 ed25519 密钥时的输出；改用 ECDSA 后重新执行会显示登录成功。"
            captured("2026-10-01", """
                debug1: Remote protocol version 2.0, remote software version dropbear_2019.78
                debug1: Authentications that can continue: publickey
                debug1: Offering public key: /home/deck/.ssh/z6x_ed25519 ED25519 SHA256:… explicit
                debug1: Authentications that can continue: publickey
                deck@192.168.0.109: Permission denied (publickey).
            """)
        }
        text("""
            • 服务端是 **dropbear 2019.78**，而 dropbear 从 **2020.79** 起才支持 ed25519，因此无法识别这把密钥。
            • `Authentications that can continue: publickey` 表明密码登录已关闭（因为 authorized_keys 已存在）。
            • 结果：密钥不被接受，密码登录又已关闭，**完全无法登录**。而 authorized_keys 位于 SimpleSSHD 的私有目录中，ADB 的 shell（uid 2000）也无权修改。
        """)
        danger("清除 SimpleSSHD 的数据，恢复密码登录", "pm clear org.galexander.sshd", Host.Adb) {
            captured("2026-10-01", "Success")
            note = "删除该应用的全部私有数据：authorized_keys、主机密钥和设置。应用本身保留，重新打开后恢复默认设置（端口 2222），可再次使用一次性密码登录。"
        }
        change("删除旧的服务端指纹", "ssh-keygen -R \"[192.168.0.109]:2222\"", Host.Deck) {
            note = "清除数据后 SimpleSSHD 会生成新的主机密钥。Deck 的 known_hosts 中仍保存着旧指纹，若不删除，ssh 会警告「主机身份已改变」并拒绝连接。非 22 端口需写成 `[IP]:端口` 的形式。"
        }
        text("随后改用 ECDSA 密钥重新登记，一次成功。")
    }

    consequences("重启以后") {
        text("""
            • 公钥登记**重启后仍然有效**：authorized_keys 保存在 SimpleSSHD 的私有目录中（2026-10-01 重启后验证）。
            • 但 SimpleSSHD 的服务**不会自动启动**：重启后需要在电视上打开它并点击 Start，`ssh z6x` 才能连接；未启动时报 `Connection refused`。
            • 在本项目中，SSH 是 ADB 的备用通道。ADB 在重启后会自动运行，日常使用 ADB 即可。
        """)
    }

    lesson("经验") {
        text("""
            • **先确认对方的软件版本，再选择方案。** `ssh -v` 的第一行即显示服务端版本。旧设备和嵌入式设备上的 SSH 服务端版本往往较旧。
            • 在修改可能导致无法登录的配置前，先准备好**恢复手段**。本次的恢复手段是 ADB 的 `pm clear`。
            • 用 `adb exec-out screencap -p > 文件.png` 可随时截取投影仪画面，查看 SimpleSSHD 的状态，无需走到电视前。见「投影仪截图」。
        """)
    }

    related("ssh-permission-wall", "tv-screencap", "force-adb")
}

val TvScreencap = module("tv-screencap", "投影仪截图") {
    keywords = "screencap · exec-out · 远程看画面"
    overview = "通过 ADB 将投影仪当前画面截取为 PNG 并保存到 Deck，无需走到投影仪前查看。"
    verified("2026-10-01")

    steps {
        change("截图保存到 Deck", "adb exec-out screencap -p > tv.png", Host.Deck) {
            note = """
                `screencap -p` 在投影仪上截屏并输出 PNG 数据；`exec-out` 使数据原样传回（`adb shell` 会转换换行符，导致图片损坏）；`>` 将其保存为 Deck 上的文件。
                连接多台设备时，加 `-s 192.168.0.109:5555` 指定设备。
            """
            outcome = "在当前目录生成 tv.png（1920x1080），即投影仪此刻显示的画面。"
        }
        read("确认图片正常", "file tv.png", Host.Deck) {
            manual = true
            captured("2026-10-01", "tv.png: PNG image data, 1920 x 1080, 8-bit/color RGBA, non-interlaced")
        }
    }

    consequences {
        text("""
            • 截图包含屏幕上的全部内容，包括一次性密码和账号信息。分享前应先检查。
            • 播放受版权保护（DRM）的视频时，截图可能为黑屏。
        """)
    }

    related("ssh-key-login")
}
