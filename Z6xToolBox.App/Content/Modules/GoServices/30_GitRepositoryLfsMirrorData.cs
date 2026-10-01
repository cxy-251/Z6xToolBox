using System.Collections.Generic;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Content.Modules.GoServices;

public static class GitRepositoryLfsMirrorData
{
    public static ModuleDefinition Create() => new()
    {
        Id = "git-repository-lfs-mirror",
        Title = "30. 自研微型 Git HTTP 仓库与 LFS 备份镜像节点：U 盘私有代码仓（Z6X GitMirror）",
        Group = "Go原生服务",
        Summary = "纯 Go 编写的轻量 Git Smart HTTP 与 LFS 大文件存储服务端，把极米外接 U 盘变成家庭私有 Git 备份节点，常驻内存仅 15MB。",
        Sections =
        [
            new ContentSection
            {
                Heading = "主要解决的事情（应用场景）",
                Text = "对于局域网内的开发者或极客，经常需要对脚本、配置文件、本地私有代码与游戏模型进行定点离线备份。在局域网部署 Gitea 或 GitLab 内存消耗过大（动辄 300MB 以上），而在公网云仓库上备份私密脚本又存在隐私顾虑。\n\n" +
                       "使用 Go 自研的轻量 Git 服务端，基于纯 Go 原生解析 Git Smart HTTP 传输协议与 Git LFS（Large File Storage）规范。无需安装 C 语言版 Git 二进制或外部数据库，直接在 U 盘指定目录下维护裸仓库（Bare Repository），支持 `git push`、`git pull` 与大文件对象存取，常驻物理内存约 15MB。",
                BulletPoints =
                [
                    "纯标准 Git 客户端支持：无需安装客户端专用插件，原生 `git clone/push` 直接兼容。",
                    "集成 LFS 大文件对象存储：支持存放大容量音视频资源、固件包与模型文件。",
                    "低开销单文件落地：无数据库依赖，每个仓库为一个纯净的本地目录。"
                ]
            },
            new ContentSection
            {
                Heading = "技术方案深入拆解与架构收益",
                Text = "技术名词解释与底层运行原理：",
                BulletPoints =
                [
                    "技术栈：Go 1.27 + `go-git/go-git/v5`（纯 Go 实现的 Git 协议栈）+ Smart HTTP v2 协议封装。",
                    "Smart HTTP 报文流式处理：通过解析 `git-receive-pack` 和 `git-upload-pack` 协议包头，使用流式管道在 HTTP 请求与磁盘 pack 文件之间传递，内存占用保持平稳。",
                    "LFS SHA256 内容寻址：LFS 对象采用 SHA256 哈希作为文件名存储在 U 盘对象的二级目录中，具备天然内容去重与完整性校验能力。",
                    "【参考开源项目】go-git（Go 原生 Git 工具库）；giftless（轻量 Git LFS 独立服务）。"
                ]
            },
            new ContentSection
            {
                Heading = "核心服务启动与测试命令",
                Text = "启动 Git 服务端并在电脑端执行克隆与推送测试：",
                CodeBlocks =
                [
                    new CodeBlock
                    {
                        Label = "启动微型 Git 服务并执行推送测试",
                        Code = "# 1. 启动轻量 Git 仓库服务（监听 9418 端口，指定 U 盘根目录）\n" +
                               "nohup /data/local/tmp/z6x_git \\\n" +
                               "  -port 9418 \\\n" +
                               "  -root /mnt/media_rw/USB_DISK/git_repos > /data/local/tmp/git.log 2>&1 &\n\n" +
                               "# 2. PC 端测试克隆与推送代码\n" +
                               "git clone http://192.168.1.100:9418/scripts.git\n" +
                               "cd scripts && echo \"echo 'hello'\" > test.sh && git add . && git commit -m \"init\"\n" +
                               "git push origin master",
                        ExpectedOutput = "[Git] Initialized bare repo: /mnt/media_rw/USB_DISK/git_repos/scripts.git\n[SmartHTTP] Handled service: git-receive-pack (Objects: 3, 842 bytes transferred)\nBranch 'master' set up to track remote branch."
                    }
                ]
            },
            new ContentSection
            {
                Heading = "可能遇到的问题与排坑方案",
                Text = "针对极米系统特性的注意事项：",
                BulletPoints =
                [
                    "pack-objects 临时文件写入：大工程推送时 Git 会生成临时的 index-pack，需确保写入目录具备足够的连续剩余空间，避免跨分区临时文件拷贝。",
                    "FAT32 文件系统硬链接缺失：由于 FAT32 不支持硬链接，裸仓库的 objects 目录维护采用直接复制或散列分片，防止系统抛出 `EPERM` 错误。"
                ]
            }
        ]
    };
}
