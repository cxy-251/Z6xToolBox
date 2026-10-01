using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Instructions;

public static class SelinuxAuditReferenceData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "selinux-audit-reference",
        Title = "22. SELinux审计与安全域边界（avc: denied / restorecon / getenforce）",
        Group = "设备接入",
        Summary = "讲解 Android 12 强制访问控制（MAC）日志审计、安全上下文重置与 ADB 提权限制根因。",
        Sections =
        [
            new ContentSection
            {
                Heading = "SELinux 拒绝日志拦截与字段拆解（avc: denied）",
                Text = "当命令在 Linux 层面有读写权限但仍报错 Permission denied 时，必须检查内核审计拦截记录：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "从内核缓冲区或事件日志检索拦截事件",
                        Code = "dmesg | grep -i \"avc: denied\" | tail -n 5 || logcat -b events -d | grep avc",
                        ExpectedOutput =
                            "type=1400 audit(0.0:42): avc: denied { read } for comm=\"sh\" path=\"/data/system/packages.xml\" dev=\"mmcblk0p58\" ino=12048 scontext=u:r:untrusted_app:s0:c512,c768 tcontext=u:object_r:system_data_file:s0 tclass=file permissive=0\n" +
                            "# 逐字段解析：\n" +
                            "# { read }：被拦截的操作类型（读取）\n" +
                            "# comm=\"sh\"：发起拦截操作的执行体程序名称\n" +
                            "# path=\"/data/system/packages.xml\"：目标受限文件的绝对路径\n" +
                            "# scontext=u:r:untrusted_app:s0：主体安全域（当前为 SSH 运行的第三方普通应用域）\n" +
                            "# tcontext=u:object_r:system_data_file:s0：客体安全上下文（系统保护数据文件）\n" +
                            "# permissive=0：当前处于强制阻断模式（Enforcing），拦截即拒绝执行"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "安全上下文恢复与修复（restorecon / chcon）",
                Text = "用于排查通过 ADB push 进 `/data/local/tmp` 的二进制脚本由于标签错误导致无法执行：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "递归重置临时目录文件的安全标签为默认策略值",
                        Code = "restorecon -Rv /data/local/tmp/",
                        ExpectedOutput =
                            "SELinux: Relabeling /data/local/tmp/busybox from u:object_r:shell_data_file:s0 to u:object_r:shell_data_file:s0\n" +
                            "# 确保推入的可执行文件具备合法的 shell_data_file 上下文"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "模式查询与 setenforce 0 失败根因",
                Text = "解释为什么无法在极米 Z6X Pro 上临时关闭 SELinux：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "查询当前 SELinux 状态与尝试设为 Permissive",
                        Code = "getenforce && setenforce 0 2>&1",
                        ExpectedOutput =
                            "Enforcing   # 当前处于强制开启状态\n" +
                            "setenforce: Couldn't set enforcing status to '0': Permission denied   # 内核安全策略限制：setenforce 仅允许 u:r:su 或 u:r:init 域执行，ADB 的 u:r:shell 域无权修改内核布尔值"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "SSH 与 ADB 权限差异",
                Text = "权限边界：",
                BulletPoints =
                [
                    "SSH 进程归属于 `u:r:untrusted_app:s0` 域，受 Android CTS 严格沙盒限制，无法访问 `/data/local/tmp` 甚至大部分 `/data` 目录。",
                    "ADB 进程归属于 `u:r:shell:s0` 域，拥有调试专用的系统权限，可调用大多数 Binder 系统服务与读写临时执行路径。"
                ]
            }
        ]
    };
}
