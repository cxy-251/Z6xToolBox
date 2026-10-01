using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class AdbCommandReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "adb-command-reference",
        Title = "9. ADB 调试桥常用指令全解",
        Group = "设备接入",
        Summary = "系统讲解 ADB 工具架构机制与实用参数，包含设备连接、包管理、文件同步、logcat 闪退诊断、端口转发与按键文本直输。",
        Sections =
        [
            new ContentSection
            {
                Heading = "ADB 工具架构与执行环境",
                Text = "ADB 采用 C/S（Client-Server）三层架构：\n\n" +
                       "1. adb client（运行在 Steam Deck）：接收命令行指令；\n" +
                       "2. adb server（后台运行在 Steam Deck，端口 5037）：维护与电视的通信链路；\n" +
                       "3. adbd daemon（运行在极米电视）：监听 5555 端口，以 uid=2000(shell) 身份执行特权指令。\n\n" +
                       "注：adb 指令是在控制端（Deck/PC）执行并发送给电视，不能在电视自身的 SSH 终端内直接调用 adb，因为电视内部没有 adb 客户端，只有被控的 adbd 服务端。"
            },
            new ContentSection
            {
                Heading = "连接维护、状态核对与端口转发",
                Text = "网络调试链路管理：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "建立与断开网络调试连接",
                        Code = "adb connect 192.168.0.109:5555     # 发起连接\n" +
                               "adb disconnect 192.168.0.109:5555  # 主动释放连接\n" +
                               "adb kill-server && adb start-server # 通信卡死时重启本地 ADB 守护进程",
                        ExpectedOutput = "connected to 192.168.0.109:5555   # 握手成功状态"
                    },
                    new CodeBlock
                    {
                        Label = "查看已连接设备的详细硬件与传输信息",
                        Code = "adb devices -l",
                        ExpectedOutput = "192.168.0.109:5555   device product:huanglong model:Z6X_Pro device:huanglong transport_id:1   # huanglong 为 MT9669 平台代号"
                    },
                    new CodeBlock
                    {
                        Label = "端口转发与反向代理（forward / reverse）",
                        Code = "adb forward tcp:8080 tcp:8080   # 将 Deck 本地 8080 端口转发到电视 8080 端口\n" +
                               "adb reverse tcp:9090 tcp:9090   # 将电视请求的 9090 反向代理到 Deck 本地 9090 端口",
                        Note = "在电视需要访问 Deck 本地运行的代理或临时 Web 服务时非常有用。"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "应用部署、覆盖安装与文件双向同步",
                Text = "免 U 盘无线传输与安装维护：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "安装、降级或覆盖安装本地 APK",
                        Code = "adb install -r -d -t ./app.apk\n" +
                               "# 参数解析:\n" +
                               "# -r: 覆盖安装并保留应用既有数据和缓存\n" +
                               "# -d: 允许版本降级（低版本覆盖高版本）\n" +
                               "# -t: 允许安装标记为测试版（testOnly）的 APK\n" +
                               "# -g: 安装时自动授予该应用声明的所有运行时危险权限",
                        ExpectedOutput = "Performing Streamed Install\nSuccess"
                    },
                    new CodeBlock
                    {
                        Label = "文件推送到电视或拉取到本地（push / pull）",
                        Code = "adb push ./config.yaml /data/local/tmp/   # 上传文件到电视临时开放目录\n" +
                               "adb pull /sdcard/Download/app.apk ./      # 从电视存储下载文件到本地当前目录"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "实时日志排查、崩溃过滤与故障转储（logcat）",
                Text = "排查应用闪退、系统报错的核心工具：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "清空历史缓冲区并精准捕获崩溃与异常堆栈",
                        Code = "adb logcat -c && adb logcat -v time *:E | grep -iE \"AndroidRuntime|FATAL|Exception\"",
                        ExpectedOutput =
                            "FATAL EXCEPTION: main                                                  # 捕获主线程崩溃\n" +
                            "Process: com.v2ray.ang, PID: 1234                                      # 崩溃进程 PID 与包名\n" +
                            "android.content.ActivityNotFoundException: ... class ... VpnDialogs    # 报错根因：系统缺失 VpnDialogs 组件"
                    },
                    new CodeBlock
                    {
                        Label = "导出系统全量缓冲区日志或生成 Bugreport",
                        Code = "adb logcat -d -v threadtime > tv_system.log   # 导出全部日志到本地文件\n" +
                               "adb bugreport ./bugreport.zip                 # 转储完整系统级错误诊断压缩包"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "免遥控输入、截图与系统软重启控制",
                Text = "远程控制电视与快捷操作：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "直接向当前光标输入长字符串（解决遥控输入长配置痛点）",
                        Code = "adb shell input text \"https://my-token-url-here\"\n" +
                               "# 说明: 直接模拟键盘打字，将长文本注入当前获得焦点的输入框中，避免手动逐字按键"
                    },
                    new CodeBlock
                    {
                        Label = "模拟遥控器标准按键",
                        Code = "adb shell input keyevent 3     # 主页键（Home）\n" +
                               "adb shell input keyevent 4     # 返回键（Back）\n" +
                               "adb shell input keyevent 19    # 上（D-pad Up）\n" +
                               "adb shell input keyevent 20    # 下（D-pad Down）\n" +
                               "adb shell input keyevent 21    # 左（D-pad Left）\n" +
                               "adb shell input keyevent 22    # 右（D-pad Right）\n" +
                               "adb shell input keyevent 23    # 确认键（D-pad Center）\n" +
                               "adb shell input keyevent 82    # 菜单键（Menu）\n" +
                               "adb shell input keyevent 26    # 电源键（Power 休眠/唤醒）"
                    },
                    new CodeBlock
                    {
                        Label = "远程重启电视系统",
                        Code = "adb reboot             # 标准重启\n" +
                               "adb reboot recovery    # 重启进入 Recovery 恢复模式"
                    }
                ]
            }
        ]
    };
}
