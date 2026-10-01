using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class MultiPaneVideoStreamingArchitectureData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "multi-pane-video-streaming-architecture",
        Title = "4. 大屏多联短视频与媒体画廊端到端架构",
        Group = "Go原生服务",
        Summary = "Go 静态流媒体后端 + 内嵌 CSS Grid 响应式 Web 前端 + Android Chromium WebView 硬解容器，针对几百 GB U 盘大容量内容与 MT9669 硬解限制设计。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "常规电视播放器（如 Kodi、MX Player 或系统播放器）只能一次打开并播放一个视频，不支持像监控墙或视频画廊一样的多屏并发播放；当 U 盘中存放几百 GB、成千上万个短视频时，传统播放器在扫描和加载列表时常常发生长时间假死或崩溃。\n\n" +
                       "本方案专门解决大容量短视频在投影大屏上的管理与多联播放需求：在后台极速扫描几百 GB 视频库，前台通过网格式视窗实现 2 联或 4 联短视频同时循环播放，并支持电视遥控器方向键移动焦点与全屏切换。",
                BulletPoints =
                [
                    "突破单片播放局限：实现 2 联或 4 联视窗网格同时循环播放短视频，适合沉浸式大屏画廊展示。",
                    "解决海量文件扫描卡死：Go 协程轻量遍历目录，几百 GB 视频索引常驻内存仅约 15MB，免除几分钟漫长加载等待。",
                    "完全适配电视遥控器：方向键移动高亮框，确认键一键全屏或切换声音通道。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 原生后台（文件索引与 HTTP Range 串流）+ 内嵌单页 Web（CSS Grid 响应式网格）+ Android 系统 Chromium WebView（硬解呈现容器）。",
                    "为什么不能由 Go 原生直接在屏幕上画窗口？Android 系统屏幕输出受 SurfaceFlinger 与 WindowManager 控制，Linux 底层显示设备节点对非 Root shell 权限不可写；必须通过合法的 Android 视窗载体（电视浏览器或极简 WebView APK）来创建图层。",
                    "什么是 VPU 硬件解码限制？极米 Z6X Pro 搭载联发科 MT9669 芯片，其硬件视频解码器（VPU）只有固定数量的硬解通道，通常支持 2~4 路 1080p（或 1 路 4K）同时硬解。硬解不占用 CPU 算力；但若同时播放 6~9 路视频，超出部分会强制回退到 CPU 软解，导致 4 核 A73 占满、投影仪发热和界面卡死。",
                    "什么是「动态焦点硬解」机制？界面虽然可展示 6 格或 9 格网格，但只有遥控器当前选中的 2~4 个视窗挂载 `<video>` 标签并发拉流，其他未选中的网格仅展示轻量的首帧静态图片，既能保证视觉丰富度，又严格守住硬件解码安全线。"
                ]
            },
            new ContentSection
            {
                Heading = "核心组件交互与启动命令",
                Text = "架构包含两层组件协作：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 Go 媒体流媒体后端",
                        Code = "# 启动后端（监听 8088 端口，挂载 U 盘短视频目录）\n" +
                               "nohup /data/local/tmp/z6x_media_server -dir /storage/XXXX-XXXX/ShortVideos -port 8088 > /data/local/tmp/media.log 2>&1 &\n\n" +
                               "# 极米前台打开全屏 WebView 或系统浏览器访问本地服务\n" +
                               "am start -a android.intent.action.VIEW -d \"http://127.0.0.1:8088/\"",
                        ExpectedOutput = "# 后端就绪，前台拉起全屏浏览器展示多联短视频画廊"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "多联视频播放常见问题与规避策略：",
                BulletPoints =
                [
                    "问题 1：多路视频同时发声导致声音混杂。解决：所有并发网格窗口在 HTML5 中默认强制添加 `muted` 属性静音播放，仅当前遥控器焦点所在的视窗在按下确认键后解静音，离开时自动恢复静音。",
                    "问题 2：视频无法硬解或黑屏。原因与解决：部分手机端录制的特殊编码格式（如 10-bit HDR 或某些非标准 profile）可能无法被 MT9669 芯片直接解码。需保证视频为主流的 H.264/AVC 或 H.265/HEVC 编码（8-bit 1080p/720p 兼容性最高）。",
                    "问题 3：海量文件扫描引发内存激增。解决：扫描几百 GB 目录时不要一次性将所有视频元数据和缩略图读入内存，采用分页 API（如 `/api/videos?page=1&size=20`）按需加载，内存可恒定维持在 20MB 以内。"
                ]
            }
        ]
    };
}
