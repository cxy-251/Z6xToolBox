using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class InitDaemonIceSeaReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "init-daemon-icesea-reference",
        Title = "27. Init系统守护进程与极米IceSea核心（init.svc / IceSea / ctl）",
        Group = "设备接入",
        Summary = "剖析 Linux Init 管理的原生服务状态、极米硬件核心守护进程 IceSea 角色与 ctl 控制机制。",
        Sections =
        [
            new ContentSection
            {
                Heading = "原生 Init 守护服务状态查询（init.svc.*）",
                Text = "查看 Linux 内核启动后通过 init.rc 派生的各系统级原生守护进程运行状态：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询正在运行与已退出的系统核心服务",
                        Code = "getprop | grep '\\[init.svc\\.' | head -n 10",
                        ExpectedOutput =
                            "[init.svc.IceSea]: [running]   # 极米光机硬件控制中枢（运行中）\n" +
                            "[init.svc.adbd]: [running]     # ADB 守护进程（运行中）\n" +
                            "[init.svc.audioserver]: [running] # 音频处理服务（运行中）\n" +
                            "[init.svc.bootanim]: [stopped]    # 开机动画服务（已停止退出）"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "极米专属硬件守护中枢：IceSea 分析",
                Text = "实测表明，`IceSea` 是 PPID=1（init）直接派生的 root 级别专有守护进程，负责：",
                BulletPoints =
                [
                    "光机激光光源与 RGB 色轮物理点亮与亮度控制。",
                    "机身电动步进马达对焦微调与 TOF 测距联动。",
                    "内置散热风扇多级 PWM 温控调速。",
                    "自动梯形校正陀螺仪姿态算法驱动。"
                ],
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查看 IceSea 进程树与资源占用",
                        Code = "ps -ef | grep -i icesea",
                        ExpectedOutput = "root          4227     1 0 19:33:34 ?     00:00:18 IceSea   # UID 0 root 运行，PPID 1，切勿强杀"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "ctl 控制属性（ctl.start / ctl.stop / ctl.restart）",
                Text = "Init 进程监听特定的 socket 属性变更来控制原生服务的生命周期：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "重启 adbd 守护进程示例",
                        Code = "setprop ctl.restart adbd",
                        ExpectedOutput = "# 无输出表示 init 捕获信号并成功重新拉起 adbd 进程"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "`getprop init.svc.*`：SSH（UID 10068）与 ADB 均可读取属性。",
                    "`setprop ctl.*`：SSH 无权触发（抛出 Permission denied 或被 SELinux 直接拦截）；ADB（UID 2000）具备操作受限 init 服务（如 adbd）的权限。"
                ]
            }
        ]
    };
}
