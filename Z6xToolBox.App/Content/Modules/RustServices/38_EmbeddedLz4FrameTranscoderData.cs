using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class EmbeddedLz4FrameTranscoderData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "embedded-lz4-frame-transcoder",
        Title = "38. 自研无内存分配 LZ4 实时数据帧解压与转码流：ARM64 向量高速解压（Z6X RustLz4Stream）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 编写的流式 LZ4 帧解压与转码引擎，基于零堆内存分配与 NEON 向量复制，常驻内存仅 1.2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在极米与其他局域网设备同步遥测日志、大批量元数据或传输低延迟音频/图形帧时，未经压缩的数据占用大量 Wi-Fi 带宽，而常规 gzip/zlib 压缩算法 CPU 开销过高，ARM64 四核处理器在全速解压时会导致前台掉帧。\n\n" +
                       "使用 Rust 自研的流式 LZ4 转码器，利用 LZ4 极高解压吞吐特性，配合预分配的单页滑动窗口缓冲。在网络管道两端实现边接收边解压，全程无堆内存动态分配，常驻物理内存仅约 1.2MB，单核 CPU 开销 < 1%。",
                BulletPoints =
                [
                    "高速解压吞吐：利用 LZ4 算法特性与 ARM64 向量复制，解压吞吐高。",
                    "零堆分配流式解压：基于 64KB 环形窗口就地解码，杜绝频繁 GC 与内存扩张。",
                    "低延迟流式对接：支持直接套接在 TCP/Unix Socket 上作为透明解压中继。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `lz4_flex`（纯安全 Rust 实现的 LZ4 编解码器，无 C 动态库绑定）。",
                    "ARM64 内存未对齐读取优化：利用 ARMv8 架构对未对齐内存访问的硬件支持，直接执行 u32/u64 匹配与快速回填复制。",
                    "管道式流处理：对外提供与 Unix 管道完全兼容的输入输出接口，可作为标准输入管道过滤器使用。",
                    "【参考开源项目】lz4（官方 LZ4 规范与参考实现）；lz4_flex（Rust 高性能纯自研 LZ4 库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "流式管道解压与性能基准比对：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "执行流式管道解压与吞吐测算",
                        Code = "# 1. 通过管道将压缩日志流送入解压引擎并落盘\n" +
                               "cat /data/local/tmp/telemetry.lz4 | \\\n" +
                               "  /data/local/tmp/z6x_lz4 -d > /data/local/tmp/telemetry.log\n\n" +
                               "# 2. 查看解压速率与内存消耗\n" +
                               "/data/local/tmp/z6x_lz4 bench -file /data/local/tmp/telemetry.lz4",
                        ExpectedOutput = "[LZ4Stream] Decompressed 120.4 MB in 118ms (Throughput: 1020 MB/s)\n[Memory] RingBuffer peak: 64 kB, VmRSS: 1180 kB"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "LZ4 Block 模式与 Frame 模式格式差异：标准传输协议推荐使用 LZ4 Frame 模式（带 4 字节魔数 `0x184D2204` 与头校验和），若对接第三方裸 Block 流需显式指定 `-raw` 标志。",
                    "标准输出重定向缓存：在 shell 中使用管道重定向时，务必关闭标准 C 库的全量 4KB 缓冲，以保持流式实时性。"
                ]
            }
        ]
    };
}
