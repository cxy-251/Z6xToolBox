using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class DeckSyncArchiveGatewayData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "deck-sync-archive-gateway",
        Title = "9. 自研掌机同步网关：Steam Deck 存档与截图自动归档（DeckSync Hub）",
        Group = "Go原生服务",
        Summary = "基于 Go 自研的轻量 HTTP 归档接收网关，配合 Steam Deck 本地脚本，连上家庭 Wi-Fi 自动增量备份游戏存档与 4K 截图至外接 U 盘。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "Steam Deck 掌机内部 NVMe 固态硬盘容量昂贵且紧张，特别是离线运行的独立非 Steam 游戏、各类模拟器（RPCS3、Yuzu、PPSSPP、RetroArch）的即时存档，以及高频截取的高清截图。\n\n" +
                       "平时手动用数据线拷出来极其麻烦且容易遗忘。自研该网关后，极米投影仪在后台充当无线冷备站。Deck 只要开机连上家里的 Wi-Fi，后置脚本自动触发增量打包，通过网络推送到极米外接的 100GB+ 大容量 U 盘中，并自动按日期归档，保障存档万无一失。",
                BulletPoints =
                [
                    "全自动无线备份：无需插线、无需手动打开任何客户端，只要两台设备在同一 Wi-Fi 下即可静默完成。",
                    "历史版本回滚：按年月日保留备份版本，防止游戏坏档或误覆盖。",
                    "释放掌机内部存储：截图与旧存档备份后可一键从 Deck 本地安全清理。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 标准库 `net/http` + Steam Deck 端轻量 Bash 脚本（tar + curl）。",
                    "什么是增量哈希比对？备份时不需要每次把几十 GB 目录整体重新传一遍。Deck 端先计算文件的 SHA-256 哈希值或修改时间戳，仅将产生变动的文件打包推送到极米的 `/api/upload` 接口，节省传输时间与网络带宽。",
                    "为什么不用现成的云同步软件？商业网盘在掌机 Linux 桌面模式下通常没有适配好的无感后台，且有速度限制和隐私顾虑；自研 Go 网关走内网百兆局域网直传，单文件传输速度可达 30MB/s ~ 60MB/s，空闲内存常驻仅 8MB ~ 12MB。",
                    "【参考开源项目】Restic（参考其内容寻址增量快照和版本元数据设计，自研时采用轻量简化的 tar 快照代替复杂树结构）；Syncthing（参考其基于 UDP 局域网广播感知设备的机制）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "极米端启动接收服务，Deck 端执行推送测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "极米端启动接收网关与 Deck 端测试推送",
                        Code = "# 1. 极米后台启动归档网关（存储路径指定为 U 盘）\n" +
                               "nohup /data/local/tmp/z6x_decksync \\\n" +
                               "  -target /storage/XXXX-XXXX/DeckBackups \\\n" +
                               "  -port 8089 > /data/local/tmp/decksync.log 2>&1 &\n\n" +
                               "# 2. Steam Deck 终端执行单指令归档测试（打包模拟器存档并推送）\n" +
                               "tar -czf - ~/.var/app/org.libretro.RetroArch/config/retroarch/saves | \\\n" +
                               "  curl -X POST --data-binary @- http://192.168.0.109:8089/api/upload?name=retroarch_saves",
                        ExpectedOutput = "{\"status\":\"ok\",\"backup\":\"retroarch_saves_20261001_1508.tar.gz\",\"size\":\"12.4MB\"}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "归档网关运维排坑：",
                BulletPoints =
                [
                    "问题 1：传输大压缩包时 Go 服务内存飙升。原因与解决：若在内存中用 `io.ReadAll` 一次性接收整个请求体，几百兆的大包会撑爆极米内存。后端必须采用流式落盘机制：`io.Copy(file, request.Body)`，边收包边写 U 盘，内存开销恒定在 5MB 以内。",
                    "问题 2：断网重试与并发写入冲突。原因与解决：Deck 在休眠唤醒瞬间网络不稳定。Deck 端脚本添加 `curl --retry 3` 机制，极米端在写入时先写入 `.tmp` 后缀临时文件，接收完毕后再原子重命名，防止写入半截损坏归档。"
                ]
            }
        ]
    };
}
