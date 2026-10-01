using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class MqttInfluxDbMetricsCollectorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "mqtt-influx-db-metrics-collector",
        Title = "54. 自研 MQTT 传感器时序数据收集与轻量归档服务：环境指标沉淀（Z6X MetricStore）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型时序数据接收与归档引擎，监听家庭 MQTT 传感器主题并以紧凑格式保存至 U 盘，常驻内存仅 14MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "家庭智能温湿度计、空气净化器、甲醛仪和电量插座每隔几秒就会上报一次遥测数据。如果要在电视大屏上展示过去 24 小时或 7 天的环境曲线，官方 InfluxDB 内存动辄消耗数百兆，在电视上无法长久常驻。\n\n" +
                       "使用 Go 自研的轻量时序数据接收器，订阅本地 MQTT Broker 的传感器主题（如 `home/sensor/#`）。在内存中按时间步长将数据做降采样聚合（Downsampling），以类似于 Line Protocol 的紧凑结构写入 U 盘文件，对外提供 HTTP 范围查询 API，常驻物理内存约 14MB。",
                BulletPoints =
                [
                    "时序数据按需降采样：自动将秒级高频数据压缩聚合为 5 分钟均值，节约 90% 存储。",
                    "低开销单文件持久化：采用追加写入格式（Append-Only），减少闪存随机写入损耗。",
                    "兼容标准图表查询：提供 `/api/query?metric=temp&range=24h` 接口，大屏即时渲染曲线。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `eclipse/paho.mqtt.golang` + 内存滚动环形缓冲（单二进制体积约 13MB）。",
                    "Gorilla 变长浮点压缩：借鉴 Facebook Gorilla 论文算法，对连续时间戳与浮点差值执行 XOR 异或位压缩，每条记录仅占数个字节。",
                    "批量异步刷盘（Sync Flush）：内存中缓冲 500 条记录或间隔 60 秒触发单次批量写入，避免高频打断 U 盘休眠。",
                    "【参考开源项目】influxdb（工业标准时序数据库，本模块为其最小化轻量重构版）；victoriametrics（高性能时序库设计参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动指标采集服务并查询历史均值：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动时序指标归档服务并查询数据",
                        Code = "# 1. 启动轻量时序归档服务（连接本地 MQTT，数据写入 U 盘）\n" +
                               "nohup /data/local/tmp/z6x_metrics \\\n" +
                               "  -broker tcp://127.0.0.1:18833 \\\n" +
                               "  -dir /mnt/media_rw/USB_DISK/metrics \\\n" +
                               "  -port 8094 > /data/local/tmp/metrics.log 2>&1 &\n\n" +
                               "# 2. 查询客厅温度过去 1 小时聚合数据\n" +
                               "curl -s \"http://127.0.0.1:8094/api/query?sensor=livingroom_temp&window=5m\"",
                        ExpectedOutput = "{\n  \"sensor\": \"livingroom_temp\",\n  \"points\": [[1710000000, 24.2], [1710000300, 24.3], [1710000600, 24.1]]\n}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "断电前数据丢失防范：内存聚合数据需捕获 `SIGTERM` 并在关机前执行强制 `Sync()` 刷盘，防止未保存的数据丢失。",
                    "历史文件自动轮转清理：内置 Retention 策略，超过 30 天的老旧时序文件自动清理，防止长期运行占满外接存储。"
                ]
            }
        ]
    };
}
