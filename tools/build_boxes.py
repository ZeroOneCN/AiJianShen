# -*- coding: utf-8 -*-
"""给每个动作算一个「主体框」，让不同动作在同样大小的卡片里看起来一样大。

素材是 960x960 白底，但人物在画面里的大小差别很大（占比 18%–100%），直接等比
缩放会出现「这张人很小、那张顶到边」。这里离线算全部内容（静图 + 动图关键帧的并集，
保证动起来不会跑到框外），归一化后写进 assets/boxes.json，运行时 PhotoView 按框
做 fitCenter。

用法：python tools/build_boxes.py
"""
import json
import os
from PIL import Image, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'app', 'src', 'main', 'assets')
SRC = os.path.join(ASSETS, 'img')
ANIM = os.path.join(ASSETS, 'anim')
OUT = os.path.join(ASSETS, 'boxes.json')

THRESH = 10      # 与纯白的差异阈值（0-255）
PAD = 0.035      # 四周留白，占画面边长比例
WORK = 192       # 先缩到 192 再算，快且够准
MAX_FRAMES = 6   # 动图最多抽这么多帧


def bbox_px(im):
    im = im.convert('L')
    if im.size != (WORK, WORK):
        im = im.resize((WORK, WORK), Image.BILINEAR)
    diff = ImageChops.difference(im, Image.new('L', im.size, 255))
    return diff.point(lambda v: 255 if v > THRESH else 0).getbbox()


def union_box(img_id):
    l = t = WORK
    r = b = 0
    seen = False

    boxes = []
    still = os.path.join(SRC, img_id + '.webp')
    if os.path.exists(still):
        boxes.append(bbox_px(Image.open(still)))
    anim = os.path.join(ANIM, img_id + '.webp')
    if os.path.exists(anim):
        try:
            a = Image.open(anim)
            n = getattr(a, 'n_frames', 1)
            step = max(1, n // MAX_FRAMES)
            for i in range(0, n, step):
                a.seek(i)
                boxes.append(bbox_px(a))
        except Exception:
            pass

    for bb in boxes:
        if not bb:
            continue
        seen = True
        l, t = min(l, bb[0]), min(t, bb[1])
        r, b = max(r, bb[2]), max(b, bb[3])
    if not seen:
        return [0.0, 0.0, 1.0, 1.0]

    l, t, r, b = [v / float(WORK) for v in (l, t, r, b)]
    # 极窄的线（伸直的腿、杠铃杆）撑不满画面，给最小边长，避免过度放大
    min_side = 0.32
    if r - l < min_side:
        c = (l + r) / 2.0
        l, r = c - min_side / 2.0, c + min_side / 2.0
    if b - t < min_side:
        c = (t + b) / 2.0
        t, b = c - min_side / 2.0, c + min_side / 2.0
    l, t = l - PAD, t - PAD
    r, b = r + PAD, b + PAD
    # 夹回画面内，同时保持边长不缩水（否则又回到大小不一）
    if l < 0:
        r -= l
        l = 0
    if t < 0:
        b -= t
        t = 0
    if r > 1:
        l -= r - 1
        r = 1
    if b > 1:
        t -= b - 1
        b = 1
    l, t = max(0.0, l), max(0.0, t)
    return [round(l, 3), round(t, 3), round(r, 3), round(b, 3)]


def main():
    if not os.path.isdir(SRC):
        raise SystemExit('缺少 %s，先跑 tools/build_media.py' % SRC)
    ids = sorted(f[:-5] for f in os.listdir(SRC) if f.endswith('.webp'))
    out = {}
    for i, k in enumerate(ids):
        out[k] = union_box(k)
        if i % 150 == 0:
            print('  %d/%d' % (i, len(ids)))
    with open(OUT, 'w', encoding='utf-8') as fp:
        json.dump(out, fp, separators=(',', ':'))
    sides = [max(v[2] - v[0], v[3] - v[1]) for v in out.values()]
    print('写出 %d 个主体框 -> %s (%.1f KB)' %
          (len(out), os.path.relpath(OUT, ROOT), os.path.getsize(OUT) / 1024.0))
    print('框最长边 min %.2f / avg %.2f / max %.2f' %
          (min(sides), sum(sides) / len(sides), max(sides)))


if __name__ == '__main__':
    main()
