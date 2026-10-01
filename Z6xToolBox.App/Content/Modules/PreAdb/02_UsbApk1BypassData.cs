using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class UsbApk1BypassData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "usb-apk1-bypass",
        Title = "2. U 盘安装限制与 .apk1 改名绕过",
        Group = "设备接入",
        Summary = "解释极米文件管理器拦截 APK 的原理，使用 .apk1 改名绕过机制，并提供单文件与批量改名工具命令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "为什么要将安装包重命名为 .apk1",
                Text = "极米系统自带的文件管理器（GMUI 资源管理器）设置了安全白名单限制：\n\n" +
                       "1. 直接点击 .apk 文件时，文件管理器会拦截安装意图，弹出提示“禁止安装未知来源应用”或直接无响应。\n" +
                       "2. 若将文件名后缀改为 .apk1（例如 TVBro.apk -> TVBro.apk1），文件管理器无法根据扩展名识别该文件类型，会将其判定为未知文件。\n" +
                       "3. 点击该未知文件时，系统会弹出“打开方式”对话框，此时选择系统的“软件包安装程序（PackageInstaller）”，系统安装器便会读取文件头（PK 压缩包结构并包含 AndroidManifest.xml），正常进入应用安装确认界面，从而绕过文件管理器的拦截。",
                BulletPoints =
                [
                    "U 盘格式要求：建议格式化为 FAT32 或 exFAT，避免 NTFS 权限问题导致投影仪无法识别。",
                    "安装后清理：安装完成后可在电视上直接删除 .apk1 文件释放内部空间，或通过文件管理器清理安装包缓存。"
                ]
            },
            new ContentSection
            {
                Heading = "Linux / Steam Deck 下改名命令",
                Text = "在 Steam Deck 上准备 U 盘文件时使用终端命令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "单个 APK 文件改名",
                        Code = "mv my_app.apk my_app.apk1",
                        ExpectedOutput = "（文件后缀重命名为 .apk1）"
                    },
                    new CodeBlock
                    {
                        Label = "批量将当前目录下所有 .apk 改为 .apk1",
                        Code = "for f in *.apk; do [ -f \"$f\" ] && mv -- \"$f\" \"${f}1\"; done",
                        ExpectedOutput = "（目录下所有 app.apk 变为 app.apk1）",
                        Note = "若需要批量还原回 .apk，执行：for f in *.apk1; do [ -f \"$f\" ] && mv -- \"$f\" \"${f%.apk1}.apk\"; done"
                    },
                    new CodeBlock
                    {
                        Label = "查看 U 盘挂载路径与写入状态",
                        Code = "lsblk -f && sync",
                        ExpectedOutput = "sdb1  vfat  /run/media/deck/...   # 确认为 FAT32/exFAT 并在拔盘前 sync 刷盘"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Windows 系统下改名命令备用",
                Text = "若在 Windows 电脑上制作 U 盘安装盘：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "Windows CMD 批量重命名为 .apk1",
                        Code = "ren *.apk *.apk1",
                        ExpectedOutput = "（批量更改完成）",
                        Note = "若要批量还原回 apk，在 CMD 中执行：ren *.apk1 *.apk"
                    },
                    new CodeBlock
                    {
                        Label = "Windows PowerShell 批量改名",
                        Code = "Get-ChildItem *.apk | Rename-Item -NewName { $_.Name + '1' }"
                    }
                ]
            }
        ]
    };
}
