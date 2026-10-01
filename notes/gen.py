# 用法：python3 notes/gen.py notes
# 根据 notes/proposals.py 生成 composeApp/.../proposals/Reviews.kt。
# 内容全部来自 proposals.py（Claude 重写的说明）；只有错误卡片里的「agy 原话」取自旧代码，并逐字校验确实存在。
import os, re, sys
sys.path.insert(0, sys.argv[1])
from proposals import GO, RS, GROUPS
from proposals_tech import T
from proposals_stack import S

root = os.path.abspath(os.path.join(sys.argv[1], '..'))
# 旧代码已从工作区删除，原文从 git 标签 avalonia-baseline 里读
import subprocess
TAG, OLD = 'avalonia-baseline', 'Z6xToolBox.App/Content/Modules/'
git = lambda *a: subprocess.run(['git', '-C', root, *a], capture_output=True, text=True, check=True).stdout
def plain(dirn):
    out = {}
    for f in git('ls-tree', '--name-only', f'{TAG}:{OLD}{dirn}/').split():
        out[int(f.split('_')[0])] = git('show', f'{TAG}:{OLD}{dirn}/{f}').replace('\\"', '"').replace('\\\\', '\\')
    return out
OLDG, OLDR = plain('GoServices'), plain('RustServices')
assert len(GO) == 60 and len(RS) == 49 and len(OLDG) == 60 and len(OLDR) == 49

V = {'C': 'Verdict.Confirmed', 'U': 'Verdict.Unverified', 'D': 'Verdict.Disproved'}
kt = lambda s: s.replace('$', "${'$'}")
q = lambda s: '"' + kt(s).replace('\\', '\\\\').replace('"', '\\"') + '"'
errors = []

def entry(kind, n):
    name, use, how, here, dest, wrong = (GO if kind == 'G' else RS)[n]
    tag = ('Go' if kind == 'G' else 'Rust') + f'-{n:02d}'
    src = (OLDG if kind == 'G' else OLDR)[n]
    stack, refs, direction = S[kind, n]
    out = f'    story({q(f"{name} · {tag.split(chr(45))[0]}")}) {{\n        facts(\n'
    for k, v in (('用途', use), ('技术栈', stack), ('做法', how), ('在这台投影仪上', here), ('参考项目', refs), ('发展方向', f'**{dest}**。{direction}')):
        out += f'            {q(k)} to {q(v)},\n'
    out += '        )\n'
    how_, care = T[kind, n]
    body = '**原理**\n' + '\n'.join('• ' + x for x in how_) + '\n\n**注意**\n' + '\n'.join('• ' + x for x in care)
    out += '        text(' + q(body).replace('\n', '\\n') + ')\n'
    for quote, verdict, finding in wrong:
        if any(seg.strip() not in src for seg in quote.split('……')):
            errors.append((tag, quote[:30]))
        out += f'        claim({q(quote)}, {V[verdict]}, {q(finding)})\n'
    return out + '    }\n'

mods = []
for gid, title, desc, items in GROUPS:
    name = ''.join(w.capitalize() for w in gid.split('-'))
    body = ''.join(entry(k, n) for k, n in items)
    mods.append(f'''val {name} = module("{gid}", "提案：{title}") {{
    keywords = "{desc}"
    overview = """
        这一类共 {len(items)} 个提案。每个提案依次说明：用途、技术栈、做法、在这台投影仪上是否可行、参考项目、发展方向，以及原理和注意事项。原方案中与实测不符的说法，用核对卡片标出。
    """
    proposal()

{body}
    related("review-summary", "spec-hub", "spec-tools")
}}
''')

rows = []
for kind, d in (('Go', GO), ('Rust', RS)):
    for n in sorted(d):
        rows.append(f'            {q(f"{d[n][0]} · {kind}")} to {q(d[n][4])},')

summary = f'''val ReviewSummary = module("review-summary", "提案总表：109 个小项目的去向") {{
    keywords = "Go 60 个 · Rust 49 个 · 第一期 / 第二期 / 第三期 / 不纳入 / 不可行"
    overview = """
        共收集了 109 个可在投影仪上运行的小项目提案（Go 60 个、Rust 49 个）。审核原则：只采纳在这台投影仪上**实测可行**、日常**确有用途**、且**不重复**的项目；Go 项目并入 z6x-hub（常驻服务，在本项目内实现）；Rust 项目归入 z6x-tools（命令集），因 Deck 上无法完成 Rust 编译，目前暂缓。
        各项目的用途、技术栈、原理和判断依据见五个分类页。
    """
    proposal()

    why("统计") {{
        facts(
            "第一期" to "hub：core、files、paste、control、wol、metrics、speed；tools（暂缓）：sys、ports、key、keymap、hash、watch、ping",
            "第二期" to "hub：notify、lanscan、webshell、dlna、配置页、iperf3；tools（暂缓）：iobench、pack、run",
            "第三期候选" to "离线下载、相册、阅读、直播源、MQTT、HomeKit、Tailscale、mDNS、终端会话保持等",
            "不可行" to "依赖 root、1024 以下端口、抓包权限、声卡、tun、cgroup、蓝牙 hci、CEC、hidg0 或 fb0 的项目",
            "重复合并" to "WebDAV、网页终端、MQTT、监控、DNS、代理等在 Go 和 Rust 中各有一份方案，只保留其一",
        )
    }}

    story("「不纳入」的项目类型") {{
        text("""
            1. 已有现成方案：DNS 去广告、加密 DNS、局域网代理、PAC、用户态隧道（由 Clash 负责）；SMB、S3、SFTP、Git 仓库（文件共享已由 WebDAV 覆盖）；Go 与 Rust 的重复实现。
            2. 需要将投影仪暴露到公网：路由器端口映射、动态域名、HTTPS 证书、Tailscale 中继。本项目只在家庭局域网内使用。
            3. 依赖外网服务或目前没有需求：影视刮削、字幕下载、RSS 追更、磁力转种子、直播录制、定时测速、网盘挂载、传感器数据存储、视频切片。需求变化时可从第三期候选中提前。
            4. 偏重技术演示，日常没有使用场景：共享内存、只读索引库、日志脱敏、UDP 纠错、组播信令、P2P 分发、限速代理、浏览器网页投屏。
        """)
    }}

    verify("各项目的去向") {{
        facts(
{chr(10).join(rows)}
        )
    }}

    related("spec-hub", "spec-tools", "review-files", "review-media", "review-network", "review-control", "review-system")
}}
'''

if errors:
    print('agy 原话在旧代码里找不到：', errors); sys.exit(1)
out = root + '/composeApp/src/commonMain/kotlin/z6x/content/proposals/Reviews.kt'
open(out, 'w').write('package z6x.content.proposals\n\nimport z6x.framework.Verdict\nimport z6x.framework.module\n\n'
    '// 由 notes/gen.py 根据 notes/proposals.py 生成，不要直接改这个文件。\n\n' + summary + '\n' + '\n'.join(mods))
print('ok', sum(len(g[3]) for g in GROUPS), '条')
