using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class AdbScreencapDiagnosticData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "adb-screencap-diagnostic",
        Title = "8. 实机无侵入截屏与焦点诊断（exec-out screencap / dumpsys window）",
        Group = "深度定制",
        Summary = "通过 ADB 管道直接在主机端无损捕获投影仪当前物理渲染画面，并定位弹窗所属组件。",
        Sections =
        [
            new ContentSection
            {
                Heading = "管道截屏实测指令（无需设备端中转）",
                Text = "无需在投影仪内部保存临时图片，直接通过管道拉取标准 PNG 二进制流保存到主机：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "从投影仪实时抓取当前屏幕至主机",
                        Code = "adb exec-out screencap -p > /home/deck/Games/agy/projector_screen.png",
                        ExpectedOutput = "# 执行后主机本地直接生成 1080p 无损 PNG 图像，耗时约 800ms"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "定位屏幕弹窗所属组件（排查非广告弹窗）",
                Text = "当画面出现悬浮窗时，通过 WindowManager 抓取实际所有者，防止误判：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "检查当前持焦窗口与顶层 Activity",
                        Code = "dumpsys window | grep -E \"mCurrentFocus|mFocusedApp\"",
                        ExpectedOutput =
                            "mCurrentFocus=Window{... u0 trapezoidShift}                                        # 当前焦点在梯形校正/快捷设置悬浮窗\n" +
                            "mFocusedApp=ActivityRecord{... u0 com.xgimi.home/...HomeActivity t2}              # 底层前台桌面仍为极米官方桌面"
                    },
                    new CodeBlock
                    {
                        Label = "发送遥控器返回键关闭悬浮面板",
                        Code = "input keyevent 4   # 模拟按下遥控器返回键",
                        ExpectedOutput = "# 快捷设置悬浮窗关闭，画面切回主桌面"
                    }
                ]
            }
        ]
    };
}
