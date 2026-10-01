using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class StaticBinaryP2PChunkDistributorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "static-binary-p2p-chunk-distributor",
        Title = "47. 自研局域网大文件 P2P 分片差分加速探针：内容寻址分发（Z6X RustChunkP2P）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的局域网内容寻址大文件分片同步探针，基于定长分块哈希仅传输差异数据块，常驻内存仅 2MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在局域网内将数十 GB 的高清游戏资源包、4K 演示片或系统更新镜像同步到多台设备时，如果采用传统的整体 HTTP/Samba 复制，一旦网络中断就必须重新传输，且无法利用局域网已存在相同片段的其他节点。\n\n" +
                       "使用 Rust 自研的 P2P 分块同步工具，将大文件切分为 4MB 定长数据块，以 BLAKE3 计算内容哈希建立清单。局域网各节点通过 UDP 组播宣告各自持有的分块，自动从局域网内所有持有该块的设备拉取片段，仅同步缺失块。常驻物理内存稳定在 2MB 以内。",
                BulletPoints =
                [
                    "内容寻址差分传输：仅传输发生变动的分块，大幅减少局域网无效传输。",
                    "自动断点续传：以块为最小原子单位校验写入，断网无需重新传输整文件。",
                    "轻量去中心化：无需集中式 Tracker 服务器，纯局域网组播自主协同。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `blake3`（SIMD 快速哈希）+ `socket2` + Linux pwrite 随机块写入。",
                    "BLAKE3 高速块哈希：单核计算吞吐超过 1GB/s，在投影仪 ARM64 处理器上几秒内完成多 GB 文件的分块校验。",
                    "预分配定长位图：采用固定位图（BitMap）追踪各分块下载进度，零堆内存分配，有效抗内存膨胀。",
                    "【参考开源项目】bittorrent（P2P 文件分发协议经典参考）；syncthing（文件同步工具，本模块为其简化微型版）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动分块分发节点并同步大文件分片：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 P2P 分块分发与校验测试",
                        Code = "# 1. 扫描文件并生成清单\n" +
                               "/data/local/tmp/z6x_p2p manifest \\\n" +
                               "  -file /mnt/media_rw/USB_DISK/game_pack.iso \\\n" +
                               "  -chunk-size 4M \\\n" +
                               "  -out /data/local/tmp/game_pack.manifest\n\n" +
                               "# 2. 启动节点参与局域网协同同步\n" +
                               "nohup /data/local/tmp/z6x_p2p sync \\\n" +
                               "  -manifest /data/local/tmp/game_pack.manifest \\\n" +
                               "  -dir /mnt/media_rw/USB_DISK/ > /data/local/tmp/p2p.log 2>&1 &\n\n" +
                               "# 3. 查看分块同步进度\n" +
                               "cat /data/local/tmp/p2p.log",
                        ExpectedOutput = "[BLAKE3] Hashed 12.0 GB in 8.4s (3072 chunks)\n[P2P] Discovered 2 LAN peers via multicast\n[Sync] Received Chunk #148 (4096 kB) from 192.168.1.105 (Speed: 78.4 MB/s)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "稀疏文件支持：在非 ext4 格式的外置存储（如 FAT32/exFAT）上无法创建稀疏文件，下载前工具会自动使用 `posix_fallocate` 或定长零填充预分配空间。",
                    "多节点读写 I/O 争用：外接机械移动硬盘随机寻道慢，并发拉取分块数建议限制在 2~4 个，避免磁头频繁寻道导致读写降速。"
                ]
            }
        ]
    };
}
