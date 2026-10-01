# 用法：python3 notes/gen.py notes
# 根据 notes/proposals.py 生成 composeApp/.../proposals/Reviews.kt。
# 内容全部来自 proposals.py（Claude 重写的说明）；只有错误卡片里的「agy 原话」取自旧代码，并逐字校验确实存在。
import glob, os, re, sys
sys.path.insert(0, sys.argv[1])
from proposals import GO, RS, GROUPS
from proposals_tech import T

root = os.path.abspath(os.path.join(sys.argv[1], '..'))
old = root + '/Z6xToolBox.App/Content/Modules/'
def plain(dirn):
    out = {}
    for f in glob.glob(old + dirn + '/*.cs'):
        out[int(os.path.basename(f).split('_')[0])] = open(f).read().replace('\\"', '"').replace('\\\\', '\\')
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
    out = f'    story({q(f"{tag} · {name}")}) {{\n        facts(\n'
    for k, v in (('用途', use), ('做法', how), ('在这台投影仪上', here), ('去向', f'**{dest}**')):
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
        agy 推荐的小项目中属于这一类的 {len(items)} 个。每个小节：用途、做法、在这台投影仪上行不行、去向。agy 原方案里说错的具体事实，用旧记录卡片标出。
    """
    proposal()

{body}
    related("review-summary", "spec-hub", "spec-tools")
}}
''')

rows = []
for kind, d in (('Go', GO), ('Rust', RS)):
    for n in sorted(d):
        rows.append(f'            {q(f"{kind}-{n:02d} {d[n][0]}")} to {q(d[n][4])},')

summary = f'''val ReviewSummary = module("review-summary", "提案总表：109 个小项目的去向") {{
    keywords = "Go 60 个 · Rust 49 个 · 第一期 / 第二期 / 第三期 / 不纳入 / 不可行"
    overview = """
        agy 推荐过 109 个可以在投影仪上跑的小项目（Go 60 个、Rust 49 个）。审核原则：只做这台投影仪上**实测可行**、日常**用得上**、且**不重复**的；Go 的并入 z6x-hub（常驻服务），Rust 的并入 z6x-tools（命令集）。
        每个项目的用途、做法和判断依据在五个分类页里。
    """
    proposal()

    why("统计") {{
        facts(
            "第一期" to "hub：core、files、paste、control、wol、metrics、speed；tools：sys、ports、key、keymap、hash、watch、ping",
            "第二期" to "hub：notify、lanscan、webshell、dlna、配置页、iperf3；tools：iobench、pack、run",
            "第三期候选" to "离线下载、相册、阅读、直播源、MQTT、HomeKit、Tailscale、mDNS、终端会话保持等",
            "不可行" to "需要 root、1024 以下端口、抓包权限、声卡、tun、cgroup、蓝牙 hci、CEC、hidg0、fb0 的项目",
            "重复合并" to "WebDAV、网页终端、MQTT、监控、DNS、代理等在 Go 和 Rust 里各写过一遍，只保留一份",
        )
    }}

    story("「不纳入」的都是些什么") {{
        text("""
            1. 已经有现成的在做：DNS 去广告、加密 DNS、局域网代理、PAC、用户态隧道（Clash 已负责）；SMB、S3、SFTP、Git 仓库（WebDAV 已覆盖文件共享）；Go 和 Rust 的重复实现。
            2. 要把投影仪暴露到公网：路由器端口映射、动态域名、HTTPS 证书、Tailscale 中继。本项目只在家里局域网用。
            3. 依赖外网服务，或者目前用不上：影视刮削、字幕下载、RSS 追更、磁力转种子、直播录制、定时测速、网盘挂载、传感器数据存储、视频切片。需求变了可以从第三期候选里提上来。
            4. 偏技术演示，日常没有使用场景：共享内存、只读索引库、日志脱敏、UDP 纠错、组播信令、P2P 分发、限速代理、浏览器网页投屏。
        """)
    }}

    verify("逐个去向") {{
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
