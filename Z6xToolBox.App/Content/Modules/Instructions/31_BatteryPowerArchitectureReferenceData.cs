using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class BatteryPowerArchitectureReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "battery-power-architecture-reference",
        Title = "31. 电源管理与虚拟电池状态探查（dumpsys battery / cmd battery）",
        Group = "设备接入",
        Summary = "讲解投影仪无内置锂电池硬件架构、虚拟电量探查与命令行供电状态仿真。",
        Sections =
        [
            new ContentSection
            {
                Heading = "电池服务状态实测（dumpsys battery）",
                Text = "实测表明极米 Z6X Pro 采用纯 DC 适配器供电，内核虚拟化了电量接口：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询电源服务当前参数",
                        Code = "dumpsys battery",
                        ExpectedOutput =
                            "Current Battery Service state:\n" +
                            "  AC powered: false   # 未接标准 Android AC 充电协议（由 19V 独立 DC 接口供电）\n" +
                            "  present: false      # 硬件物理电池不存在（无锂电安全隐患）\n" +
                            "  level: 42           # 虚拟电量默认固定为 42%\n" +
                            "  temperature: 424    # 电源芯片/主板传感器温度 42.4℃（单位：0.1℃）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "命令行伪造供电与电量（cmd battery）",
                Text = "部分侧载的手机应用检测到非充电或低电量会拒绝运行，可通过命令行强制伪装：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "伪装为交流电供电且满电状态",
                        Code = "cmd battery set ac 1 && cmd battery set level 100",
                        ExpectedOutput = "# 无输出表示执行成功，全局广播 ACTION_BATTERY_CHANGED"
                    },
                    new CodeBlock
                    {
                        Label = "恢复硬件真实检测状态",
                        Code = "cmd battery reset",
                        ExpectedOutput = "# 无输出表示重置成功，恢复默认虚报参数"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`dumpsys battery`：SSH（UID 10068）与 ADB 均可读取。",
                    "`cmd battery set/reset`：SSH 无权调用（SecurityException: Requires DUMP permission）；ADB（UID 2000）拥有完整控制权。"
                ]
            }
        ]
    };
}
