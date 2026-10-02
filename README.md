# 📽 Z6xToolBox

极米 Z6X Pro 投影仪与安卓手机的实践手册和工具箱。整个项目由三部分组成：

| 部分 | 语言 | 作用 |
| --- | --- | --- |
| 🧰 桌面工具箱（`composeApp/`） | Kotlin + Compose Multiplatform | 汇集接管投影仪、改造手机的全部经验、命令和实测结果；可直接连接设备读取信息、一键体检、部署 hub |
| 🛰 z6x-hub（`hub/`） | Go | 运行在投影仪和手机上的常驻服务：文件管理与 WebDAV、资源库（网页游戏、漫画、多联放映、音声、小说）、任务管理、遥控器按键、SSH 等 |
| 🔧 z6x-tools（`tools/`） | Rust | BusyBox 式命令集 `z6x`：系统指标、端口所属、按键注入、遥控器重映射守护、查重、目录监听、ping、存储测速、进程守护 |

知识库的原则：

- 每条命令都标明**执行位置**（Deck 终端 / 投影仪 ADB / 手机 ADB / Termux / 电视界面）和**风险等级**（只读 / 可撤销的修改 / 难以撤销），可一键复制。
- 命令的输出均来自真机实际运行，并注明日期，没有编造的「预期输出」。
- 网上流传或旧记录中的说法，逐条在真机上核对，标注为 ✓ 成立、? 未验证或 ✗ 不成立，并附实测结果；走过的弯路与失误也如实记录。

## 内容结构

| 专区 | 分类 |
| --- | --- |
| 📖 实践记录 | 设备接入 · 获取权限（SSH、网络 ADB）· 系统定制（精简预装、桌面、输入法、代理、投屏、遥控器按键重映射）· 开发环境（自编译程序、开机自启）· 应急与安全 |
| 🔍 设备查询 | 原理（权限模型）· 查看工具 · 命令手册 · 核查案例 · 经验（方案调整记录） |
| 📱 手机 | 设备信息与参数获取 · 环境配置（无线调试、Termux、hub 运行方式、大量文件传输）· 系统精简（两个空间）· 游戏移植原理 |
| 💡 提案 | 规格（z6x-hub、z6x-tools）· 小项目审核（109 个提案）· 开发辅助 |
| 🛠 本项目技术栈 | 环境搭建（JDK、Gradle）· 代码解读（Kotlin、Compose）· 项目维护 |

## 下载

在 [Releases](https://github.com/cxy-251/Z6xToolBox/releases) 页面下载：

- **桌面工具箱**（自带 Java 运行时）：Linux / Steam Deck 用 `.deb` 或 `.tar.gz`；Windows 用 `.msi`（不需要管理员权限）或 `-portable.zip`；macOS（Apple Silicon）用 `.dmg`，未经 Apple 公证，首次打开需在「系统设置 → 隐私与安全性」中允许。
- **安卓端程序**：`z6x-android-arm64.tar.gz`（多数设备）或 `z6x-android-arm.tar.gz`（32 位用户空间），内含 z6x-hub、z6x、配置示例与启动脚本，用法见包内 README.txt。

部署 hub、改键、连接设备等功能需要从源码运行（依赖仓库中的脚本与 Go 工具链）；下载的安装包可以完整阅读知识库。

## 从源码运行

```bash
./run.sh                 # 打开工具箱
./run.sh --health        # 不打开窗口，在命令行中体检投影仪（必检项异常时退出码为 1）
./run.sh --device        # 启动后直接打开「📡 设备」页
./run.sh --check         # 知识库静态检查：风险标注、格式、是否泄露敏感信息
./run.sh --try-read      # 在真机上重新运行只读命令，与记录的输出对比
```

需要 JDK 21（Deck 上放在 `~/Applications/jdk`，见「本项目技术栈 → 在 Deck 上安装 JDK」）。首次运行会下载 Gradle 和依赖。

窗口顶栏的「🛰 投影仪 hub」「🛰 手机 hub」可一键打开两台设备的 hub 网页并自动登录。

## z6x-hub

投影仪与手机运行同一个程序，按各自的配置（`hub/devices/<设备名>.yaml`，含 token，不入库）启用不同模块：

- **投影仪**（ADB 方式，shell 身份）：文件共享、发送文字、遥控、网络唤醒、系统状态、测速、通知、局域网扫描、任务管理、遥控器按键、SSH（8022）。投影仪本机的浏览器访问免 token。
- **手机**（Termux 方式，普通应用身份）：资源库（与 omni-deck 的资源库目录结构兼容）、文件共享、系统状态、测速；只在家里的 Wi-Fi 上对外服务。

```bash
./hub/deploy.sh projector              # 编译并部署（首次部署时生成配置与随机 token）
./hub/ctl.sh phone start|stop|status   # 开关
./hub/library-import.sh <文件夹> games.slg   # 向手机资源库导入一个游戏
```

**投影仪重启后自动恢复**：Termux:Boot 经本机 ADB（127.0.0.1:5555）以 shell 身份启动 hub 与 keymap，开机约 70 秒后可用，不需要 Deck。详见「实践记录 → 开发环境 → 开机自动启动 hub 与 keymap」与「提案 → 规格 → 规格：z6x-hub」。

## z6x-tools

```bash
./tools/build.sh test                  # 单元测试
./tools/build.sh deploy projector      # 编译并推送到投影仪
./tools/keymap.sh projector            # 推送遥控器按键配置并启动 keymap 守护进程
```

编译参数为 Steam Deck 调整过：最低优先级（nice 19、ionice idle）、并行数 4、只依赖 libc，首次编译约 8 秒，程序约 650KB。遥控器的四个影视快捷键与调焦键两侧的两个键可在 hub 的「遥控器按键」页面设置短按与长按（共 12 项），原理见「实践记录 → 系统定制 → 遥控器按键重映射」。

## 发布新版本

1. 修改仓库根目录的 `VERSION`（工具箱、hub、z6x-tools 都使用它）。**只有第二位变化（x.Y.0）才发布**，第三位的修订不发布。
2. 提交后打标签并推送：`git tag v<版本号> && git push origin main --tags`。

推送标签后由 GitHub Actions 在 Linux、Windows、macOS 上构建工具箱，在 Linux 上交叉编译安卓端程序，生成校验值并创建 Release。平时推送代码不触发任何构建。

## 故障排查

| 现象 | 参考 |
| --- | --- |
| ADB 无法连接 | 「实践记录 → 应急与安全 → 应急恢复手册」：重启投影仪即可（开机脚本会先启动 adbd），或在 Termux 中执行 `/system/bin/setprop ctl.start adbd` |
| 精简系统后需要恢复 | 投影仪：`scripts/z6x_debloat_restore.sh`；手机：`scripts/phone_debloat.sh restore` |
| 手机 hub 被系统结束或冻结 | 「手机 → 环境配置 → hub 改在 Termux 中运行」：省电策略设为「无限制」，不持有唤醒锁 |
| 后台 Java 进程占用大量内存 | 「本项目技术栈 → 项目维护 → 问题记录」；执行 `./gradlew --stop` 可立即释放 |

## 其他

- 最初的 Avalonia（C#）版本已删除，其内容均已整理进来；如有需要，可从 git 标签 `avalonia-baseline` 取回。
- 网页终端内嵌的 xterm.js 以 MIT 许可证发布，见 `hub/internal/modules/webshell/assets/LICENSE-xterm.txt`。
- 不要将 token、代理节点、订阅链接、序列号、MAC 地址等个人信息写入仓库；`--check` 会检查常见的几类。
