using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class OtaFirmwareBlockData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "ota-firmware-block",
        Title = "12. 固件在线升级与配件更新守护拦截（com.xgimi.upgrade / ota.*）",
        Group = "深度定制",
        Summary = "停用极米系统 OTA 在线升级与外设固件更新服务，防止自动升级覆盖系统定制配置或重置 ADB 状态。",
        Sections =
        [
            new ContentSection
            {
                Heading = "升级守护进程职责与拦截必要性",
                Text = "各更新包的具体功能分析：",
                BulletPoints =
                [
                    "`com.xgimi.upgrade`：极米系统固件 OTA 更新核心。周期性联网向官方服务器轮询新固件包、静默后台下载并弹出强制升级提示。升级可能覆写 system 分区并重置 pm disable 状态。",
                    "`com.xgimi.ota.accessories`：极米外设固件 OTA 更新组件。常驻后台轮询检测蓝牙遥控器、3D 眼镜等硬件的新固件，产生不必要的网络请求与唤醒锁。"
                ]
            },
            new ContentSection
            {
                Heading = "执行指令与状态验证",
                Text = "冻结升级服务并确认后台进程终止：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "停用系统与外设 OTA 升级服务",
                        Code = "adb shell pm disable-user --user 0 com.xgimi.upgrade && \\\n" +
                               "adb shell pm disable-user --user 0 com.xgimi.ota.accessories",
                        ExpectedOutput = "Package com.xgimi.upgrade new state: disabled-user\n" +
                                         "Package com.xgimi.ota.accessories new state: disabled-user"
                    },
                    new CodeBlock
                    {
                        Label = "强杀已驻留的 OTA 检查进程",
                        Code = "adb shell am force-stop com.xgimi.upgrade && \\\n" +
                               "adb shell am force-stop com.xgimi.ota.accessories",
                        ExpectedOutput = "# 进程退出，后台升级轮询完全终止"
                    }
                ]
            }
        ]
    };
}
