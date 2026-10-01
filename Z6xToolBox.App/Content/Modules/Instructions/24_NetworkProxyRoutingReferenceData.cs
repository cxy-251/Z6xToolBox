using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class NetworkProxyRoutingReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "network-proxy-routing-reference",
        Title = "24. 网络代理注入与策略路由表（http_proxy / ip rule / cacerts）",
        Group = "设备接入",
        Summary = "讲解全局 HTTP 代理命令行注入与恢复、多网络策略路由表查询与系统 CA 根证书位置。",
        Sections =
        [
            new ContentSection
            {
                Heading = "全局 HTTP 代理注入与撤销（settings put global）",
                Text = "无需在遥控器 Wi-Fi 高级设置中手动输入 IP 和端口，直接通过命令行配置全局代理：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "设置全局代理为本地抓包或网关代理",
                        Code = "settings put global http_proxy 192.168.0.10:7890\n# 验证当前设置：\nsettings get global http_proxy",
                        ExpectedOutput = "192.168.0.10:7890   # 系统已将所有应用 HTTP/HTTPS 流量引导向该代理服务器"
                    },
                    new CodeBlock
                    {
                        Label = "清除全局代理恢复直连",
                        Code = "settings put global http_proxy :0",
                        ExpectedOutput = "# 无输出表示执行成功，全局代理已注销"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "策略路由与多网络分流（ip rule / ip route）",
                Text = "Android 使用多路由表（NetworkPolicy / VPN / Local）进行流量分发：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看系统策略路由规则链",
                        Code = "ip rule show",
                        ExpectedOutput =
                            "0:      from all lookup local\n" +
                            "10000:  from all fwmark 0xc0000/0xd0000 lookup legacy_system   # 系统基础网络转发标记\n" +
                            "13000:  from all fwmark 0x10063/0x1ffff lookup wlan0           # Wi-Fi 物理网卡分流表\n" +
                            "32000:  from all unreachable"
                    },
                    new CodeBlock
                    {
                        Label = "查看指定网卡（wlan0）的专用路由表",
                        Code = "ip route show table wlan0",
                        ExpectedOutput =
                            "default via 192.168.0.1 dev wlan0 proto static\n" +
                            "192.168.0.0/24 dev wlan0 proto kernel scope link src 192.168.0.109"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "系统 CA 证书信任与抓包排查（cacerts）",
                Text = "排查 HTTPS 抓包为何出现证书错误（SSLHandshakeException）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询系统内置的受信任根证书数量",
                        Code = "ls /system/etc/security/cacerts/ | wc -l",
                        ExpectedOutput = "138   # 系统级信任的公共 CA 证书数量（只读挂载于 /system 分区，应用层无法篡改）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`settings put global http_proxy`：SSH（UID 10068）无权写入全局设置（抛 SecurityException: Permission Denial: requires WRITE_SECURE_SETTINGS）；ADB（UID 2000）拥有完整权限。",
                    "`ip rule` 与 `ip route`：SSH 仅能只读查看部分非敏感路由规则；ADB 可查看全量策略路由表并配合 netd 调试。"
                ]
            }
        ]
    };
}
