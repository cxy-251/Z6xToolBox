using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class HttpProxyAdBlockPacServerData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "http-proxy-ad-block-pac-server",
        Title = "38. 自研 PAC 自动代理分流配置生成与分发服务：动态智能分流（Z6X PacServer）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的 PAC（Proxy Auto-Config）脚本动态生成与 HTTP 分发服务，根据局域网客户端请求下发智能直连与代理规则，常驻内存仅 9MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "家庭局域网内的手机、iPad 或电脑如果全局配置 HTTP 代理，会导致所有国内流媒体流量全走代理导致卡顿；如果不配代理，又无法访问部署在局域网内部的私有服务。\n\n" +
                       "使用 Go 自研的 PAC 服务端，对外提供一个轻量 HTTP 链接（如 `http://192.168.1.100:8077/proxy.pac`）。服务内置常见域名规则库，根据客户端发起的请求动态渲染 JavaScript 编写的 `FindProxyForURL` 函数，智能实现内网直连、广告拦截与特定私有域名重定向，常驻物理内存约 9MB。",
                BulletPoints =
                [
                    "全平台原生支持：iOS、macOS、Windows 与 Android 均原生支持填入 PAC 地址。",
                    "动态规则热更新：修改后端规则列表即刻生效，无需逐台设备重新修改 Wi-Fi 配置。",
                    "内置规则压缩优化：生成的 PAC 经过 AST 紧凑压缩，浏览器执行匹配延迟 < 1ms。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http` + 纯内存 Gzip 压缩传输（单二进制体积约 10MB）。",
                    "动态 IP 模板替换：模板引擎自动检测当前极米主网卡 IP，将 PAC 脚本中的 `PROXY 127.0.0.1:10811` 动态替换为真实的极米局域网 IP，免除写死 IP 的弊端。",
                    "缓存控制头（Cache-Control）：精确输出 `Cache-Control: max-age=300`，使客户端每 5 分钟自动更新一次规则，兼顾性能与时效性。",
                    "【参考开源项目】gfwlist2pac（经典 PAC 生成工具）；squid（代理分流规则设计参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 PAC 服务并在控制台查看输出的 JS 脚本：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动 PAC 分发服务并请求脚本验证",
                        Code = "# 1. 启动 PAC 服务（监听 8077 端口，指定代理目标为 10811）\n" +
                               "nohup /data/local/tmp/z6x_pac \\\n" +
                               "  -port 8077 \\\n" +
                               "  -proxy-port 10811 \\\n" +
                               "  -rules /data/local/tmp/rules.txt > /data/local/tmp/pac.log 2>&1 &\n\n" +
                               "# 2. PC 端请求生成的 PAC 脚本\n" +
                               "curl -s http://192.168.1.100:8077/proxy.pac | head -n 10",
                        ExpectedOutput = "function FindProxyForURL(url, host) {\n  var proxy = \"PROXY 192.168.1.100:10811; DIRECT;\";\n  if (shExpMatch(host, \"192.168.*\") || shExpMatch(host, \"10.*\")) return \"DIRECT\";\n  if (shExpMatch(host, \"*.z6x.local\")) return proxy;\n  return \"DIRECT\";\n}"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "MIME 类型设置兼容性：响应头必须严格指定 `Content-Type: application/x-ns-proxy-autoconfig`，若设置为 `text/plain` 某些旧版 iOS 会拒绝识别为自动代理脚本。",
                    "大规则集内存膨胀防范：若引入几十万条广告规则，PAC 脚本体积会达数兆导致电视与手机解析缓慢，建议精简至 2000 条以内的常用规则。"
                ]
            }
        ]
    };
}
