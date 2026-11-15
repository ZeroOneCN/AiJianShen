package com.yuanlian.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 身体成分估算（BIA）。
 *
 * <p>公式、阈值与取整规则逐项移植自 AntAF 的 {@code weight/body_composition.py}：
 * 瘦体重系数、体脂率分龄阈值、内脏脂肪经验式、骨量 / 肌肉量分档、Mifflin-St Jeor
 * 基础代谢。秤只给体重与阻抗，其余全部是估算值，不能作为医学诊断依据。
 */
public final class BodyComp {

    private BodyComp() {
    }

    /** 阻抗缺失时的兜底值，避免公式出现极端结果（与 Python 版一致）。 */
    public static final float FALLBACK_IMPEDANCE = 500f;

    private static final float SUB_FAT_COEFF = 0.71f;
    private static final float SKELETAL_MUSCLE_COEFF = 0.558f;
    private static final float PROTEIN_COEFF = 0.238f;

    /** 单项指标：key 与 AntAF 的 body 字段同名，同步过来的记录可直接按 key 取值。 */
    public static class Item {
        public final String key;
        public final String label;
        public final String unit;
        public final String text;
        public final String grade;
        public final double value;

        Item(String key, String label, String unit, String text, String grade, double value) {
            this.key = key;
            this.label = label;
            this.unit = unit;
            this.text = text;
            this.grade = grade;
            this.value = value;
        }
    }

    public static class Result {
        public final boolean valid;
        /** 是否用了兜底阻抗（代表没有真实阻抗数据，数值仅供参考）。 */
        public final boolean fallbackImpedance;
        public final List<Item> items;
        public final double bmi;
        public final double bodyFat;
        public final double muscle;
        public final double water;
        public final double bmr;
        public final double leanMass;
        private final Map<String, Item> byKey;

        Result(boolean valid, boolean fallback, List<Item> items) {
            this.valid = valid;
            this.fallbackImpedance = fallback;
            this.items = items;
            this.byKey = new LinkedHashMap<>();
            for (Item it : items) {
                byKey.put(it.key, it);
            }
            this.bmi = value("bmi");
            this.bodyFat = value("body_fat");
            this.muscle = value("muscle");
            this.water = value("water");
            this.bmr = value("bmr");
            this.leanMass = value("lean_mass");
        }

        public double value(String key) {
            Item it = byKey.get(key);
            return it == null ? 0 : it.value;
        }

        public String text(String key) {
            Item it = byKey.get(key);
            return it == null ? "--" : it.text;
        }

        public String grade(String key) {
            Item it = byKey.get(key);
            return it == null ? "" : it.grade;
        }
    }

    private static Result empty() {
        return new Result(false, false, new ArrayList<Item>());
    }

    /** 体脂率分龄阈值：{偏低上限, 标准上限, 偏高水平上限}。 */
    private static float[] fatRange(boolean male, int age) {
        if (male) {
            if (age < 40) return new float[]{11f, 17f, 22f};
            if (age < 60) return new float[]{12f, 18f, 23f};
            return new float[]{14f, 20f, 25f};
        }
        if (age < 40) return new float[]{21f, 28f, 35f};
        if (age < 60) return new float[]{22f, 29f, 36f};
        return new float[]{23f, 30f, 37f};
    }

    private static String gradeFat(double v, boolean male, int age) {
        float[] r = fatRange(male, age);
        if (v < r[0]) return "偏低";
        if (v < r[1]) return "标准";
        if (v < r[2]) return "偏高";
        return "过高";
    }

    private static String band(double v, double low, double high) {
        if (v < low) return "偏低";
        if (v <= high) return "标准";
        return "偏高";
    }

    private static String gradeWater(double v, boolean male) {
        return male ? band(v, 50, 65) : band(v, 45, 60);
    }

    private static void boneRange(boolean male, double kg, double[] out) {
        if (male) {
            if (kg >= 75) { out[0] = 2.0; out[1] = 4.2; return; }
            if (kg >= 60) { out[0] = 1.9; out[1] = 4.1; return; }
            out[0] = 1.6; out[1] = 3.9;
            return;
        }
        if (kg >= 60) { out[0] = 1.8; out[1] = 3.9; return; }
        if (kg >= 45) { out[0] = 1.5; out[1] = 3.8; return; }
        out[0] = 1.3; out[1] = 3.6;
    }

    private static void muscleRange(boolean male, double cm, double[] out) {
        if (male) {
            if (cm >= 170) { out[0] = 49.4; out[1] = 59.5; return; }
            if (cm >= 160) { out[0] = 44.0; out[1] = 52.5; return; }
            out[0] = 38.5; out[1] = 46.6;
            return;
        }
        if (cm >= 160) { out[0] = 36.5; out[1] = 42.6; return; }
        if (cm >= 150) { out[0] = 32.9; out[1] = 37.6; return; }
        out[0] = 29.1; out[1] = 34.8;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String f2(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    /**
     * 估算身体成分。
     *
     * @param kg        体重（千克）
     * @param impedance 阻抗（欧姆），≤0 时用兜底值
     * @param heightCm  身高（厘米）
     * @param age       年龄（岁）
     * @param male      是否男性
     */
    public static Result calculate(float kg, float impedance, float heightCm, int age,
                                   boolean male) {
        if (kg <= 0 || heightCm <= 0 || age <= 0) {
            return empty();
        }
        double weight = kg;
        double height = heightCm;
        double years = age;
        boolean fallback = impedance <= 0;
        double imp = fallback ? FALLBACK_IMPEDANCE : impedance;

        double bmi = weight / Math.pow(height / 100.0, 2);

        // 瘦体重系数（LBM coefficient）
        double lbm = (height * 9.058 / 100.0) * (height / 100.0)
                + weight * 0.32
                + 12.226
                - imp * 0.0068
                - years * 0.0542;

        // 体脂率
        double fatCoeff = 1.0;
        double fatConst;
        if (male) {
            fatConst = 0.8;
            if (weight < 61) fatCoeff = 0.98;
            if (height > 160) fatCoeff *= 1.03;
        } else {
            fatConst = age <= 49 ? 9.25 : 7.25;
            if (weight > 60) fatCoeff = 0.96;
            else if (weight < 50) fatCoeff = 1.02;
            if (height > 160) fatCoeff *= 1.03;
        }
        double fatPct = (1.0 - (((lbm - fatConst) * fatCoeff) / weight)) * 100.0;
        fatPct = Math.min(60.0, Math.max(3.0, fatPct));

        // 水分率
        double waterPct = (100.0 - fatPct) * 0.7;
        double waterCoeff = waterPct <= 50 ? 1.02 : 0.98;
        waterPct = Math.min(75.0, Math.max(35.0, waterPct * waterCoeff));

        // 骨量（千克）
        double bone = male
                ? (0.18016894 - (lbm * 0.05158)) * -1
                : (0.245691014 - (lbm * 0.07158)) * -1;
        bone += bone > 2.2 ? 0.1 : -0.1;
        bone = Math.max(0.5, bone);

        double fatMass = fatPct * 0.01 * weight;
        double muscleMass = weight - fatMass - bone * 0.85;
        double muscleRate = muscleMass / weight * 100.0;
        double leanMass = weight - fatMass;

        // 内脏脂肪等级：经验公式，限 1–50
        double visceralRaw = Math.min(50.0, Math.max(1.0, Math.round(bmi * 0.3 - years * 0.05 + 0.4)));
        int visceral = (int) visceralRaw;

        double subPct = fatPct * SUB_FAT_COEFF;
        double proteinMass = leanMass * PROTEIN_COEFF;
        double proteinRate = proteinMass / weight * 100.0;

        double skeletal = muscleMass * SKELETAL_MUSCLE_COEFF;
        double skeletalRate = skeletal / weight * 100.0;
        double skeletalRef = male ? 33.0 : 21.0;

        double bmr = male
                ? 10 * weight + 6.25 * height - 5 * years + 5
                : 10 * weight + 6.25 * height - 5 * years - 161;

        String fatGrade = gradeFat(fatPct, male, age);
        double[] mr = new double[2];
        muscleRange(male, height, mr);
        String muscleBand = band(muscleMass, mr[0], mr[1]);
        String muscleGrade = "偏高".equals(muscleBand) ? "优秀" : muscleBand;
        float[] fr = fatRange(male, age);
        double[] br = new double[2];
        boneRange(male, weight, br);

        String subGrade = band(subPct, fr[0] * SUB_FAT_COEFF, fr[1] * SUB_FAT_COEFF);
        String boneGrade = band(bone, br[0], br[1]);
        String waterGrade = gradeWater(waterPct, male);
        String proteinGrade = proteinRate < 16 ? "不足" : (proteinRate > 20 ? "优秀" : "标准");
        String skeletalGrade = band(skeletal, skeletalRef * 0.9, skeletalRef * 1.1);

        List<Item> items = new ArrayList<>(18);
        items.add(new Item("bmi", "BMI", "", f2(bmi), "", round2(bmi)));
        items.add(new Item("visceral_fat", "内脏脂肪", "级",
                String.valueOf(visceral), visceral <= 9 ? "标准" : "高", visceral));
        items.add(new Item("body_fat", "体脂率", "%", f2(fatPct), fatGrade, round2(fatPct)));
        items.add(new Item("fat_mass", "脂肪量", "kg", f2(fatMass), fatGrade, round2(fatMass)));
        items.add(new Item("sub_fat_pct", "皮下脂肪率", "%", f2(subPct), subGrade, round2(subPct)));
        items.add(new Item("sub_fat_mass", "皮下脂肪量", "kg",
                f2(weight * subPct * 0.01), subGrade, round2(weight * subPct * 0.01)));
        items.add(new Item("water", "体水分率", "%", f2(waterPct), waterGrade, round2(waterPct)));
        items.add(new Item("water_mass", "体水分量", "kg",
                f2(weight * waterPct * 0.01), waterGrade, round2(weight * waterPct * 0.01)));
        items.add(new Item("protein_mass", "蛋白质含量", "kg", f2(proteinMass), proteinGrade,
                round2(proteinMass)));
        items.add(new Item("protein_rate", "蛋白质占比", "%", f2(proteinRate), proteinGrade,
                round2(proteinRate)));
        items.add(new Item("bone", "骨量", "kg", f2(bone), boneGrade, round2(bone)));
        items.add(new Item("bone_rate", "骨量占比", "%", f2(bone / weight * 100.0), "",
                round2(bone / weight * 100.0)));
        items.add(new Item("muscle", "肌肉量", "kg", f2(muscleMass), muscleGrade,
                round2(muscleMass)));
        items.add(new Item("muscle_rate", "肌肉率", "%", f2(muscleRate), muscleGrade,
                round2(muscleRate)));
        items.add(new Item("skeletal_muscle", "骨骼肌量", "kg", f2(skeletal), skeletalGrade,
                round2(skeletal)));
        items.add(new Item("skeletal_muscle_rate", "骨骼肌率", "%", f2(skeletalRate),
                skeletalGrade, round2(skeletalRate)));
        items.add(new Item("bmr", "基础代谢", "kcal", String.valueOf(Math.round(bmr)),
                "", round2(bmr)));
        items.add(new Item("lean_mass", "去脂体重", "kg", f2(leanMass), "", round2(leanMass)));

        return new Result(true, fallback, items);
    }

    /** 把分级映射到提示色：绿 = 正常 / 优秀，黄 = 偏低 / 不足，红 = 偏高 / 过高。 */
    public static int gradeColor(String grade) {
        if (grade == null || grade.isEmpty()) {
            return Ui.INK_SOFT;
        }
        if ("标准".equals(grade) || "优秀".equals(grade)) {
            return 0xFF17A673;
        }
        if ("偏高".equals(grade) || "过高".equals(grade) || "高".equals(grade)) {
            return 0xFFE5484D;
        }
        return 0xFFE0A106;
    }

    private static final String[] KEYS = {
            "bmi", "visceral_fat", "body_fat", "fat_mass", "sub_fat_pct", "sub_fat_mass",
            "water", "water_mass", "protein_mass", "protein_rate", "bone", "bone_rate",
            "muscle", "muscle_rate", "skeletal_muscle", "skeletal_muscle_rate", "bmr",
            "lean_mass"
    };

    private static final String[] LABELS = {
            "BMI", "内脏脂肪", "体脂率", "脂肪量", "皮下脂肪率", "皮下脂肪量",
            "体水分率", "体水分量", "蛋白质含量", "蛋白质占比", "骨量", "骨量占比",
            "肌肉量", "肌肉率", "骨骼肌量", "骨骼肌率", "基础代谢", "去脂体重"
    };

    private static final String[] UNITS = {
            "", "级", "%", "kg", "%", "kg", "%", "kg", "kg", "%", "kg", "%",
            "kg", "%", "kg", "%", "kcal", "kg"
    };

    /**
     * 读取 AntAF 已经算好的体成分（同步来的记录里带 {@code body} 字段）。
     *
     * <p>字段名与 {@code body_composition.py} 的返回值一致；分级在 {@code grades} 子对象里，
     * 皮下脂肪与蛋白质两组共用同一个分级键。
     */
    public static Result fromJson(org.json.JSONObject body) {
        if (body == null) {
            return empty();
        }
        org.json.JSONObject grades = body.optJSONObject("grades");
        List<Item> items = new ArrayList<>(KEYS.length);
        for (int i = 0; i < KEYS.length; i++) {
            String k = KEYS[i];
            double v = body.optDouble(k, 0);
            boolean integer = "visceral_fat".equals(k) || "bmr".equals(k);
            String text = integer ? String.valueOf(Math.round(v)) : f2(v);
            String gradeKey = k;
            if (k.startsWith("sub_fat")) {
                gradeKey = "sub_fat";
            } else if (k.startsWith("protein")) {
                gradeKey = "protein";
            }
            String grade = grades == null ? "" : grades.optString(gradeKey, "");
            items.add(new Item(k, LABELS[i], UNITS[i], text, grade, v));
        }
        return new Result(true, false, items);
    }
}
