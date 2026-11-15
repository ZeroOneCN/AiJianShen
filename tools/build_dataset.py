#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 exercises-dataset 的自重动作转成本项目使用的紧凑资源。

数据来源：https://github.com/hasaneyldrm/exercises-dataset （MIT，动作元数据 + 中文说明）
媒体（GIF/缩略图）为 © Gym visual，本项目不使用。
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, ".data", "exercises.json")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "exercises.json")

PART_ZH = {
    "waist": "核心", "upper legs": "腿部", "back": "背部", "chest": "胸部",
    "upper arms": "手臂", "cardio": "有氧", "lower legs": "小腿",
    "lower arms": "前臂", "shoulders": "肩部", "neck": "颈部",
}

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
}

# 短语优先（长的排前面），再退到单词
PHRASES = [
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
    ("half knee bends", "半蹲"),
    ("half sit-up", "半程仰卧起坐"),
    ("quarter sit-up", "四分之一仰卧起坐"),
    ("full sit-up", "全程仰卧起坐"),
    ("sit-up", "仰卧起坐"),
    ("sit up", "仰卧起坐"),
    ("push-up", "俯卧撑"),
    ("push up", "俯卧撑"),
    ("pull-up", "引体向上"),
    ("pull up", "引体向上"),
    ("chin-up", "反握引体"),
    ("chin ups", "反握引体"),
    ("chin-up", "反握引体"),
    ("muscle-up", "双力臂"),
    ("muscle up", "双力臂"),
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
    ("hyperextension", "背伸"),
    ("inverse leg curl", "反向腿弯举"),
    ("glute-ham raise", "臀腿挺身"),
    ("back extension", "背部伸展"),
    ("good morning", "早安式"),
    ("mountain climber", "登山跑"),
    ("bear crawl", "熊爬"),
    ("spider crawl", "蜘蛛爬"),
    ("inchworm", "毛毛虫爬"),
    ("star jump", "星跳"),
    ("jack jump", "开合跳"),
    ("jack burpee", "开合波比"),
    ("power point", "力量"),
    ("wind sprints", "短距离冲刺"),
    ("high knee", "高抬腿"),
    ("high knees", "高抬腿"),
    ("jump squat", "深蹲跳"),
    ("split squat", "分腿蹲"),
    ("walking lunge", "行进箭步蹲"),
    ("forward lunge", "前弓步"),
    ("lunge", "弓步"),
    ("squat", "深蹲"),
    ("wall sit", "靠墙静蹲"),
    ("potty squat", "深蹲起身"),
    ("sissy squat", "西斯深蹲"),
    ("dip-pull-up cage", "双杠架"),
    ("on parallel bars", "双杠"),
    ("parallel grip", "对握"),
    ("straight bar", "直杆"),
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
    ("inverted", "倒立"),
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
    ("ab", "腹"),
    ("cobra", "眼镜蛇式"),
    ("upward facing dog", "上犬式"),
    ("butterfly yoga pose", "蝴蝶式"),
    ("widest angle pose", "广角式"),
    ("wide angle pose sequence", "广角式序列"),
    ("world greatest stretch", "全身舒展"),
    ("worlds greatest stretch", "全身舒展"),
    ("lat stretch", "背阔肌拉伸"),
    ("hamstring stretch", "腘绳肌拉伸"),
    ("quads stretch", "股四头肌拉伸"),
    ("pect stretch", "胸肌拉伸"),
    ("chest stretch", "胸部拉伸"),
    ("triceps stretch", "肱三头肌拉伸"),
    ("calf stretch", "小腿拉伸"),
    ("glute stretch", "臀部拉伸"),
    ("piriformis stretch", "梨状肌拉伸"),
    ("neck stretch", "颈部拉伸"),
    ("back stretch", "背部拉伸"),
    ("wrist pull stretch", "手腕拉伸"),
    ("side lying floor stretch", "侧卧拉伸"),
    ("iron cross", "十字"),
    ("towel row", "毛巾划船"),
    ("inverted row", "反向划船"),
    ("squatting row", "深蹲划船"),
    ("standing row", "站姿划船"),
    ("one arm row", "单臂划船"),
    ("korean dips", "韩式双杠臂屈伸"),
    ("bench dip", "凳上臂屈伸"),
    ("triceps dip", "肱三头肌臂屈伸"),
    ("chest dip", "胸部臂屈伸"),
    ("elbow dips", "肘撑臂屈伸"),
    ("dips", "臂屈伸"),
    ("dip", "臂屈伸"),
    ("planche", "水平支撑"),
    ("maltese", "马耳他"),
    ("lever", "杠杆"),
    ("flag", "人体旗帜"),
    ("l-sit", "L 字支撑"),
    ("v-sit", "V 字支撑"),
    ("elbow-to-knee", "肘碰膝"),
    ("elbow to knee", "肘碰膝"),
    ("heel touchers", "摸脚跟"),
    ("toe touch", "摸脚"),
    ("twist", "转体"),
    ("twisting", "转体"),
    ("rotation", "旋转"),
    ("circles", "绕环"),
    ("circle", "绕环"),
    ("march", "行军步"),
    ("step", "踏步"),
    ("walk", "行走"),
    ("run", "跑"),
    ("jump", "跳"),
    ("hop", "跳"),
    ("hops", "跳"),
    ("kick", "腿踢"),
    ("raise", "抬起"),
    ("press", "推举"),
    ("push", "推"),
    ("pull", "拉"),
    ("curl", "弯举"),
    ("crunch", "卷腹"),
    ("crunches", "卷腹"),
    ("flexion", "屈曲"),
    ("extension", "伸展"),
    ("stretch", "拉伸"),
    ("squeeze", "静力夹胸"),
    ("pelvic tilt", "骨盆后倾"),
    ("pelvic", "骨盆"),
    ("spine", "脊柱"),
    ("neck", "颈部"),
    ("shoulder", "肩部"),
    ("wrist", "手腕"),
    ("ankle", "踝"),
    ("knee", "膝"),
    ("groin", "腹股沟"),
    ("glute", "臀"),
    ("ham", "腘绳"),
    ("quad", "股四"),
    ("calf", "小腿"),
    ("lower back", "下背"),
    ("upper back", "上背"),
    ("back", "背"),
    ("chest", "胸"),
    ("core", "核心"),
    ("squats", "深蹲"),
    ("balance board", "平衡板"),
    ("handstand", "倒立"),
    ("self assisted", "自我辅助"),
    ("suspended", "悬吊"),
    ("support", "支撑"),
    ("bench leg", "长凳"),
    ("bench", "长凳"),
    ("floor", "地面"),
    ("wall", "靠墙"),
    ("box", "跳箱"),
    ("strap", "吊带"),
    ("straps", "吊带"),
    ("bar", "单杠"),
    # ---- 第二轮补充 ----
    ("pull ups", "引体向上"),
    ("chin up", "反握引体"),
    ("push ups", "俯卧撑"),
    ("sit ups", "仰卧起坐"),
    ("muscle ups", "双力臂"),
    ("parallel bars", "双杠"),
    ("flat bench", "平板凳"),
    ("lower arms", "前臂"),
    ("upper arms", "上臂"),
    ("all fours", "四足跪姿"),
    ("squad stretch", "拉伸"),
    ("arm slingers hanging bent knee legs", "悬垂屈膝摆腿"),
    ("arm slingers hanging straight legs", "悬垂直腿摆腿"),
    ("back and forth step", "往复踏步"),
    ("pec stretch", "胸肌拉伸"),
    ("pec", "胸肌"),
    ("bottoms up", "提臀举腿"),
    ("butt ups", "提臀"),
    ("dead bug", "死虫式"),
    ("curtsey squat", "交叉屈膝蹲"),
    ("clock push up", "钟面俯卧撑"),
    ("cocoons", "茧式卷腹"),
    ("hindu push up", "印度俯卧撑"),
    ("janda sit up", "扬达仰卧起坐"),
    ("l pull up", "L 字引体向上"),
    ("l sit", "L 字支撑"),
    ("v sit", "V 字支撑"),
    ("russian twist", "俄罗斯转体"),
    ("semi squat jump", "半蹲跳"),
    ("scissor jumps", "剪刀跳"),
    ("skin the cat", "猫式翻越"),
    ("split squats", "分腿蹲"),
    ("stalder press", "斯托尔德推举"),
    ("straddle", "宽分腿"),
    ("sternum chin", "胸骨反握引体"),
    ("gironda", "吉隆达"),
    ("outstretched leg", "伸展腿"),
    ("platform slide", "平台滑步"),
    ("hug keens to chest", "抱膝触胸"),
    ("incline push up depth jump", "上斜俯卧撑落深跳"),
    ("isometric wipers", "等长雨刷"),
    ("knee touch crunch", "触膝卷腹"),
    ("leg pull in", "收腿"),
    ("pull in", "收腿"),
    ("quick feet", "快速碎步"),
    ("reverse hyper", "反向背伸"),
    ("rocky pull up pulldown", "摇摆引体下拉"),
    ("runners stretch", "跑者拉伸"),
    ("shoulder grip", "宽握"),
    ("side to side", "左右"),
    ("straight leg outer hip abductor", "直腿髋外展"),
    ("suspended abdominal fallout", "悬吊腹部前伸"),
    ("swimmer kicks", "游泳打水"),
    ("between benches", "双凳间"),
    ("benches", "长凳"),
    ("vertical", "垂直"),
    ("wheel run", "滚轮跑"),
    ("walking high knees lunge", "行进高抬腿弓步"),
    ("on high parallel bars", "高双杠"),
    ("outside leg kick push up", "外侧踢腿俯卧撑"),
    ("inside leg kick", "内侧踢腿"),
    ("push up plus", "加强俯卧撑"),
    ("cable machine", "绳索器械"),
    ("chest dip on dip pull up cage", "双杠架臂屈伸"),
    ("outside", "外侧"),
    ("inside", "内侧"),
    ("into", "接"),
    ("touch", "触碰"),
    ("narrow", "窄距"),
    ("upper", "上"),
    ("lower", "下"),
    ("abductor", "外展肌"),
    ("grip", "握法"),
    ("reps", "次数"),
    ("legs", "腿"),
    ("knees", "膝"),
    ("feet", "足"),
    ("jumps", "跳"),
    ("kicks", "踢"),
    ("ups", "上"),
]

# 组合读起来别扭的整名覆盖
EXACT = {
    "all fours squad stretch": "四足跪姿拉伸",
    "astride jumps (male)": "分腿跳",
    "back and forth step": "往复踏步",
    "bench pull-ups": "长凳引体向上",
    "biceps narrow pull-ups": "窄距肱二头肌引体",
    "bottoms-up": "提臀举腿",
    "butt-ups": "提臀",
    "captains chair straight leg raise": "船长椅直腿举腿",
    "chest dip (on dip-pull-up cage)": "双杠架臂屈伸",
    "clock push-up": "钟面俯卧撑",
    "cocoons": "茧式卷腹",
    "curtsey squat": "交叉屈膝蹲",
    "dead bug": "死虫式",
    "flutter kicks": "打水踢腿",
    "front lever reps": "前水平杠杆次数",
    "gironda sternum chin": "吉隆达胸骨反握引体",
    "gorilla chin": "猩猩反握引体",
    "hug keens to chest": "抱膝触胸",
    "isometric wipers": "等长雨刷",
    "inverse leg curl (on pull-up cable machine)": "绳索器械反向腿弯举",
    "inverted row bent knees": "屈膝反向划船",
    "l-pull-up": "L 字引体向上",
    "l-sit on floor": "地面 L 字支撑",
    "leg pull in flat bench": "平板凳收腿",
    "lying leg raise flat bench": "平板凳仰卧举腿",
    "mixed grip chin-up": "正反握引体",
    "modified hindu push-up (male)": "简化印度俯卧撑",
    "modified push up to lower arms": "简化前臂俯卧撑",
    "one arm chin-up": "单臂引体向上",
    "outside leg kick push-up": "外侧踢腿俯卧撑",
    "pelvic tilt into bridge": "骨盆后倾接臀桥",
    "push-up inside leg kick": "内侧踢腿俯卧撑",
    "push-up on lower arms": "前臂俯卧撑",
    "push-up plus": "加强俯卧撑",
    "quick feet v. 2": "快速碎步二式",
    "reverse hyper on flat bench": "平板凳反向背伸",
    "rocky pull-up pulldown": "摇摆引体下拉",
    "runners stretch": "跑者拉伸",
    "russian twist": "俄罗斯转体",
    "scissor jumps (male)": "剪刀跳",
    "seated lower back stretch": "坐姿下背拉伸",
    "semi squat jump (male)": "半蹲跳",
    "shoulder grip pull-up": "宽握引体向上",
    "side-to-side chin": "左右摆头",
    "single leg bridge with outstretched leg": "单腿伸展腿臀桥",
    "single leg platform slide": "单腿平台滑步",
    "skin the cat": "猫式翻越",
    "split squats": "分腿蹲",
    "stalder press": "斯托尔德推举",
    "straddle maltese": "宽分腿马耳他",
    "straddle planche": "宽分腿水平支撑",
    "straight leg outer hip abductor": "直腿髋外展",
    "suspended abdominal fallout": "悬吊腹部前伸",
    "swimmer kicks v. 2 (male)": "游泳打水二式",
    "triceps dip (between benches)": "双凳间肱三头肌臂屈伸",
    "upper back stretch": "上背拉伸",
    "v-sit on floor": "地面 V 字支撑",
    "vertical leg raise (on parallel bars)": "双杠垂直举腿",
    "walking high knees lunge": "行进高抬腿弓步",
    "wheel run": "滚轮跑",
    "wide-grip chest dip on high parallel bars": "高双杠宽距胸部臂屈伸",
    "chin-up": "反握引体向上",
    "chin-ups (narrow parallel grip)": "窄距对握引体向上",
    "close grip chin-up": "窄距反握引体",
    "one arm chin-up": "单臂引体向上",
}

# 第三轮：逐条修正读起来别扭的译名
EXACT.update({
    "3/4 sit-up": "四分之三仰卧起坐",
    "cross body crunch": "交叉卷腹",
    "incline leg hip raise (leg straight)": "上斜直腿提髋",
    "push-up to side plank": "俯卧撑接侧平板",
    "power point plank": "肘撑平板支撑",
    "curl-up": "屈体卷腹",
    "flexion leg sit up (bent knee)": "屈膝屈体仰卧起坐",
    "flexion leg sit up (straight arm)": "直臂屈体仰卧起坐",
    "bridge - mountain climber (cross body)": "臀桥接交叉登山跑",
    "seated side crunch (wall)": "靠墙坐姿侧卷腹",
    "hip raise (bent knee)": "屈膝提髋",
    "shoulder tap": "点肩",
    "kneeling plank tap shoulder (male)": "跪姿平板点肩",
    "low glute bridge on floor": "地面低位臀桥",
    "side lying hip adduction (male)": "侧卧髋内收",
    "hands clasped circular toe touch (male)": "双手交握绕环摸脚",
    "arms apart circular toe touch (male)": "双臂分开绕环摸脚",
    "hands reversed clasped circular toe touch (male)": "反手交握绕环摸脚",
    "single leg squat (pistol) male": "手枪深蹲",
    "inverse leg curl (bench support)": "长凳支撑反向腿弯举",
    "rear decline bridge": "后倾臀桥",
    "chair leg extended stretch": "椅上伸腿拉伸",
    "potty squat with support": "扶持深蹲起身",
    "pike-to-cobra push-up": "折体接眼镜蛇俯卧撑",
    "kick out sit": "外踢坐姿",
    "glute bridge two legs on bench (male)": "长凳双腿臀桥",
    "glute-ham raise": "臀腿挺身",
    "self assisted inverse leg curl (on floor)": "地面自我辅助反向腿弯举",
    "march sit (wall)": "靠墙坐姿踏步",
    "twist hip lift": "转体提髋",
    "lower back curl": "下背屈曲",
    "elevator": "升降卷腹",
    "two toe touch (male)": "双脚并拢摸脚",
    "muscle-up (on vertical bar)": "垂直杆双力臂",
    "sphinx": "狮身人面式",
    "standing archer": "站姿弓手式",
    "back pec stretch": "背部胸肌拉伸",
    "back lever": "后水平杠杆",
    "front lever": "前水平杠杆",
    "pull up (neutral grip)": "对握引体向上",
    "reverse grip pull-up": "反手握引体向上",
    "crunch (hands overhead)": "双手过顶卷腹",
    "side hip (on parallel bars)": "双杠侧髋",
    "reverse plank with leg lift": "反向平板抬腿",
    "posterior step to overhead reach": "后撤步过顶伸展",
    "body-up": "反撑起",
    "isometric chest squeeze": "等长静力夹胸",
    "chest tap push-up (male)": "点胸俯卧撑",
    "shoulder tap push-up": "点肩俯卧撑",
    "raise single arm push-up": "单臂抬起俯卧撑",
    "push and pull bodyweight": "推拉支撑",
    "chest and front of shoulder stretch": "胸肩前侧拉伸",
    "chest dip on straight bar": "直杆胸部臂屈伸",
    "three bench dip": "三凳臂屈伸",
    "bench dip on floor": "地面凳上臂屈伸",
    "bench dip (knees bent)": "屈膝凳上臂屈伸",
    "triceps dips floor": "地面肱三头肌臂屈伸",
    "triceps dip (bench leg)": "长凳肱三头肌臂屈伸",
    "biceps leg concentration curl": "单腿集中弯举",
    "bodyweight side lying biceps curl": "侧卧自重弯举",
    "push to run": "俯撑起身跑",
    "swing 360": "360 度摆动",
    "standing calves": "站姿提踵",
    "standing calves calf stretch": "站姿小腿拉伸",
    "calf push stretch with hands against wall": "双手推墙小腿拉伸",
    "circles knee stretch": "绕膝拉伸",
    "box jump down with one leg stabilization": "跳箱单腿落地稳定",
    "left hook. boxing": "左勾拳",
    "side push neck stretch": "侧向颈部拉伸",
    "single leg bridge with outstretched leg": "单腿伸展臀桥",
    "dip-pull-up": "臂屈伸引体",
    "incline push-up (on box)": "跳箱上斜俯卧撑",
    "full planche push-up": "水平支撑俯卧撑",
    "close-grip push-up (on knees)": "跪姿窄距俯卧撑",
    "biceps narrow pull-ups": "窄距反握引体",
    "triceps press": "肱三头肌伸展",
    "oblique crunches floor": "地面腹斜卷腹",
    "run (equipment)": "器械跑",
    "run": "跑步",
    "standing calf raise (on a staircase)": "台阶站姿提踵",
    "high knee against wall": "靠墙高抬腿",
    "bodyweight incline side plank": "上斜侧平板支撑",
})

WORDS = {
    "and": "", "with": "", "on": "", "the": "", "of": "", "to": "", "a": "",
    "for": "", "in": "", "at": "", "male": "", "female": "", "equipment": "",
    "left": "左", "right": "右", "both": "", "cross": "交叉", "body": "身",
    "full": "全程", "half": "半程", "quarter": "四分之一", "alternate": "交替",
    "alternating": "交替", "side": "侧", "front": "前", "rear": "后侧",
    "backward": "向后", "forward": "向前", "lateral": "侧向", "out": "外",
    "up": "上", "down": "下", "high": "高", "low": "低", "deep": "深",
    "quick": "快速", "slow": "慢速", "short": "短", "stride": "步幅",
    "single": "单", "double": "双", "two": "双", "one": "单", "three": "三",
    "leg": "腿", "arm": "臂", "knee": "膝", "hip": "髋", "toe": "脚尖",
    "heel": "脚跟", "hand": "手", "hands": "手", "head": "头", "neck": "颈",
    "shoulder": "肩", "elbow": "肘", "spine": "脊柱", "chest": "胸",
    "abs": "腹肌", "ab": "腹肌", "belly": "腹", "obliques": "腹斜肌",
    "glutes": "臀", "hamstrings": "腘绳肌", "quads": "股四头肌",
    "calves": "小腿", "lats": "背阔肌", "biceps": "肱二头肌",
    "triceps": "肱三头肌", "deltoid": "三角肌", "traps": "斜方肌",
    "forearms": "前臂", "wrists": "腕", "ankles": "踝",
    "sit": "坐", "stand": "站", "standing": "站姿", "seated": "坐姿",
    "lying": "仰卧", "kneeling": "跪姿", "hanging": "悬垂", "sitting": "坐姿",
    "bridge": "桥", "plank": "平板支撑", "twist": "转体",
    "cycle": "单车", "bike": "单车", "air": "空中", "bicycle": "自行车",
    "burpee": "波比跳", "burpees": "波比跳", "skater": "滑冰式",
    "ski": "滑雪", "swimmer": "游泳", "sphinx": "狮身人面",
    "superman": "超人式", "cobra": "眼镜蛇式", "cat": "猫式",
    "dog": "犬式", "cow": "牛式", "child": "婴儿式",
    "pose": "式", "yoga": "瑜伽", "sequence": "序列", "stretch": "拉伸",
    "warm": "热身", "cool": "放松", "rest": "休息",
    "board": "板", "chain": "链", "ring": "吊环", "rings": "吊环",
    "step": "踏步", "march": "行军", "walk": "走", "run": "跑",
    "jump": "跳", "hop": "跳", "kick": "踢", "raise": "抬",
    "lift": "抬", "press": "推", "pull": "拉", "push": "推",
    "hold": "保持", "squeeze": "夹", "tap": "点", "reach": "伸展",
    "circle": "绕环", "circles": "绕环", "pulse": "脉冲",
    "elevator": "升降", "wiper": "雨刷", "scissor": "剪刀", "flutter": "打水",
    "cocoon": "茧式", "jackknife": "折刀", "rocking": "摇摆", "rocky": "摇摆",
    "slide": "滑", "swing": "摆", "monster": "怪兽", "gorilla": "猩猩",
    "prisoner": "抱头", "captain": "船长", "chair": "椅上",
    "crab": "蟹式", "frog": "蛙式", "donkey": "驴式", "horse": "马步",
    "wall": "靠墙", "floor": "地面", "bench": "长凳", "box": "跳箱",
    "bar": "单杠", "pole": "杆", "strap": "吊带", "towel": "毛巾",
    "suspended": "悬吊", "inverted": "倒置", "incline": "上斜",
    "decline": "下斜", "reverse": "反向", "negative": "退让",
    "isometric": "等长", "explosive": "爆发", "plyo": "爆发",
    "concentration": "集中", "assisted": "辅助", "self": "自我",
    "static": "静态", "dynamic": "动态", "modified": "简化",
    "classic": "经典", "basic": "基础", "full": "全程", "half": "半程",
    "knee": "膝", "bent": "屈", "straight": "直", "extended": "伸展",
    "flexion": "屈曲", "extension": "伸展", "stabilization": "稳定",
    "twist": "转体", "twisted": "转体", "touchers": "触碰",
    "clasped": "交握", "reversed": "反手", "overhead": "过顶",
    "apart": "分开", "circular": "绕环", "astride": "分腿",
    "balance": "平衡", "posterior": "后侧", "anterior": "前侧",
    "control": "控制", "tempo": "节奏", "drop": "落差",
    "clap": "击掌", "archer": "弓手", "diamond": "钻石", "pike": "折体",
    "scapula": "肩胛", "scapular": "肩胛", "handstand": "倒立",
    "planche": "水平支撑", "maltese": "马耳他", "lever": "杠杆",
    "flag": "人体旗帜", "muscle": "肌肉", "kipping": "借力",
    "hook": "勾拳", "boxing": "拳击", "hook.": "勾拳",
    "impossible": "极限", "lean": "前倾", "tuck": "团身",
    "pistol": "手枪", "squat": "深蹲", "lunge": "弓步",
    "dip": "臂屈伸", "dips": "臂屈伸", "crunch": "卷腹",
    "curl": "弯举", "row": "划船", "rotation": "旋转", "tilt": "倾斜",
}

# 需要器械辅助（非纯自重）的判定
GEAR = [
    ("pull-up", "需单杠"), ("chin-up", "需单杠"), ("chin up", "需单杠"),
    ("hanging", "需单杠"), ("muscle-up", "需单杠"), ("dip", "需双杠或凳"),
    ("dips", "需双杠或凳"), ("lever", "需器械"), ("planche", "需较高基础"),
    ("maltese", "需器械"), ("parallel bars", "需双杠"), ("bench", "需长凳"),
    ("inverted row", "需低杠"), ("handstand", "需较高基础"), ("flag", "需较高基础"),
]

TIMING = {"cardio": 45, "waist": 40, "chest": 40, "upper legs": 45,
          "back": 40, "upper arms": 35, "lower legs": 35, "lower arms": 30,
          "shoulders": 35, "neck": 30}


def clean(name):
    n = name.lower()
    n = re.sub(r"\((male|female)\)", "", n)
    n = n.replace("(", " ").replace(")", " ").replace(",", " ")
    n = n.replace("/", " ").replace("-", " ")
    n = re.sub(r"\bv\s*\.?\s*2\b", "v2", n)
    n = re.sub(r"\s+", " ", n).strip()
    return n


def translate(name):
    """短语最长匹配 + 单词兜底；有任一未识别片段则返回 None。"""
    if name.lower() in EXACT:
        return EXACT[name.lower()], EXACT[name.lower()]
    s = " " + clean(name) + " "
    s = s.replace("v2", " v. 2 ")
    out = []
    pending = False
    phrases = sorted(PHRASES, key=lambda p: -len(p[0]))
    i = 0
    # 先做短语替换，用占位符保护
    for idx, (en, zh) in enumerate(phrases):
        pat = r"(?<![\w])" + re.escape(en) + r"(?![\w])"
        if re.search(pat, s):
            s = re.sub(pat, " \u0001%d\u0001 " % idx, s)
    for tok in s.split():
        if tok.startswith("\u0001"):
            out.append(phrases[int(tok.strip("\u0001"))][1])
        elif tok in WORDS:
            out.append(WORDS[tok])
        elif re.fullmatch(r"[0-9/°]+", tok):
            out.append(tok)
        else:
            pending = True
            out.append("[%s]" % tok)
    zh = "".join(out).strip()
    return (None if pending else zh), zh


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
    bw = [x for x in data if x["equipment"] == "body weight"]
    out, bad = [], []
    for rec in bw:
        zh, raw = translate(rec["name"])
        if not zh:
            bad.append((rec["name"], raw))
            continue
        part = PART_ZH.get(rec["body_part"], rec["body_part"])
        gear = ""
        low = rec["name"].lower()
        for k, v in GEAR:
            if k in low:
                gear = v
                break
        item = {
            "id": rec["id"],
            "n": zh,
            "e": rec["name"],
            "p": part,
            "t": TARGET_ZH.get(rec["target"], rec["target"]),
            "g": gear,
            "s": TIMING.get(rec["body_part"], 40),
            "d": steps_of(rec),
        }
        out.append(item)
    out.sort(key=lambda x: (list(PART_ZH.values()).index(x["p"])
                            if x["p"] in PART_ZH.values() else 99, x["n"]))

    # 同名去重：数据集中 male/female 等变体译名会撞车，按出现顺序加后缀
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
        json.dump({"source": "https://github.com/hasaneyldrm/exercises-dataset",
                   "license": "MIT (data & instructions); media (c) Gym visual - not used",
                   "items": out}, f, ensure_ascii=False, separators=(",", ":"))
    print("写入", OUT, "动作数", len(out), "未识别", len(bad))
    for n, r in bad:
        print("  MISS", n, "->", r)


if __name__ == "__main__":
    main()
