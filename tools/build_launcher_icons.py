# -*- coding: utf-8 -*-
"""把品牌矢量渲染成位图，写进 mipmap 各密度目录。

为什么需要这个：
  ic_launcher 目前只有 mipmap-anydpi-v26 下的 adaptive icon（XML 矢量）。
  Android 8+ 自己能渲染，但任何「从 APK 抓图标」的第三方工具（应用仓库、
  分发平台、老的 apk-parser 类库）只会去 res/mipmap-*dpi 找位图，
  找不到就退回默认图标——这就是平台上是旧图标的原因。

产物：
  res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png        方形满幅
  res/mipmap-*/ic_launcher_round.png                                  圆形裁剪
  art/ic_launcher_512.png                                             平台上传用的圆角版

渲染路线：SVG → Chrome headless 截图 → PIL 缩放。
本机没有 cairosvg/ImageMagick，浏览器是唯一能正确渲染渐变和贝塞尔曲线的工具。

用法：python tools/build_launcher_icons.py
"""
import json
import os
import shutil
import subprocess
import tempfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')
ART = os.path.join(ROOT, 'art')
PRESETS = os.path.expanduser('~/.trae-cn/skills/brand-icon-system/assets/presets.json')

# 与 build_brand_icon.py 保持同一套规范
BASE_TOP = '#FFFFFF'
BASE_CUT = '#F3F8FD'
BASE_BOTTOM = '#E9F1FA'
STROKE = '#DCE5F1'
GRADIENT = [('#64C1F7', 0), ('#2E93E4', 0.55), ('#1A6DBC', 1)]
CANVAS = 108.0
RADIUS_RATIO = 0.219
GLYPH_ADAPTIVE = 0.65
GLYPH_BRAND = 0.92

DENSITIES = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}

BROWSERS = [
    r'C:\Program Files\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
]

DEFS_HEAD = '''<defs>
<linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="108" y2="108">
  <stop offset="0" stop-color="%(top)s"/>
  <stop offset="0.414" stop-color="%(top)s"/>
  <stop offset="0.42" stop-color="%(cut)s"/>
  <stop offset="1" stop-color="%(bottom)s"/>
</linearGradient>
<linearGradient id="blue" gradientUnits="userSpaceOnUse" x1="140" y1="40" x2="900" y2="1000">
%(stops)s
</linearGradient>
'''

def stops():
    return '\n'.join('  <stop offset="%s" stop-color="%s"/>' % (off, color)
                     for color, off in GRADIENT)

def glyph(ratio):
    """1024 坐标系的图形等比缩放到 ratio 并居中，返回 <g> 片段。"""
    k = CANVAS * ratio / 1024.0
    t = CANVAS / 2 - 512 * k
    return ('<g transform="translate(%.4f,%.4f) scale(%.6f)">' % (t, t, k),
            '</g>')

def build_svg(paths, ratio, shape):
    """shape: square（满幅方形）/ round（圆形裁剪）/ tile（圆角 + 描边）"""
    r = CANVAS * RADIUS_RATIO
    head = '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" ' \
           'viewBox="0 0 108 108">' + (DEFS_HEAD % {
               'top': BASE_TOP, 'cut': BASE_CUT, 'bottom': BASE_BOTTOM, 'stops': stops()})
    if shape == 'tile':
        clip = ('<clipPath id="tile"><rect x="0" y="0" width="108" height="108" '
                'rx="%.3f" ry="%.3f"/></clipPath>' % (r, r))
    elif shape == 'round':
        clip = '<clipPath id="tile"><circle cx="54" cy="54" r="54"/></clipPath>'
    else:
        clip = '<clipPath id="tile"><rect x="0" y="0" width="108" height="108"/></clipPath>'
    body = head + clip + '</defs>'
    body += '<g clip-path="url(#tile)">'
    body += '<rect x="0" y="0" width="108" height="108" fill="url(#bg)"/>'
    open_g, close_g = glyph(ratio)
    body += open_g
    for p in paths:
        rule = ' fill-rule="evenodd"' if p.get('fill_rule') == 'evenodd' else ''
        body += '<path d="%s" fill="url(#blue)"%s/>' % (p['d'], rule)
    body += close_g + '</g>'
    if shape == 'tile':
        # 1px 淡描边，和 ic_brand_mark 一致
        body += ('<rect x="0.5" y="0.5" width="107" height="107" rx="%.3f" ry="%.3f" '
                 'fill="none" stroke="%s" stroke-width="1"/>'
                 % (r - 0.5, r - 0.5, STROKE))
    return body + '</svg>'

def render(svg, out_png, size=1024):
    """用无头浏览器把 SVG 截成 PNG。透明背景靠 default-background-color。"""
    browser = next((b for b in BROWSERS if os.path.exists(b)), None)
    if browser is None:
        raise SystemExit('找不到 Chrome 或 Edge，没法渲染')
    html = ('<!doctype html><html><head><meta charset="utf-8"><style>'
            'html,body{margin:0;padding:0;background:transparent;overflow:hidden}'
            'svg{display:block;width:%dpx;height:%dpx}</style></head><body>%s</body></html>'
            % (size, size, svg))
    tmp = tempfile.mkdtemp(prefix='icon-')
    page = os.path.join(tmp, 'icon.html')
    with open(page, 'w', encoding='utf-8') as f:
        f.write(html)
    cmd = [browser, '--headless=new', '--disable-gpu', '--hide-scrollbars',
           '--force-device-scale-factor=1', '--window-size=%d,%d' % (size, size),
           '--default-background-color=00000000',
           '--screenshot=%s' % out_png, 'file:///' + page.replace('\\', '/')]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                   timeout=120)
    shutil.rmtree(tmp, ignore_errors=True)
    if not os.path.exists(out_png):
        raise SystemExit('渲染失败，没生成 %s' % out_png)

def main():
    with open(PRESETS, encoding='utf-8') as f:
        paths = json.load(f)['fitness']['paths']

    raw = os.path.join(tempfile.gettempdir(), 'icon-src')
    os.makedirs(raw, exist_ok=True)
    sources = {
        'square': build_svg(paths, GLYPH_ADAPTIVE, 'square'),
        'round': build_svg(paths, GLYPH_ADAPTIVE, 'round'),
        'tile': build_svg(paths, GLYPH_BRAND, 'tile'),
    }
    big = {}
    for name, svg in sources.items():
        p = os.path.join(raw, name + '.png')
        render(svg, p)
        big[name] = p
        print('渲染 %s' % os.path.basename(p))

    # 启动器位图：方形 + 圆形，各 5 档密度
    for kind, src in (('ic_launcher', big['square']), ('ic_launcher_round', big['round'])):
        for bucket, px in DENSITIES.items():
            d = os.path.join(RES, 'mipmap-' + bucket)
            os.makedirs(d, exist_ok=True)
            out = os.path.join(d, kind + '.png')
            Image.open(src).convert('RGBA').resize((px, px), Image.LANCZOS).save(
                out, 'PNG', optimize=True)
        print('写入 mipmap-*/%s.png（%s）' % (kind, '/'.join(str(v) for v in DENSITIES.values())))

    # 平台上传用的 512 圆角图标
    os.makedirs(ART, exist_ok=True)
    out512 = os.path.join(ART, 'ic_launcher_512.png')
    Image.open(big['tile']).convert('RGBA').resize((512, 512), Image.LANCZOS).save(
        out512, 'PNG', optimize=True)
    print('写入 art/ic_launcher_512.png（平台上传用）')

if __name__ == '__main__':
    main()
