using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class IncompatibleAppCleanData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "incompatible-app-clean",
        Title = "14. 非TV适配与异常包清理（Aurora Store / Aptoide TV 卸载）",
        Group = "深度定制",
        Summary = "卸载因 GMS 缺失及大屏排版冲突导致异常的外部商店与客户端，确立 ADB 与局域网共享安装机制。",
        Sections =
        [
            new ContentSection
            {
                Heading = "组件功能定位与清理依据",
                Text = "卸载包具体分析：",
                BulletPoints =
                [
                    "`com.aurora.store`：第三方 Google Play 客户端。因极米固件无 GMS 框架且电视端横屏布局冲突，运行数秒即抛出空指针闪退。",
                    "`cm.aptoidetv.pt`：Aptoide TV 商店。虽有大屏 UI，但其软件库未做无 GMS 设备过滤，分发的海外应用普遍依赖 Google 框架，导致实机可用度极低。",
                    "`com.google.android.apps.youtube.music`：官方 YouTube Music。强依赖 Google Play 服务组件，无 GMS 环境下无法完成 SSL 握手直接退出。"
                ]
            },
            new ContentSection
            {
                Heading = "执行指令与状态验证",
                Text = "执行卸载指令并核对当前保留的纯净第三方软件清单：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "卸载异常与无用第三方包",
                        Code = "adb uninstall com.aurora.store && \\\n" +
                               "adb uninstall cm.aptoidetv.pt && \\\n" +
                               "adb uninstall com.google.android.apps.youtube.music",
                        ExpectedOutput = "Success\nSuccess\nSuccess"
                    },
                    new CodeBlock
                    {
                        Label = "核查当前保留的第三方应用清单",
                        Code = "adb shell pm list packages -3",
                        ExpectedOutput = "package:de.szalkowski.activitylauncher.oss  # 活动启动器\n" +
                                         "package:com.spocky.projengmenu            # Projectivy Launcher\n" +
                                         "package:org.smarttube.stable              # SmartTube\n" +
                                         "package:com.phlox.tvwebbrowser            # TV 网页浏览器\n" +
                                         "package:com.cxinventor.file.explorer      # CX 文件浏览器\n" +
                                         "package:org.galexander.sshd               # SimpleSSHD\n" +
                                         "package:com.github.metacubex.clash.meta   # Clash Meta"
                    }
                ]
            }
        ]
    };
}
