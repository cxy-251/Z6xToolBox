using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class TorrentMetadataMagnetIndexerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "torrent-metadata-magnet-indexer",
        Title = "39. 自研 DHT 磁力链接解析与种子元数据提取器：快速转 .torrent 文件（Z6X MagnetHub）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型 Mainline DHT 节点与 BEP 0009 扩展客户端，快速将磁力链接 InfoHash 解析并下载为本地 .torrent 文件，常驻内存仅 18MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端 Aria2 在下载磁力链接（magnet:?xt=urn:btih:...）时，由于缺少公共节点加速，通常需要等待数分钟甚至半小时才能完成 Peer 握手与种子元数据下载，期间一直处于假死无速度状态。\n\n" +
                       "使用 Go 自研的轻量 DHT 磁力转种子工具，直接加入全球 BitTorrent DHT 骨干网络（Kademlia 路由算法）。利用 BEP 0009（Extension for Peers to Send Metadata Files）扩展协议，直接从对端节点高速抓取 Info 字典数据块并生成标准的 `.torrent` 文件保存至 U 盘，随后喂给 Aria2 秒开满速下载，常驻物理内存约 18MB。",
                BulletPoints =
                [
                    "磁力转种子秒开：绕过客户端漫长等待，直接在网络层提取完整元数据文件清单。",
                    "自动保存种子文件：将解析出的 `.torrent` 文件持久化归档至 U 盘，便于二次备份。",
                    "低开销单文件交付：纯 Go 协程驱动 UDP 消息收发，无外部 C 动态库依赖。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `anacrolix/dht/v2` + `anacrolix/torrent/bencode` 编解码器（单二进制体积约 15MB）。",
                    "Kademlia 距离度量：基于 160 位异或（XOR）距离度量路由，并发向全球数十个最近的 DHT 节点发出 `get_peers` 查询请求快速定位持种者。",
                    "BEP 9 分块拼装校验：将对端传来的 16KB 元数据数据块放入内存缓冲，全部分块就绪后执行 SHA1 哈希校验，校验一致才落盘为 `.torrent` 文件。",
                    "【参考开源项目】anacrolix/torrent（Go 生态最成熟的 BitTorrent 协议栈）；libtorrent（C++ 经典 BT 库设计参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动磁力解析服务并执行转换测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动磁力链接解析工具并提取种子",
                        Code = "# 1. 运行解析工具将磁力链接转换为实体 .torrent 种子文件\n" +
                               "/data/local/tmp/z6x_magnet \\\n" +
                               "  -hash \"4b4081c7e937d579294ef64a3875ff7be4876d05\" \\\n" +
                               "  -out /mnt/media_rw/USB_DISK/Torrents/ \\\n" +
                               "  -timeout 60s\n\n" +
                               "# 2. 查看输出种子信息\n" +
                               "ls -lh /mnt/media_rw/USB_DISK/Torrents/*.torrent",
                        ExpectedOutput = "[DHT] Connected to 48 routing table nodes\n[BEP9] Located 8 metadata peers, fetching 4 blocks (64 kB)\n[Torrent] Validated SHA1: 4b4081... [OK]\n[Output] Saved to /mnt/media_rw/USB_DISK/Torrents/Ubuntu_24.04.torrent (in 4.2s)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "路由表节点冷启动耗时：初次启动 DHT 节点时需要从 `router.bittorrent.com:6881` 引导加入网络，需保障电视能正常收发 UDP 外部数据包。",
                    "内存泄露防范：解析完成后应立即调用 `Close()` 释放所有网络连接与路由表，避免后台挂死数百个 UDP 套接字。"
                ]
            }
        ]
    };
}
