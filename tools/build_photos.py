# -*- coding: utf-8 -*-
"""把下载到的实拍图裁切、统一比例、压缩成 WebP，放进 assets/img/<slug>/0|1.webp

裁剪思路：
  1) 两帧（起始位/结束位）做差 -> 会动的肢体区域
  2) 局部梯度能量 -> 人所在的高对比区域（地面/墙面一般很平）
  3) 两者合并取外接框，按分位数去掉离群点
  4) 外接框扩展留白，限制放大倍数，统一 3:2
"""
import json, os, sys
from PIL import Image, ImageFilter, ImageChops

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'tools', 'cache', 'photos')
DST = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'img')
MAXW, MAXH = 780, 520          # 3:2
QUALITY = 74
ASPECT = 3 / 2


def edge_mask(im, thresh=26):
    g = im.convert('L').resize((im.width // 4, im.height // 4))
    e = g.filter(ImageFilter.FIND_EDGES)
    return e.point(lambda v: 255 if v > thresh else 0)


def bbox_of(mask, keep=0.985):
    w, h = mask.size
    px = mask.load()
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            if px[x, y]:
                xs.append(x)
                ys.append(y)
    if len(xs) < 40:
        return None
    xs.sort(); ys.sort()
    n = len(xs)
    lo = int(n * (1 - keep) / 2)
    hi = max(lo + 1, int(n * (1 + keep) / 2))
    return xs[lo] * 4, ys[lo] * 4, xs[min(hi, n - 1)] * 4, ys[min(hi, n - 1)] * 4


def union(a, b):
    if a is None: return b
    if b is None: return a
    return min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3])


def diff_bbox(im0, im1):
    if im1 is None:
        return None
    a = im0.convert('L').resize((im0.width // 4, im0.height // 4))
    b = im1.convert('L').resize((im0.width // 4, im0.height // 4))
    d = ImageChops.difference(a, b).point(lambda v: 255 if v > 22 else 0)
    d = d.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(5))
    return bbox_of(d, keep=0.97)


def crop_box(im0, im1):
    w, h = im0.size
    m = edge_mask(im0)
    if im1 is not None:
        m = ImageChops.lighter(m, edge_mask(im1))
    box = union(bbox_of(m), diff_bbox(im0, im1))
    if box is None:
        box = (0, 0, w, h)
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    bw, bh = max(x1 - x0, 40), max(y1 - y0, 40)
    # 留白
    bw *= 1.12
    bh *= 1.18
    # 限制放大（避免把人物怼满整屏）
    bw = max(bw, w * 0.45)
    bh = max(bh, h * 0.45)
    # 统一宽高比，并严格收进原图内，避免 crop 越界补黑边
    if bw / bh < ASPECT:
        bw = bh * ASPECT
    else:
        bh = bw / ASPECT
    bw = min(bw, float(w))
    bh = bw / ASPECT
    if bh > h:
        bh = float(h)
        bw = bh * ASPECT
    left = cx - bw / 2
    top = cy - bh / 2
    left = max(0.0, min(left, w - bw))
    top = max(0.0, min(top, h - bh))
    return (int(left), int(top), int(left + bw), int(top + bh))


def slug(ex):
    s = ex['id'].replace(' ', '_')
    return s


def main():
    d = json.load(open(os.path.join(ROOT, 'tools', 'cache', 'gym.zh.json'), encoding='utf-8'))
    sel = [x for x in d if x['equipment'] == 'body only' or x['equipment'] is None]
    os.makedirs(DST, exist_ok=True)
    total = 0
    for ex in sel:
        p0 = os.path.join(CACHE, '%s_0.jpg' % ex['id'])
        p1 = os.path.join(CACHE, '%s_1.jpg' % ex['id'])
        if not os.path.exists(p0):
            print('MISS', ex['id']); continue
        im0 = Image.open(p0).convert('RGB')
        im1 = Image.open(p1).convert('RGB') if os.path.exists(p1) else None
        base = (im0, im1) if im1 is not None else (im0, im0)
        box = crop_box(*base)
        out = os.path.join(DST, slug(ex))
        os.makedirs(out, exist_ok=True)
        for i, im in enumerate((im0, im1)):
            if im is None:
                continue
            c = im.crop(box)
            c.thumbnail((MAXW, MAXH), Image.LANCZOS)
            fp = os.path.join(out, '%d.webp' % i)
            c.save(fp, 'WEBP', quality=QUALITY, method=5)
            total += os.path.getsize(fp)
    print('images size %.1f MB' % (total / 1048576))


if __name__ == '__main__':
    main()
