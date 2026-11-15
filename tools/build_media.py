#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 Gym visual 的缩略图与演示 GIF 处理成可直接融合白底的资源。

- 统一裁掉纯白留边（按 GIF 全部帧的并集求包围盒，轻微留白）
- 缩略图 360x360、动图 320x320，LANCZOS 放大 + 轻度锐化
- img/<id>.webp   静态缩略图，列表/占位用
- anim/<id>.webp  动态 WebP，详情页/训练页循环播放

媒体 © Gym visual（https://gymvisual.com/），用户已购授权。
"""
import json
import os
import sys
from concurrent.futures import ProcessPoolExecutor

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC_ROOT = "E:/Code/Gym"
DATA = os.path.join(ROOT, "app", "src", "main", "assets", "exercises.json")
IMG_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "img")
ANIM_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "anim")

STILL_SIZE = 360
STILL_Q = 86
ANIM_SIZE = 320
ANIM_Q = 62
PAD = 0.08
BG_CUT = 245


def crop_box(paths):
    """所有帧的非白像素并集，扩成正方形并留 PAD 白边。"""
    x0, y0, x1, y1 = 180, 180, 0, 0
    for path in paths:
        im = Image.open(path)
        while True:
            gray = im.convert("L")
            bb = gray.point(lambda v: 255 if v < BG_CUT else 0).getbbox()
            if bb:
                x0, y0 = min(x0, bb[0]), min(y0, bb[1])
                x1, y1 = max(x1, bb[2]), max(y1, bb[3])
            try:
                im.seek(im.tell() + 1)
            except EOFError:
                break
    if x0 >= x1 or y0 >= y1:
        return (0, 0, 180, 180)
    side = min(180.0, max(x1 - x0, y1 - y0) * (1 + PAD * 2))
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    left = max(0.0, cx - side / 2.0)
    top = max(0.0, cy - side / 2.0)
    if left + side > 180:
        left = 180 - side
    if top + side > 180:
        top = 180 - side
    return (round(left), round(top), round(left + side), round(top + side))


def frames_of(gif):
    im = Image.open(gif)
    out, durations = [], []
    while True:
        out.append(im.convert("RGB"))
        durations.append(im.info.get("duration") or 100)
        try:
            im.seek(im.tell() + 1)
        except EOFError:
            break
    return out, durations


def sharpen(im):
    return im.filter(ImageFilter.UnsharpMask(radius=0.8, percent=40, threshold=3))


def convert(job):
    rid, still_rel, gif_rel = job
    still_src = os.path.join(SRC_ROOT, still_rel)
    gif_src = os.path.join(SRC_ROOT, gif_rel)
    try:
        box = crop_box([gif_src])
        frames, durations = frames_of(gif_src)

        if os.path.exists(still_src):
            still = Image.open(still_src).convert("RGB")
            still = sharpen(still.crop(box).resize((STILL_SIZE, STILL_SIZE), Image.LANCZOS))
            still.save(os.path.join(IMG_OUT, rid + ".webp"),
                       "WEBP", quality=STILL_Q, method=4)

        out = [sharpen(f.crop(box).resize((ANIM_SIZE, ANIM_SIZE), Image.LANCZOS))
               for f in frames]
        dst = os.path.join(ANIM_OUT, rid + ".webp")
        out[0].save(dst, save_all=True, append_images=out[1:],
                    duration=durations, loop=0, quality=ANIM_Q, method=4)
        return rid, os.path.getsize(dst), len(out), None
    except Exception as e:  # noqa: BLE001
        return rid, 0, 0, repr(e)


def main():
    items = json.load(open(DATA, encoding="utf-8"))["items"]
    os.makedirs(IMG_OUT, exist_ok=True)
    os.makedirs(ANIM_OUT, exist_ok=True)
    records = {r["id"]: r for r in json.load(
        open(os.path.join(ROOT, ".data", "gym.json"), encoding="utf-8"))}

    jobs = []
    for it in items:
        rec = records.get(it["id"])
        if rec:
            jobs.append((it["id"], rec["image"], rec["gif_url"]))

    total, frames = 0, 0
    errors = []
    with ProcessPoolExecutor(max_workers=6) as ex:
        for n, (rid, size, nframes, err) in enumerate(ex.map(convert, jobs, chunksize=6), 1):
            if err:
                errors.append((rid, err))
            else:
                total += size
                frames += nframes
            if n % 100 == 0:
                print("  %d/%d  %.1fMB" % (n, len(jobs), total / 1048576), flush=True)

    stills = sum(os.path.getsize(os.path.join(IMG_OUT, f))
                 for f in os.listdir(IMG_OUT) if f.endswith(".webp"))
    print("动图 %d 张 / %d 帧，%.1fMB；缩略图 %.1fMB；错误 %d"
          % (len(jobs) - len(errors), frames, total / 1048576,
             stills / 1048576, len(errors)))
    for rid, err in errors[:10]:
        print("  ERR", rid, err)


if __name__ == "__main__":
    main()
