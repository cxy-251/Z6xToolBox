using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LocalClipboardTextRelayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "local-clipboard-text-relay",
        Title = "11. 自研局域网中继：免登文本便签与投屏直调（Z6X LocalPaste）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的局域网轻量便签与指令中继，手机扫码粘贴文本或视频直链，一键向极米原生系统派发播放或输入指令。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在手机或 Steam Deck 上查找到复杂的流媒体 m3u8 直链、长网址或密码时，想要输入到投影仪上极其困难：电视没有实体键盘，用遥控器软键盘一个字母一个字母按十分痛苦，借助微信或第三方 TV 输入法又涉及隐私上报与广告。\n\n" +
                       "自研该中继服务后，极米在后台提供一个极简 Web 页面。手机或电脑扫码打开，直接把文字或链接粘贴进去并点击【投屏打开】；极米后台自动调用 Android 系统广播或按键注入，直接全屏拉起播放或填入当前焦点输入框，实现免登录、局域网私密传输。",
                BulletPoints =
                [
                    "解决大屏输入法反人类问题：手机直接作为输入端，长文本、直链秒级传给电视。",
                    "视频直链一键唤醒播放：在手机粘贴视频 URL，极米端自动调用硬件解码播放器全屏播放。",
                    "零云端上传隐私保护：数据完全在家庭 Wi-Fi 内部流动，不经过任何外部服务器。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 内存并发 SafeMap + Android 本地 `am start` / `input text` 调度（单二进制体积约 4MB）。",
                    "什么是内存临时中继？用户发送的文本只暂存在 Go 服务的内存哈希表中，不落盘、不写数据库，重启自动清空，保证绝对的轻量与隐私安全。",
                    "什么是直接拉起播放（Intent 投递）？当接收到包含 `http` 的视频地址时，后端直接在极米本地执行 `am start -a android.intent.action.VIEW -d \"<URL>\"`，Android 系统会自动调起内置的最佳播放器（或 VLC）进行硬件解码播放，跳过繁琐的投屏协议配对。",
                    "【参考开源项目】microbin（参考其极简文本粘贴前端交互与密码锁设计）；transfer.sh（参考其纯命令行 curl 上传提取接口风格）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动中继服务并在 PC 上模拟推送文本：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动中继服务并推送一条视频播放指令",
                        Code = "# 1. 启动自研中继服务（监听 8090 端口）\n" +
                               "nohup /data/local/tmp/z6x_localpaste -port 8090 > /data/local/tmp/paste.log 2>&1 &\n\n" +
                               "# 2. PC 终端模拟推送视频链接并自动触发极米全屏播放\n" +
                               "curl -X POST http://192.168.0.109:8090/api/play \\\n" +
                               "  -d \"url=http://vfx.mtime.cn/Video/2019/02/04/mp4/190204084208765161.mp4\"",
                        ExpectedOutput = "{\"status\":\"ok\",\"action\":\"launched_player\"}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "中继服务常见问题与调试建议：",
                BulletPoints =
                [
                    "问题 1：推送中文文本时输入框显示乱码。原因与解决：Android 底层的 `input text` 命令原生仅支持 ASCII 字符，输入中文字符会截断或报错。需在 Go 逻辑中判断：若为纯 ASCII 则走 `input text`，若包含中文字符则走系统剪贴板广播注入（或通过 ADB IME 方案写入）。",
                    "问题 2：端口冲突。若 8090 端口被其他应用占用，启动参数支持自定义换用 8091 等任意高位端口。"
                ]
            }
        ]
    };
}
