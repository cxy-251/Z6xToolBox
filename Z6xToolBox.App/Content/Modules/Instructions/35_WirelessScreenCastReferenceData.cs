using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class WirelessScreenCastReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "wireless-screen-cast-reference",
        Title = "35. 极米无线投屏协议与网络端口（wirelessscreen / 8080端口）",
        Group = "设备接入",
        Summary = "实测极米无线投屏协议栈（DLNA/AirPlay/Miracast）、网络监听端口与投屏调试指令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "投屏核心服务与网络监听端口实测",
                Text = "实测极米系统运行的投屏服务包名与监听端口：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看投屏包名与网络端口状态",
                        Code = "pm list packages | grep wirelessscreen && netstat -tlpn | grep 8080",
                        ExpectedOutput =
                            "package:com.xgimi.wirelessscreen   # 极米原厂投屏主服务（集成乐播/自研协议）\n" +
                            "tcp6       0      0 [::]:8080               [::]:*                  LISTEN      # 投屏控制与媒体协商服务端口"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "投屏媒体会话状态（dumpsys media_session）",
                Text = "排查投屏时黑屏、仅有声音或音画不同步问题：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前激活的投屏流媒体会话",
                        Code = "dumpsys media_session | grep -iE \"com.xgimi.wirelessscreen|state=PlaybackState\"",
                        ExpectedOutput = "package=com.xgimi.wirelessscreen\nstate=PlaybackState {state=3, position=0}   # state=3 表示正在播放投屏媒体流"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`netstat -tlpn`：SSH 与 ADB 均可列出监听端口；但 SSH 无法看到非本 UID 进程的 PID/Program name 列。",
                    "`dumpsys media_session`：SSH 无 DUMP 权限；ADB 拥有完整查询权。"
                ]
            }
        ]
    };
}
