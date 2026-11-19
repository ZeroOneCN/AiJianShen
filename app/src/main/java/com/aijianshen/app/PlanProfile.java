package com.aijianshen.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户条件：目标、水平、频率、时长、器械与重点部位。
 * 只存本地 SharedPreferences，不联网。
 */
public class PlanProfile {

    public static final String[] GOALS = {"减脂", "增肌", "塑形", "放松"};
    public static final String[] GOAL_HINTS = {
            "有氧 + 力量循环，心率拉起来",
            "力量为主，部位分化、组间多歇",
            "力量 + 拉伸，线条和体态一起练",
            "全拉伸，肩颈腰背松一松"
    };
    public static final String[] LEVELS = {"新手", "进阶", "高手"};
    public static final String[] DAY_OPTIONS = {"2 天", "3 天", "4 天", "5 天", "6 天"};
    public static final int[] DAY_VALUES = {2, 3, 4, 5, 6};
    /** 3 分钟不是「训练」而是「别断」：累到不想动的时候，只要求自己动起来。 */
    public static final String[] MINUTE_OPTIONS = {
            "3 分钟", "10 分钟", "15 分钟", "20 分钟", "30 分钟"};
    public static final int[] MINUTE_VALUES = {3, 10, 15, 20, 30};
    public static final String[] GEAR_OPTIONS = {"徒手", "哑铃 / 弹力带", "单杠 / 长凳", "健身房"};
    public static final String[] FOCUS = {"全身", "核心", "腿臀", "胸背", "肩臂", "有氧"};

    static final int GEAR_BODY = 1;
    static final int GEAR_HOME = 2;
    static final int GEAR_BAR = 4;
    static final int GEAR_GYM = 8;

    private static final Set<String> HOME_GEAR = new HashSet<>(Arrays.asList(
            "哑铃", "弹力带", "壶铃", "负重", "瑜伽球", "药球", "泡沫轴", "阻力带",
            "波速球", "健腹轮", "毛巾", "椅子", "台阶", "跳箱"));
    private static final Set<String> BAR_GEAR = new HashSet<>(Arrays.asList(
            "单杠", "双杠", "长凳", "低杠", "墙面"));

    public int goal = 2;
    public int level = 0;
    public int days = 3;
    public int minutes = 15;
    public int gearMask = GEAR_BODY;
    public final boolean[] focus = new boolean[FOCUS.length];

    public PlanProfile() {
        focus[0] = true;
    }

    public static PlanProfile defaults() {
        return new PlanProfile();
    }

    public PlanProfile copy() {
        PlanProfile p = new PlanProfile();
        p.goal = goal;
        p.level = level;
        p.days = days;
        p.minutes = minutes;
        p.gearMask = gearMask;
        System.arraycopy(focus, 0, p.focus, 0, focus.length);
        return p;
    }

    public int exerciseSeconds() {
        return 30 + level * 10;
    }

    public int restSeconds() {
        switch (goal) {
            case 0:
                return 15;
            case 1:
                return 30;
            case 3:
                return 10;
            default:
                return 20;
        }
    }

    /** 目标时长内能安排的动作段数（含休息）。 */
    private int slots() {
        int cycle = exerciseSeconds() + restSeconds();
        return Math.max(2, Math.round(minutes * 60f / cycle));
    }

    public int rounds() {
        int base = 5 + level;
        return Math.max(1, Math.min(8, Math.round(slots() / (float) base)));
    }

    public int perRound() {
        int r = rounds();
        return Math.max(3, Math.round(slots() / (float) r));
    }

    public String goalLabel() {
        return GOALS[clamp(goal, GOALS.length)];
    }

    public String levelLabel() {
        return LEVELS[clamp(level, LEVELS.length)];
    }

    public String summary() {
        return "每周 " + days + " 练 · 每次 " + minutes + " 分钟 · " + gearSummary();
    }

    public String gearSummary() {
        List<String> parts = new ArrayList<>();
        if ((gearMask & GEAR_BODY) != 0) {
            parts.add("徒手");
        }
        if ((gearMask & GEAR_HOME) != 0) {
            parts.add("小器械");
        }
        if ((gearMask & GEAR_BAR) != 0) {
            parts.add("单杠");
        }
        if ((gearMask & GEAR_GYM) != 0) {
            parts.add("健身房");
        }
        return parts.isEmpty() ? "徒手" : join(parts);
    }

    public boolean focusAll() {
        return focus[0];
    }

    /** 重点部位的位掩码，用于判断「条件是否变了」。 */
    public int focusMask() {
        int m = 0;
        for (int i = 0; i < focus.length; i++) {
            if (focus[i]) {
                m |= 1 << i;
            }
        }
        return m;
    }

    /** 允许的部位集合；全身 = null 表示不限。 */
    public Set<String> parts() {
        if (focusAll()) {
            return null;
        }
        Set<String> set = new HashSet<>();
        if (focus[1]) {
            set.add("核心");
        }
        if (focus[2]) {
            set.add("腿臀");
            set.add("小腿");
        }
        if (focus[3]) {
            set.add("胸");
            set.add("背");
        }
        if (focus[4]) {
            set.add("肩");
            set.add("手臂");
            set.add("小臂");
        }
        if (focus[5]) {
            set.add("有氧");
        }
        if (set.isEmpty()) {
            set.add("核心");
            set.add("腿臀");
            set.add("胸");
            set.add("背");
        }
        return set;
    }

    public boolean gearAllowed(String gear) {
        if (gear == null || gear.isEmpty()) {
            return true; // 徒手动作不占器械名额，始终保留
        }
        if ((gearMask & GEAR_GYM) != 0) {
            return true;
        }
        if (HOME_GEAR.contains(gear)) {
            return (gearMask & GEAR_HOME) != 0;
        }
        if (BAR_GEAR.contains(gear)) {
            return (gearMask & GEAR_BAR) != 0;
        }
        return false;
    }

    public int seed() {
        int f = 0;
        for (int i = 1; i < focus.length; i++) {
            if (focus[i]) {
                f = f * 3 + i;
            }
        }
        return goal * 131 + level * 37 + days * 17 + minutes * 7 + gearMask * 3 + f;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("goal", goal);
            o.put("level", level);
            o.put("days", days);
            o.put("minutes", minutes);
            o.put("gear", gearMask);
            JSONArray f = new JSONArray();
            for (boolean b : focus) {
                f.put(b);
            }
            o.put("focus", f);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static PlanProfile fromJson(String text) {
        PlanProfile p = defaults();
        if (text == null || text.isEmpty()) {
            return p;
        }
        try {
            JSONObject o = new JSONObject(text);
            p.goal = o.optInt("goal", p.goal);
            p.level = o.optInt("level", p.level);
            p.days = o.optInt("days", p.days);
            p.minutes = o.optInt("minutes", p.minutes);
            p.gearMask = o.optInt("gear", p.gearMask);
            JSONArray f = o.optJSONArray("focus");
            if (f != null && f.length() == p.focus.length) {
                for (int i = 0; i < p.focus.length; i++) {
                    p.focus[i] = f.optBoolean(i);
                }
            }
        } catch (Exception ignored) {
        }
        p.goal = clamp(p.goal, GOALS.length);
        p.level = clamp(p.level, LEVELS.length);
        p.days = Math.max(2, Math.min(6, p.days));
        boolean minutesOk = false;
        for (int v : MINUTE_VALUES) {
            if (v == p.minutes) {
                minutesOk = true;
                break;
            }
        }
        if (!minutesOk) {
            p.minutes = 15;
        }
        if (p.gearMask == 0) {
            p.gearMask = GEAR_BODY;
        }
        boolean any = false;
        for (boolean b : p.focus) {
            any |= b;
        }
        if (!any) {
            p.focus[0] = true;
        }
        return p;
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
