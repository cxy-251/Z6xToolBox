using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class LiveProxyStreamingHubData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "live-proxy-streaming-hub",
        Title = "12. 自研直播源代理与 EPG 聚合：Go 测活与内网分流（Z6X LiveProxy）",
        Group = "Go原生服务",
        Summary = "基于 Go 自研的轻量 IPTV 代理服务，定时并发检测直播源连通性并剔除死链，聚合 XMLTV 节目单输出统一局域网播放源。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端观看网络电视直播（IPTV m3u8）时，网络收集的免费直播源经常几天就失效死链，且缺少统一的节目单（EPG）。家庭多台设备（极米、手机、平板）每次都要手动更新和替换播放列表。\n\n" +
                       "在极米后台部署自研直播代理后，由极米充当全家的直播流网关：后台定时自动对所有源进行并发测活，自动剔除超时黑屏源并保留最优可用路线；同时拉取并缓存电子节目单，全家所有设备只需订阅极米输出的统一地址（`http://192.168.0.109:8092/live.m3u`）即可永不断流。",
                BulletPoints =
                [
                    "全自动测活去死链：后台自动探测源状态，告别逐个频道试错黑屏。",
                    "局域网统一订阅地址：家庭其他设备无需反复更新播放列表，极米本地自动维护更新。",
                    "EPG 节目单本地缓存：电视端瞬间加载当前节目与预告信息，免受外网服务器拥堵影响。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + 标准库 `net/http` + Goroutine 并发池（单文件体积约 5MB）。",
                    "什么是并发测活（HTTP HEAD 探测）？检查直播源是否可用不需要把完整视频流下载下来。Go 协程池向各个频道地址发送轻量的 `HEAD` 请求，仅检查返回的状态码（200 OK）与响应耗时，20 秒内即可完成几百个频道的全量可用性筛选。",
                    "什么是 XMLTV 节目单解析？国际标准的电视节目单 XML 格式。Go 服务定时拉取公网 EPG，解析频道对应的时间表并存入内存哈希表，请求时注入到 m3u 播放列表的 `tvg-id` 标签中。",
                    "【参考开源项目】iptv-checker（参考其基于 Go 协程的高并发直播源连通性探测算法）；epg-go（参考其对 XMLTV 标准格式的流式解析设计）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "在极米后台启动代理服务并测试聚合源输出：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "后台启动直播代理并获取聚合列表",
                        Code = "# 1. 启动直播源代理与测活守护进程（监听 8092 端口）\n" +
                               "nohup /data/local/tmp/z6x_liveproxy \\\n" +
                               "  -sources /data/local/tmp/sources.txt \\\n" +
                               "  -interval 2h \\\n" +
                               "  -port 8092 > /data/local/tmp/liveproxy.log 2>&1 &\n\n" +
                               "# 2. PC 终端验证获取经过测活清洗的聚合 m3u 播放列表\n" +
                               "curl -s http://192.168.0.109:8092/live.m3u | head -n 10",
                        ExpectedOutput = "#EXTM3U\n#EXTINF:-1 tvg-id=\"CCTV1\" tvg-name=\"CCTV-1\" group-title=\"央视频道\",CCTV-1 综合\nhttp://..."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "直播代理运维注意事项：",
                BulletPoints =
                [
                    "问题 1：测活瞬间打满家庭路由器连接数。原因与解决：若同时启动数百个协程发起 TCP 连接，容易触发低端路由器的 NAT 连接数保护导致家庭网络短暂掉线。Go 后端必须使用带缓冲的 channel 限制最大并发 Worker 数在 20 以内，单请求设置 3 秒超时。",
                    "问题 2：内存泄露。解析超大型 XMLTV（数十兆纯文本）时避免使用 `xml.Unmarshal` 一次性载入，改用 `xml.Decoder` 流式解析，保持内存常驻在 15MB 左右。"
                ]
            }
        ]
    };
}
