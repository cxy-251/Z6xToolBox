using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class WifiSupplicantStateReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "wifi-supplicant-state-reference",
        Title = "29. Wi-Fi网络状态机与握手流程（dumpsys wifi / wpa_supplicant）",
        Group = "设备接入",
        Summary = "实测 Wi-Fi 状态机（AUTHENTICATING/ASSOCIATING/HANDSHAKE）握手生命周期与链路参数。",
        Sections =
        [
            new ContentSection
            {
                Heading = "Wi-Fi 状态机握手日志（dumpsys wifi）",
                Text = "实测排查无线网络连不上或频繁断线认证超时：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 Wi-Fi Supplicant 连接握手状态机流转",
                        Code = "dumpsys wifi | grep -iE \"SUPPLICANT_STATE_CHANGE_EVENT\" | tail -n 6",
                        ExpectedOutput =
                            "what=SUPPLICANT_STATE_CHANGE_EVENT state: AUTHENTICATING   # 阶段1：向 AP 发起 802.11 认证\n" +
                            "what=SUPPLICANT_STATE_CHANGE_EVENT state: ASSOCIATING        # 阶段2：向 AP 请求关联\n" +
                            "what=SUPPLICANT_STATE_CHANGE_EVENT state: ASSOCIATED         # 阶段3：关联成功\n" +
                            "what=SUPPLICANT_STATE_CHANGE_EVENT state: FOUR_WAY_HANDSHAKE # 阶段4：WPA2/WPA3 四次密钥握手\n" +
                            "what=SUPPLICANT_STATE_CHANGE_EVENT state: GROUP_HANDSHAKE    # 阶段5：组播密钥握手\n" +
                            "what=SUPPLICANT_STATE_CHANGE_EVENT state: COMPLETED          # 阶段6：连接建立完成，准备获取 DHCP IP"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "无线网卡实时状态与快速开关",
                Text = "网络异常时通过命令行软复位 Wi-Fi 芯片：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询当前 Wi-Fi 芯片开关状态",
                        Code = "cmd wifi status",
                        ExpectedOutput = "Wifi is enabled   # Wi-Fi 射频已启用"
                    },
                    new CodeBlock
                    {
                        Label = "重置 Wi-Fi 连接（断开并重连）",
                        Code = "cmd wifi set-wifi-enabled disabled && sleep 2 && cmd wifi set-wifi-enabled enabled",
                        ExpectedOutput = "# 无输出表示执行成功，无线网卡已软重启并重新触发握手"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`dumpsys wifi`：SSH（UID 10068）受限，输出不完整；ADB 拥有完整 dumpsys 访问权。",
                    "`cmd wifi set-wifi-enabled`：SSH 执行报权限不足；ADB 具备管理无线网络的权限。"
                ]
            }
        ]
    };
}
