using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.RustServices;

public static class EpollHybridSocksHttpProxyData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "epoll-hybrid-socks-http-proxy",
        Title = "26. 自研 Epoll 驱动轻量 Socks5/HTTP 混合反向代理：透明分流与穿透（Z6X RustHybridProxy）",
        Group = "Rust原生服务",
        Summary = "纯 Rust 开发的高并发 Socks5/HTTP 混合双协议代理，利用 Linux Epoll 单线程事件驱动，常驻内存仅 1.5MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "电视端部分应用只支持配置 HTTP 代理，而另一些网络工具或游戏串流只支持 SOCKS5 代理。如果同时在后台运行两个独立的代理服务，不仅容易端口冲突，还会产生双倍的常驻内存开销。\n\n" +
                       "使用 Rust 自研的混合代理服务，同一个端口自动通过首包协议嗅探（Protocol Sniffing）识别客户端是发送的 SOCKS5 握手包还是 HTTP 请求方法，动态分流至对应的处理状态机。单进程常驻内存仅约 1.5MB。",
                BulletPoints =
                [
                    "双协议单端口监听：同一监听端口同时兼容 SOCKS5 与 HTTP/HTTPS CONNECT 代理请求。",
                    "Epoll 单线程事件循环：单线程事件驱动，避免多线程上下文切换与栈开销。",
                    "轻量内存占用：常驻物理内存仅约 1.5MB，比常规方案节省近 70% 内存。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与开源参考",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Rust 1.98 + `mio`（Epoll 抽象）+ 协议首包特征嗅探状态机（二进制仅 750KB）。",
                    "连接池与环形缓冲区：预先分配 64KB 临时中转缓冲，采用零拷贝非阻塞数据搬运，空闲时不触发系统动态内存分配。",
                    "域名直连与白名单分流：支持在内存中维护轻量域名规则表，局域网 IP 与国内域名直接穿透，加速内外网访问流转。",
                    "【参考开源项目】shadowsocks-rust（Rust 高性能网络代理库）；tinyproxy（C 语言轻量 HTTP 代理）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动混合代理并分别测试两种协议：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动混合代理服务与协议自适应测试",
                        Code = "# 1. 启动混合代理（监听本地 10811 端口）\n" +
                               "nohup /data/local/tmp/z6x_hybrid_proxy \\\n" +
                               "  -port 10811 \\\n" +
                               "  -max-conn 64 > /data/local/tmp/proxy.log 2>&1 &\n\n" +
                               "# 2. 测试 SOCKS5 协议连接\n" +
                               "curl -x socks5://127.0.0.1:10811 http://www.baidu.com -I\n\n" +
                               "# 3. 测试 HTTP 代理协议连接（相同端口）\n" +
                               "curl -x http://127.0.0.1:10811 http://www.baidu.com -I",
                        ExpectedOutput = "HTTP/1.1 200 OK\n[Proxy] Protocol auto-detected: SOCKS5 (Session #1)\nHTTP/1.1 200 OK\n[Proxy] Protocol auto-detected: HTTP (Session #2)"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "长连接半关闭（TCP FIN）处理：部分视频播放器在跳段拉流时直接关闭写端，代理需及时调用 `shutdown(Write)` 并清理挂起描述符，防止 fd 泄漏堆积。",
                    "非特权端口监听：外部设备访问代理时，请勿绑定 80/443 等系统特权端口，选用 10811 等大于 1024 的端口。"
                ]
            }
        ]
    };
}
