using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ThermalThrottleMonitorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "thermal-throttle-monitor",
        Title = "29. 自研光机与 CPU 动态温控调频巡检器：内核 sysfs 实时采样（Z6X RustThermalWatch）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的光机温度与处理器降频监控探针，无动态内存分配轮询 sysfs 传感器，常驻内存仅 500KB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "投影仪的光机光源与处理器在密闭机身内发热量大，夏季或进出风口积灰时容易达到临界温度。一旦触发系统过热保护，Android 底层会强制将 CPU 大核降频至最低频率甚至关闭屏幕，导致观影播放发生剧烈掉帧，用户往往无法判断是软件故障还是硬件过热。\n\n" +
                       "使用 Rust 自研的温控监控器，通过打开内核 `/sys/class/thermal/thermal_zone*` 虚拟文件节点，在固定内存缓冲区内流式解析整数温度值。无需拉起 Java 运行时，常驻物理内存仅约 500KB，能在温度跨越警戒线时提前触发降功耗策略或发送提醒。",
                BulletPoints =
                [
                    "光机与芯片实时温度采集：追踪 CPU 核心、GPU 以及光学投影模组温度趋势。",
                    "零堆分配循环读取：固定 64 字节缓冲区配合 pread/lseek，避免频繁产生内存碎片。",
                    "温升速率预警：通过微积分斜率算法推算升温趋势，比被动式阈值触发提前预警。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `nix`（POSIX 文件描述符系统调用）+ 栈内存解析（单二进制体积仅 550KB）。",
                    "流式整数转换：避免使用标准库的字符串分割与堆分配，采用自定义定长栈缓冲直接反序列化 ASCII 温度整数。",
                    "资源占用对比：Android 系统自带 ThermalService 常驻开销较大，RustThermalWatch 仅占 500KB，单次采样耗时 < 30μs。",
                    "【参考开源项目】thermald（Linux 开源温控守护进程）；lm-sensors（Linux 硬件监控驱动套件）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动温控巡检探针并查看实时采集数据：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动光机温度监控与高温告警输出",
                        Code = "# 1. 启动轻量温控监控器（每 2 秒采样一次，设置 80 度告警阈值）\n" +
                               "nohup /data/local/tmp/z6x_thermal \\\n" +
                               "  -interval-ms 2000 \\\n" +
                               "  -warn-temp 80000 > /data/local/tmp/thermal.log 2>&1 &\n\n" +
                               "# 2. 查看当前光机与 CPU 实时温度读数\n" +
                               "cat /data/local/tmp/thermal.log",
                        ExpectedOutput = "[Thermal] Zone 0 (CPU-cluster0): 54.2 C\n[Thermal] Zone 1 (CPU-cluster1): 58.6 C\n[Thermal] Zone 2 (Optical-Engine): 62.1 C, Slope: +0.05 C/s [NORMAL]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "sysfs 节点权限与命名：不同 Android 内核版本的 thermal_zone 顺序可能不同，启动前需扫描 `/sys/class/thermal/thermal_zone*/type` 确认具体对应的是 `cpu-thermal` 还是 `gpu-thermal`。",
                    "采样频率权衡：读取虚拟文件节点每次都会触发内核上下文切换，建议将采样间隔设置在 1 秒至 3 秒，避免毫秒级高频读取占用总线带宽。"
                ]
            }
        ]
    };
}
