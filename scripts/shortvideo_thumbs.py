#!/usr/bin/env python3
"""为资源库中的短视频生成封面，放进资源库本身，随作品一起移动。在手机的 Termux 中运行（也可在 Deck 上运行）。

用法：
  ./scripts/shortvideo_thumbs.py [资源库根目录…] [--jobs 3] [--only 抖音/博主名] [--limit N] [--dry-run]
  不指定根目录时：手机上处理 /storage/emulated/0/omni_library，Deck 上处理 ~/Games/omni_library 与 SD 卡上的 omni_library。

手机上按温度自动调节，避免发烫（Termux 作为普通应用读不到屏幕亮灭与电池状态，只能读温度传感器；
使用手机时温度上升，脚本随之减速）：机身温度（quiet_therm 与 battery 取较高者）低于 --cool 时
同时运行 --jobs 个 ffmpeg，介于两者之间时 1 个，达到 --hot 时暂停，降到 --cool + 1 以下再继续。

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
import threading
import time

VIDEO_EXTS = {".mp4", ".webm", ".mov", ".m4v", ".mkv"}
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp"}
THUMB_DIR = ".thumbs"
WIDTH = 360
OMNI_THUMBS = os.environ.get("OMNI_THUMBS", os.path.expanduser("~/Games/omni-deck/var/cache/thumbs"))
PHONE_ROOT = "/storage/emulated/0/omni_library"
DEFAULT_ROOTS = ([PHONE_ROOT] if os.path.isdir(PHONE_ROOT) else
                 [os.path.expanduser("~/Games/omni_library"), "/run/media/deck/FUCKDECK/omni_library"])
LOW = ["nice", "-n", "19"] + (["ionice", "-c", "3"] if shutil.which("ionice") else [])


def thermal_zones(names=("quiet_therm", "battery")):
    """找到机身与电池温度传感器（手机上的普通应用可读）；Deck 上没有，返回空列表。"""
    out = []
    base = "/sys/class/thermal"
    try:
        for z in os.listdir(base):
            try:
                with open(os.path.join(base, z, "type")) as f:
                    if f.read().strip() in names:
                        out.append(os.path.join(base, z, "temp"))
            except OSError:
                pass
    except OSError:
        pass
    return out


def read_temp(zones):
    vals = []
    for z in zones:
        try:
            with open(z) as f:
                vals.append(int(f.read().strip()) / 1000)
        except (OSError, ValueError):
            pass
    return max(vals) if vals else None


class Throttle:
    """按温度调节允许同时运行的任务数：低于 cool 为 jobs，cool～hot 为 1，达到 hot 暂停直到低于 cool+1。"""

    def __init__(self, jobs, cool, hot):
        self.jobs, self.cool, self.hot = jobs, cool, hot
        self.zones = thermal_zones()
        self.allowed, self.running, self.paused = jobs, 0, False
        self.cv = threading.Condition()
        self.temp = None
        if self.zones:
            threading.Thread(target=self.watch, daemon=True).start()

    def watch(self):
        while True:
            t = read_temp(self.zones)
            with self.cv:
                self.temp = t
                if t is not None:
                    if t >= self.hot:
                        self.paused = True
                    elif self.paused and t < self.cool + 1:
                        self.paused = False
                    self.allowed = 0 if self.paused else (self.jobs if t < self.cool else 1)
                self.cv.notify_all()
            time.sleep(5)

    def __enter__(self):
        with self.cv:
            while self.running >= self.allowed:
                self.cv.wait(5)
            self.running += 1

    def __exit__(self, *exc):
        with self.cv:
            self.running -= 1
            self.cv.notify_all()


NAMES = {"skip": "已有", "reuse": "复用 omni-deck", "made": "新生成", "fail": "失败", "todo": "待生成"}


def natural_key(s):
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]


def omni_cache(src, tag):
    key = hashlib.sha1((os.path.abspath(src) + "|" + tag).encode("utf-8")).hexdigest()
    p = os.path.join(OMNI_THUMBS, key + ".webp")
    return p if os.path.isfile(p) else None


def clean_tmp(platform_dir):
    """删除上次中断留下的临时文件（.thumbs/ 下以点开头、以 .tmp 结尾；旧版为 *.tmp.webp）。"""
    n = 0
    for root, dirs, files in os.walk(platform_dir):
        if os.path.basename(root) == THUMB_DIR:
            for f in files:
                if f.endswith(".tmp") or f.endswith(".tmp.webp"):
                    os.remove(os.path.join(root, f))
                    n += 1
            dirs[:] = []
        else:
            dirs[:] = [d for d in dirs if d == THUMB_DIR or not d.startswith(".")]
    return n


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


def log(msg):
    print(time.strftime("%m-%d %H:%M:%S ") + msg, flush=True)


failures = []


def fail(msg):
    """记录失败；前 5 个立即写进日志，便于运行中发现问题（例如 ffmpeg 参数错误导致全部失败）。"""
    failures.append(msg)
    if len(failures) <= 5:
        log("失败：" + msg)


def make(job, dry):
    try:
        return _make(job, dry)
    except Exception as e:  # 单个作品出错不影响其他作品
        fail(f"{job[0]}：{e}")
        return "fail"


def _make(job, dry):
    src, dst, tag = job
    if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        return "skip"
    if dry:
        return "reuse" if omni_cache(src, tag) else "todo"
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    # 临时文件以点开头、不以 .webp 结尾：中断后残留也不会被当作封面（启动时清理）
    tmp = os.path.join(os.path.dirname(dst), "." + os.path.basename(dst) + ".tmp")
    cached = omni_cache(src, tag)
    if cached:
        shutil.copyfile(cached, tmp)
        os.replace(tmp, dst)
        return "reuse"
    scale = f"scale='min({WIDTH},iw)':-2"
    attempts = ([["-ss", "0.5", "-i", src], ["-i", src]] if tag == "vthumb" else [["-i", src]])
    for inp in attempts:
        cmd = LOW + ["ffmpeg", "-v", "error", "-y"] + inp + ["-frames:v", "1", "-vf", scale, "-c:v", "libwebp", "-quality", "70", "-f", "webp", tmp]
        r = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
        err = r.stderr or ""
        if r.returncode == 0 and os.path.exists(tmp) and os.path.getsize(tmp) > 0:
            os.replace(tmp, dst)
            return "made"
    if os.path.exists(tmp):
        os.remove(tmp)
    fail(f"{src}：ffmpeg 无法生成封面（{err.strip()[-200:] or '没有输出'}）")
    return "fail"


def main():
    ap = argparse.ArgumentParser(description="为资源库中的短视频生成封面（放在作品目录的 .thumbs/ 下）")
    ap.add_argument("roots", nargs="*", default=DEFAULT_ROOTS)
    ap.add_argument("--jobs", type=int, default=3, help="不热时同时运行的 ffmpeg 数（默认 3）")
    ap.add_argument("--cool", type=float, default=36, help="低于此温度（°C）全速（默认 36）")
    ap.add_argument("--hot", type=float, default=40, help="达到此温度（°C）暂停（默认 40）")
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
            if not a.dry_run:
                n = clean_tmp(pdir)
                if n:
                    log(f"清理了上次中断留下的 {n} 个临时文件：{pdir}")
            jobs.extend(jobs_in(pdir, who))
    if a.limit:
        jobs = jobs[: a.limit]
    log(f"开始：共 {len(jobs)} 个作品" + (f"；复用 omni-deck 的封面缓存 {OMNI_THUMBS}" if os.path.isdir(OMNI_THUMBS) else ""))

    stats, done, lock = {}, [0], threading.Lock()
    th = Throttle(max(1, a.jobs), a.cool, a.hot)
    if th.zones:
        log(f"温度控制：低于 {a.cool}°C 同时 {a.jobs} 个，{a.cool}～{a.hot}°C 1 个，{a.hot}°C 以上暂停")
    it = iter(jobs)

    def worker():
        while True:
            with lock:
                job = next(it, None)
            if job is None:
                return
            with th:
                r = make(job, a.dry_run)
            with lock:
                stats[r] = stats.get(r, 0) + 1
                done[0] += 1
                if done[0] % 500 == 0 or done[0] == len(jobs):
                    temp = f"，{th.temp:.1f}°C" + ("（暂停中）" if th.paused else "") if th.temp is not None else ""
                    log(f"[{done[0]}/{len(jobs)}] " + "，".join(f"{NAMES.get(k, k)} {v}" for k, v in sorted(stats.items())) + temp)

    ts = [threading.Thread(target=worker) for _ in range(max(1, a.jobs))]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    for f in failures[5:20]:
        log("失败：" + f)
    if len(failures) > 20:
        log(f"……另有 {len(failures) - 20} 个失败")
    log("完成：" + "，".join(f"{NAMES.get(k, k)} {v}" for k, v in sorted(stats.items())))
    return 1 if stats.get("fail") else 0


if __name__ == "__main__":
    sys.exit(main())
