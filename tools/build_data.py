# -*- coding: utf-8 -*-
"""生成 app 用的动作数据 exercises.json。

数据来源（均为公有领域 / Unlicense，可自由商用）：
  - 动作条目与中文要领：gengyueworks/gym-exercise-guide（Unlicense）
  - 原始动作库与示意图：yuhonas/free-exercise-db（Unlicense / public domain）

只保留徒手与拉伸类（equipment = body only 或为空），保证「打开就能练」。
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'tools', 'cache', 'gym.zh.json')
IMG = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'img')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'exercises.json')

# 自动译名里少数读不通的，逐个改成健身房常用叫法
NAME_FIX = {
    '34仰卧起坐': '3/4 仰卧起坐',
    '90腘绳肌拉伸': '90/90 腘绳肌拉伸',
    '内收肌腹股沟/内收肌拉伸': '内收肌拉伸',
    '身体向上': '俯卧撑撑起',
    '身体肱三头肌推举': '自重臂屈伸',
    '底部朝上': '仰卧抬臀举腿',
    '臀': '仰卧抬臀',
    '椅下部背拉伸': '扶椅下背拉伸',
    '引体胸拉伸': '下巴触胸拉伸',
    '窄握俯卧撑哑铃': '窄距俯卧撑（扶哑铃）',
    '肘膝': '肘触膝卷腹',
    '肘背拉伸': '肘后拉胸拉伸',
    '快速跳绳': '原地快速小步跑',
    '前侧举腿拉伸': '扶墙前摆腿',
    '蹲跳伸展': '蹲姿髋部伸展',
    '山羊挺身（不使用山羊挺身卧凳）': '俯卧挺身',
    '静力颈（前侧背）': '静力颈部对抗（前后）',
    '静力颈（两侧）': '静力颈部对抗（侧向）',
    '腿提举': '仰卧举腿',
    '直线3': '三步启动跑',
    '直线加速靠墙': '靠墙加速跑姿',
    '下部背弯举拉伸': '仰卧抱膝滚动',
    '移动抓握': '抓握爬行',
    '单半蝗虫式': '半蝗虫式',
    '骨盆后倾转臀桥拉伸': '骨盆后倾过渡臀桥',
    '后侧举腿拉伸': '跪姿后摆腿',
    '侧向站姿长跳跃': '侧向立定跳远',
    '侧向腕拉拉伸': '侧向腕部拉伸',
    '站姿体前屈触趾拉伸': '站姿体前屈',
    '星式跳跃': '星跳',
    '触趾拉伸': '坐姿触趾',
    '后腿跑步行走': '小步跑',
    'V 型把手引体向上': 'V 把引体向上',
    '宽握后侧引体向上': '宽握颈后引体向上',
    '风冲刺跑': '短距冲刺',
    '腕绕环拉伸': '腕绕环',
    '剪刀腿拉伸': '仰卧剪刀腿',
    '侧向仰卧地面拉伸': '侧卧背阔肌拉伸',
    '侧向臀桥': '侧桥',
    '侧向折刀式': '侧向折刀',
    '侧向举腿拉伸': '侧卧举腿',
    '侧向仰卧腹股沟/内收肌拉伸': '侧卧内收肌拉伸',
    '腹部真空拉伸': '腹部真空收缩',
    '上部背腿抓握拉伸': '上背腿部抓握拉伸',
    '上部背拉伸': '上背拉伸',
    '俯身膝髋上举': '屈膝举腿',
    '跪姿手臂': '跪姿摆臂',
    '绕肘拉伸': '肘绕环',
    '绕肩拉伸': '肩绕环',
    '踝拉伸（膝）': '踝搭膝拉伸',
    '交替踢腿': '仰卧交替打腿',
    '反式划船': '反向划船',
    '仰头看天花板拉伸': '抬头颈部拉伸',
    '仰卧交叉拉伸': '仰卧交叉腿拉伸',
    '仰卧俯卧股四头肌拉伸': '俯卧股四头肌拉伸',
    '俯卧撑（窄距宽距手）': '俯卧撑（窄/宽距）',
    '坐姿前侧三角肌拉伸': '坐姿前三角肌拉伸',
    '坐姿腿收腹': '坐姿收腿',
    '单腿后踢腿': '单腿后踢',
    '坐姿深蹲拉伸': '蹲姿静态拉伸',
    '分腿深蹲拉伸': '分腿蹲',
    '站姿长跳跃': '立定跳远',
    '站姿骨盆后倾拉伸': '站姿骨盆后倾',
    '站姿毛巾肱三头肌臂屈伸': '站姿毛巾臂屈伸',
    '登阶（举膝）': '登阶提膝',
    '腿向上腘绳肌拉伸': '抬腿腘绳肌拉伸',
    '腿拉': '仰卧收腿',
    '自然臀腘绳肌上举': '自然臀腿挺身',
    '单臂抵住靠墙拉伸': '扶墙侧腰拉伸',
    '坐姿平板卧凳腿拉': '坐姿收腿（长凳）',
    '平板卧凳腿拉': '长凳收腿',
    '平板卧凳仰卧举腿': '长凳仰卧举腿',
    '蛙式单脚跳拉伸': '蛙跳',
    '腹股沟/内收肌背拉伸': '跪姿内收肌拉伸',
    '髋绕环拉伸（俯卧）': '俯卧髋绕环',
    '毛毛虫爬行拉伸': '毛毛虫爬行',
    '铁十字拉伸（拉伸）': '铁十字拉伸',
    '膝绕环拉伸': '膝绕环',
    '肩上举拉伸': '耸肩拉伸',
    '宽握后侧引体向上': '宽握颈后引体向上',
}

# 部位归一：数据里是 7 大部位
PART_ORDER = ['核心', '腿臀', '胸', '背', '肩', '手臂', '全身']

# 分类 -> (标签, 时长秒)
KIND = {
    '拉伸放松': ('拉伸', 30),
    '力量训练': ('力量', 40),
    '爆发力/弹跳': ('爆发', 30),
    '有氧': ('有氧', 40),
}


# 有些「自重」动作仍需借助家具/器械，标注出来，别让人以为躺地上就能做
GEAR_RULES = [
    ('墙面', ['wall']),
    ('长凳', ['bench', 'chair', 'decline', 'seated flat']),
    ('单杠', ['pullup', 'pull-up', 'chin', 'hanging', 'gorilla', 'v-bar']),
    ('双杠', ['dip']),
    ('低杠', ['inverted row']),
    ('毛巾', ['towel']),
]
GEAR_FIX = {
    'Chin_To_Chest_Stretch': '',
    'One_Arm_Against_Wall': '墙面',
    'Hyperextensions_With_No_Hyperextension_Bench': '',
    'Pullups': '单杠',
}


def gear_of(x):
    if x['id'] in GEAR_FIX:
        return GEAR_FIX[x['id']]
    n = x['name_en'].lower()
    for g, keys in GEAR_RULES:
        if any(k in n for k in keys):
            return g
    return ''



# gym-exercise-guide 对少数动作只给了模板化要点，这里按动作模式手写补齐
CUE_FIX = {
    'Alternate_Heel_Touchers': [
        '屈膝仰卧，双脚踩地与髋同宽，双臂自然放在身体两侧。',
        '呼气，肩胛离地，躯干向一侧屈，手沿地面滑向同侧脚跟。',
        '回到中间，再向另一侧，左右交替，腹部全程收紧。',
        '靠侧腹收缩带动，不要用惯性甩动上半身。',
    ],
    'Butt-Ups': [
        '仰卧屈膝，双脚踩实地面，双臂放在体侧、掌心压地。',
        '呼气，用下腹把骨盆卷向胸口，臀部离地几厘米即可。',
        '顶峰停一秒，主动收紧下腹。',
        '吸气缓慢放下，别用脖子或手臂撑地借力。',
    ],
    'Bottoms_Up': [
        '仰卧，双手压地，双腿并拢伸直抬起约 45°。',
        '呼气，下腹发力把骨盆卷起、臀部离地，腿向天花板方向顶。',
        '顶峰停一秒，感受下腹收缩。',
        '吸气缓慢还原，全程双腿不落地。',
    ],
    'Leg_Pull-In': [
        '仰卧，双手抓住身后固定物或压地，双腿抬起悬空。',
        '呼气，收膝靠向胸口，同时骨盆后倾、下背贴地。',
        '吸气用 2–3 秒缓慢伸腿还原，下背始终不离地。',
    ],
    'Seated_Flat_Bench_Leg_Pull-In': [
        '坐在长凳边缘，双手抓凳体两侧，上身略后倾。',
        '呼气，收膝靠向胸口，上身顺势前倾一点。',
        '吸气缓慢伸腿还原，别让上身来回摆动借力。',
    ],
    'Air_Bike': [
        '仰卧，下背贴紧地面，双手轻扶耳侧，双腿抬起屈膝。',
        '对侧手肘与膝盖靠拢，另一条腿伸直悬空。',
        '左右交替，像蹬自行车，节奏中等偏慢。',
        '转体由腹斜肌带动，不要用手把头拽起来。',
    ],
    'Elbow_to_Knee': [
        '仰卧屈膝，双手轻扶耳侧，肩胛离地。',
        '呼气，一侧手肘转向对侧膝盖，同时该侧腿屈膝靠拢。',
        '回到中间换边，腹部持续保持张力。',
    ],
    'Spider_Crawl': [
        '四点支撑：手在肩正下方，膝在髋正下方，背部放平。',
        '对侧手与脚同时小幅前移，身体保持低位、平稳。',
        '前进 5–8 步后原路退回，髋部不要上下起伏。',
    ],
    'Flat_Bench_Leg_Pull-In': [
        '仰卧在长凳上，双手抓住凳沿或两侧。',
        '呼气，收膝靠向胸口，同时骨盆略微卷起。',
        '控制着伸腿还原，别让腰在凳面上过度反弓。',
    ],
    'Leg_Lift': [
        '仰卧，双手压地，双腿并拢伸直。',
        '呼气，下腹发力把腿抬到与地面垂直，臀部可微微离地。',
        '吸气用约 3 秒缓慢放下，脚跟不触地即开始下一次。',
        '腰不要拱起，腿放下时下背始终贴地。',
    ],
    'Prone_Manual_Hamstring': [
        '俯卧，屈膝约 90°，让同伴（或自己用手）抵住脚跟。',
        '腘绳肌发力对抗阻力，保持 5–10 秒，正常呼吸。',
        '缓慢放松，换另一条腿。',
    ],
    'Body-Up': [
        '从俯卧撑起始位开始，身体保持一条直线。',
        '屈肘用前臂撑起上身，再由掌根推起回到直臂。',
        '全程收紧核心，别塌腰或耸臀。',
    ],
}

def kind_of(x):
    c = x.get('category_zh') or ''
    for k, v in KIND.items():
        if k.startswith(c[:3]):
            return v
    return ('力量', 40)


def main():
    d = json.load(open(SRC, encoding='utf-8'))
    sel = [x for x in d if x['equipment'] == 'body only' or x['equipment'] is None]
    items = []
    missing = 0
    for x in sel:
        img = x['id']
        if not os.path.isdir(os.path.join(IMG, img)):
            missing += 1
            continue
        name = NAME_FIX.get(x['name_zh'], x['name_zh'])
        kind, secs = kind_of(x)
        steps = CUE_FIX.get(x['id']) or [s for s in (x.get('cues_zh') or []) if s]
        mistakes = [s for s in (x.get('mistakes_zh') or []) if s]
        items.append({
            'id': img,
            'n': name,
            'e': x['name_en'],
            'p': x['bodyPart_zh'],
            't': '、'.join(x['primaryMuscles_zh']),
            'k': kind,
            'g': gear_of(x),
            's': secs,
            'd': steps,
            'm': mistakes,
        })
    order = {p: i for i, p in enumerate(PART_ORDER)}
    items.sort(key=lambda o: (order.get(o['p'], 99), o['n']))
    out = {
        'source': 'free-exercise-db (Unlicense) / gym-exercise-guide (Unlicense)',
        'license': 'The Unlicense - public domain',
        'items': items,
    }
    json.dump(out, open(OUT, 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
    print('items', len(items), 'missing img', missing,
          'size %.0f KB' % (os.path.getsize(OUT) / 1024))
    from collections import Counter
    print(Counter(o['p'] for o in items).most_common())
    print(Counter(o['k'] for o in items).most_common())


if __name__ == '__main__':
    main()
