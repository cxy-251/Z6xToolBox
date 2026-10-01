using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class ShellEnvironmentPathReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "shell-environment-path-reference",
        Title = "40. 终端环境变量与极米PATH路径全貌（echo $PATH / trapezoidcorrect / pantilt）",
        Group = "设备接入",
        Summary = "实测极米系统内置可执行文件搜索路径全图谱、梯形校正点阵参数与自定义工具箱注入方案。",
        Sections =
        [
            new ContentSection
            {
                Heading = "系统 PATH 环境变量实测",
                Text = "实测极米系统的二进制搜索路径分布：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "输出完整 PATH 环境变量",
                        Code = "echo $PATH",
                        ExpectedOutput = "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/mnt/vendor/xgimidatabase/xbin\n" +
                                       "# 注意尾部：/mnt/vendor/xgimidatabase/xbin 专为极米底层私有二进制设计"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "极米专属数据目录内部结构实测",
                Text = "实测 `/mnt/vendor/xgimidatabase/` 下的核心硬件配置文件：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "遍历极米专属数据库分区文件",
                        Code = "ls -la /mnt/vendor/xgimidatabase/",
                        ExpectedOutput =
                            "drwxr-xr-x G0094                                 # Z6X Pro 机型对应的校准文件夹\n" +
                            "drwxr-xr-x pantilt                               # 云台/俯仰角传感器控制参数\n" +
                            "-rw-r--r-- trapezoidcorrect_points_offset.ini    # 梯形校正四角坐标偏移点阵表"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "免 Root 挂载与临时环境变量注入",
                Text = "在未解锁设备上使用自定义 Linux 工具（如独立 busybox 或 gdbserver）：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "将 /data/local/tmp 临时追加至 PATH 首位",
                        Code = "export PATH=/data/local/tmp:$PATH\n# 验证当前优先执行路径：\nwhich busybox 2>/dev/null || echo $PATH",
                        ExpectedOutput = "/data/local/tmp:/product/bin:...\n# 成功注入，优先使用推入的高版本工具"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "SSH 终端环境（SimpleSSHD）：运行在独立沙盒内，默认 PATH 仅包含 `/system/bin` 与 `/system/xbin`，缺失 Apex 与极米私有路径；且无权写入 `/data/local/tmp`。",
                    "ADB 终端环境：继承系统完整的 10 个 PATH 路径，拥有在 `/data/local/tmp` 存放和执行 ELF 可执行文件的特权。"
                ]
            }
        ]
    };
}
