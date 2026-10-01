using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class ProjectivyLauncherSetupData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "projectivy-launcher-setup",
        Title = "9. 开源桌面 Projectivy Launcher 部署与主页接管（com.spocky.projengmenu）",
        Group = "深度定制",
        Summary = "实测部署开源 TV 启动器 Projectivy Launcher，实机渲染自然壁纸与纯净应用网格，替换原厂桌面广告。",
        Sections =
        [
            new ContentSection
            {
                Heading = "安装与组件确认",
                Text = "实测下载 GitHub 开源 Release 并通过 ADB 安装：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "推送安装并查询主活动入口",
                        Code = "adb install -r ProjectivyLauncher-4.71.apk && \\\n" +
                               "cmd package resolve-activity --brief com.spocky.projengmenu",
                        ExpectedOutput =
                            "Success\n" +
                            "com.spocky.projengmenu/.ui.home.MainActivity"
                    },
                    new CodeBlock
                    {
                        Label = "启动 Projectivy Launcher 桌面",
                        Code = "am start -n com.spocky.projengmenu/.ui.home.MainActivity",
                        ExpectedOutput = "Starting: Intent { cmp=com.spocky.projengmenu/.ui.home.MainActivity }"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "Home 键接管与无障碍服务激活（UI 界面无法开启排坑）",
                Text = "极米 GMUI 裁切了原生无障碍设置页，导致在 Projectivy 界面内点击开启无障碍毫无反应。必须通过 ADB 命令行强制写入：",
                BulletPoints =
                [
                    "UI 无法开启根因：Projectivy 尝试调起 `android.settings.ACCESSIBILITY_SETTINGS`，极米系统无此活动组件，点击直接被静默忽略。",
                    "多服务共存机制：极米系统默认启用了 `com.xgimi.duertts` 语音服务，命令行写入时必须用冒号 `:` 连接多个服务，避免顶掉系统必要监听。"
                ],
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "命令行强制注入并开启 Projectivy 无障碍服务",
                        Code = "settings put secure enabled_accessibility_services \\\n" +
                               "com.xgimi.duertts/com.xgimi.duertts.MonitorService:com.spocky.projengmenu/com.spocky.projengmenu.services.ProjectivyAccessibilityService && \\\n" +
                               "settings put secure accessibility_enabled 1",
                        ExpectedOutput = "# 无障碍服务强制激活成功，遥控器 Home 键捕获就绪"
                    },
                    new CodeBlock
                    {
                        Label = "停用官方桌面（开机直达第三方桌面）",
                        Code = "pm disable-user --user 0 com.xgimi.home\n# 随时恢复原厂命令：\npm enable com.xgimi.home",
                        ExpectedOutput = "Package com.xgimi.home new state: disabled-user"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "账号安全与数据隔离结论",
                Text = "替换桌面并不影响任何已安装应用（SmartTube、Clash、芒果等）的登录状态：",
                BulletPoints =
                [
                    "桌面仅是系统 Intent 调用器，各应用的用户登录 Token、Session 保存在各自的 `/data/data/<package>/` 隔离沙盒内。",
                    "更换启动器只改变桌面图标入口的排列与渲染，不会清除任何应用私有数据。"
                ]
            }
        ]
    };
}
