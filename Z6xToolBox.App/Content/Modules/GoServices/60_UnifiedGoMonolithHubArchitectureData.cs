using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class UnifiedGoMonolithHubArchitectureData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "unified-go-monolith-hub-architecture",
        Title = "60. 架构整合：极米 Go 原生多服务单二进制聚合架构（Z6X Hub）",
        Group = "Go原生服务",
        Summary = "将 WebDAV、Aria2、MQTT、影视刮削、S3 与流媒体等各项 Go 原生服务整合成单一二进制程序，统一配置文件与生命周期调度，总常驻内存约 35MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（设计动机）",
                Text = "如果将前述各项 Go 原生服务编译为数十个独立程序分别部署在极米上，会面临进程碎片化和内存开销问题：每个独立 Go 程序启动均包含一套完整的运行时与垃圾回收器（GC），单个程序即使只占 6MB，数十个累加也会消耗 150MB 以上内存。\n\n" +
                       "将各项功能整合为一个静态编译的二进制文件（`z6x_hub`），单文件交付、单个后台进程守护，通过统一的 `config.yaml` 声明式按需开关各模块。所有子模块共享同一个 Go 运行时与内存池，总常驻内存控制在 30MB ~ 45MB 以内。",
                BulletPoints =
                [
                    "单二进制交付：只需向 `/data/local/tmp` 推送一个文件并维护单个后台进程。",
                    "降低物理内存占用：所有子模块共享 Go 运行时与内存池，总常驻物理内存约 30MB ~ 45MB。",
                    "进程内高效协作：模块间通过通道（Channel）直接传递事件，无需跨网络 HTTP 序列化开销。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `context.Context` 统一生命周期管理 + YAML 集中配置（单二进制体积约 18MB）。",
                    "Netpoller 与运行时复用：Linux 下 Go 使用 epoll 机制管理网络套接字。合并后，所有 59 项子服务的 TCP/UDP 监听与并发连接由内核中唯一的 Netpoller 统一调度，不再为每个服务创建多余的系统调度线程，CPU 上下文切换消耗降低 70% 以上。",
                    "模块化子服务注册机制：代码层面定义统一的 `Service` 接口（包含 `Name()`, `Start(ctx)`, `Stop()`），各功能作为独立包开发，在 `main.go` 中根据配置文件以 Goroutine 启动，互不耦合。",
                    "集中化端口与存储路径规划：统一在配置文件中分配非特权端口段（8070-8099、18833、2022），统一管理外接 U 盘读写权限。"
                ]
            },
            new ContentSection
            {
                Heading = "统一配置文件与启动命令样例",
                Text = "单配置管理所有模块开关与参数：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "聚合配置样例与后台守护启动",
                        Code = "# 1. 编写统一配置文件 config.yaml\n" +
                               "cat << 'EOF' > /data/local/tmp/z6x_hub.yaml\n" +
                               "storage_root: /mnt/media_rw/USB_DISK\n" +
                               "services:\n" +
                               "  webdav:      { enable: true,  port: 8085 }\n" +
                               "  wol_relay:   { enable: true,  port: 9099 }\n" +
                               "  notify_hub:  { enable: true,  port: 8098 }\n" +
                               "  speed_probe: { enable: false, port: 8094 }\n" +
                               "  mqtt_broker: { enable: true,  port: 18833 }\n" +
                               "EOF\n\n" +
                               "# 2. 单命令启动聚合服务\n" +
                               "nohup /data/local/tmp/z6x_hub -c /data/local/tmp/z6x_hub.yaml > /data/local/tmp/hub.log 2>&1 &",
                        ExpectedOutput = "[Hub] Starting Z6X Integrated Hub v1.0\n[Hub] Registered: webdav (:8085)\n[Hub] Registered: wol_relay (:9099)\n[Hub] Registered: notify_hub (:8098)\n[Hub] Registered: mqtt_broker (:18833)\n[Hub] All enabled services running."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "聚合架构潜在问题与防御性设计：",
                BulletPoints =
                [
                    "单点崩溃风险与 panic 隔离：若某个子服务发生不可预料的 panic 会导致宿主进程退出。解决方案：在每个子服务的核心 Goroutine 外层统一包裹 `defer func() { if r := recover(); r != nil { log.Printf(...) } }()`，确保局部异常被隔离，不影响其他服务持续运行。",
                    "端口冲突防护：在服务启动加载阶段，统一做端口占用与重复检测，若发现某项子服务端口被占则报错告警并跳过该模块，防止整体启动失败。"
                ]
            }
        ]
    };
}
