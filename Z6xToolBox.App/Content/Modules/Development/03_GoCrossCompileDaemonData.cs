using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.Development;

public static class GoCrossCompileDaemonData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "go-cross-compile-daemon",
        Title = "3. Go 交叉编译流水线与后台微服务实测（4.2MB 物理内存）",
        Group = "开发环境",
        Summary = "采用 PC 交叉编译、设备直接运行模式，在极米实机后台托管 Go 原生 HTTP 微服务，实测物理内存占用仅 4.2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "PC 端单指令交叉编译",
                Text = "无需在投影仪上部署笨重的编译器或包管理器，直接在 PC（开发机）上利用 Go 原生交叉编译机制生成静态链接的 ARM64 ELF 二进制：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "Go 静态二进制交叉编译指令",
                        Code = "CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags=\"-s -w\" -o z6x_go_server main.go",
                        ExpectedOutput = "# 生成约 5.2MB 纯静态无动态依赖的 ELF 64-bit LSB executable, ARM aarch64"
                    }
                ],
                BulletPoints =
                [
                    "CGO_ENABLED=0：完全禁用 CGO，生成的二进制不依赖 target 系统的 libc/glibc/bionic，可在任意 Linux 5.x ARM64 内核无缝运行。",
                    "-ldflags=\"-s -w\"：剥离符号表和调试信息，有效缩减二进制体积。"
                ]
            },
            new ContentSection
            {
                Heading = "部署并启动后台守护进程",
                Text = "通过 ADB 将二进制推送至设备并以后台守护进程形式启动：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "推送并启动后台微服务",
                        Code = "adb push z6x_go_server /data/local/tmp/z6x_go_server && \\\n" +
                               "adb shell chmod 755 /data/local/tmp/z6x_go_server && \\\n" +
                               "adb shell \"nohup /data/local/tmp/z6x_go_server > /data/local/tmp/go_server.log 2>&1 &\"",
                        ExpectedOutput = "# 服务启动并监听非特权端口 :8088"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "局域网访问与实测资源占用取证",
                Text = "通过局域网发起 HTTP 请求验证服务存活性，并检查 Linux 内核进程真实资源占用：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "局域网 curl 验证与内核进程状态检查",
                        Code = "curl -s http://192.168.0.109:8088/\n" +
                               "adb shell \"cat /proc/$(pidof z6x_go_server)/status | grep -E 'Name|VmRSS|VmSize|Threads'\"",
                        ExpectedOutput =
                            "Z6X Pro Native Go Server\n" +
                            "OS: linux\n" +
                            "Arch: arm64\n" +
                            "Hostname: localhost\n" +
                            "GoVersion: go1.27.1\n\n" +
                            "Name:   z6x_go_server\n" +
                            "VmSize:  1263836 kB\n" +
                            "VmRSS:      4288 kB\n" +
                            "Threads: 5"
                    }
                ],
                BulletPoints =
                [
                    "物理内存仅 4.2MB（VmRSS 4288 kB）：相比容器或解释型语言环境（Python/Node 通常需要 50MB-150MB），开销降低 90% 以上。",
                    "CPU 占用趋近 0%：无解释器空转与 JIT 预热，空闲状态不占用投影仪计算资源。"
                ]
            }
        ]
    };
}
