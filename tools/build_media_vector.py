#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""矢量化重制 Gym visual 演示素材。

源图只有 180x180。直接插值放大会发软，所以先把裁好的画面做 LANCZOS
预放大，再用 vtracer 描成 SVG，最后用 resvg 渲染到目标尺寸。输出仍是
WebP（Android 不理解 SVG），但线条来自矢量路径，边缘比位图插值干净。

静态图在 960 尺寸下会暴露描摹分层产生的色带，所以用更高保真的描摹
参数，渲染后加 0.5px 柔化；动画帧尺寸小、又在运动，保留更省时的参数。

- img/<id>.webp   960x960，静态缩略图 / 详情占位
- anim/<id>.webp  640x640，逐帧矢量化后合成的动态 WebP
- 保留 GIF 的逐帧时长与循环标记

媒体 © Gym visual（https://gymvisual.com/），用户已购授权。
"""
import argparse
import io
import json
import os
import shutil
import tempfile
import time
from concurrent.futures import ProcessPoolExecutor

from PIL import Image
import resvg_py
import vtracer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC_ROOT = "E:/Code/Gym"
DATA = os.path.join(ROOT, "app", "src", "main", "assets", "exercises.json")
DEFAULT_OUT = os.path.join(ROOT, ".data", "assets-vector")

STILL_SIZE = 960
STILL_Q = 84
ANIM_SIZE = 640
ANIM_Q = 58
PAD = 0.08
BG_CUT = 245
STILL_SCALE = 3
ANIM_SCALE = 2

STILL_TRACE = dict(
    colormode="color",
    hierarchical="stacked",
    mode="spline",
    filter_speckle=0,
    color_precision=8,
    layer_difference=1,
    corner_threshold=60,
    length_threshold=4.0,
    path_precision=8,
)
STILL_SOFTEN = 0.5

ANIM_TRACE = dict(
    colormode="color",
    hierarchical="stacked",
    mode="spline",
    filter_speckle=1,
    color_precision=8,
    layer_difference=8,
    corner_threshold=50,
    length_threshold=2.0,
    path_precision=8,
)


def load_gif(path):
    im = Image.open(path)
    frames, durations = [], []
    while True:
        frames.append(im.convert("RGB").copy())
        durations.append(im.info.get("duration") or 100)
        try:
            im.seek(im.tell() + 1)
        except EOFError:
            break
    return frames, durations


def crop_box(frames):
    x0, y0, x1, y1 = 180, 180, 0, 0
    for im in frames:
        bb = im.convert("L").point(lambda v: 255 if v < BG_CUT else 0).getbbox()
        if bb:
            x0, y0 = min(x0, bb[0]), min(y0, bb[1])
            x1, y1 = max(x1, bb[2]), max(y1, bb[3])
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


def square(im, box):
    out = Image.new("RGB", (box[2] - box[0], box[3] - box[1]), "white")
    out.paste(im.crop(box), (0, 0))
    return out


def vector_frame(im, tmp_svg, size, scale, trace, soften=0.0):
    big = im.resize((im.width * scale, im.height * scale), Image.LANCZOS)
    big.save(tmp_svg + ".png")
    vtracer.convert_image_to_svg_py(tmp_svg + ".png", tmp_svg, **trace)
    raw = resvg_py.svg_to_bytes(svg_path=tmp_svg, width=size, height=size)
    out = Image.open(io.BytesIO(bytes(raw))).convert("RGB")
    if soften:
        from PIL import ImageFilter
        out = out.filter(ImageFilter.GaussianBlur(soften))
    return out


def convert(job):
    rid, still_rel, gif_rel, out_root, mode = job
    img_dir = os.path.join(out_root, "img")
    anim_dir = os.path.join(out_root, "anim")
    try:
        gif = os.path.join(SRC_ROOT, gif_rel)
        frames, durations = load_gif(gif)
        box = crop_box(frames)
        size = 0

        with tempfile.TemporaryDirectory(prefix="yl-vec-") as td:
            svg = os.path.join(td, "frame.svg")

            if mode in ("all", "stills"):
                still_src = os.path.join(SRC_ROOT, still_rel) if still_rel else ""
                still = Image.open(still_src).convert("RGB") if os.path.exists(still_src) else frames[0]
                dst = os.path.join(img_dir, rid + ".webp")
                vector_frame(square(still, box), svg, STILL_SIZE, STILL_SCALE,
                             STILL_TRACE, STILL_SOFTEN).save(
                    dst, "WEBP", quality=STILL_Q, method=6)
                size += os.path.getsize(dst)

            if mode in ("all", "anims"):
                out = [vector_frame(square(f, box), svg, ANIM_SIZE, ANIM_SCALE,
                                    ANIM_TRACE) for f in frames]
                dst = os.path.join(anim_dir, rid + ".webp")
                out[0].save(dst, save_all=True, append_images=out[1:],
                            duration=durations, loop=0, quality=ANIM_Q, method=6)
                size += os.path.getsize(dst)
        return rid, size, len(frames), None
    except Exception as e:  # noqa: BLE001
        return rid, 0, 0, repr(e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--ids", default="")
    ap.add_argument("--workers", type=int, default=8)
    ap.add_argument("--mode", choices=["all", "stills", "anims"], default="all")
    args = ap.parse_args()

    items = json.load(open(DATA, encoding="utf-8"))["items"]
    records = {r["id"]: r for r in json.load(
        open(os.path.join(ROOT, ".data", "gym.json"), encoding="utf-8"))}
    selected = set(x.strip() for x in args.ids.split(",") if x.strip())

    jobs = []
    for it in items:
        rid = it["id"]
        if selected and rid not in selected:
            continue
        rec = records.get(rid)
        if rec:
            jobs.append((rid, rec.get("image") or "", rec["gif_url"], args.out, args.mode))
        if not selected and args.limit and len(jobs) >= args.limit:
            break

    os.makedirs(os.path.join(args.out, "img"), exist_ok=True)
    os.makedirs(os.path.join(args.out, "anim"), exist_ok=True)

    total, frames, errors = 0, 0, []
    started = time.time()
    with ProcessPoolExecutor(max_workers=args.workers) as ex:
        for n, (rid, size, nframes, err) in enumerate(ex.map(convert, jobs, chunksize=1), 1):
            if err:
                errors.append((rid, err))
            else:
                total += size
                frames += nframes
            if n % 50 == 0 or n == len(jobs):
                print("  %d/%d  %.1fMB  %.1fs" % (
                    n, len(jobs), total / 1048576, time.time() - started), flush=True)

    stills = sum(os.path.getsize(os.path.join(args.out, "img", f))
                 for f in os.listdir(os.path.join(args.out, "img")) if f.endswith(".webp"))
    print("%s 模式：%d 条 / %d 帧，本次 %.1fMB；静态图总量 %.1fMB；错误 %d；用时 %.1f 分钟"
          % (args.mode, len(jobs) - len(errors), frames, total / 1048576,
             stills / 1048576, len(errors), (time.time() - started) / 60))
    for rid, err in errors[:10]:
        print("  ERR", rid, err)
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
