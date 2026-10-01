using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class DisplayWindowDensityReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "display-window-density-reference",
        Title = "30. 窗口缩放调优与Android 12废弃指令排坑（wm density / overscan废弃）",
        Group = "设备接入",
        Summary = "讲解显示分辨率与 DPI 缩放微调、Android 12 废弃 wm overscan 实测排坑与恢复方法。",
        Sections =
        [
            new ContentSection
            {
                Heading = "物理分辨率与 DPI 缩放微调（wm density）",
                Text = "部分侧载的平板或手机应用在 TV 240 DPI 下控件过大或文字溢出时进行动态缩放：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看当前物理分辨率与屏幕密度",
                        Code = "wm size && wm density",
                        ExpectedOutput =
                            "Physical size: 1920x1080   # 极米物理输出分辨率为点对点 1080p\n" +
                            "Physical density: 240      # 原厂默认 DPI 240"
                    },
                    new CodeBlock
                    {
                        Label = "临时调整 DPI 为 210（增大显示区域）与恢复",
                        Code = "wm density 210\n# 恢复原厂默认值：\nwm density reset",
                        ExpectedOutput = "# 执行后界面无需重启即可即时重绘刷新"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Android 12 核心排坑：wm overscan 已被废弃",
                Text = "在旧版 Android（Android 9/10/11）中，常通过 `wm overscan` 裁剪投影仪边缘遮挡或过扫描，但在 Android 12（API 31）实测：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行 wm overscan 报错验证",
                        Code = "wm overscan 0,0,0,0",
                        ExpectedOutput = "Unknown command: overscan   # Google 在 Android 12 源码中移除了 overscan 接口"
                    }
                ],
                BulletPoints =
                [
                    "替代方案：投影仪画面切边或缩放必须依赖极米硬件级梯形校正/画面缩放（由 `IceSea` 守护进程驱动），无法再通过 Android 系统级 overscan 裁剪。"
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`wm density <value>`：SSH（UID 10068）调用会抛出 `SecurityException: Must hold permission android.permission.WRITE_SECURE_SETTINGS`；只有 ADB（UID 2000）拥有窗口管理器修改权限。"
                ]
            }
        ]
    };
}
