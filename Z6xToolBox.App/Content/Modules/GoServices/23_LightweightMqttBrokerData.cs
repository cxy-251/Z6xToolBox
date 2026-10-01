using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LightweightMqttBrokerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "lightweight-mqtt-broker",
        Title = "23. 自研本地轻量 MQTT Broker：纯 Go 物联网消息总线与 WebSocket 桥接（Z6X MqttHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的轻量 MQTT 消息服务器，为家庭物联网传感器与智能家居提供本地消息发布/订阅中枢，无需独立服务器或 NAS 硬件。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "家庭中的各类 DIY 智能设备、ESP32/ESP8266 传感器、温湿度计或智能开关通常依赖 MQTT 协议进行数据通信。为跑一个 MQTT Broker（如 EMQX 或 Mosquitto），用户往往需要单独添置工控机、树莓派或 NAS，增加额外硬件成本与耗电。\n\n" +
                       "自研该轻量 MQTT 服务后，极米投影仪在熄屏待机（仅几瓦功耗）时即充当全家的物联网消息总线。所有传感器将温度、人体感应数据发布给极米，极米再广播给 Home Assistant 或大屏仪表盘，打造零额外硬件成本的家庭 IoT 中枢。",
                BulletPoints =
                [
                    "省去独立服务器硬件：投影仪长期待机，无需购买额外软路由或工控机。",
                    "低延迟消息中转：局域网内设备间消息发布订阅往返延迟 < 2ms。",
                    "自带 WebSocket 桥接：网页端无需安装客户端，通过 WS 即可实时接收传感器数据图表。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `mochi-mqtt/server`（纯 Go 用户态 MQTT v3.1.1/v5 协议引擎，单二进制体积约 6MB）。",
                    "什么是 MQTT 发布/订阅模式？设备之间不直接点对点握手。温度传感器向主题 `home/livingroom/temp` 发布消息，极米或手机只需订阅该主题即可被动收到数据，极大简化了设备间组网拓扑。",
                    "为什么比 Mosquitto / EMQX 更好维护？Mosquitto 依赖 C 动态链接库与配置文件；EMQX 基于 Erlang 运行时占用数百兆内存；纯 Go 静态编译的单二进制零动态依赖，空闲物理内存常驻仅 10MB ~ 15MB。",
                    "【参考开源项目】mochi-mqtt（纯 Go 打造的高性能轻量 MQTT 核心库）；eclipse-mosquitto（工业标准报文处理与 Topic 通配符树匹配设计参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动 MQTT Broker 并测试消息收发：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 MQTT 服务并测试订阅与发布",
                        Code = "# 1. 启动轻量 MQTT Broker（监听 TCP 1883 端口与 WebSocket 1884 端口）\n" +
                               "nohup /data/local/tmp/z6x_mqtthub -port 1883 -ws 1884 > /data/local/tmp/mqtt.log 2>&1 &\n\n" +
                               "# 2. PC 终端模拟向极米发布一条传感器测试消息\n" +
                               "mosquitto_pub -h 192.168.0.109 -p 1883 -t \"z6x/status\" -m \"{\\\"temp\\\":24.5}\"",
                        ExpectedOutput = "# 极米 MQTT 服务收到消息并分发给所有订阅客户端"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "MQTT 服务运维排坑：",
                BulletPoints =
                [
                    "问题 1：端口权限冲突。1883 为标准非特权端口（> 1024），Android 非 Root 用户可正常绑定监听；若被其他 TV 服务占用，启动参数支持指定 `-port 11883` 替换。",
                    "问题 2：QoS 1/2 消息堆积内存溢出。家庭环境网络通常极为稳定，配置中应默认开启内存消息过期机制（清除超过 24 小时未消费的离线消息），避免低内存设备堆积爆炸。"
                ]
            }
        ]
    };
}
