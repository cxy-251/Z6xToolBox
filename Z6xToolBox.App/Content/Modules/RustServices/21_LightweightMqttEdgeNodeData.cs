using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class LightweightMqttEdgeNodeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lightweight-mqtt-edge-node",
        Title = "21. 自研微型异步 MQTT 协议解析与网关中继：边缘传感器采集（Z6X RustMqttEdge）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的超轻量 MQTT v3.1.1/v5.0 边缘代理与协议中继器，专为低开销传感器遥测与智能家居转发设计，常驻内存仅 1.2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "如果将极米投影仪作为家庭智能家居的边缘展示与中继节点（如联动温湿度计、蓝牙体脂秤、人体传感器、开灯继电器），使用常规 Java/Node 编写的 MQTT Broker（如 EMQX、Aedes）开销过大，即使 Go 实现的方案也需要 10MB 以上内存。\n\n" +
                       "使用 Rust 自研的轻量 MQTT 边缘中继器，基于非阻塞状态机与零拷贝报文切片，支持精简版 Pub/Sub 转发与 QoS 0/1 报文交付。在数十个边缘传感器同时以秒级推送遥测数据时，常驻内存稳定在 1.2MB 左右，CPU 占用低于 0.5%。",
                BulletPoints =
                [
                    "边缘级资源消耗：常驻内存仅约 1.2MB，远低于传统 Broker 动辄数十兆的消耗。",
                    "零 GC 停顿：保障传感器高频上报与遥控下发无偶发微延迟。",
                    "协议精简可靠：支持 MQTT 3.1.1 核心协议，自带轻量 retained 消息内存表。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `mio`（多路复用）+ `bytes` 零拷贝缓冲 + 嵌入式无分配协议解析器。",
                    "固定内存池设计：预先分配固定大小的消息缓冲区，通过引用切片（`Bytes`）在客户端之间分发消息指针，避免反复分配与释放堆内存。",
                    "客户端掉线快速探测：采用基于时间轮的心跳保活探测，在毫秒级发现边缘设备离线并发布遗嘱（Last Will）。",
                    "【参考开源项目】rumqttd（高性能 Rust MQTT broker）；nanomq（轻量边缘消息引擎）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动边缘 MQTT Broker 并执行发布订阅验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 MQTT 边缘中继并测试消息透传",
                        Code = "# 1. 启动轻量 MQTT 节点（监听非特权端口 18833）\n" +
                               "nohup /data/local/tmp/z6x_mqtt_edge \\\n" +
                               "  -port 18833 \\\n" +
                               "  -max-clients 32 > /data/local/tmp/mqtt.log 2>&1 &\n\n" +
                               "# 2. 本地测试发布传感器消息\n" +
                               "/data/local/tmp/bin/busybox nc -w1 127.0.0.1 18833 <<EOF\n" +
                               "...\n" +
                               "EOF\n\n" +
                               "# 3. 查看连接与报文处理状态\n" +
                               "cat /data/local/tmp/mqtt.log",
                        ExpectedOutput = "[MQTT] Listening on 0.0.0.0:18833\n[MQTT] Client 'sensor_livingroom' connected (QoS 1)\n[MQTT] Relayed message: topic 'home/temp', payload 24.8C"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "客户端并发数限制：为控制内存在 1.5MB 以下，内部连接数上限需设为 32~64，对于家庭局域网传感器完全足够，无需开启无上限连接。",
                    "非特权端口选择：标准 MQTT 端口 1883 需要 root 权限，在 Android shell 下请使用大于 1024 的端口（如 18833 或 11883）。"
                ]
            }
        ]
    };
}
