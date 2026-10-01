using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class OAuth2LocalAuthProxyData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "oauth2-local-auth-proxy",
        Title = "29. 自研局域网统一认证与反向代理网关：JWT 与 BasicAuth 鉴权（Z6X AuthProxy）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型反向代理与统一鉴权层，为本地无密码 Web 界面（Aria2、WebDAV、终端）提供统一登录保护，常驻内存仅 13MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "部署在极米上的各类原生服务中，部分开源工具（如简易文件浏览、测速页面、调试控制台）本身不带用户鉴权系统，或者各自有一套独立的账号密码管理。如果直接向局域网甚至公网开放，存在安全被窥探的隐患。\n\n" +
                       "使用 Go 自研的反向代理鉴权网关，作为所有 Web 入口的前置统一关卡。支持基于 JWT Cookie 的单点登录（SSO）或标准 HTTP BasicAuth。验证通过后才将请求透明反向代理至后端的 127.0.0.1 目标端口，常驻物理内存约 13MB。",
                BulletPoints =
                [
                    "统一访问前置保护：给无密码的极米后台服务添加访问防护层。",
                    "单点登录体验：一次登录生成签名 JWT Cookie，在各子服务间无缝切换跳转。",
                    "轻量资源开销：相比 Nginx+Lua 或 OAuth2-Proxy，无额外动态运行库依赖。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `net/http/httputil.ReverseProxy` + `golang-jwt/jwt/v5`（HMAC-SHA256 签名校验）。",
                    "连接池复用：反向代理配置自定义 `http.Transport`，开启 Keep-Alive 与长连接复用，避免每次请求向本地后端重新握手。",
                    "路径路由分发：基于 URL 前缀（如 `/aria2/`、`/files/`、`/term/`）自动重写请求头并分发给对应的本地私有端口。",
                    "【参考开源项目】oauth2-proxy（通用认证反向代理）；traefik（Go 语言高性能反向代理网关）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动统一鉴权网关并测试未授权拦截：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动反向代理网关与鉴权拦截验证",
                        Code = "# 1. 启动鉴权反代服务（监听 8080，代理后端 6800 与 8085）\n" +
                               "nohup /data/local/tmp/z6x_auth_proxy \\\n" +
                               "  -port 8080 \\\n" +
                               "  -user admin -pass \"pass1234\" \\\n" +
                               "  -routes \"/aria2=127.0.0.1:6800,/webdav=127.0.0.1:8085\" > /data/local/tmp/auth.log 2>&1 &\n\n" +
                               "# 2. 未携带凭证直接请求测试拦截\n" +
                               "curl -I http://192.168.1.100:8080/aria2\n\n" +
                               "# 3. 携带 BasicAuth 认证信息请求测试放行\n" +
                               "curl -I -u admin:pass1234 http://192.168.1.100:8080/aria2",
                        ExpectedOutput = "HTTP/1.1 401 Unauthorized\nWWW-Authenticate: Basic realm=\"Z6X Gateway\"\n\nHTTP/1.1 200 OK\n[Proxy] Forwarded request to 127.0.0.1:6800"
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "WebSocket 升级协议支持：终端或 Aria2 RPC 依赖 WebSocket，反向代理需显式转发 `Upgrade` 和 `Connection` 头部，防止长连接握手被阻断。",
                    "反代大文件上传内存溢出防护：`ReverseProxy` 默认不会缓冲请求体，但需禁用中间件的日志全量记录，防止上传 GB 级文件时将请求体拷贝入内存。"
                ]
            }
        ]
    };
}
