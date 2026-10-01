using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class PropertyCommandReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "property-command-reference",
        Title = "11. 系统属性 getprop 与 setprop 解析",
        Group = "设备接入",
        Summary = "深入解析 Android init 系统属性机制、命名空间规则（ro/persist/ctl）及在电视强开调试中的实战用法。",
        Sections =
        [
            new ContentSection
            {
                Heading = "系统属性机制与权限边界",
                Text = "Android 系统的属性服务由根进程 init 提供，底层使用共享内存（__system_property_area__）实现跨进程全局读取：\n\n" +
                       "• 读取属性（getprop）：在 SSH（UID 10068）与 ADB（UID 2000）下均可直接执行，几乎所有公开属性均对普通用户开放读取。\n" +
                       "• 写入属性（setprop）：受到 SELinux property_contexts 策略严格限制。普通应用沙箱只能写入系统未加保护的特定控制属性（如 ctl.start adbd、极米预埋的 xgimi.remoteDebug.on），大部分 persist.* 与 ro.* 属性禁止被普通应用修改。"
            },
            new ContentSection
            {
                Heading = "属性命名空间与前缀分类字典",
                Text = "通过前缀判断属性的生命周期与用途：",
                BulletPoints =
                [
                    "ro.*（Read Only，只读属性）：开机前由 Bootloader 或 init 在挂载阶段确定，写入后全局锁定，任何用户包括 root 均不可在运行时 setprop 修改（如 ro.product.cpu.abi、ro.board.platform）。",
                    "persist.*（Persistent，持久化属性）：写入后系统会自动同步写到 /data/property 目录持久化，断电重启后修改仍然保留（如 persist.sys.timezone）。",
                    "ctl.*（Control，服务控制属性）：向其写入服务名称通知 init 启动、终止或重启对应的底层原生守护进程（如 ctl.start adbd、ctl.restart adbd）。",
                    "service.* 与 sys.*（运行时状态属性）：记录系统当前网络服务与硬件运行状态，断电重启后重置（如 service.adb.tcp.port）。",
                    "厂商私有定制属性：极米自行添加的硬件与业务控制开关（如 xgimi.remoteDebug.on）。"
                ]
            },
            new ContentSection
            {
                Heading = "极米 Z6X Pro 核心硬件与系统属性全景表",
                Text = "实测提取的关键属性字典与注释说明：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "芯片平台与底层架构类属性",
                        Code = "getprop ro.board.platform && getprop ro.product.cpu.abi && getprop ro.product.cpu.abilist",
                        ExpectedOutput =
                            "huanglong                   # 主板平台代号（联发科 MT9669）\n" +
                            "armeabi-v7a                 # 当前系统默认的主 CPU ABI（32 位）\n" +
                            "armeabi-v7a,armeabi         # 系统支持运行的完整 ABI 列表（仅支持 32 位，无 arm64-v8a）"
                    },
                    new CodeBlock
                    {
                        Label = "系统版本与固件编译属性",
                        Code = "getprop ro.build.version.release && getprop ro.build.version.sdk && getprop ro.build.display.id",
                        ExpectedOutput =
                            "12                          # Android 12 系统版本\n" +
                            "31                          # API Level 31\n" +
                            "GMUI_...                    # 极米深度定制系统版本标识"
                    },
                    new CodeBlock
                    {
                        Label = "调试与网络监听控制属性",
                        Code = "getprop service.adb.tcp.port && getprop ro.adb.secure && getprop xgimi.remoteDebug.on",
                        ExpectedOutput =
                            "5555                        # 网络 ADB 默认监听端口\n" +
                            "0                           # 未开启 ADB RSA 证书校验弹窗限制（免弹窗直接连接）\n" +
                            "0                           # 极米私有远程调试开关（当前关闭）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "setprop 实战：强开网络 ADB 完整逻辑",
                Text = "利用 init 的 ctl 服务控制属性激活后台网络调试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "在 SSH 终端执行强开指令",
                        Code = "setprop service.adb.tcp.port 5555 && setprop ctl.start adbd\n" +
                               "# 极米私有属性备用:\n" +
                               "setprop xgimi.remoteDebug.on 1",
                        ExpectedOutput = "（无报错输出，init 进程在后台拉起 adbd 并在 5555 端口监听）"
                    },
                    new CodeBlock
                    {
                        Label = "重启 adbd 守护进程（若网络连接中断卡死）",
                        Code = "setprop ctl.restart adbd"
                    }
                ]
            }
        ]
    };
}
