using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class ClashLocalProxyData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "clash-local-proxy",
        Title = "6. 本地自启代理链路与流量分流（Clash Meta 7890端口）",
        Group = "深度定制",
        Summary = "实测 Clash Meta 后台常驻监听 [::]:7890 端口，避开 TV 端 VPN 弹窗崩溃的代理接入方案。",
        Sections =
        [
            new ContentSection
            {
                Heading = "Clash Meta 进程与端口实测",
                Text = "实测确认 `com.github.metacubex.clash.meta` 开机常驻并开放本地 HTTP/SOCKS 混入端口：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 Clash Meta 后台进程与监听端口",
                        Code = "ps -ef | grep -i metacubex && netstat -tlpn | grep 7890",
                        ExpectedOutput =
                            "u0_a69  7124  2665 3 19:34:05 ? 00:08:19 com.github.metacubex.clash.meta:background\n" +
                            "tcp6       0      0 [::]:7890               [::]:*                  LISTEN      -"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "避开 VpnDialogs 崩溃的代理接入策略",
                Text = "极米系统删减了 `VpnDialogs.apk`，导致应用内点击“启动 VPN (TUN 模式)”必定崩溃。稳定接入方案如下：",
                BulletPoints =
                [
                    "方案 1（第三方应用内独立代理）：如 SmartTube 或 TV Bro 内置网络设置直接填入代理地址 `127.0.0.1` 端口 `7890`，完全不调用系统 VPN 接口。",
                    "方案 2（命令行注入系统级 HTTP 代理）：通过 ADB 为系统全局注入本地代理，使原生 WebView 与流媒体应用透明过墙。"
                ],
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "设置系统全局 HTTP 代理指向本地 Clash 端口",
                        Code = "settings put global http_proxy 127.0.0.1:7890\n# 查看当前生效代理：\nsettings get global http_proxy",
                        ExpectedOutput = "127.0.0.1:7890"
                    },
                    new CodeBlock
                    {
                        Label = "清除全局代理恢复直连",
                        Code = "settings put global http_proxy :0",
                        ExpectedOutput = "# 执行成功无回显"
                    }
                ]
            }
        ]
    };
}
