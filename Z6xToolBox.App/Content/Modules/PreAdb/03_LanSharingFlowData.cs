using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.PreAdb;

public static class LanSharingFlowData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lan-sharing-flow",
        Title = "3. 局域网传输与电视剪贴板同步",
        Group = "设备接入",
        Summary = "解决电视端无法复制长文本的痛点，通过局域网网页中转剪贴板内容，并提供 HTTP 共享与传输工具用法。",
        Sections =
        [
            new ContentSection
            {
                Heading = "电视端无法复制文本的痛点与解决方案",
                Text = "电视系统搭配遥控器时存在一个极为头疼的问题：电视上没有鼠标指针，也没有系统级快捷键，遇到几十位上百位的网络代理订阅链接、Token 或命令时，根本无法像手机电脑那样复制粘贴；用遥控器按键一个个字母打非常容易打错且耗时极长。\n\n" +
                       "解决方法是利用局域网中转：\n" +
                       "1. Steam Deck 将包含长文本的内容写入文件或网页（如 share.txt），在局域网开启 HTTP 共享；\n" +
                       "2. 电视端安装的 TV Bro 浏览器按遥控器菜单键可开启“虚拟鼠标指针”模式；\n" +
                       "3. 在 TV Bro 中打开局域网网页，用指针模式长按选中文本点击复制，该长文本便直接进入了电视系统的全局剪贴板；\n" +
                       "4. 切换到目标应用（如 Clash），在输入框中长按确定键即可直接粘贴完成。"
            },
            new ContentSection
            {
                Heading = "局域网共享与剪贴板中转实操命令",
                Text = "在 Steam Deck 终端启动局域网共享服务：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "在指定目录启动局域网 HTTP 文件与文本共享",
                        Code = "mkdir -p ~/share && echo \"https://your-clash-sub-url-token-here\" > ~/share/sub.txt\npython3 -m http.server 8000 --directory ~/share",
                        ExpectedOutput = "Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ...   # 局域网服务已启动",
                        Note = "电视浏览器输入 http://<Deck局域网IP>:8000/sub.txt 打开即可直接复制长文本。"
                    },
                    new CodeBlock
                    {
                        Label = "查看 Steam Deck 本机的局域网 IP 地址",
                        Code = "ip -4 addr show | grep -E \"wlan0|eth0|wlp\" | grep inet",
                        ExpectedOutput = "inet 192.168.0.21/24 ...   # 记录此 IP 供电视端访问"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "局域网网络传输工具常用命令备用",
                Text = "局域网排查与文件抓取常用命令集合：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "curl 测试局域网网页与服务连通性",
                        Code = "curl -I http://192.168.0.109:8000\n# 常用参数：-I（仅看响应头）、-v（详细握手日志）、-s（静默输出）"
                    },
                    new CodeBlock
                    {
                        Label = "wget 局域网断点续传下载文件",
                        Code = "wget -c http://192.168.0.21:8000/app.apk1 -O /tmp/app.apk1\n# -c 表示断点续传，-O 表示指定输出路径"
                    }
                ]
            }
        ]
    };
}
