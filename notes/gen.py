import re, glob, sys
sys.path.insert(0, sys.argv[1])
from decisions import G, R, GROUPS
base='/home/deck/Games/claude/Z6xToolBox/Z6xToolBox.App/Content/Modules/'
def load(dirn):
    out={}
    for f in glob.glob(base+dirn+'/*.cs'):
        n=int(os.path.basename(f).split('_')[0]); s=open(f).read()
        def field(k):
            m=re.search(k+r'\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)',s)
            return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',m.group(1))).replace('\\"','"').replace('\\\\','\\')
        title=re.sub(r'^\d+\.\s*','',field('Title'))
        plain=s.replace('\\"','"').replace('\\\\','\\')
        out[n]=(title, field('Summary'), plain, os.path.relpath(f, '/home/deck/Games/claude/Z6xToolBox'))
    return out
import os
GO=load('GoServices'); RS=load('RustServices')
assert len(GO)==60 and len(RS)==49
V={'C':'Verdict.Confirmed','U':'Verdict.Unverified','D':'Verdict.Disproved'}
def kt(s): return s.replace('$','${\'$\'}')
errors=[]
def card(kind,n):
    src=GO if kind=='G' else RS; d=(G if kind=='G' else R)[n]
    title,summ,plain,path=src[n]
    tag=('Go' if kind=='G' else 'Rust')+f'-{n:02d}'
    orig=f'**{tag}《{title}》**\n{summ}'
    if d[3]:
        if any(seg.strip() not in plain for seg in d[3].split('……')): errors.append((tag,d[3][:40]))
        orig+=f'\n原文说：「{d[3]}」'
    find=f'**去向：{d[1]}**\n{d[2]}\n原文：`{path}`'
    return f'        claim(\n            """{kt(orig)}""",\n            {V[d[0]]},\n            """{kt(find)}""",\n        )\n'
files={}
for gid,title,desc,items in GROUPS:
    name=''.join(w.capitalize() for w in gid.split('-'))
    body=''.join(card(k,n) for k,n in items)
    files[gid]=f'''val {name} = module("{gid}", "{title}") {{
    keywords = "{desc}"
    overview = """
        agy 提案逐篇审核（{len(items)} 篇）。每张卡片：原文标题和摘要照录；有具体技术说法的另外摘一句原文；「实测」写去向和依据。全文见旧 C# 文件。
    """
    proposal()

    audit("逐篇审核") {{
{body}    }}

    related("review-summary", "spec-hub", "spec-tools")
}}
'''
# summary table
rows=[]
for kind,src,d in (('Go',GO,G),('Rust',RS,R)):
    for n in sorted(d): rows.append(f'            "{kind}-{n:02d} {src[n][0].split('：')[0]}" to "{d[n][1]}",')
summary=f'''val ReviewSummary = module("review-summary", "审核总表：109 篇提案的去向") {{
    keywords = "Go 60 篇 · Rust 49 篇 · 纳入 / 第二期 / 第三期 / 不纳入 / 不可行"
    overview = """
        agy 推荐了 109 个小项目（Go 60、Rust 49）。审核原则：只做这台投影仪上**实测可行**、对日常使用**有价值**、且**不重复**的；Go 的进 z6x-hub（常驻服务），Rust 的进 z6x-tools（命令集）。
        每篇的原文摘录、实测依据在五个分类审核页里。
    """
    proposal()

    why("统计") {{
        facts(
            "纳入第一期" to "hub：core、files、paste、control、wol、metrics、speed；tools：sys、ports、key、keymap、hash、watch、ping",
            "第二期" to "hub：notify、lanscan、webshell、dlna、配置页、iperf3；tools：iobench、pack、run",
            "第三期候选" to "离线下载、相册、阅读、直播源、MQTT、HomeKit、Tailscale、mDNS、终端复用等",
            "不可行（实测依据）" to "需要 root / 1024 以下端口 / CAP_NET_RAW / 声卡 / tun / cgroup / hci / cec / hidg0 / fb0 的提案",
            "重复合并" to "WebDAV、网页终端、MQTT、监控导出、DNS、代理等在 Go 和 Rust 里各写过一遍，只保留一份",
        )
    }}

    verify("逐篇去向") {{
        facts(
{chr(10).join(rows)}
        )
    }}

    related("spec-hub", "spec-tools", "review-files", "review-media", "review-network", "review-control", "review-system")
}}
'''
if errors:
    print("引用原文找不到：", errors); sys.exit(1)
out='/home/deck/Games/claude/Z6xToolBox/composeApp/src/commonMain/kotlin/z6x/content/proposals/Reviews.kt'
open(out,'w').write('package z6x.content.proposals\n\nimport z6x.framework.Verdict\nimport z6x.framework.module\n\n// 由 notes/gen.py 根据 notes/decisions.py 生成：每张卡片的原文标题和摘要直接取自 agy 的旧 C# 文件。\n// 要改审核结论，改 decisions.py 后运行 python3 notes/gen.py notes 重新生成，不要直接改这个文件。\n\n'+summary+'\n'+'\n'.join(files.values()))
print("ok", sum(len(g[3]) for g in GROUPS))
