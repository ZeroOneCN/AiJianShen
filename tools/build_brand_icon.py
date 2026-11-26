# -*- coding: utf-8 -*-
"""把 brand-icon-system 的预设图形落成 Android 矢量资源。

品牌规范（白底 + 渐变蓝）来自 ~/.trae-cn/skills/brand-icon-system：
  ｜ 底      | #FFFFFF 42% 硬切 #F3F8FD → #E9F1FA |
  ｜ 图形    | #64C1F7 → #2E93E4 → #1A6DBC        |
  ｜ 图形占比 | 桌面 65%（补偿启动器放大）/ 92%    |

图形占比：应用内圆角标识按规范 92%；桌面自适应图标用 65%。
真机实测 vivo 启动器把自适应图标放大 ~1.42 倍、只露中间 72dp，65% 乘回去
正好是设计意图的 92% 密度，且边缘不再被切。Pixel/三星等不缩放的启动器上会略小一点。

输出：
  res/drawable/ic_launcher_background.xml   自适应图标背景层（满幅渐变）
  res/drawable/ic_launcher_foreground.xml   自适应图标前景层（渐变图形）
  res/drawable/ic_launcher_monochrome.xml   主题图标单色层
  res/drawable/ic_brand_mark.xml            应用内用的圆角完整版（带描边）

只生成矢量。启动器位图（mipmap-*/ic_launcher.png）和平台上传用的 512 图
由 tools/build_launcher_icons.py 从同一套预设渲染，改了图形要两个脚本都跑。

用法：python tools/build_brand_icon.py [--preset fitness] [--skill <skill 目录>]
"""
import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable')
DEFAULT_SKILL = os.path.expanduser('~/.trae-cn/skills/brand-icon-system')

BASE_TOP = 'FFFFFF'
BASE_CUT = 'F3F8FD'
BASE_BOTTOM = 'E9F1FA'
STROKE = 'DCE5F1'
GRADIENT = [('64C1F7', 0), ('2E93E4', 0.55), ('1A6DBC', 1)]
RADIUS_RATIO = 0.219
GLYPH_ADAPTIVE = 0.65
GLYPH_BRAND = 0.92
CANVAS = 108.0

HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n')


def rounded_rect(x, y, w, h, r):
    """圆角矩形路径，四个角用圆弧收口。"""
    return ('M%g,%g H%g A%g,%g 0 0 1 %g,%g V%g A%g,%g 0 0 1 %g,%g '
            'H%g A%g,%g 0 0 1 %g,%g V%g A%g,%g 0 0 1 %g,%g Z') % (
        x + r, y, x + w - r,
        r, r, x + w, y + r,
        y + h - r,
        r, r, x + w - r, y + h,
        x + r,
        r, r, x, y + h - r,
        y + r,
        r, r, x + r, y,
    )


def bg_gradient():
    """底色渐变：42% 处硬切，方向左上 → 右下。"""
    return ('            <gradient\n'
            '                android:type="linear"\n'
            '                android:startX="0" android:startY="0"\n'
            '                android:endX="108" android:endY="108">\n'
            '                <item android:offset="0" android:color="#FF%s"/>\n'
            '                <item android:offset="0.414" android:color="#FF%s"/>\n'
            '                <item android:offset="0.42" android:color="#FF%s"/>\n'
            '                <item android:offset="1" android:color="#FF%s"/>\n'
            '            </gradient>\n') % (BASE_TOP, BASE_TOP, BASE_CUT, BASE_BOTTOM)


def blue_gradient(indent):
    """图形渐变：坐标落在图形自己的 1024 坐标系里，缩放后方向不变。"""
    pad = ' ' * indent
    stops = ''.join(
        '%s    <item android:offset="%s" android:color="#FF%s"/>\n' % (pad, off, color)
        for color, off in GRADIENT)
    return ('%s<gradient\n'
            '%s    android:type="linear"\n'
            '%s    android:startX="140" android:startY="40"\n'
            '%s    android:endX="900" android:endY="1000">\n'
            '%s%s</gradient>\n') % (pad, pad, pad, pad, stops, pad)


def glyph_group(paths, ratio, fill_gradient=True, solid=None, indent=4):
    """把 1024 图形等比缩到 ratio 占比并居中。"""
    k = CANVAS * ratio / 1024.0
    t = CANVAS / 2 - 512 * k
    pad = ' ' * indent
    out = ['%s<group android:scaleX="%.6f" android:scaleY="%.6f"\n'
           '%s    android:translateX="%.4f" android:translateY="%.4f">\n' % (pad, k, k, pad, t, t)]
    for p in paths:
        rule = '\n%s        android:fillType="evenOdd"' % pad if p.get('fill_rule') == 'evenodd' else ''
        if fill_gradient:
            out.append('%s    <path\n'
                       '%s        android:pathData="%s"%s>\n'
                       '%s        <aapt:attr name="android:fillColor">\n'
                       '%s'
                       '%s        </aapt:attr>\n'
                       '%s    </path>\n' % (pad, pad, p['d'], rule, pad,
                                            blue_gradient(indent + 12), pad, pad))
        else:
            out.append('%s    <path\n'
                       '%s        android:fillColor="#FF%s"\n'
                       '%s        android:pathData="%s"%s/>\n' % (pad, pad, solid, pad, p['d'], rule))
    out.append('%s</group>\n' % pad)
    return ''.join(out)


def write(name, body):
    path = os.path.join(RES, name)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(body)
    print('写入 %s' % os.path.relpath(path, ROOT))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preset', default='fitness')
    ap.add_argument('--skill', default=DEFAULT_SKILL)
    args = ap.parse_args()

    presets = json.load(open(os.path.join(args.skill, 'assets', 'presets.json'),
                             encoding='utf-8'))
    if args.preset not in presets:
        raise SystemExit('没有这个预设：%s（可选 %s）' % (args.preset, ', '.join(sorted(presets))))
    paths = presets[args.preset]['paths']

    # 背景层：满幅渐变，由启动器裁形状
    write('ic_launcher_background.xml',
          HEAD +
          '    <path android:pathData="M0,0H108V108H0Z">\n'
          '        <aapt:attr name="android:fillColor">\n'
          + bg_gradient() +
          '        </aapt:attr>\n'
          '    </path>\n'
          '</vector>\n')

    # 前景层：渐变图形，四周留出圆形遮罩的安全余量
    write('ic_launcher_foreground.xml',
          HEAD + glyph_group(paths, GLYPH_ADAPTIVE) + '</vector>\n')

    # 单色层：Android 13 主题图标，系统自己上色
    write('ic_launcher_monochrome.xml',
          HEAD + glyph_group(paths, GLYPH_ADAPTIVE, fill_gradient=False, solid='000000')
          + '</vector>\n')

    # 应用内标识：圆角 22% + 1px 描边，完整品牌外壳
    r = CANVAS * RADIUS_RATIO
    tile = rounded_rect(0, 0, CANVAS, CANVAS, r)
    write('ic_brand_mark.xml',
          HEAD +
          '    <path android:pathData="%s">\n'
          '        <aapt:attr name="android:fillColor">\n'
          % tile + bg_gradient() +
          '        </aapt:attr>\n'
          '    </path>\n'
          '    <group>\n'
          '        <clip-path android:pathData="%s"/>\n' % tile +
          glyph_group(paths, GLYPH_BRAND, indent=8) +
          '    </group>\n'
          '    <path\n'
          '        android:pathData="%s"\n'
          '        android:strokeColor="#FF%s"\n'
          '        android:strokeWidth="1"\n'
          '        android:fillColor="@android:color/transparent"/>\n'
          % (rounded_rect(0.5, 0.5, CANVAS - 1, CANVAS - 1, r - 0.5), STROKE) +
          '</vector>\n')


if __name__ == '__main__':
    main()
