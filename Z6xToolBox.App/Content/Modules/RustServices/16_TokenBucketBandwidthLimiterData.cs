using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class TokenBucketBandwidthLimiterData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "token-bucket-bandwidth-limiter",
        Title = "16. 自研用户态带宽整形与令牌桶限速器：无 GC 流量调度（Z6X RustBandwidthLimiter）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 自研的轻量用户态流量整形中继，基于令牌桶（Token Bucket）算法对后台下载与同步流量做平滑限速，杜绝占用大屏观影与串流游戏带宽。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "极米在后台执行脱机大文件下载或向 Steam Deck 传输大容量数据时，满速并发读写往往会吃满家庭 Wi-Fi 路由器的上下行带宽与无线空口调度，导致正在大屏看在线视频或掌机串流游戏时发生严重的网络卡顿与丢包。\n\n" +
                       "使用 Rust 自研轻量流量整形代理后，数据流经过一个无锁令牌桶调度器。用户可动态将后台服务的流量上限锁定在指定速度（如 5MB/s），超出配额的数据被微秒级平滑切片延时，既不中断后台下载，又彻底保障前台游戏与视频的绝对流畅。",
                BulletPoints =
                [
                    "平滑流量突发峰值：利用令牌桶算法消除由于突发流量引起的家庭局域网丢包。",
                    "无 GC 纳秒级整形：避免 Go 垃圾回收导致的流量突发抖动，流量曲线平直如尺。",
                    "按服务细粒度分配：为离线下载、WebDAV、同步网关分配各自独立的带宽配额。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + 纯 Rust 无锁原子浮点（`atomic-float`）令牌桶算法（单文件体积约 650KB）。",
                    "什么是令牌桶（Token Bucket）算法？系统以恒定速率（如 5MB/s）向内存桶中添加虚拟“令牌”。当应用有数据包要发送时，必须先从桶中消费等同于字节数的令牌。若桶已空，当前发送任务被 `tokio::time::sleep` 精确挂起相应微秒数，实现平滑控速。",
                    "为什么不用 Linux tc 流量控制？Android 系统未开放 `tc` 命令且修改 qdisc 队列需要 root 特权。Rust 方案作为本地 TCP 反向代理在用户空间实现流量整形，完全免 Root 运行。",
                    "【参考开源项目】governor（Rust 最经典的高性能无锁速率限制库）；ratelimit_meter（轻量泄漏桶/令牌桶实现参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动限速代理并测试下载速率受控：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动限速中继并测试流量压制",
                        Code = "# 1. 启动限速代理（将 8085 端口的 WebDAV 限速为 5MB/s，映射到 8086）\n" +
                               "nohup /data/local/tmp/z6x_limiter \\\n" +
                               "  -upstream 127.0.0.1:8085 \\\n" +
                               "  -listen 0.0.0.0:8086 \\\n" +
                               "  -rate 5M > /data/local/tmp/limiter.log 2>&1 &\n\n" +
                               "# 2. PC 发起下载测试，观察速率是否严格稳定在 5MB/s\n" +
                               "curl -o /dev/null http://192.168.0.109:8086/storage/bigfile.bin",
                        ExpectedOutput = "100  200M  100  200M    0     0  5120k      0  0:00:40"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "带宽整形排坑指南：",
                BulletPoints =
                [
                    "问题 1：休眠定时器精度不足。Linux 默认定时器精度为毫秒级，微秒级限速可能会被合并唤醒。Rust 内部应使用纳秒精度的 `tokio::time::Instant` 结合自旋（Spinlock）补偿，确保速率偏差 < 1%。",
                    "问题 2：缓冲区积压。限速时慢连接可能导致发送缓冲区堆积。工具内部限制最大待发队列不超过 2MB，防止挤占物理内存。"
                ]
            }
        ]
    };
}
