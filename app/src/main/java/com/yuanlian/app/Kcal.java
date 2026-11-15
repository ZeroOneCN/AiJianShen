package com.yuanlian.app;

/**
 * 消耗估算：按 MET（代谢当量）算，比「每分钟固定 6 千卡」更贴近实际。
 *
 * <pre>
 * 千卡 = MET × 3.5 × 体重(kg) / 200 × 分钟
 * </pre>
 *
 * MET 取自公开的运动能量消耗表（力量训练 5–6、拉伸 2.3–2.8、有氧 7–9），
 * 这里按动作类型取一档，站着的组间休息按 1.5 计。没有体重时用 65 kg 兜底，
 * 保证同一套算法在没称过体重时也能给出稳定数字。
 */
public final class Kcal {

    /** 没有体重数据时的兜底体重。 */
    public static final float DEFAULT_WEIGHT = 65f;

    private Kcal() {
    }

    /** 动作对应的 MET。 */
    public static double metFor(Exercise e) {
        if (e == null) {
            return 1.5;
        }
        switch (e.kind) {
            case "有氧":
                return 8.0;
            case "爆发":
                return 7.0;
            case "拉伸":
                return 2.5;
            case "核心":
                return 4.5;
            case "力量":
            default:
                // 用到器械的力量动作强度更高（推拉/下蹲类），徒手略低
                return e.needsGear() ? 6.0 : 5.0;
        }
    }

    /** 站着的组间休息。 */
    public static final double MET_REST = 1.5;

    /** 一段动作（seconds 秒）的消耗。 */
    public static float burn(float weightKg, int seconds, double met) {
        if (seconds <= 0) {
            return 0f;
        }
        float kg = weightKg > 0 ? weightKg : DEFAULT_WEIGHT;
        return (float) (met * 3.5 * kg / 200.0 * (seconds / 60.0));
    }

    /** 没有体重、只知道分钟数时的粗略值，用于旧记录。 */
    public static int rough(int minutes) {
        return Math.max(0, minutes) * 6;
    }
}
