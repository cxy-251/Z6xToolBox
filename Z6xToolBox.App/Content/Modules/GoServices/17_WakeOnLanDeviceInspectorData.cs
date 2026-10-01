using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class WakeOnLanDeviceInspectorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "wake-on-lan-device-inspector",
        Title = "17. 自研局域网网络唤醒与设备巡检网关：UDP 魔术包与在线感知（Z6X WakeOnLanHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 自研的常驻网络唤醒（WOL）网关，提供一键唤醒 PC/NAS 网页接口，并周期性巡检局域网主机在线状态。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在客厅打开投影仪准备用 Steam Link / Moonlight 串流玩 PC 游戏或访问家庭 NAS 时，电脑通常处于睡眠或关机状态，必须特意跑到书房按下机箱电源按键。\n\n" +
                       "极米投影仪常插电且低功耗在线。自研该唤醒网关后，手机或极米大屏浏览器打开网页，点击一次【开机】，极米即可向目标电脑网卡发送唤醒魔术包触发开机；同时后台自动 Ping 巡检，开机完成后网页状态指示灯自动变绿，无缝进入游戏串流。",
                BulletPoints =
                [
                    "解决远程串流开机痛点：躺在沙发上用手机或遥控器直接远程唤醒书房电脑与 NAS。",
                    "局域网设备在线状态一览：定时探测家庭核心设备 IP 连通性，防止串流盲目重试。",
                    "低功耗中枢：空闲内存常驻仅约 4MB ~ 6MB，待机时零硬件负担。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net`（UDP 局域网广播）+ 轻量 Ticker 状态巡检（单文件体积约 4MB）。",
                    "什么是 WOL 唤醒魔术包（Magic Packet）？标准以太网数据帧。内容为 6 个字节的 `0xFF` 紧接着目标网卡物理 MAC 地址连续重复 16 次（共 102 字节）。Go 通过 UDP 向局域网广播地址（`255.255.255.255:9`）发送该报文，通电待机的主板网卡芯片检测到自身 MAC 即刻触发电源上电。",
                    "什么是设备巡检感知？Go 服务内部维护设备列表，每隔 30 秒向目标 IP 发送一次 ICMP Echo 请求，将设备状态（在线/离线/时延）缓存在内存中供前端轮询渲染。",
                    "【参考开源项目】wakeonlan（参考其标准魔术包二进制构造算法）；wol-go（参考其对多网卡广播地址自动绑定的处理）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "后台启动 WOL 唤醒网关并模拟发送开机指令：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动网络唤醒网关并测试触发开机",
                        Code = "# 1. 启动唤醒与巡检服务（监听 8097 端口）\n" +
                               "nohup /data/local/tmp/z6x_wol -port 8097 > /data/local/tmp/wol.log 2>&1 &\n\n" +
                               "# 2. PC 模拟发送针对特定 MAC 地址的唤醒请求\n" +
                               "curl -X POST http://192.168.0.109:8097/api/wake?mac=AA:BB:CC:DD:EE:FF",
                        ExpectedOutput = "{\"status\":\"ok\",\"action\":\"magic_packet_sent\",\"mac\":\"AA:BB:CC:DD:EE:FF\"}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "网络唤醒排坑指南：",
                BulletPoints =
                [
                    "问题 1：魔术包发送成功但电脑不开机。原因与解决：目标电脑 BIOS 未开启「Wake-on-LAN」或 Windows 网卡高级属性开启了「快速启动（Fast Startup）」（导致关机时彻底切断网卡供电）。需在电脑端开启网卡电源管理中的「允许此设备唤醒计算机」。",
                    "问题 2：广播包跨网段失效。WOL 依赖局域网 Layer 2 广播，极米与目标电脑必须处于同一个 VLAN 和子网掩码（如 `192.168.0.0/24`）中。"
                ]
            }
        ]
    };
}
