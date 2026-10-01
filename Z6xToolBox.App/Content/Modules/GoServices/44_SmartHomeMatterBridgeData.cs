using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class SmartHomeMatterBridgeData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "smart-home-matter-bridge",
        Title = "44. 自研智能家居 Matter 与 Zigbee2MQTT 状态中继网关：协议桥接（Z6X MatterGate）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型 Matter/HomeKit 与 MQTT 协议转接网关，将投影仪开关机、输入源与亮度虚拟化为标准智能家居配件，常驻内存仅 16MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "如果想通过 iPhone 的“家庭”（Apple HomeKit）或者米家、小爱同学语音控制极米投影仪开关机、调整亮度或切换 HDMI 输入源，由于极米原生并未接入 Apple HomeKit 或开放的 Matter 协议，用户必须在软路由额外搭建庞大的 HomeBridge 容器。\n\n" +
                       "使用 Go 自研的轻量 Matter/HomeKit 网关，直接在极米后台作为智能设备节点运行。通过 HAP（HomeKit Accessory Protocol）协议或 Matter 桥接协议，向局域网广播一个虚拟 TV 配件。在苹果手机或 Matter 控制器扫描配对后，即可实现原生语音与场景控制，常驻物理内存约 16MB。",
                BulletPoints =
                [
                    "原生接入苹果 HomeKit：无需外部服务器中转，iPhone 控制中心直接出现极米遥控卡片。",
                    "语音联动全屋场景：支持 Siri / 小爱同学语音控制“打开影院模式”联动投影降幕布。",
                    "低开销单文件交付：纯 Go 原生实现加密握手与 SRP 协议，无复杂依赖。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `brutella/hap`（纯 Go HomeKit 协议实现）+ 本地 mDNS 广播宣告。",
                    "ChaCha20-Poly1305 加密会话：遵循苹果 HAP 规范，对局域网内的所有控制信令进行硬件级 AEAD 对称加密，保障家庭自动化通讯隐私。",
                    "TV Accessory 特性映射：将极米内部的 HDMI1、HDMI2、本地媒体播放器精确映射为苹果 TV 的 Input Source 列表，遥控器方向键无缝映射。",
                    "【参考开源项目】hap（Go 语言标准 HomeKit Accessory 协议库）；homebridge（Node.js 智能家居桥接框架参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 HomeKit 桥接网关并进入配对模式：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 HomeKit/Matter 桥接服务并查看配对码",
                        Code = "# 1. 启动桥接服务（生成默认配对码 001-02-003）\n" +
                               "nohup /data/local/tmp/z6x_matter \\\n" +
                               "  -pin 00102003 \\\n" +
                               "  -name \"Z6X Projector\" > /data/local/tmp/matter.log 2>&1 &\n\n" +
                               "# 2. 查看配对二维码与监听状态\n" +
                               "cat /data/local/tmp/matter.log",
                        ExpectedOutput = "[HomeKit] Service published on _hap._tcp:51827\n[Pairing] Setup PIN: 001-02-003, Setup ID: Z6XP\n[Status] Waiting for controller pairing request..."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "配对元数据持久化：HAP 配对生成的客户端长期公钥（Pairings）必须持久化保存在 `/data/local/tmp/hap_data/`，避免电视重启后丢失配对导致 iPhone 提示设备无响应。",
                    "关机状态断连处理：投影仪在真正切断总电源时网络会中断，需配置待机低功耗模式或结合 WoL 网络唤醒模块实现假关机待机侦听。"
                ]
            }
        ]
    };
}
