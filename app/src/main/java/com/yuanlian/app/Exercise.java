package com.yuanlian.app;

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

    /** 训练页展示用：取前两步要领，过长自动截断。 */
    /** 训练页只需要一句提示：优先整句，其次在句号处截断，不要半句加省略号。 */
    public String brief() {
        if (steps.isEmpty()) {
            return "";
        }
        String s = steps.get(0).replace("首先，", "").replace("首先", "").trim();
        if (s.length() < 26 && steps.size() > 1) {
            s = s + " " + steps.get(1);
        }
        if (s.length() <= 78) {
            return s;
        }
        int cut = -1;
        for (int i = 0; i < 78; i++) {
            char c = s.charAt(i);
            if (c == '。' || c == '；') {
                cut = i + 1;
            }
        }
        return cut > 30 ? s.substring(0, cut) : s.substring(0, 78) + "…";
    }
}
