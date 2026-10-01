using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class ZeroCopyMmapKvEngineData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "zero-copy-mmap-kv-engine",
        Title = "6. 自研零拷贝内存映射 KV 存储引擎：mmap 驱动与纳秒级只读检索（Z6X RustKV）",
        Group = "Rust原生服务",
        Summary = "利用 Rust 零成本类型转换自研的嵌入式只读键值存储引擎，基于 Linux mmap 内存映射，为几百 GB U 盘媒体索引提供纳秒级查询与零堆分配。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "在管理 U 盘上数万个音视频、图片文件时，前端（如多联播放器、相册）需要极高频地根据文件路径查询元数据（分辨率、时长、海报路径、哈希值）。常规做法是使用 SQLite，但在并发只读读取时，SQLite 的 B-Tree 解码与动态内存分配会产生显著的 CPU 开销与锁争抢。\n\n" +
                       "使用 Rust 自研只读二进制索引引擎后，离线生成一份高度紧凑的单文件只读数据库。服务启动时通过 Linux `mmap` 直接将文件映射到虚拟内存，查询时直接根据内存指针进行二分或跳表寻址，查询延迟降至数十纳秒，堆内存分配恒定为 0 字节。",
                BulletPoints =
                [
                    "纳秒级只读查询延迟：内存指针直取，跳过 SQL 解析与上下文状态机损耗。",
                    "零额外物理内存堆开销：借助 Linux 内核 PageCache 按需缺页换入，不占常驻内存。",
                    "抗异常拔插断电：只读映射绝不回写破坏文件，外接 U 盘被拔出时不会产生数据库文件损坏。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `memmap2` + `zerocopy` crate（单二进制体积约 700KB）。",
                    "什么是零拷贝反序列化（Zero-Copy Deserialization）？通常反序列化（如 JSON 或 Protobuf）需要先从字节流中解析出字段并在堆上分配新的内存对象；Rust 的 `zerocopy` 允许在编译期保证内存对齐的前提下，直接将字节切片 `&[u8]` 安全转换为 Rust 结构体引用 `&MediaMeta`，整个过程 CPU 仅需读取指针，耗时为 0。",
                    "内核 PageCache 自动换页：100MB 的元数据文件映射后，只有被实际访问到的页面才会占用物理内存，闲置页面自动被内核换出，内存占用趋近于 0。",
                    "【参考开源项目】heed（基于 LMDB 的纯 Rust 封装）；rkv（Mozilla 打造的零拷贝键值存储库）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "构建只读索引并执行高并发基准测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "构建索引文件并测试纳秒级检索",
                        Code = "# 1. 扫描 U 盘生成紧凑的只读二进制索引库 media.kv\n" +
                               "/data/local/tmp/z6x_rust_kv -build /storage/XXXX-XXXX/Videos -out /data/local/tmp/media.kv\n\n" +
                               "# 2. 本地执行单键查询验证响应时间\n" +
                               "/data/local/tmp/z6x_rust_kv -db /data/local/tmp/media.kv -get \"/Videos/Inception.mp4\"",
                        ExpectedOutput = "{\"duration\":8880,\"size\":2147483648,\"codec\":\"h264\"}\n[Lookup Time: 42 ns]"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "只读存储引擎排坑指南：",
                BulletPoints =
                [
                    "问题 1：跨平台结构体内存对齐（Endianness / Padding）。在 PC（x86-64）上构建索引而在极米（ARM64）上读取时，必须显式声明 `#[repr(C, packed)]` 或固定字段对齐长度，防止因架构补齐差异导致指针偏移越界。",
                    "问题 2：增量更新处理。该引擎专精于高频只读；若有新文件写入，采用双文件切换机制（生成 `media.kv.new` 后原子重命名），避免写锁阻塞只读请求。"
                ]
            }
        ]
    };
}
