using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class TimedTaskWebhookDispatcherData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "timed-task-webhook-dispatcher",
        Title = "34. 自研 Webhook 事件监听与自动化动作分发器：多源 HTTP 触发（Z6X HookHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量 Webhook 接收与指令分发网关，接收外部自动化通知并联动极米本地弹窗、下载或休眠，常驻内存仅 10MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "局域网内的群晖 NAS 备份完成、GitHub Action 编译完成、或者云端监控报警时，通常需要一个接收端来触发电视端动作（如大屏弹出通知、唤醒极米同步文件、或者触发本地脚本备份）。\n\n" +
                       "使用 Go 自研的 Webhook 分发器，暴露标准的 HTTP POST 接口。支持配置多种 JSON 载荷解析规则，收到特定事件后，通过 Go 协程异步调用本地 Shell 脚本、向系统日志写入记录或通过本地 IPC 触发大屏弹窗，常驻物理内存约 10MB。",
                BulletPoints =
                [
                    "多系统事件接入：支持群晖、HomeAssistant、GitLab 与自建系统的 Webhook 回调。",
                    "安全密钥校验：支持 HMAC-SHA256 签名校验，杜绝伪造假报警触发。",
                    "异步非阻塞调度：接收到 HTTP 请求后 1ms 内返回 200 ACK，耗时任务移交后台协程池。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `adnanh/webhook` 核心逻辑精简重构 + `os/exec` 严格参数逃逸过滤。",
                    "安全参数化执行：避免直接拼接 shell 字符串执行引起命令注入，所有外部传参通过静态白名单数组提取与严格转义。",
                    "速率限制（Rate Limiting）：每个 Webhook 端点内置基于令牌桶的限流器，防止外部服务重试风暴引起电视 CPU 飙升。",
                    "【参考开源项目】webhook（经典的 Go 语言轻量 Webhook 服务器）；gotify（轻量实时消息推送平台）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 Webhook 服务并模拟发送触发请求：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Webhook 接收服务并发送触发报文",
                        Code = "# 1. 启动 Webhook 服务（监听 9001 端口，加载规则 hooks.json）\n" +
                               "nohup /data/local/tmp/z6x_hookhub \\\n" +
                               "  -port 9001 \\\n" +
                               "  -rules /data/local/tmp/hooks.json > /data/local/tmp/hook.log 2>&1 &\n\n" +
                               "# 2. 发送测试 Webhook POST 请求\n" +
                               "curl -X POST http://192.168.1.100:9001/hooks/backup-done \\\n" +
                               "  -H \"Content-Type: application/json\" \\\n" +
                               "  -d '{\"event\":\"nas_backup\",\"status\":\"success\",\"bytes\":104857600}'",
                        ExpectedOutput = "{\"status\":\"ok\",\"job_id\":\"job_20261001_01\"}\n[Hook] Matched rule 'backup-done', triggered script /data/local/tmp/notify.sh in background"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "子进程环境变量继承：通过 Go 的 `exec.Command` 执行 shell 脚本时，Android 默认环境缺少 `/data/local/tmp/bin` 路径，需在代码中显式补全 `PATH` 环境变量。",
                    "后台任务超时回收：为避免死锁脚本长期挂死，每个触发动作必须附带 `context.WithTimeout(ctx, 30*time.Second)` 硬性超时机制。"
                ]
            }
        ]
    };
}
