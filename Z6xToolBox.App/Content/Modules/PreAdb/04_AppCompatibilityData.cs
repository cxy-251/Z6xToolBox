using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class AppCompatibilityData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "app-compatibility",
        Title = "4. 应用安装顺序与实机兼容排查",
        Group = "设备接入",
        Summary = "记录实际测试过的 APK 安装顺序、各应用安装与闪退原因分析，以及 32 位环境限制排查。",
        Sections =
        [
            new ContentSection
            {
                Heading = "实测应用安装顺序与目的",
                Text = "在未开启 ADB 调试阶段，应用安装按照以下先后顺序进行尝试：\n\n" +
                       "1. 第一步：TV Bro 浏览器\n" +
                       "   • 目的：解决遥控器没有鼠标指针、电视无法复制长文本、免去频繁插拔 U 盘的问题。\n" +
                       "   • 状态：安装成功，运行正常。长按遥控器菜单键可呼出虚拟鼠标指针，并支持从局域网下载 APK。\n\n" +
                       "2. 第二步：SimpleSSHD\n" +
                       "   • 目的：在电视端启动一个 Dropbear SSH 服务，获取 Linux Shell，查看系统内部硬件与属性。\n" +
                       "   • 状态：安装成功，监听 2222 端口，Steam Deck 可通过终端直接远程登录。\n\n" +
                       "3. 第三步：v2rayNG（闪退）\n" +
                       "   • 目的：配置网络代理。\n" +
                       "   • 状态：安装成功，但点击启动连接时瞬间闪退。\n" +
                       "   • 闪退原因：极米系统在精简系统组件时，删除了 Android 原生的 VpnDialogs（VPN 权限授权确认弹窗）应用。v2rayNG 尝试调用系统 VpnService.prepare() 弹出授权窗口时，系统找不到该 Activity 抛出异常崩溃。\n" +
                       "   • 解决方法：放弃使用依赖系统 VpnDialogs 的轻量客户端，改用 ClashMetaforAndroid。\n\n" +
                       "4. 第四步：ClashMetaforAndroid（替代方案）\n" +
                       "   • 目的：替代 v2rayNG 实现代理与分流。\n" +
                       "   • 状态：安装成功，正常运行。分流规则中需将局域网段设为 DIRECT 直连，避免内网访问失败。\n\n" +
                       "5. 第五步：Aurora Store（闪退）\n" +
                       "   • 目的：安装第三方 Google Play 客户端以便更新软件。\n" +
                       "   • 状态：安装成功，但启动后白屏转圈随后闪退崩溃。\n" +
                       "   • 闪退原因：系统完全缺少 GMS（Google 移动服务）核心框架，且 Z6X Pro 的 240 DPI 密度导致手机/平板版布局计算溢出崩溃。\n" +
                       "   • 解决方法：电视端直接通过 TV Bro 局域网下载或 ADB 安装，不依赖应用商店。\n\n" +
                       "6. 第六步：测试安装纯 64 位 APK（无法安装）\n" +
                       "   • 目的：验证系统是否支持纯 64 位架构应用。\n" +
                       "   • 状态：系统安装器直接报错 INSTALL_FAILED_NO_MATCHING_ABIS。\n" +
                       "   • 原因：极米虽然使用了 64 位 Linux 内核（armv8l），但系统运行库全部裁剪为 32 位（armeabi-v7a），无法加载仅包含 arm64-v8a 原生库的 APK。\n\n" +
                       "7. 第七步：Projectivy Launcher 与 SmartTube\n" +
                       "   • 目的：替换自带广告桌面，安装无广告视频客户端。\n" +
                       "   • 状态：安装成功，运行流畅无异常。"
            },
            new ContentSection
            {
                Heading = "本地核对 APK 架构方法",
                Text = "为避免再次踩坑纯 64 位包，在 Deck 端下载 APK 后可预先检查压缩包内包含的动态库：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 APK 支持的 CPU 架构",
                        Code = "unzip -l app.apk | grep -i \"lib/\"",
                        ExpectedOutput =
                            "lib/armeabi-v7a/libnative.so   # 包含 32 位库，可在 Z6X Pro 正常运行\n" +
                            "# 若只有 lib/arm64-v8a/ 则无法在此设备安装",
                        Note = "下载 APK 时务必选择 armeabi-v7a 或 universal（通用）版本。"
                    }
                ]
            }
        ]
    };
}
