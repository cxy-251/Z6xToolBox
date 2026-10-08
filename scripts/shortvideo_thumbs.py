#!/usr/bin/env python3
"""在 Deck 上为资源库中的短视频生成封面，放进资源库本身，随作品一起移动。

用法：
  ./scripts/shortvideo_thumbs.py [资源库根目录…] [--jobs 3] [--only 抖音/博主名] [--limit N] [--dry-run]
  不指定根目录时处理 ~/Games/omni_library 与 SD 卡上的 omni_library。

封面位置（与 hub 的约定，见 hub/internal/modules/library/media.go 的 thumbDir）：
  视频 <博主>/abc.mp4      → <博主>/.thumbs/abc.mp4.webp
  图集 <博主>/abc/（图片） → <博主>/.thumbs/abc.webp
.thumbs 以点开头，omni-deck 与 hub 扫描作品时都会跳过；移动博主文件夹时封面一起移动，不必重新生成。

来源：omni-deck 已生成过的封面（其 var/cache/thumbs，按「源文件绝对路径|标签」的 SHA1 命名）能对上的直接复制，
其余用 ffmpeg 生成：视频取第 0.5 秒的一帧，图集取第一张图，缩到 360 像素宽的 WebP。
已存在且不旧于作品的封面跳过，因此可以随时中断、重新运行只补新作品。以最低优先级运行（nice 19、ionice idle）。
只读取 omni-deck 的缓存，不修改 omni-deck。
"""
import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

VIDEO_EXTS = {".mp4", ".webm", ".mov", ".m4v", ".mkv"}
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"}
THUMB_DIR = ".thumbs"
WIDTH = 360
OMNI_THUMBS = os.environ.get("OMNI_THUMBS", os.path.expanduser("~/Games/omni-deck/var/cache/thumbs"))
DEFAULT_ROOTS = [os.path.expanduser("~/Games/omni_library"), "/run/media/deck/FUCKDECK/omni_library"]
LOW = ["nice", "-n", "19", "ionice", "-c", "3"]


def natural_key(s):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]


def omni_cache(src, tag):
    key = hashlib.sha1((os.path.abspath(src) + "|" + tag).encode("utf-8")).hexdigest()
    p = os.path.join(OMNI_THUMBS, key + ".webp")
    return p if os.path.isfile(p) else None


def jobs_in(platform_dir, only):
    """遍历一个平台目录，产出 (源文件, 封面路径, omni 标签)。图集以第一张图为源。"""
    for root, dirs, files in os.walk(platform_dir):
        dirs[:] = sorted(d for d in dirs if not d.startswith("."))
        rel = os.path.relpath(root, platform_dir)
        if only and not (rel + "/").startswith(only.rstrip("/") + "/") and rel != ".":
            continue
        depth = 0 if rel == "." else rel.count(os.sep) + 1
        files = [f for f in files if not f.startswith(".")]
        for f in files:
            if os.path.splitext(f)[1].lower() in VIDEO_EXTS:
                if only and rel == ".":
                    continue
                yield os.path.join(root, f), os.path.join(root, THUMB_DIR, f + ".webp"), "vthumb"
        # 图集：博主目录下的子目录（至少两级），直接含图片
        imgs = sorted((f for f in files if os.path.splitext(f)[1].lower() in IMAGE_EXTS), key=natural_key)
        if depth >= 2 and imgs:
            parent, name = os.path.split(root)
            yield os.path.join(root, imgs[0]), os.path.join(parent, THUMB_DIR, name + ".webp"), "gthumb"


def make(job, dry):
    src, dst, tag = job
    try:
        if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
            return "skip"
    except OSError:
        return "fail"
    if dry:
        return "reuse" if omni_cache(src, tag) else "todo"
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    tmp = dst + ".tmp.webp"
    cached = omni_cache(src, tag)
    if cached:
        shutil.copyfile(cached, tmp)
        os.replace(tmp, dst)
        return "reuse"
    scale = f"scale='min({WIDTH},iw)':-2"
    attempts = ([["-ss", "0.5", "-i", src], ["-i", src]] if tag == "vthumb" else [["-i", src]])
    for inp in attempts:
        cmd = LOW + ["ffmpeg", "-v", "error", "-y"] + inp + ["-frames:v", "1", "-vf", scale, "-c:v", "libwebp", "-quality", "70", tmp]
        r = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if r.returncode == 0 and os.path.getsize(tmp) > 0:
            os.replace(tmp, dst)
            return "made"
    if os.path.exists(tmp):
        os.remove(tmp)
    return "fail"


def main():
    ap = argparse.ArgumentParser(description="为资源库中的短视频生成封面（放在作品目录的 .thumbs/ 下）")
    ap.add_argument("roots", nargs="*", default=DEFAULT_ROOTS)
    ap.add_argument("--jobs", type=int, default=3, help="同时运行的 ffmpeg 数（默认 3）")
    ap.add_argument("--only", default="", help="只处理某个平台或博主，如 抖音 或 抖音/博主名")
    ap.add_argument("--limit", type=int, default=0, help="最多处理多少个作品（测试用）")
    ap.add_argument("--dry-run", action="store_true", help="只统计需要生成多少个，不写文件")
    a = ap.parse_args()

    jobs = []
    for root in a.roots:
        base = os.path.join(root, "media_library", "shortvideo")
        if not os.path.isdir(base):
            print(f"跳过（没有短视频目录）：{root}")
            continue
        for plat in sorted(os.listdir(base)):
            pdir = os.path.join(base, plat)
            if plat.startswith(".") or not os.path.isdir(pdir):
                continue
            pl, _, who = a.only.partition("/")
            if pl and pl != plat:
                continue
            jobs.extend(jobs_in(pdir, who))
    if a.limit:
        jobs = jobs[: a.limit]
    print(f"共 {len(jobs)} 个作品；omni-deck 封面缓存：{OMNI_THUMBS}", flush=True)

    stats = {}
    with ThreadPoolExecutor(max_workers=max(1, a.jobs)) as ex:
        for i, r in enumerate(ex.map(lambda j: make(j, a.dry_run), jobs), 1):
            stats[r] = stats.get(r, 0) + 1
            if i % 1000 == 0 or i == len(jobs):
                print(f"[{i}/{len(jobs)}] " + "，".join(f"{k} {v}" for k, v in sorted(stats.items())), flush=True)
    names = {"skip": "已有", "reuse": "复用 omni-deck", "made": "新生成", "fail": "失败", "todo": "待生成"}
    print("完成：" + "，".join(f"{names.get(k, k)} {v}" for k, v in sorted(stats.items())))
    return 1 if stats.get("fail") else 0


if __name__ == "__main__":
    sys.exit(main())
