using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class InputSensorsEventReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "input-sensors-event-reference",
        Title = "28. 输入事件总线与遥控按键扫描码（getevent -S / event13 / AccSensor）",
        Group = "设备接入",
        Summary = "实测极米 Z6X Pro 全机 15 个输入设备节点、遥控器键值捕获与机身重力传感器总线。",
        Sections =
        [
            new ContentSection
            {
                Heading = "输入设备节点拓扑清单（getevent -S）",
                Text = "实测列出极米投影仪内核注册的输入总线驱动：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "枚举所有输入事件设备节点与驱动名称",
                        Code = "getevent -S",
                        ExpectedOutput =
                            "add device 1: /dev/input/event14\n" +
                            "  name:     \"XGIMI RC Consumer Control\"   # 蓝牙遥控器媒体/音量控制通道\n" +
                            "add device 2: /dev/input/event13\n" +
                            "  name:     \"XGIMI RC Keyboard\"           # 蓝牙遥控器主按键（导航/确定/主页/返回）\n" +
                            "add device 4: /dev/input/event11\n" +
                            "  name:     \"XGIMI KEYPAD\"                # 机身实体电源/快捷按键\n" +
                            "add device 5: /dev/input/event10\n" +
                            "  name:     \"Virtual AccSensor\"           # 虚拟重力加速度计（用于跌落保护与倾斜校准）\n" +
                            "add device 10: /dev/input/event0\n" +
                            "  name:     \"HL keyboard\"                 # MT9669 (黄龙) SoC 内核键盘驱动"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "实时捕获按键扫描码与时延（getevent -lt）",
                Text = "排查遥控器按键无响应或双击抖动问题：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "实时监听遥控器导航事件流（以 event13 为例）",
                        Code = "getevent -lt /dev/input/event13",
                        ExpectedOutput =
                            "[     520.124500] EV_KEY       KEY_UP               DOWN   # 向上按键按下\n" +
                            "[     520.210400] EV_KEY       KEY_UP               UP     # 向上按键释放（按压时延约 86ms）\n" +
                            "[     521.050100] EV_KEY       KEY_ENTER            DOWN   # 确定键按下"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`/dev/input/event*` 字符设备节点属于 `root:input` 权限：",
                    "SSH（UID 10068）：无读写权限，执行 `getevent` 报 `could not open /dev/input/event*: Permission denied`。",
                    "ADB（UID 2000）：拥有 `input` 组辅助权限，可以自由读取输入流并调用 `input` 模拟按键。"
                ]
            }
        ]
    };
}
