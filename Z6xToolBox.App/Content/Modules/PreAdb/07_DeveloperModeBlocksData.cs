using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class DeveloperModeBlocksData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "developer-mode-blocks",
        Title = "7. 寻找 ADB 入口碰壁（遥控按键与工厂模式）",
        Group = "设备接入",
        Summary = "记录在开启 ADB 之前尝试过的遥控器按键偏方、系统设置翻查、工厂模式寻找入口等失败过程。",
        Sections =
        [
            new ContentSection
            {
                Heading = "尝试 1：遥控器按键组合（偏方失效）",
                Text = "为了开启调试，尝试了网络上流传的各种极米遥控器按键方案：\n\n" +
                       "• 尝试在系统“关于本机”界面连续按确定键 5 到 7 次；\n" +
                       "• 尝试按方向键“左键、确认、右键”、长按或快速连按左右键；\n" +
                       "• 尝试长按遥控器顶部的设置键等。\n\n" +
                       "测试结果：在新版 Android 12 固件下，以上所有按键组合均无任何响应，新版 GMUI 已屏蔽此类隐蔽入口。"
            },
            new ContentSection
            {
                Heading = "尝试 2：系统自带设置应用排查",
                Text = "排查系统现存的设置组件，确认原生开发者选项入口是否存在：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看系统中现存的 setting 相关软件包",
                        Code = "pm list packages | grep -iE \"setting|developer\"",
                        ExpectedOutput =
                            "package:com.android.settings.intelligence\n" +
                            "package:com.android.providers.settings\n" +
                            "package:com.android.newsettings   # 极米深度重构的定制版设置，删除了原生关于与开发者选项",
                        Note = "系统删除了原生的 com.android.settings 和 com.android.tv.settings，因此原生通过 Android 版本号开开发者选项的入口不存在。"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "尝试 3：强行拉起工厂模式（minitvfactory）翻找入口",
                Text = "发现极米内置了工厂工程模式应用 com.xgimi.minitvfactory，通过命令行或 Activity Launcher 尝试强开：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查找工厂模式的主 Activity 并尝试拉起",
                        Code = "dumpsys package com.xgimi.minitvfactory | grep -i activity",
                        ExpectedOutput = "ActivityRecord{... com.xgimi.minitvfactory/.MainActivity}",
                        Note = "在电视上拉起该界面后，翻遍了两页工程菜单（包含老化模式、光机参数、色温调整、按键测试、网络测试等），未发现任何直观的 ADB 或网络调试开关。"
                    },
                    new CodeBlock
                    {
                        Label = "全局检索包含 developer 或 debug 的隐藏 Activity",
                        Code = "pm query-activities -a android.intent.action.MAIN | grep -iE \"developer|debug\"",
                        ExpectedOutput = "仅检索到 WebView DevTools，不存在系统级的开发者选项 Activity"
                    }
                ]
            }
        ]
    };
}
