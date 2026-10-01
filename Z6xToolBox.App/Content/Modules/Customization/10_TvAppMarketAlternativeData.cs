using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class TvAppMarketAlternativeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "tv-app-market-alternative",
        Title = "10. 免GMS电视应用市场选型与部署（Aptoide TV / F-Droid）",
        Group = "深度定制",
        Summary = "分析 Aurora Store 闪退根因，实测部署无 GMS 依赖、电视大屏遥控优化的第三方应用市场。",
        Sections =
        [
            new ContentSection
            {
                Heading = "Aurora Store 在极米投影仪闪退根因",
                Text = "经日志审计定位两项硬性限制：",
                BulletPoints =
                [
                    "GMS 库缺失：Aurora Store 依赖 Google Play Services 分发协议，极米底层完全阉割了 GMS 核心，通信时抛出空指针或 API 异常。",
                    "DPI 布局冲突：极米投影仪默认为 240 DPI，部分依赖手机竖屏特性的页面渲染时在 Android TV 宽屏上触发窗口测量崩溃。"
                ]
            },
            new ContentSection
            {
                Heading = "替代方案选型：Aptoide TV 与 F-Droid",
                Text = "两款经过 TV 环境验证的免 GMS 市场：",
                BulletPoints =
                [
                    "Aptoide TV：专为 Android TV 盒子和投影仪定制的大屏应用商店，所有卡片均经过遥控器导航优化，无需 Google 框架，收录海量电视与手机端常用软件。",
                    "F-Droid：开源安全市场，收录纯净无广告开源工具，完全与 GMS 解耦，适合安装网络与系统工具。"
                ],
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "Aptoide TV 安装与组件入口查询",
                        Code = "adb install -r aptoide_tv.apk && \\\n" +
                               "cmd package resolve-activity --brief cm.aptoidetv.pt",
                        ExpectedOutput = "Success\ncm.aptoidetv.pt/.activity.MainActivity"
                    },
                    new CodeBlock
                    {
                        Label = "启动 Aptoide TV 并验证前台焦点",
                        Code = "am start -n cm.aptoidetv.pt/.activity.MainActivity && \\\n" +
                               "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'",
                        ExpectedOutput = "Starting: Intent { cmp=cm.aptoidetv.pt/.activity.MainActivity }\n" +
                                         "mCurrentFocus=Window{... cm.aptoidetv.pt/cm.aptoidetv.pt.activity.MainActivity}\n" +
                                         "mFocusedApp=ActivityRecord{... cm.aptoidetv.pt/.activity.MainActivity ...}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "实测缺陷与最终卸载依据",
                Text = "实测从 Aptoide TV 下载安装 YouTube Music（com.google.android.apps.youtube.music），启动即闪退：",
                BulletPoints =
                [
                    "分发缺陷：Aptoide TV 缺少针对国内无 GMS 电视的兼容过滤，分发了大量强依赖 Google Play Services 的官方手机端应用。",
                    "日志排查证据：抓取 PID 8637 日志显示 `GooglePlayServices not available due to error 9` 与 `requires the Google Play Store, but it is missing`，因缺失 GMS 握手失败直接退出。",
                    "处理决策：聚合商店内大部分主流海外应用均无法正常运行，继续保留意义有限，选择卸载并转为 ADB 直推与局域网安装。"
                ],
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "卸载 Aptoide TV 及闪退测试应用",
                        Code = "adb uninstall cm.aptoidetv.pt && \\\n" +
                               "adb uninstall com.google.android.apps.youtube.music",
                        ExpectedOutput = "Success\nSuccess"
                    }
                ]
            }
        ]
    };
}
