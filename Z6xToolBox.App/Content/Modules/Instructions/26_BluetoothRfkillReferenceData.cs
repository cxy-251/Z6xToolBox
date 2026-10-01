using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class BluetoothRfkillReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "bluetooth-rfkill-reference",
        Title = "26. 蓝牙协议栈与外设连接诊断（dumpsys bluetooth_manager / cmd）",
        Group = "设备接入",
        Summary = "讲解极米蓝牙服务堆栈状态、MAC 地址与广播名查询、配对设备列表及命令行控制。",
        Sections =
        [
            new ContentSection
            {
                Heading = "蓝牙管理器状态与射频参数（dumpsys bluetooth_manager）",
                Text = "查询蓝牙适配器开关状态、硬件 MAC 地址与当前对外广播名称：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看极米蓝牙芯片状态与硬件地址",
                        Code = "dumpsys bluetooth_manager | grep -iE \"enabled|name|address|bond\" | head -n 8",
                        ExpectedOutput =
                            "enabled: true   # 蓝牙射频当前已开启\n" +
                            "address: 9C:12:21:17:42:9D   # MT9669 蓝牙芯片物理 MAC 地址\n" +
                            "name: 我的Z6X Pro 三色激光 旗舰版   # 蓝牙广播设备名称\n" +
                            "Enabled due to APPLICATION_REQUEST by com.xgimi.bluetoothservice   # 由极米蓝牙系统服务拉起"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "命令行启停蓝牙适配器（cmd bluetooth_manager）",
                Text = "用于蓝牙遥控器突发假死或手柄断连时的远程硬件重置：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "重启系统蓝牙射频模块",
                        Code = "cmd bluetooth_manager disable && sleep 2 && cmd bluetooth_manager enable",
                        ExpectedOutput = "# 无输出表示执行成功，蓝牙驱动已软重启并重新重连配对遥控器"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "已配对设备拓扑查询",
                Text = "检查当前绑定的蓝牙遥控器、蓝牙音箱或手柄连接状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "列出所有已配对设备（Bonded devices）",
                        Code = "dumpsys bluetooth_manager | grep -A 5 \"Bonded devices:\"",
                        ExpectedOutput =
                            "Bonded devices:\n" +
                            "  04:79:B0:XX:XX:XX [XGIMI RC] (1)   # 极米原装红外/蓝牙双模语音遥控器（状态1: 已连接）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`dumpsys bluetooth_manager`：SSH（UID 10068）受限，输出不完整且无法查看敏感绑定设备列表；ADB（UID 2000）拥有 dumpsys 完整权限。",
                    "`cmd bluetooth_manager disable/enable`：SSH 执行报权限拒绝（SecurityException: Requires BLUETOOTH_ADMIN or BLUETOOTH_PRIVILEGED）；ADB 具备控制权。"
                ]
            }
        ]
    };
}
