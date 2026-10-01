using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class BusyboxEnvironmentData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "busybox-environment",
        Title = "2. 静态 BusyBox 部署与 396 项 Linux 命令注入",
        Group = "开发环境",
        Summary = "部署 1.1MB 静态编译 ARM64 BusyBox 二进制，创建完整符号链接集，补齐 Android 原生缺失的标准 Linux 命令行工具。",
        Sections =
        [
            new ContentSection
            {
                Heading = "BusyBox 静态二进制安装与权限设置",
                Text = "Android 原生仅附带功能极简的 Toybox，缺少 wget、vi、tar、nc、awk 等大量日常运维与开发工具。通过将静态链接编译的 BusyBox 写入设备，零依赖扩展系统能力：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "推送 BusyBox 静态二进制并赋予执行权限",
                        Code = "adb push busybox /data/local/tmp/busybox && \\\n" +
                               "adb shell chmod 755 /data/local/tmp/busybox",
                        ExpectedOutput =
                            "busybox: 1 file pushed, 0 skipped. ... MB/s (1148524 bytes in ...)\n" +
                            "# 权限已变更为 -rwxr-xr-x"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "符号链接生成与工具箱初始化",
                Text = "执行 BusyBox 自带的安装功能，在 /data/local/tmp/bin 目录下自动生成所有标准 Linux 工具软链接：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "生成 396 个独立 Linux 命令软链接",
                        Code = "adb shell \"mkdir -p /data/local/tmp/bin && /data/local/tmp/busybox --install -s /data/local/tmp/bin\"\n" +
                               "adb shell \"ls /data/local/tmp/bin | wc -l\"",
                        ExpectedOutput = "396"
                    }
                ],
                BulletPoints =
                [
                    "网络工具：nc (netcat), wget, ping, traceroute, nslookup, netstat, arp, route 等。",
                    "文本与过滤：awk, sed, grep, diff, vi, head, tail, sort, uniq, cut, tr 等。",
                    "归档与压缩：tar, gzip, bzip2, xz, cpio, unzip 等。",
                    "进程与系统分析：ps, top, pkill, pidof, fuser, free, uptime, iostat 等。"
                ]
            },
            new ContentSection
            {
                Heading = "PATH 环境变量注入与验证",
                Text = "将 /data/local/tmp/bin 加入 PATH 环境变量前缀，即可像在标准 Linux 终端中一样直接调用所有工具：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "验证 BusyBox 工具直接执行",
                        Code = "adb shell \"export PATH=/data/local/tmp/bin:$PATH && which wget && which vi && which nc\"",
                        ExpectedOutput =
                            "/data/local/tmp/bin/wget\n" +
                            "/data/local/tmp/bin/vi\n" +
                            "/data/local/tmp/bin/nc"
                    }
                ]
            }
        ]
    };
}
