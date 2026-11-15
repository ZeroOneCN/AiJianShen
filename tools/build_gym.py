#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 E:/Code/Gym 的 1324 条动作转成本项目使用的紧凑资源。

数据来源：https://github.com/hasaneyldrm/exercises-dataset
  - 动作元数据与中文分步说明：MIT
  - 缩略图与演示 GIF：© Gym visual（https://gymvisual.com/），用户已购授权
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(ROOT, ".data", "gym.json")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "exercises.json")

PART_ZH = {
    "waist": "核心", "upper legs": "腿臀", "lower legs": "小腿",
    "chest": "胸", "back": "背", "shoulders": "肩",
    "upper arms": "手臂", "lower arms": "小臂", "cardio": "有氧", "neck": "颈部",
}
PART_ORDER = ["核心", "腿臀", "胸", "背", "肩", "手臂", "小腿", "小臂", "有氧", "颈部"]

TARGET_ZH = {
    "abs": "腹肌", "quads": "股四头肌", "lats": "背阔肌", "pectorals": "胸大肌",
    "glutes": "臀大肌", "calves": "小腿", "triceps": "肱三头肌", "biceps": "肱二头肌",
    "upper back": "上背", "cardiovascular system": "心肺", "hamstrings": "腘绳肌",
    "abductors": "外展肌", "adductors": "内收肌", "spine": "竖脊肌",
    "hip flexors": "髋屈肌", "delts": "三角肌", "traps": "斜方肌",
    "forearms": "前臂", "lower back": "下背", "obliques": "腹斜肌",
    "serratus anterior": "前锯肌", "levator scapulae": "肩胛提肌",
    "rhomboids": "菱形肌", "wrists": "腕部", "hands": "手部",
    "sternocleidomastoid": "胸锁乳突肌", "brachialis": "肱肌",
    "ankles": "踝部", "feet": "足部", "chest": "胸部", "neck": "颈部",
    "shoulders": "肩部", "upper arms": "上臂", "lower arms": "前臂",
    "upper legs": "大腿", "lower legs": "小腿", "waist": "核心",
    "gluteus maximus": "臀大肌", "hip adductors": "内收肌",
}

EQUIP_ZH = {
    "body weight": "",
    "dumbbell": "哑铃", "barbell": "杠铃", "ez barbell": "曲杆杠铃",
    "olympic barbell": "奥杆", "cable": "绳索", "leverage machine": "器械",
    "smith machine": "史密斯机", "kettlebell": "壶铃", "band": "弹力带",
    "resistance band": "阻力带", "stability ball": "瑜伽球", "bosu ball": "波速球",
    "medicine ball": "药球", "weighted": "负重", "assisted": "辅助器械",
    "sled machine": "雪橇机", "rope": "绳索", "roller": "泡沫轴",
    "wheel roller": "健腹轮", "upper body ergometer": "上肢测功仪",
    "stationary bike": "固定单车", "elliptical machine": "椭圆机",
    "stepmill machine": "台阶机", "trap bar": "六角杠铃", "tire": "轮胎",
    "hammer": "锤", "exercise ball": "瑜伽球", "skierg machine": "滑雪测功仪",
    "sled": "雪橇", "battling rope": "战绳", "short resistance band": "短阻力带",
    "medicine ball": "药球", "cambered bar": "弧形杆", "trap bar": "六角杠铃",
}

# 名称里出现但设备为 body weight 时，实际仍需借助的东西
GEAR_NAME = [
    ("pull-up", "单杠"), ("pull up", "单杠"), ("chin-up", "单杠"),
    ("chin up", "单杠"), ("hanging", "单杠"), ("muscle-up", "单杠"),
    ("muscle up", "单杠"), ("inverted row", "低杠"), ("handstand", "墙面"),
    ("bench dip", "长凳"), ("bench", "长凳"), ("box", "跳箱"),
    ("wall", "墙面"), ("parallel bars", "双杠"), ("dip", "双杠"),
    ("chair", "椅子"), ("towel", "毛巾"), ("stirrups", "吊环"),
    ("ring", "吊环"), ("band", "弹力带"), ("stability ball", "瑜伽球"),
    ("exercise ball", "瑜伽球"), ("bosu", "波速球"), ("sled", "雪橇"),
    ("tire", "轮胎"), ("rope", "战绳"), ("step", "台阶"), ("roller", "健腹轮"),
    ("suspended", "悬吊带"), ("strap", "悬吊带"), ("assisted", "辅助器械"),
    ("weighted", "负重"),
]

TIMING = {"cardio": 45, "waist": 40, "chest": 40, "upper legs": 45,
          "back": 40, "upper arms": 35, "lower legs": 35, "lower arms": 30,
          "shoulders": 35, "neck": 30}

PHRASES = [
    # 设备 + 常见复合动作，长的优先
    ("upper body ergometer", "上肢测功仪"),
    ("stationary bike", "固定单车"),
    ("elliptical machine", "椭圆机"),
    ("stepmill machine", "台阶机"),
    ("smith machine", "史密斯机"),
    ("leverage machine", "器械"),
    ("sled machine", "雪橇机"),
    ("stability ball", "瑜伽球"),
    ("exercise ball", "瑜伽球"),
    ("medicine ball", "药球"),
    ("bosu ball", "波速球"),
    ("resistance band", "阻力带"),
    ("ez barbell", "曲杆杠铃"),
    ("olympic barbell", "奥杆"),
    ("trap bar", "六角杠铃"),
    ("cambered bar", "弧形杆"),
    ("straight bar", "直杆"),
    ("parallel bars", "双杠"),
    ("dip-pull-up cage", "双杠引体架"),
    ("pull up cage", "引体架"),
    ("dip cage", "双杠架"),
    ("clean and jerk", "挺举"),
    ("power clean", "力量翻举"),
    ("hang clean", "悬垂翻举"),
    ("split jerk", "分腿挺举"),
    ("front squat", "前蹲"),
    ("box jump", "跳箱"),
    ("jumping jack", "开合跳"),
    ("mountain climber", "登山跑"),
    ("high knees", "高抬腿"),
    ("high knee", "高抬腿"),
    ("hip thrusts", "臀推"),
    ("hip thrust", "臀推"),
    ("lateral raise", "侧平举"),
    ("front raise", "前平举"),
    ("rear delt", "后束三角肌"),
    ("upright row", "直立划船"),
    ("bent over", "俯身"),
    ("lat pulldown", "高位下拉"),
    ("bench press", "卧推"),
    ("step up", "登阶"),
    ("step-up", "登阶"),
    ("skull crusher", "碎颅式"),
    ("good morning", "早安式"),
    ("front lever", "前水平杠杆"),
    ("back lever", "后水平杠杆"),
    ("iron cross", "十字"),
    ("turkish get up", "土耳其起身"),
    ("farmers walk", "农夫行走"),
    ("farmers carry", "农夫搬运"),
    ("tire flip", "轮胎翻"),
    ("battling ropes", "战绳"),
    ("world greatest stretch", "全身舒展"),
    ("worlds greatest stretch", "全身舒展"),
    ("figure 8", "8 字"),
    ("figure eight", "8 字"),
    ("t-raise", "T 字抬举"),
    ("y-raise", "Y 字抬举"),
    ("t raise", "T 字抬举"),
    ("y raise", "Y 字抬举"),
    ("w raise", "W 字抬举"),
    ("front shoulder raise", "前平举"),
    ("rear shoulder raise", "后平举"),
    ("t-bar", "T 杠"),
    ("sz-bar", "SZ 杆"),
    ("back pov", "背面视角"),
    ("side pov", "侧面视角"),
    ("pull-up", "引体向上"),
    ("pull ups", "引体向上"),
    ("pull up", "引体向上"),
    ("chin-up", "反握引体"),
    ("chin up", "反握引体"),
    ("chin ups", "反握引体"),
    ("muscle-up", "双力臂"),
    ("muscle up", "双力臂"),
    ("push-up", "俯卧撑"),
    ("push ups", "俯卧撑"),
    ("push up", "俯卧撑"),
    ("sit-up", "仰卧起坐"),
    ("sit ups", "仰卧起坐"),
    ("sit up", "仰卧起坐"),
    ("l-sit", "L 字支撑"),
    ("l sit", "L 字支撑"),
    ("v-sit", "V 字支撑"),
    ("v sit", "V 字支撑"),
    ("v-up", "V 字起"),
    ("v up", "V 字起"),
    ("leg raise", "举腿"),
    ("leg-hip raise", "举腿提髋"),
    ("hip raise", "提髋"),
    ("knee raise", "屈膝抬腿"),
    ("calf raise", "提踵"),
    ("toe touch", "体前屈摸脚"),
    ("side bend", "体侧屈"),
    ("side bridge", "侧桥"),
    ("side plank", "侧平板"),
    ("front plank", "平板支撑"),
    ("plank", "平板支撑"),
    ("glute bridge", "臀桥"),
    ("hip extension", "伸髋"),
    ("hip abduction", "髋外展"),
    ("hip adduction", "髋内收"),
    ("leg curl", "腿弯举"),
    ("leg extension", "腿屈伸"),
    ("leg press", "腿举"),
    ("hyperextension", "背伸"),
    ("inverse leg curl", "反向腿弯举"),
    ("glute-ham raise", "臀腿挺身"),
    ("back extension", "背部伸展"),
    ("mountain climber", "登山跑"),
    ("bear crawl", "熊爬"),
    ("spider crawl", "蜘蛛爬"),
    ("inchworm", "毛毛虫爬"),
    ("star jump", "星跳"),
    ("jack jump", "开合跳"),
    ("jack burpee", "开合波比"),
    ("wind sprints", "短距离冲刺"),
    ("jump squat", "深蹲跳"),
    ("split squat", "分腿蹲"),
    ("walking lunge", "行进箭步蹲"),
    ("forward lunge", "前弓步"),
    ("lunge", "弓步"),
    ("squat", "深蹲"),
    ("wall sit", "靠墙静蹲"),
    ("potty squat", "深蹲起身"),
    ("sissy squat", "西斯深蹲"),
    ("hack squat", "哈克深蹲"),
    ("sumo squat", "相扑深蹲"),
    ("goblet squat", "高脚杯深蹲"),
    ("zercher squat", "泽奇深蹲"),
    ("parallel grip", "对握"),
    ("vertical bar", "垂直杆"),
    ("neutral grip", "对握"),
    ("reverse grip", "反握"),
    ("mixed grip", "正反握"),
    ("close-grip", "窄距"),
    ("close grip", "窄距"),
    ("wide-grip", "宽距"),
    ("wide grip", "宽距"),
    ("wide hand", "宽距"),
    ("narrow grip", "窄距"),
    ("one arm", "单臂"),
    ("single arm", "单臂"),
    ("one leg", "单腿"),
    ("single leg", "单腿"),
    ("two legs", "双腿"),
    ("two toe", "双脚"),
    ("bent knee", "屈膝"),
    ("straight leg", "直腿"),
    ("leg straight", "直腿"),
    ("knees bent", "屈膝"),
    ("on knees", "跪姿"),
    ("kneeling", "跪姿"),
    ("seated", "坐姿"),
    ("standing", "站姿"),
    ("lying", "仰卧"),
    ("hanging", "悬垂"),
    ("incline", "上斜"),
    ("decline", "下斜"),
    ("reverse", "反向"),
    ("inverted", "倒置"),
    ("isometric", "等长"),
    ("plyo", "爆发式"),
    ("drop push up", "落差俯卧撑"),
    ("clap push up", "击掌俯卧撑"),
    ("archer", "弓手"),
    ("diamond", "钻石"),
    ("pike", "折体"),
    ("handstand", "倒立"),
    ("scapula", "肩胛"),
    ("scapular", "肩胛"),
    ("deltoid", "三角肌"),
    ("delts", "三角肌"),
    ("oblique", "腹斜"),
    ("abdominal", "腹部"),
    ("cobra", "眼镜蛇式"),
    ("upward facing dog", "上犬式"),
    ("butterfly yoga pose", "蝴蝶式"),
    ("world greatest stretch", "全身舒展"),
    ("lat stretch", "背阔肌拉伸"),
    ("hamstring stretch", "腘绳肌拉伸"),
    ("quads stretch", "股四头肌拉伸"),
    ("chest stretch", "胸部拉伸"),
    ("triceps stretch", "肱三头肌拉伸"),
    ("calf stretch", "小腿拉伸"),
    ("glute stretch", "臀部拉伸"),
    ("piriformis stretch", "梨状肌拉伸"),
    ("neck stretch", "颈部拉伸"),
    ("back stretch", "背部拉伸"),
    ("wrist pull stretch", "手腕拉伸"),
    ("side lying floor stretch", "侧卧拉伸"),
    ("towel row", "毛巾划船"),
    ("inverted row", "反向划船"),
    ("squatting row", "深蹲划船"),
    ("standing row", "站姿划船"),
    ("bench dip", "凳上臂屈伸"),
    ("triceps dip", "肱三头肌臂屈伸"),
    ("chest dip", "胸部臂屈伸"),
    ("elbow dips", "肘撑臂屈伸"),
    ("bodyweight", "自重"),
    ("body weight", "自重"),
    ("hands reversed clasped", "反手交握"),
    ("hands clasped", "双手交握"),
    ("hands overhead", "双手过顶"),
    ("arms overhead", "双臂过顶"),
    ("arms apart", "双臂分开"),
    ("arms on chest", "抱胸"),
    ("on a staircase", "台阶"),
    ("against wall", "靠墙"),
    ("hands against wall", "双手推墙"),
    ("with hands against wall", "双手推墙"),
    ("with towel", "毛巾辅助"),
    ("v. 2", "二式"),
    ("v.2", "二式"),
    ("v. 3", "三式"),
    ("v. 4", "四式"),
    ("v. 5", "五式"),
    ("v. 6", "六式"),
    ("half knee bends", "半蹲"),
    ("half sit-up", "半程仰卧起坐"),
    ("quarter sit-up", "四分之一仰卧起坐"),
    ("full sit-up", "全程仰卧起坐"),
    ("gluteus", "臀"),
    ("pectoralis", "胸肌"),
    ("tibialis", "胫骨肌"),
    ("peroneals", "腓骨肌"),
    ("femoral", "股"),
    ("femoris", "股"),
    ("rectus", "直肌"),
    ("retractor", "回缩肌"),
    ("depresor", "下压肌"),
    ("levator scapulae", "肩胛提肌"),
    ("serratus anterior", "前锯肌"),
]

WORDS = {
    # 连接词
    "a": "", "the": "", "of": "", "to": "", "in": "", "on": "", "with": "",
    "and": "", "from": "", "into": "接", "for": "", "at": "", "male": "",
    "female": "", "equipment": "器械", "style": "式",
    # 设备
    "dumbbell": "哑铃", "dumbbells": "哑铃", "barbell": "杠铃", "cable": "绳索",
    "kettlebell": "壶铃", "band": "弹力带", "ball": "球", "bosu": "波速球",
    "smith": "史密斯", "machine": "器械", "ez": "曲杆", "olympic": "奥杆",
    "rope": "绳索", "ropes": "绳索", "sled": "雪橇", "sledge": "雪橇",
    "tire": "轮胎", "wheel": "健腹轮", "roller": "泡沫轴", "gripper": "握力器",
    "treadmill": "跑步机", "elliptical": "椭圆机", "bike": "单车",
    "cycle": "单车", "ergometer": "测功仪", "stepmill": "台阶机",
    "stepbox": "跳箱", "box": "箱", "bench": "长凳", "benches": "长凳",
    "rack": "架", "cage": "架", "bar": "杆", "bars": "双杠", "ring": "环",
    "stirrups": "吊环", "strap": "吊带", "straps": "吊带", "board": "板",
    "platform": "平台", "pad": "垫", "chair": "椅上", "towel": "毛巾",
    "medicine": "药球", "stability": "稳定", "resistance": "阻力",
    "weighted": "负重", "assisted": "辅助", "suspended": "悬吊",
    "attachment": "附件", "handle": "把手", "pulley": "滑轮",
    "battling": "战绳", "kayak": "皮划艇", "bowling": "保龄球",
    "tennis": "网球", "judo": "柔道", "boxing": "拳击", "stork": "鹳式",
    "tire": "轮胎",
    # 动作
    "curl": "弯举", "curls": "弯举", "press": "推举", "presses": "推举",
    "push": "推", "pull": "拉", "row": "划船", "raise": "抬", "raises": "抬",
    "raised": "抬高", "lift": "抬", "lifting": "抬", "squat": "深蹲",
    "squats": "深蹲", "squatting": "深蹲", "lunge": "弓步", "dip": "臂屈伸",
    "dips": "臂屈伸", "pushdown": "下压", "pulldown": "下拉",
    "pullover": "仰卧上拉", "fly": "飞鸟", "flyes": "飞鸟", "shrug": "耸肩",
    "extension": "伸展", "flexion": "屈曲", "crunch": "卷腹",
    "crunches": "卷腹", "twist": "转体", "twists": "转体",
    "twisting": "转体", "twisted": "转体", "rotation": "旋转",
    "rotational": "旋转", "rotary": "旋转", "rotate": "旋转",
    "deadlift": "硬拉", "clean": "翻举", "snatch": "抓举", "jerk": "挺举",
    "thruster": "火箭推", "thrusts": "臀推", "swing": "摆",
    "kickback": "后踢", "kickbacks": "后踢", "kick": "踢", "kicks": "踢",
    "run": "跑", "sprint": "冲刺", "sprints": "冲刺", "jump": "跳",
    "jumps": "跳", "hop": "跳", "hops": "跳", "walk": "行走",
    "walking": "行走", "march": "行军", "step": "踏步", "climb": "爬",
    "crawl": "爬", "slide": "滑", "swim": "游泳", "swimmer": "游泳",
    "slam": "砸", "throw": "抛", "catch": "接", "pass": "传递",
    "tap": "点", "touch": "触碰", "touchers": "触碰", "reach": "伸展",
    "hold": "保持", "squeeze": "夹", "tilt": "倾斜", "lean": "前倾",
    "turn": "转", "flip": "翻", "drag": "拖拽", "drive": "驱动",
    "carry": "搬运", "saw": "锯式", "wind": "风", "windmill": "风车",
    "circle": "绕环", "circles": "绕环", "circular": "绕环",
    "rotation": "旋转", "pulse": "脉冲", "elevator": "升降",
    "rocking": "摇摆", "rocky": "摇摆", "squeeze": "夹", "plus": "加强",
    "iso": "等长", "isometric": "等长", "negative": "退让",
    "concentration": "集中", "preacher": "牧师凳", "peacher": "牧师凳",
    "scott": "斯科特", "zottman": "佐特曼", "cuban": "古巴",
    "bradford": "布拉德福德", "pendlay": "彭德利", "romanian": "罗马尼亚",
    "jefferson": "杰斐逊", "janda": "扬达", "arnold": "阿诺德",
    "gironda": "吉隆达", "frankenstein": "弗兰肯斯坦", "thibaudeau": "蒂博多",
    "hyght": "海特", "svend": "斯文德", "tate": "泰特", "otis": "奥蒂斯",
    "pallof": "帕洛夫", "renegade": "叛徒", "stalder": "斯托尔德",
    "cossack": "哥萨克", "curtsey": "交叉屈膝", "pistol": "手枪",
    "sissy": "西斯", "hack": "哈克", "sumo": "相扑", "goblet": "高脚杯",
    "zercher": "泽奇", "guillotine": "断头台", "skull": "碎颅",
    "skullcrusher": "碎颅", "crusher": "碎颅", "bradford": "布拉德福德",
    "jm": "JM", "sz": "SZ", "t": "T", "l": "L 字", "v": "V 字",
    "y": "Y 字", "x": "X 字", "figure": "字", "eight": "8",
    # 身体
    "arm": "臂", "arms": "臂", "leg": "腿", "legs": "腿", "legged": "腿",
    "knee": "膝", "knees": "膝", "keens": "膝", "hip": "髋", "toe": "脚尖",
    "heel": "脚跟", "hand": "手", "hands": "手", "head": "头", "neck": "颈",
    "shoulder": "肩", "elbow": "肘", "spine": "脊柱", "chest": "胸",
    "abs": "腹肌", "ab": "腹肌", "belly": "腹", "waist": "腰",
    "oblique": "腹斜肌", "glute": "臀", "glutes": "臀", "ham": "腘绳肌",
    "hamstring": "腘绳肌", "quad": "股四头肌", "quads": "股四头肌",
    "calf": "小腿", "calves": "小腿", "lat": "背阔肌", "lats": "背阔肌",
    "bicep": "肱二头肌", "biceps": "肱二头肌", "tricep": "肱三头肌",
    "triceps": "肱三头肌", "delt": "三角肌", "deltoid": "三角肌",
    "traps": "斜方肌", "trap": "斜方肌", "forearms": "前臂",
    "wrist": "腕", "ankle": "踝", "ankles": "踝", "back": "背",
    "lower": "下", "upper": "上", "middle": "中", "inner": "内侧",
    "outer": "外侧", "inside": "内侧", "outside": "外侧", "front": "前",
    "rear": "后侧", "side": "侧", "lateral": "侧向", "posterior": "后侧",
    "anterior": "前侧", "contralateral": "对侧", "unilateral": "单侧",
    "bent": "屈", "straight": "直", "stiff": "直腿", "extended": "伸展",
    "outstretched": "伸展", "extended": "伸展", "raised": "抬高",
    "elevated": "抬高", "reclining": "斜卧", "lying": "仰卧",
    "prone": "俯卧", "supine": "仰卧", "seated": "坐姿", "sitted": "坐姿",
    "sit": "坐", "sitting": "坐姿", "standing": "站姿", "kneeling": "跪姿",
    "kneel": "跪姿", "hanging": "悬垂", "hang": "悬垂", "upright": "直立",
    "incline": "上斜", "decline": "下斜", "flat": "平板", "floor": "地面",
    "ground": "地面", "wall": "靠墙", "behind": "身后", "overhead": "过顶",
    "under": "下方", "up": "上", "down": "下", "upward": "向上",
    "backward": "向后", "forward": "向前", "left": "左", "right": "右",
    "both": "双", "one": "单", "two": "双", "double": "双", "twin": "双",
    "single": "单", "triple": "三", "three": "三", "alternate": "交替",
    "alternating": "交替", "half": "半程", "semi": "半", "quarter": "四分之一",
    "full": "全程", "deep": "深", "high": "高", "low": "低", "short": "短",
    "quick": "快速", "speed": "速度", "slow": "慢速", "dynamic": "动态",
    "static": "静态", "explosive": "爆发", "plyo": "爆发", "kipping": "借力",
    "modified": "简化", "advanced": "进阶", "basic": "基础",
    "intermediate": "中级", "classic": "经典", "pro": "专业",
    "self": "自我", "supinated": "反握", "supination": "反握",
    "pronated": "内旋", "pronate": "内旋", "pronation": "内旋",
    "overhand": "正握", "underhand": "反握", "reversed": "反手",
    "revers": "反向", "reverse": "反向", "mixed": "正反", "neutral": "对握",
    "grip": "握", "gripless": "无握", "clasped": "交握", "apart": "分开",
    "astride": "分腿", "straddle": "宽分腿", "split": "分腿",
    "cross": "交叉", "crossover": "交叉", "crossovers": "交叉",
    "diagonal": "对角", "round": "圆", "clock": "钟面", "angle": "角度",
    "angled": "斜角", "degree": "度", "degrees": "度",
    "range": "幅度", "motion": "动作", "position": "姿势", "pose": "式",
    "stretch": "拉伸", "sequence": "序列", "yoga": "瑜伽", "pov": "视角",
    "point": "点", "power": "力量", "speed": "速度", "resistance": "阻力",
    "multiple": "多次", "response": "反应", "release": "释放",
    "variation": "变式", "reps": "次数", "bottoms": "提臀", "butt": "臀",
    "hug": "抱", "palms": "掌", "palm": "掌", "finger": "指",
    "feet": "脚", "stride": "步幅", "stance": "站姿", "stability": "稳定",
    "stabilization": "稳定", "balance": "平衡", "gravity": "重力",
    "monster": "怪兽", "gorilla": "猩猩", "prisoner": "抱头",
    "captains": "船长", "captain": "船长", "donkey": "驴式", "frog": "蛙式",
    "crab": "蟹式", "spider": "蜘蛛", "bear": "熊", "cat": "猫式",
    "dog": "犬式", "cobra": "眼镜蛇式", "sphinx": "狮身人面式",
    "superman": "超人式", "butterfly": "蝴蝶", "scissor": "剪刀",
    "flutter": "打水", "jack": "开合", "burpee": "波比跳",
    "skater": "滑冰式", "ski": "滑雪", "skier": "滑雪", "jog": "慢跑",
    "inchworm": "毛毛虫", "slingers": "摆腿", "pin": "固定",
    "fixed": "固定", "supported": "有支撑", "support": "支撑",
    "isometric": "等长", "hold": "保持", "presses": "推举",
    "platform": "平台", "staircase": "台阶", "stepmill": "台阶机",
    "roman": "罗马", "chair": "椅上", "pad": "垫", "gripper": "握力器",
    "rollerer": "滚轮", "rollerout": "健腹轮", "ab": "腹肌",
    "cocoons": "茧式", "cocoon": "茧式", "bug": "虫", "dead": "死虫",
    "elevator": "升降", "wiper": "雨刷", "wipers": "雨刷",
    "jackknife": "折刀", "scapula": "肩胛", "scapular": "肩胛",
    "planche": "水平支撑", "maltese": "马耳他", "lever": "器械",
    "flag": "人体旗帜", "handstand": "倒立", "muscle": "肌肉",
    "archer": "弓手", "diamond": "钻石", "pike": "折体", "tuck": "团身",
    "prisoner": "抱头", "hook": "勾拳", "hook.": "勾拳",
    "ellipse": "椭圆", "spin": "旋转", "jumping": "跳",
    "supper": "超人", "pirate": "海盗", "breeding": "卧推",
    "can": "罐式", "spell": "施法", "squad": "深蹲",
    "weights": "负重", "bar": "杆", "bars": "双杠",
    "hammer": "锤式", "over": "过", "military": "军式", "wide": "宽距",
    "blaster": "臂托", "body": "身", "russian": "俄罗斯", "narrow": "窄距",
    "through": "穿过", "bridge": "臀桥", "flexor": "屈肌", "internal": "内侧",
    "external": "外侧", "french": "法式", "pelvic": "骨盆",
    "horizontal": "水平", "vertical": "垂直", "ups": "起",
    "trainer": "训练器", "around": "环绕", "face": "面", "off": "离",
    "between": "之间", "landmine": "地雷管", "air": "空中",
    "parallel": "平行", "major": "大", "adductor": "内收肌",
    "abductor": "外展肌", "forth": "往返", "pec": "胸肌",
    "pectoral": "胸肌", "bicycle": "自行车", "knife": "刀",
    "drop": "落差", "across": "横跨", "world": "世界", "above": "上方",
    "waiter": "侍者", "w": "W", "pyramid": "金字塔", "anti": "反",
    "groin": "腹股沟", "impossible": "极限", "depth": "深度",
    "seesaw": "跷跷板", "out": "外", "korean": "韩式", "london": "伦敦",
    "hindu": "印度", "big": "大", "hyper": "超伸", "chin": "反握引体",
    "closer": "更窄", "abductor": "外展肌", "flexors": "屈肌",
}

EXACT = {
    "dumbbell incline breeding": "哑铃上斜卧推",
    "dumbbell rear delt row_shoulder": "哑铃后束划船",
    "kettlebell pirate supper legs": "壶铃海盗超人腿",
    "spell caster": "施法者",
    "hyght dumbbell fly": "海特哑铃飞鸟",
    "otis up": "奥蒂斯起身",
    "wrist rollerer": "腕力滚轮",
    "barbell sitted alternate leg raise": "杠铃坐姿交替举腿",
    "barbell sitted alternate leg raise (female)": "杠铃坐姿交替举腿",
    "dumbbell full can lateral raise": "哑铃满罐侧平举",
    "all fours squad stretch": "四足跪姿拉伸",
    "3/4 sit-up": "四分之三仰卧起坐",
    "runners stretch": "跑者拉伸",
    "skinner stretch": "斯金纳拉伸",
    "body-up": "反撑起",
    "90/90 hamstring": "90/90 腘绳肌拉伸",
    "quick feet v. 2": "快速碎步二式",
    "swing 360": "360 度摆动",
    "push-up plus": "加强俯卧撑",
    "chest dip (on dip-pull-up cage)": "双杠架臂屈伸",
    "inverse leg curl (on pull-up cable machine)": "绳索器械反向腿弯举",
    "l-sit on floor": "地面 L 字支撑",
    "v-sit on floor": "地面 V 字支撑",
    "l-pull-up": "L 字引体向上",
    "l pull-up": "L 字引体向上",
    "mixed grip chin-up": "正反握引体",
    "one arm chin-up": "单臂引体向上",
    "pull up (neutral grip)": "对握引体向上",
    "reverse grip pull-up": "反手握引体向上",
    "muscle-up (on vertical bar)": "垂直杆双力臂",
    "skin the cat": "猫式翻越",
    "stalder press": "斯托尔德推举",
    "straddle planche": "宽分腿水平支撑",
    "straddle maltese": "宽分腿马耳他",
    "front lever reps": "前水平杠杆次数",
    "gironda sternum chin": "吉隆达胸骨反握引体",
    "gorilla chin": "猩猩反握引体",
    "side-to-side chin": "左右摆头",
    "hug keens to chest": "抱膝触胸",
    "isometric wipers": "等长雨刷",
    "vertical leg raise (on parallel bars)": "双杠垂直举腿",
    "wide-grip chest dip on high parallel bars": "高双杠宽距胸部臂屈伸",
    "triceps dip (between benches)": "双凳间肱三头肌臂屈伸",
    "reverse hyper on flat bench": "平板凳反向背伸",
    "rocky pull-up pulldown": "摇摆引体下拉",
    "russian twist": "俄罗斯转体",
    "seated lower back stretch": "坐姿下背拉伸",
    "shoulder grip pull-up": "宽握引体向上",
    "single leg bridge with outstretched leg": "单腿伸展腿臀桥",
    "single leg platform slide": "单腿平台滑步",
    "split squats": "分腿蹲",
    "suspended abdominal fallout": "悬吊腹部前伸",
    "swimmer kicks v. 2 (male)": "游泳打水二式",
    "upper back stretch": "上背拉伸",
    "walking high knees lunge": "行进高抬腿弓步",
    "wheel run": "滚轮跑",
    "push-up on lower arms": "前臂俯卧撑",
    "push-up inside leg kick": "内侧踢腿俯卧撑",
    "outside leg kick push-up": "外侧踢腿俯卧撑",
    "chest and front of shoulder stretch": "胸肩前侧拉伸",
    "standing calves calf stretch": "站姿小腿拉伸",
    "calf push stretch with hands against wall": "双手推墙小腿拉伸",
    "circles knee stretch": "绕膝拉伸",
    "side push neck stretch": "侧向颈部拉伸",
    "chair leg extended stretch": "椅上伸腿拉伸",
    "high knee against wall": "靠墙高抬腿",
    "box jump down with one leg stabilization": "跳箱单腿落地稳定",
    "standing calf raise (on a staircase)": "台阶站姿提踵",
    "left hook. boxing": "左勾拳",
    "run (equipment)": "器械跑",
    "barbell revers wrist curl v. 2": "杠铃反向腕弯举二式",
    "cable cross-over revers fly": "绳索交叉反向飞鸟",
    "dumbbell over bench revers wrist curl": "哑铃凳上反向腕弯举",
    "dumbbell revers grip biceps curl": "哑铃反握肱二头肌弯举",
    "cable reverse grip triceps pushdown (sz-bar) (with arm blaster)": "绳索反握肱三头肌下压（SZ 杆·臂托）",
    "sled 45° leg press (side pov)": "雪橇 45 度腿举（侧面视角）",
    "sled 45в° leg press (back pov)": "雪橇 45 度腿举（背面视角）",
    "sled 45в° calf press": "雪橇 45 度提踵",
    "sled 45в° leg press": "雪橇 45 度腿举",
    "sled 45в° leg wide press": "雪橇 45 度宽距腿举",
    "45° side bend": "45 度体侧屈",
    "barbell full squat (back pov)": "杠铃全程深蹲（背面视角）",
    "barbell full squat (side pov)": "杠铃全程深蹲（侧面视角）",
    "dumbbell upright row (back pov)": "哑铃直立划船（背面视角）",
}


def clean(name):
    n = name.lower().replace("в°", "°").replace("_", " ")
    n = re.sub(r"\((male|female)\)", "", n)
    n = n.replace("(", " ").replace(")", " ").replace(",", " ")
    n = n.replace("/", " ").replace("-", " ")
    n = re.sub(r"\bv\s*\.?\s*2\b", "v2", n)
    n = re.sub(r"\s+", " ", n).strip()
    return n


def exact_key(name):
    k = name.lower().replace("в°", "°").replace("_", " ").replace("-", " ")
    k = re.sub(r"\s+", " ", k).strip()
    return k


PH_SORTED = sorted(PHRASES, key=lambda p: -len(p[0]))
TOKEN_RE = re.compile(r"[0-9]+|[a-z]+")
EXACT_NORM = {}
for _k, _v in EXACT.items():
    EXACT_NORM[exact_key(_k)] = _v


def translate(name):
    key = exact_key(name)
    if key in EXACT_NORM:
        return EXACT_NORM[key]
    # 去掉 (male)/(female) 后再查一次整名
    stripped = exact_key(re.sub(r"\((male|female)\)", "", name, flags=re.I))
    if stripped in EXACT_NORM:
        return EXACT_NORM[stripped]

    s = " " + clean(name) + " "
    s = s.replace("v2", " v. 2 ")
    for idx, (en, zh) in enumerate(PH_SORTED):
        pat = r"(?<![\w])" + re.escape(en) + r"(?![\w])"
        if re.search(pat, s):
            s = re.sub(pat, " \u0001%d\u0001 " % idx, s)

    out, unknown = [], []
    for tok in s.split():
        if tok.startswith("\u0001"):
            out.append(PH_SORTED[int(tok.strip("\u0001"))][1])
        elif tok in WORDS:
            out.append(WORDS[tok])
        elif re.fullmatch(r"[0-9]+°", tok):
            out.append(tok[:-1] + " 度")
        elif re.fullmatch(r"[0-9]+", tok):
            out.append(tok)
        elif re.fullmatch(r"[°]+", tok):
            out.append("度")
        else:
            unknown.append(tok)
            out.append("[" + tok + "]")
    zh = "".join(out).strip()
    # 去掉连续重复词（例如「深蹲深蹲」）
    zh = re.sub(r"(.{2,4})\1+", r"\1", zh)
    return zh if not unknown else None


def gear_of(rec):
    eq = (rec.get("equipment") or "").strip().lower()
    low = rec["name"].lower()
    if eq in EQUIP_ZH and eq != "body weight":
        return EQUIP_ZH[eq]
    for k, v in GEAR_NAME:
        if k in low:
            return v
    return ""


def kind_of(rec):
    low = rec["name"].lower()
    if re.search(r"\b(stretch(?:es|ing)?|pose|yoga|sequence|circles?|"
                 r"rotation|sphinx|cobra|child|pigeon|butterfly)\b", low):
        return "拉伸"
    if rec.get("body_part") == "cardio" or re.search(
            r"\b(run|jumps?|jack|burpee|skater|sprint|bike|elliptical|"
            r"treadmill|walk)\b", low) or "mountain climber" in low:
        return "有氧"
    return "力量"


def steps_of(rec):
    st = (rec.get("instruction_steps") or {}).get("zh") or []
    if st:
        return [s.strip().rstrip("。") + "。" for s in st if s.strip()]
    txt = (rec.get("instructions") or {}).get("zh", "").strip()
    if not txt:
        return []
    parts = [p.strip() for p in re.split(r"[。\n]", txt) if p.strip()]
    return [p + "。" for p in parts]


def main():
    data = json.load(open(SRC, encoding="utf-8"))
    out, misses = [], []
    for rec in data:
        zh = translate(rec["name"])
        if not zh:
            misses.append(rec["name"])
            continue
        part = PART_ZH.get(rec["body_part"], "核心")
        item = {
            "id": rec["id"],
            "n": zh,
            "e": rec["name"],
            "p": part,
            "t": TARGET_ZH.get(rec["target"], rec["target"]),
            "k": kind_of(rec),
            "g": gear_of(rec),
            "s": TIMING.get(rec["body_part"], 40),
            "d": steps_of(rec),
        }
        out.append(item)

    out.sort(key=lambda x: (PART_ORDER.index(x["p"]), x["n"]))
    seen = {}
    for item in out:
        n = item["n"]
        if n in seen:
            seen[n] += 1
            item["n"] = n + ("二式" if seen[n] == 2 else "三式" if seen[n] == 3 else str(seen[n]))
        else:
            seen[n] = 1

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({
            "source": "https://github.com/hasaneyldrm/exercises-dataset",
            "license": "data & instructions MIT; media (c) Gym visual - licensed",
            "items": out,
        }, f, ensure_ascii=False, separators=(",", ":"))

    print("写入", OUT, "动作数", len(out), "未翻译", len(misses),
          "大小 %.1fKB" % (os.path.getsize(OUT) / 1024))
    for m in misses:
        print("  MISS", m)


if __name__ == "__main__":
    main()
