using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class DynamicFormWebConfigUiData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "dynamic-form-web-config-ui",
        Title = "58. 自研配置项可视化 Web 动态表单生成器：零前端依赖管理界面（Z6X ConfigWeb）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的微型可视化配置管理服务，根据 YAML/JSON 注释自动动态生成 Web 表单供手机或电脑修改电视后台参数，常驻内存仅 11MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "部署在极米后台的数十个微服务通常通过 YAML 或 TOML 配置文件管理端口、Token、目录和开关。如果用户每次修改配置都要连上 SSH 用 vi 编辑文件，操作门槛高且容易因为语法缩进错误导致服务启动失败。\n\n" +
                       "使用 Go 自研的动态配置表单生成器，通过读取各服务的结构体定义或 Schema 描述，在浏览器中自动渲染出带有下拉选框、开关切换与路径校验的自适应 Web 界面。用户在手机浏览器上点选保存后，服务在后台自动备份旧文件并原子性重写配置，常驻物理内存约 11MB。",
                BulletPoints =
                [
                    "告别 SSH 与命令行改配：手机浏览器即可直观开启/关闭后台子服务。",
                    "自动校验防配置语法写崩：提交前进行数据类型与取值范围校验，阻断错误参数。",
                    "修改后自动备份回滚：配置变更前自动生成 `.bak` 副本，支持一键还原。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `html/template` 原生渲染引擎 + 纯 CSS 紧凑内联组件（单二进制体积约 11MB）。",
                    "Schema 自省解析：通过反射读取 Go Struct 标签（如 `json:\"port\" yaml:\"port\" description:\"监听端口\" default:\"8080\"`），动态映射为 HTML `<input>` 元素。",
                    "原子文件覆盖写入：先写入临时文件 `/tmp/config.tmp`，校验合法后通过 `os.Rename` 原子覆盖目标配置文件，防止断电导致配置文件损坏为空。",
                    "【参考开源项目】json-schema（数据交换标准）；gokrazy（Go 嵌入式系统 Web 管理界面参考）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动动态配置管理 Web 界面并访问验证：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动配置表单管理后台",
                        Code = "# 1. 启动轻量配置管理界面（监听 8097 端口，挂载配置文件目录）\n" +
                               "nohup /data/local/tmp/z6x_configweb \\\n" +
                               "  -port 8097 \\\n" +
                               "  -config-dir /data/local/tmp/configs > /data/local/tmp/configweb.log 2>&1 &\n\n" +
                               "# 2. 查看监听状态\n" +
                               "cat /data/local/tmp/configweb.log",
                        ExpectedOutput = "[ConfigWeb] Web UI listening on http://0.0.0.0:8097\n[Schema] Loaded 6 configuration files from /data/local/tmp/configs\n[Status] Ready for browser requests."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "跨站请求伪造（CSRF）防护：表单提交接口必须内置基于会话的 CSRF Token 随机数验证，防止访问恶意网页时被静默篡改电视配置。",
                    "配置变更触发服务热重载：修改配置后需支持向受影响的特定服务发送 `SIGHUP` 信号，使其无缝加载新参数而无需整个重启。"
                ]
            }
        ]
    };
}
