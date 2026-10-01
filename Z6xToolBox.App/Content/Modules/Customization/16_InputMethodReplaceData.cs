using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Customization;

public static class InputMethodReplaceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "input-method-replace",
        Title = "16. 电视输入法兼容性排坑与原厂按键管道恢复",
        Group = "深度定制",
        Summary = "记录替换第三方电视输入法引发遥控器方向键延迟的排坑过程，实测恢复原厂输入法以保障蓝牙键控低延迟。",
        Sections =
        [
            new ContentSection
            {
                Heading = "第三方输入法引发遥控卡顿排坑",
                Text = "部署 LeanKeyboard 并停用搜狗输入法后，遥控器操作出现明显顿挫感：",
                BulletPoints =
                [
                    "按键分发管道挂起：在 Android 12 TV 框架下，第三方 IME 挂载后，遥控器方向键（DPAD）与确认键均需优先经过 InputMethodService 进行文本框焦点探测与按键拦截，引入了额外的分发延迟。",
                    "厂商驱动依赖：极米蓝牙遥控器协议栈（com.xgimi.remote 与 duertts）与原厂预装的搜狗输入法存在专属通信挂钩，替换为普通 AOSP 键盘后破坏了硬件按键的高优先级调度。"
                ]
            },
            new ContentSection
            {
                Heading = "恢复原厂输入法与卸载指令",
                Text = "卸载 LeanKeyboard 并恢复搜狗输入法作为底层键控载体，实测遥控响应即刻恢复正常：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "恢复搜狗输入法并设为默认",
                        Code = "adb shell pm enable com.sohu.inputmethod.sogou.tv && \\\n" +
                               "adb shell ime enable com.sohu.inputmethod.sogou.tv/.SogouIME && \\\n" +
                               "adb shell ime set com.sohu.inputmethod.sogou.tv/.SogouIME",
                        ExpectedOutput = "Package com.sohu.inputmethod.sogou.tv new state: enabled\n" +
                                         "Input method ... selected for user #0"
                    },
                    new CodeBlock
                    {
                        Label = "卸载第三方输入法避免后台干扰",
                        Code = "adb uninstall org.liskovsoft.androidtv.rukeyboard",
                        ExpectedOutput = "Success"
                    }
                ]
            }
        ]
    };
}
