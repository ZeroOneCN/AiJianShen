package com.aijianshen.app;

import java.util.List;
import java.util.Locale;

/**
 * 单个动作。
 */
public class Exercise {
    public final String id;
    public final String name;
    public final String nameEn;
    /** 部位：核心 / 腿臀 / 胸 / 背 / 肩 / 手臂 / 全身 */
    public final String part;
    /** 目标肌群 */
    public final String target;
    /** 类型：力量 / 拉伸 / 爆发 / 有氧 */
    public final String kind;
    /** 需要借助的家具/器械（长凳、单杠、墙面…），空串表示躺地上就能做 */
    public final String gear;
    public final int seconds;
    public final List<String> steps;
    /** 常见错误 */
    public final List<String> mistakes;
    /** 动作 id，对应 assets/img/<id>.jpg 与 assets/anim/<id>.webp */
    public final String img;

    /** 预先拼好的小写检索串，列表每次输入都搜一遍 1324 条，不能每次再 toLowerCase。 */
    private final String searchKey;

    public Exercise(String id, String name, String nameEn, String part, String target,
                    String kind, String gear, int seconds,
                    List<String> steps, List<String> mistakes, String img) {
        this.id = id;
        this.name = name;
        this.nameEn = nameEn;
        this.part = part;
        this.target = target;
        this.kind = kind == null ? "" : kind;
        this.gear = gear == null ? "" : gear;
        this.seconds = seconds;
        this.steps = steps;
        this.mistakes = mistakes;
        this.img = img == null ? "" : img;
        this.searchKey = (name + "\u0000" + nameEn + "\u0000" + target).toLowerCase(Locale.ROOT);
    }

    /** 关键字已经转成小写；部位传 null / 空 / 「全部」表示不限。 */
    public boolean matches(String lowerKeyword, String part) {
        if (part != null && !part.isEmpty() && !"全部".equals(part) && !this.part.equals(part)) {
            return false;
        }
        return lowerKeyword == null || lowerKeyword.isEmpty() || searchKey.contains(lowerKeyword);
    }


    public boolean needsGear() {
        return !gear.isEmpty();
    }

    public boolean hasPhoto() {
        return !img.isEmpty();
    }

    /** 呼吸提示：按动作类型给一句可执行的话。 */
    public String breathingTip() {
        if ("拉伸".equals(kind)) {
            return "保持均匀呼吸，拉到有轻微牵拉感即可，不要憋气。";
        }
        if ("有氧".equals(kind) || "爆发".equals(kind)) {
            return "鼻吸口呼，跟着动作节奏呼吸，别屏住气。";
        }
        if ("核心".equals(part)) {
            return "发力时呼气、还原时吸气，腰背始终贴稳。";
        }
        return "发力时呼气，还原时吸气；动作慢一点，呼吸跟上。";
    }

    /** 训练页提示：前两步要领整段给出，按句子收尾，不出现「半句…」。 */
    public String brief() {
        if (steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, steps.size()); i++) {
            String one = steps.get(i).replace("首先，", "").replace("首先", "").trim();
            if (one.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(one);
        }
        String s = sb.toString();
        if (s.length() <= 112) {
            return s;
        }
        // 太长时只在句末收尾，宁可少一句，也不要半句加省略号
        int cut = -1;
        for (int i = 0; i < 112 && i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '。' || c == '；') {
                cut = i + 1;
            }
        }
        return cut > 40 ? s.substring(0, cut) : s.substring(0, 112);
    }

    // ---- 难度推断 ----

    /** 明显更难的说法：单侧、爆发、体操类动作，直接比同部位高两档。 */
    private static final String[] HARD_2 = {
            "单臂", "单腿", "单手", "手枪", "击掌", "跳跃", "爆发", "深度跳",
            "倒立", "旗帜", "前水平", "马耳他", "俄挺", "慢起", "杠铃", "壶铃"};
    /** 略难：负重、下斜、器械、全程幅度。 */
    private static final String[] HARD_1 = {
            "下斜", "负重", "哑铃", "药球", "史密斯", "绳索", "全程", "加强"};
    /** 明显更简单：靠墙、辅助、跪姿、半程。 */
    private static final String[] EASY_2 = {
            "靠墙", "辅助", "助力", "跪姿", "半程", "简化"};
    /** 略简单：上斜、坐姿、静态。 */
    private static final String[] EASY_1 = {
            "上斜", "坐姿", "静态", "支撑"};

    private int difficulty = -1;

    /**
     * 粗略难度 1–5。动作库没有难度字段，只能按命名规律推断：
     * 不求每个都准，只要「同部位里谁比谁简单」的相对顺序大致对，
     * 换动作就不会再拿秒数硬猜（同部位常常全是 40 秒）。
     */
    public int difficulty() {
        if (difficulty > 0) {
            return difficulty;
        }
        String n = name == null ? "" : name;
        int d = 3;
        for (String k : HARD_2) {
            if (n.contains(k)) {
                d += 2;
            }
        }
        for (String k : HARD_1) {
            if (n.contains(k)) {
                d += 1;
            }
        }
        for (String k : EASY_2) {
            if (n.contains(k)) {
                d -= 2;
            }
        }
        for (String k : EASY_1) {
            if (n.contains(k)) {
                d -= 1;
            }
        }
        if ("爆发".equals(kind)) {
            d += 1;
        } else if ("拉伸".equals(kind)) {
            d -= 1;
        }
        difficulty = Math.max(1, Math.min(5, d));
        return difficulty;
    }
}
