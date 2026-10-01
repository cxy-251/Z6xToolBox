package z6x.content.inspect

import z6x.framework.Host
import z6x.framework.module

val SshKeyLogin = module("ssh-key-login", "SimpleSSHD 公钥登录") {
    keywords = "ssh-keygen · authorized_keys · dropbear · ~/.ssh/config"
    overview = """
        SimpleSSHD 默认每次登录都要输入一个一次性密码，很麻烦，也没法让脚本自动登录。
        把 Deck 的公钥登记到 SimpleSSHD 之后，`ssh z6x` 直接登录。过程中踩了一个坑：密钥类型不被支持，把自己锁在了门外。
    """
    verified("2026-10-01")

    why {
        text("""
            • **公钥登录**：Deck 生成一对密钥，私钥留在 Deck，公钥交给服务端，登记在 `authorized_keys` 文件里。登录时服务端用公钥验证 Deck 确实持有私钥，不需要密码。
            • SimpleSSHD 内部跑的是 **dropbear**（一个小型 SSH 服务端）。它把 `authorized_keys` 放在自己的私有目录里，只有它自己（uid 10068）能写。
            • **一旦这个文件存在，SimpleSSHD 就关掉一次性密码登录**，只接受公钥。
        """)
    }

    steps("生成密钥并登记") {
        change("生成一对 ECDSA 密钥", "ssh-keygen -t ecdsa -b 256 -f ~/.ssh/z6x_ecdsa -N \"\" -C \"deck->z6x simplesshd\"", Host.Deck) {
            note = """
                `-t ecdsa -b 256` 密钥类型和长度；`-f` 文件名（专门给这台设备用一把，和其他密钥分开）；`-N ""` 不设密钥口令；`-C` 备注。
                **为什么不用更常见的 ed25519：** 见下面的"踩坑"。
            """
            outcome = "生成 ~/.ssh/z6x_ecdsa（私钥，不要外传）和 ~/.ssh/z6x_ecdsa.pub（公钥）。"
        }
        change("给投影仪起个别名", """
            cat >> ~/.ssh/config <<'EOF'

            Host z6x
                HostName 192.168.0.109
                Port 2222
                IdentityFile ~/.ssh/z6x_ecdsa
                IdentitiesOnly yes
            EOF
        """, Host.Deck) {
            note = """
                之后 `ssh z6x` 就等于"用这把密钥登录 192.168.0.109 的 2222 端口"。
                `IdentitiesOnly yes` 只用指定的这把密钥，不把其他密钥挨个试一遍（试多了服务端会断开）。
                `<<'EOF'` 是 here-document：把到 EOF 为止的几行原样写进文件；EOF 加了引号，里面的 ~ 和 ${'$'} 不会被展开。
            """
        }
        change("用一次性密码登录一次，把公钥登记上", "ssh -o StrictHostKeyChecking=accept-new z6x 'cat >> /data/user/0/org.galexander.sshd/files/authorized_keys && chmod 600 /data/user/0/org.galexander.sshd/files/authorized_keys && echo OK' < ~/.ssh/z6x_ecdsa.pub", Host.Deck) {
            note = """
                先在电视上打开 SimpleSSHD 点 Start。提示 password 时输入 SimpleSSHD 界面上的一次性密码。
                `< ~/.ssh/z6x_ecdsa.pub` 把公钥内容送进远端命令的标准输入，`cat >>` 把它追加到 authorized_keys。
                单引号里的命令在**电视上**执行；`chmod 600` 是因为 dropbear 拒绝使用别人也能写的 authorized_keys。
                `StrictHostKeyChecking=accept-new` 第一次连接时自动记下服务端指纹，不再询问。
                这一步需要输入密码，要在 Konsole 里手动执行。
            """
            outcome = "打印 OK。从此 SimpleSSHD 只接受这把密钥。"
        }
        read("验证免密登录", "ssh -o BatchMode=yes z6x id", Host.Deck) {
            captured("2026-10-01", "uid=10068(u0_a68) gid=10068(u0_a68) groups=10068(u0_a68),3003(inet),9997(everybody),20068(u0_a68_cache),50068(all_a68) context=u:r:untrusted_app_27:s0:c68,c256,c512,c768")
            note = """
                `BatchMode=yes` 禁止任何交互提示：密钥不行就直接失败，不会卡在等密码。脚本里自动登录都应该加上。
                输出说明 SSH 里的身份是 **uid 10068**、SELinux 域是 **untrusted_app**，也就是一个普通第三方 App。和 ADB 的 uid 2000（shell）对比，见「SSH 的权限边界」。
            """
        }
    }

    story("踩坑：ed25519 密钥不被认，把自己锁在门外") {
        text("""
            第一次用的是 `ssh-keygen -t ed25519`（现在最常推荐的类型）。公钥登记成功后，登录却被拒绝。
            用 `ssh -v` 看握手过程，找到了原因：
        """)
        read("查看握手细节", "ssh -v -o BatchMode=yes z6x true 2>&1 | grep -iE 'remote software|offering|can continue|denied'", Host.Deck) {
            manual = true
            note = "`-v` 打印调试信息，`2>&1` 把它从错误输出并到标准输出，才能被 grep 过滤。这是当时（用 ed25519 密钥时）的输出，现在改用 ECDSA 后再跑会显示登录成功。"
            captured("2026-10-01", """
                debug1: Remote protocol version 2.0, remote software version dropbear_2019.78
                debug1: Authentications that can continue: publickey
                debug1: Offering public key: /home/deck/.ssh/z6x_ed25519 ED25519 SHA256:… explicit
                debug1: Authentications that can continue: publickey
                deck@192.168.0.109: Permission denied (publickey).
            """)
        }
        text("""
            • 服务端是 **dropbear 2019.78**，而 dropbear 从 **2020.79** 才支持 ed25519，所以它不认这把密钥。
            • `Authentications that can continue: publickey` 说明密码登录已经关了（因为 authorized_keys 已经存在）。
            • 结果：密钥不认、密码又关了，**彻底登不进去**。而 authorized_keys 在 SimpleSSHD 的私有目录里，ADB 的 shell（uid 2000）也没权限去改。
        """)
        danger("清除 SimpleSSHD 的数据，恢复密码登录", "pm clear org.galexander.sshd", Host.Adb) {
            captured("2026-10-01", "Success")
            note = "删除这个 App 的全部私有数据：authorized_keys、主机密钥、设置。App 本身还在，重新打开会恢复默认设置（端口 2222），又能用一次性密码登录了。"
        }
        change("忘掉旧的服务端指纹", "ssh-keygen -R \"[192.168.0.109]:2222\"", Host.Deck) {
            note = "清数据后 SimpleSSHD 会生成新的主机密钥。Deck 的 known_hosts 里还记着旧指纹，不删的话 ssh 会警告「主机身份变了」并拒绝连接。非 22 端口要写成 `[IP]:端口` 的形式。"
        }
        text("之后换成 ECDSA 密钥重新登记，一次成功。")
    }

    consequences("重启以后") {
        text("""
            • 公钥登记**重启后依然有效**：authorized_keys 存在 SimpleSSHD 的私有目录里（2026-10-01 重启后验证）。
            • 但 SimpleSSHD 的服务**不会自动启动**：重启后要在电视上打开它点 Start，`ssh z6x` 才能连上。没点 Start 时报 `Connection refused`。
            • SSH 在这个项目里是 ADB 的备用通道。ADB 重启后会自动运行，日常用 ADB 就够了。
        """)
    }

    lesson("经验") {
        text("""
            • **先看对方的软件版本，再选方案。** `ssh -v` 第一行就能看到服务端版本。老设备、嵌入式设备上的 SSH 服务端往往很旧。
            • 对"只能登一次"的系统做改动前，先想好**退路**。这次的退路是 ADB 的 `pm clear`。
            • 用 `adb exec-out screencap -p > 文件.png` 可以随时截取投影仪画面，看 SimpleSSHD 的状态，不用跑到电视前面。见「给投影仪截图」。
        """)
    }

    related("ssh-permission-wall", "tv-screencap", "force-adb")
}

val TvScreencap = module("tv-screencap", "投影仪截图") {
    keywords = "screencap · exec-out · 远程看画面"
    overview = "通过 ADB 把投影仪当前画面截成 PNG 存到 Deck 上，不用走到投影仪前面看。"
    verified("2026-10-01")

    steps {
        change("截图保存到 Deck", "adb exec-out screencap -p > tv.png", Host.Deck) {
            note = """
                `screencap -p` 在投影仪上截屏并输出 PNG 数据；`exec-out` 让数据原样传回（`adb shell` 会把换行符转换掉，图片会损坏）；`>` 存成 Deck 上的文件。
                有多台设备时加 `-s 192.168.0.109:5555` 指定。
            """
            outcome = "当前目录下生成 tv.png，1920x1080，就是投影仪此刻显示的画面。"
        }
        read("确认图片正常", "file tv.png", Host.Deck) {
            manual = true
            captured("2026-10-01", "tv.png: PNG image data, 1920 x 1080, 8-bit/color RGBA, non-interlaced")
        }
    }

    consequences {
        text("""
            • 截图会包含屏幕上的一切，包括一次性密码、账号信息。分享截图前先检查。
            • 播放有版权保护（DRM）的视频时，截出来可能是黑的。
        """)
    }

    related("ssh-key-login")
}
