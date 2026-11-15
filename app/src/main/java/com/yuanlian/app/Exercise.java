package com.yuanlian.app;

import java.util.List;

/**
 * 单个动作。
 *
 * 名称、要领来自 exercises-dataset（MIT）；
 * 演示媒体来自 © Gym visual（https://gymvisual.com/），已购授权。
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
    }

    public boolean needsGear() {
        return !gear.isEmpty();
    }

    public boolean hasPhoto() {
        return !img.isEmpty();
    }

    /** 训练页展示用：取前两步要领，过长自动截断。 */
    public String brief() {
        if (steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(steps.get(0));
        if (steps.size() > 1) {
            sb.append(" ").append(steps.get(1));
        }
        String s = sb.toString().replace("首先，", "").replace("首先", "");
        return s.length() > 96 ? s.substring(0, 96) + "…" : s;
    }
}
