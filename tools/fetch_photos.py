# -*- coding: utf-8 -*-
"""下载 gym-exercise-guide 的动作示意图（源数据 free-exercise-db / Unlicense 公有领域）。

每个动作两张：0.jpg 起始位、1.jpg 结束位。
"""
import json, os, concurrent.futures, urllib.request, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'tools', 'cache')
OUT = os.path.join(CACHE, 'photos')
BASE = 'https://raw.githubusercontent.com/gengyueworks/gym-exercise-guide/main/assets/exercises/'
UA = {'User-Agent': 'Mozilla/5.0'}

os.makedirs(OUT, exist_ok=True)


def load():
    d = json.load(open(os.path.join(CACHE, 'gym.zh.json'), encoding='utf-8'))
    return [x for x in d if x['equipment'] == 'body only' or x['equipment'] is None]


def grab(args):
    ex, idx = args
    dst = os.path.join(OUT, '%s_%d.jpg' % (ex['id'], idx))
    if os.path.exists(dst) and os.path.getsize(dst) > 5000:
        return dst, 'skip'
    url = BASE + ex['images'][idx]
    try:
        req = urllib.request.Request(url, headers=UA)
        data = urllib.request.urlopen(req, timeout=30).read()
        if len(data) < 5000:
            return dst, 'empty'
        open(dst, 'wb').write(data)
        return dst, 'ok'
    except Exception as e:
        return dst, 'fail:%s' % e


def main():
    sel = load()
    jobs = [(ex, i) for ex in sel for i in (0, 1) if len(ex.get('images') or []) > i]
    print('exercises', len(sel), 'images', len(jobs))
    ok = fail = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        for i, (dst, st) in enumerate(pool.map(grab, jobs), 1):
            if st.startswith('fail') or st == 'empty':
                fail += 1
                print('  FAIL', os.path.basename(dst), st)
            else:
                ok += 1
            if i % 60 == 0:
                print('  %d/%d' % (i, len(jobs)))
    print('done ok=%d fail=%d' % (ok, fail))


if __name__ == '__main__':
    main()
