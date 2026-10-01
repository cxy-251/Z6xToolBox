using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class JsonRpcRemoteControlBridgeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "json-rpc-remote-control-bridge",
        Title = "31. 自研 JSON-RPC 2.0 远程控制与自动化调度服务：系统调用抽象（Z6X RpcBridge）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的标准 JSON-RPC 2.0 远程过程调用服务，统一暴露投影仪音量控制、信号源切换与应用启动等系统接口，常驻内存仅 11MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "局域网内的第三方设备（HomeAssistant、自动化脚本、智能音箱、电脑快捷指令）若想控制极米投影仪，通常需要拼凑不同的 adb 命令或抓包私有广播，缺乏统一且标准化的控制网关。\n\n" +
                       "使用 Go 自研的统一 JSON-RPC 2.0 服务端，以标准规范封装电视底层控制能力。外部调用端通过单次 HTTP POST 请求发送带有方法名与参数的标准 JSON 结构体（如 `System.SetVolume`、`App.Launch`、`Display.SwitchSource`），即刻获取结构化回执，常驻物理内存约 11MB。",
                BulletPoints =
                [
                    "标准 JSON-RPC 2.0 规范：支持批量方法调用（Batch Requests）与标准错误码体系。",
                    "底层操作统一封装：把复杂的 Android shell 命令抽象为清晰的高层 API 函数。",
                    "低延迟并发调度：依托 Go 协程池，多个智能家居终端并发控制无排队阻塞。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/rpc/jsonrpc` 原生库 + 结构化参数反射校验（零三方重型 Web 框架）。",
                    "安全白名单拦截：内置 IP 白名单与 Token 签名认证，防止局域网未受信任设备恶意发送重启或关机指令。",
                    "指令防抖与状态缓存：对高频调用的音量或亮度调整做 50ms 内存防抖合并，减轻对 Android 底层系统服务的压力。",
                    "【参考开源项目】ethereum/go-ethereum/rpc（标准 JSON-RPC 2.0 实现参考）；kodi-json-rpc（Kodi 官方媒体控制 RPC 规范）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 RPC 服务并发送调用请求验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 JSON-RPC 服务并发送控制测试",
                        Code = "# 1. 启动轻量 RPC 服务（监听 9091 端口）\n" +
                               "nohup /data/local/tmp/z6x_rpc \\\n" +
                               "  -port 9091 \\\n" +
                               "  -token \"sec_token_8899\" > /data/local/tmp/rpc.log 2>&1 &\n\n" +
                               "# 2. 发送设置音量为 50% 的 JSON-RPC 请求\n" +
                               "curl -s -X POST http://192.168.1.100:9091/rpc \\\n" +
                               "  -H \"Authorization: Bearer sec_token_8899\" \\\n" +
                               "  -H \"Content-Type: application/json\" \\\n" +
                               "  -d '{\"jsonrpc\":\"2.0\",\"method\":\"System.SetVolume\",\"params\":{\"level\":50},\"id\":1}'",
                        ExpectedOutput = "{\"jsonrpc\":\"2.0\",\"result\":{\"status\":\"success\",\"current_volume\":50},\"id\":1}\n[RPC] Dispatched method: System.SetVolume in 4.2ms"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "Android 意图拉起权限：通过 RPC 启动第三方 App 时，需要使用 `am start -n` 并在非 root shell 下确认包名主 Activity 是否具备 exported 属性。",
                    "长连接心跳保活：若客户端采用 TCP/WebSocket 形式建立长连接 RPC，需在服务端设置 60s 闲置读超时，防止死连接残留消耗文件句柄。"
                ]
            }
        ]
    };
}
