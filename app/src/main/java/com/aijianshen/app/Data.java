package com.aijianshen.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 动作库与训练计划。
 */
public final class Data {

    /** 部位展示顺序。 */
    public static final String[] PARTS = {
            "核心", "腿臀", "胸", "背", "肩", "手臂", "小腿", "小臂", "有氧", "颈部"
    };

    private static final List<Exercise> ALL = new ArrayList<>();
    private static final Map<String, Exercise> BY_NAME = new LinkedHashMap<>();
    private static final Map<String, Exercise> BY_ID = new HashMap<>();
    private static final Map<String, List<Exercise>> BY_PART = new LinkedHashMap<>();
    private static volatile boolean loaded = false;

    private Data() {
    }

    /** 读取 assets 中的动作数据，重复调用无副作用。 */
    public static synchronized void init(Context ctx) {
        if (loaded) {
            return;
        }
        try {
            String text = readAsset(ctx, "exercises.json");
            JSONObject root = new JSONObject(text);
            JSONArray items = root.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                Exercise e = new Exercise(
                        o.optString("id"),
                        o.optString("n"),
                        o.optString("e"),
                        o.optString("p", "核心"),
                        o.optString("t", ""),
                        o.optString("k", "力量"),
                        o.optString("g", ""),
                        o.optInt("s", 40),
                        list(o.optJSONArray("d")),
                        list(o.optJSONArray("m")),
                        o.optString("id"));
                ALL.add(e);
                BY_NAME.put(e.name, e);
                BY_ID.put(e.id, e);
                BY_PART.computeIfAbsent(e.part, k -> new ArrayList<>()).add(e);
            }
        } catch (Throwable t) {
            // 资源异常时保持空库，界面显示占位文案而不是崩溃
        }
        loaded = true;
    }

    private static List<String> list(JSONArray arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.optString(i));
            }
        }
        return out;
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        try (InputStream is = ctx.getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static List<Exercise> all() {
        return ALL;
    }

    /** 只有存在动作的部位，按 PARTS 顺序返回。 */
    public static List<String> parts() {
        List<String> out = new ArrayList<>();
        for (String p : PARTS) {
            if (BY_PART.containsKey(p)) {
                out.add(p);
            }
        }
        return out;
    }

    public static List<Exercise> byPart(String part) {
        List<Exercise> l = BY_PART.get(part);
        return l == null ? new ArrayList<>() : l;
    }

    public static Exercise get(String name) {
        return BY_NAME.get(name);
    }

    public static Exercise byId(String id) {
        return BY_ID.get(id);
    }

    /** 关键字搜索：中文名、英文名、目标肌群。 */
    public static List<Exercise> search(String keyword, String part) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Exercise> out = new ArrayList<>();
        for (Exercise e : ALL) {
            if (e.matches(k, part)) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- 定制的一周计划 ----

    public static class Day {
        public final String title;
        public final String focus;
        public final List<Exercise> exercises;
        /** true 表示恢复日：练的是拉伸，不是休息不动 */
        public final boolean rest;
        /** 每个动作重复几轮 */
        public final int rounds;
        /** 本轮每个动作的秒数 */
        public final int seconds;
        /** 动作之间的休息秒数 */
        public final int restSeconds;

        Day(String title, String focus, List<Exercise> exercises, boolean rest,
            int rounds, int seconds, int restSeconds) {
            this.title = title;
            this.focus = focus;
            this.exercises = exercises;
            this.rest = rest;
            this.rounds = Math.max(1, rounds);
            this.seconds = seconds;
            this.restSeconds = restSeconds;
        }

        public int count() {
            return exercises.size();
        }

        public int totalSets() {
            return exercises.size() * rounds;
        }

        public int totalSeconds() {
            int sets = totalSets();
            if (sets == 0) {
                return 0;
            }
            return sets * seconds + (sets - 1) * restSeconds;
        }

        public int minutes() {
            return Math.max(1, Math.round(totalSeconds() / 60f));
        }
    }

    private static final class Theme {
        final String title;
        final String label;
        final String[] parts;

        Theme(String title, String label, String... parts) {
            this.title = title;
            this.label = label;
            this.parts = parts;
        }
    }

    private static final Theme[][] THEMES = {
            // 减脂
            {
                    new Theme("全身燃脂", "心肺 + 下肢", "有氧", "腿臀", "核心", "肩", "有氧"),
                    new Theme("核心燃脂", "腰腹 + 间歇", "核心", "有氧", "核心", "有氧", "核心"),
                    new Theme("下肢燃脂", "腿臀 + 心肺", "腿臀", "有氧", "腿臀", "核心", "腿臀"),
                    new Theme("上肢循环", "胸背肩臂", "胸", "背", "肩", "手臂", "核心"),
                    new Theme("间歇有氧", "心率冲刺", "有氧", "腿臀", "有氧", "核心", "有氧"),
                    new Theme("全身燃脂", "全身循环", "腿臀", "核心", "胸", "有氧", "背"),
            },
            // 增肌
            {
                    new Theme("上肢推", "胸肩三头", "胸", "肩", "胸", "手臂", "肩"),
                    new Theme("下肢力量", "腿臀为主", "腿臀", "腿臀", "腿臀", "核心", "小腿"),
                    new Theme("上肢拉", "背和二头", "背", "背", "手臂", "背", "肩"),
                    new Theme("全身力量", "大肌群", "腿臀", "胸", "背", "核心", "肩"),
                    new Theme("核心稳定", "躯干支撑", "核心", "核心", "腿臀", "核心", "腿臀"),
                    new Theme("胸背力量", "推拉结合", "胸", "背", "胸", "背", "核心"),
            },
            // 塑形
            {
                    new Theme("核心紧致", "腰腹线条", "核心", "核心", "核心", "有氧", "核心"),
                    new Theme("腿臀线条", "腿臀塑形", "腿臀", "腿臀", "腿臀", "核心", "小腿"),
                    new Theme("胸背塑形", "上身线条", "胸", "背", "肩", "胸", "背"),
                    new Theme("全身循环", "全身紧致", "腿臀", "核心", "胸", "背", "有氧"),
                    new Theme("肩臂紧致", "肩颈手臂", "肩", "手臂", "肩", "手臂", "核心"),
                    new Theme("下肢塑形", "臀腿分离", "腿臀", "腿臀", "核心", "腿臀", "小腿"),
            },
            // 放松
            {
                    new Theme("舒缓拉伸", "全身放松", "核心", "腿臀", "背", "肩", "颈部"),
                    new Theme("肩颈放松", "久坐松解", "肩", "颈部", "背", "肩", "手臂"),
                    new Theme("髋部拉伸", "髋屈肌放松", "腿臀", "腿臀", "核心", "腿臀", "背"),
                    new Theme("全身放松", "呼吸与伸展", "核心", "腿臀", "背", "肩", "腿臀"),
                    new Theme("下肢拉伸", "腿后侧放松", "腿臀", "小腿", "腿臀", "核心", "腿臀"),
                    new Theme("背部放松", "脊柱伸展", "背", "肩", "核心", "背", "颈部"),
            },
    };

    /** 每周训练日（周一为 0）：分散开，避免连着练同一部位。 */
    private static final int[][] TRAIN_DAYS = {
            {},
            {},
            {1, 4},
            {0, 2, 4},
            {0, 1, 3, 5},
            {0, 1, 2, 3, 4},
            {0, 1, 2, 3, 4, 5},
    };

    public static List<Day> weekPlan(PlanProfile p) {
        PlanProfile profile = p == null ? PlanProfile.defaults() : p;
        int week = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        String key = planKey(profile, week);
        List<Day> cached = PLAN_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int seed = week * 7 + profile.seed();
        Set<Integer> training = new HashSet<>();
        int[] days = TRAIN_DAYS[Math.max(2, Math.min(6, profile.days))];
        for (int d : days) {
            training.add(d);
        }

        List<Day> out = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            if (training.contains(i)) {
                out.add(trainingDay(profile, i, seed + i * 31));
            } else {
                out.add(recoveryDay(profile, i, seed + i * 17));
            }
        }
        // 首页、详情、训练页各自会取一次计划，同一周同一条件只排一次
        PLAN_CACHE.put(key, out);
        return out;
    }

    /** 计划缓存：一周内同一套条件结果相同，键里带上会改变结果的所有字段。 */
    private static final Map<String, List<Day>> PLAN_CACHE =
            new LinkedHashMap<String, List<Day>>(8) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Day>> eldest) {
                    return size() > 4;
                }
            };

    private static String planKey(PlanProfile p, int week) {
        return week + "|" + p.seed() + "|" + p.goal + "|" + p.level + "|" + p.days
                + "|" + p.minutes + "|" + p.gearMask + "|" + p.focusMask();
    }

    /** 条件变了（例如在定制页改完保存）时清掉缓存。 */
    public static void clearPlanCache() {
        PLAN_CACHE.clear();
    }

    public static Day today(PlanProfile p, Calendar cal) {
        return weekPlan(p).get(todayIndex(cal));
    }

    /** 应用本周换动作结果，其余字段保持不变。 */
    public static Day withOverrides(Day d, Map<String, String> overrides) {
        if (d == null || overrides == null || overrides.isEmpty()) {
            return d;
        }
        List<Exercise> list = new ArrayList<>(d.exercises.size());
        for (Exercise e : d.exercises) {
            Exercise chosen = e;
            java.util.Set<String> seen = new HashSet<>();
            seen.add(e.id);
            String next = overrides.get(e.id);
            for (int hop = 0; next != null && !next.isEmpty() && hop < 6; hop++) {
                Exercise alt = byId(next);
                if (alt == null || !seen.add(alt.id)) {
                    break;
                }
                chosen = alt;
                next = overrides.get(alt.id);
            }
            list.add(chosen);
        }
        return new Day(d.title, d.focus, list, d.rest, d.rounds, d.seconds, d.restSeconds);
    }

    private static Day trainingDay(PlanProfile p, int dayIndex, int seed) {
        Theme[] pool = THEMES[Math.max(0, Math.min(3, p.goal))];
        Theme theme = pool[Math.floorMod(seed / 7 + dayIndex, pool.length)];

        Set<String> allowed = p.parts();
        List<String> allowedParts = new ArrayList<>();
        for (String part : PARTS) {
            if (allowed == null || allowed.contains(part)) {
                allowedParts.add(part);
            }
        }
        if (allowedParts.isEmpty()) {
            allowedParts.add("核心");
        }

        List<String> used = new ArrayList<>();
        List<Exercise> picked = new ArrayList<>();
        int n = p.perRound();
        for (int k = 0; k < n; k++) {
            String part = theme.parts[k % theme.parts.length];
            Exercise e = null;
            if (allowed == null || allowed.contains(part)) {
                e = pickFor(part, p, seed + k * 13, used);
            }
            int guard = 0;
            while (e == null && guard++ < allowedParts.size()) {
                String alt = allowedParts.get(
                        Math.floorMod(seed + k * 13 + guard * 29, allowedParts.size()));
                e = pickFor(alt, p, seed + k * 13 + guard, used);
            }
            if (e == null) {
                break; // 当前器械条件下动作池已取尽
            }
            picked.add(e);
        }
        if (picked.isEmpty()) {
            // 条件过窄时兜底：徒手核心动作
            for (Exercise e : byPart("核心")) {
                if (!e.needsGear()) {
                    picked.add(e);
                    break;
                }
            }
        }

        int seconds = p.goal == 3 ? 50 + p.level * 10 : p.exerciseSeconds();
        return new Day(theme.title, theme.label, picked, false,
                p.rounds(), seconds, p.restSeconds());
    }

    private static Day recoveryDay(PlanProfile p, int dayIndex, int seed) {
        List<Exercise> portable = new ArrayList<>();
        List<Exercise> allStretch = new ArrayList<>();
        for (Exercise e : ALL) {
            if (!"拉伸".equals(e.kind)) {
                continue;
            }
            allStretch.add(e);
            if (!e.needsGear()) {
                portable.add(e);
            }
        }
        List<Exercise> pool = portable.size() >= 5 ? portable : allStretch;
        List<Exercise> picked = new ArrayList<>();
        List<String> used = new ArrayList<>();
        for (int k = 0; k < 5 && !pool.isEmpty(); k++) {
            Exercise e = pool.get(Math.floorMod(seed + k * 7, pool.size()));
            int guard = 0;
            while (used.contains(e.id) && guard++ < pool.size()) {
                e = pool.get(Math.floorMod(seed + k * 7 + guard, pool.size()));
            }
            if (!used.contains(e.id)) {
                used.add(e.id);
                picked.add(e);
            }
        }
        return new Day("恢复拉伸", "松一松肩背", picked, true, 1, 40, 10);
    }

    /** 从某部位取一个未用过的动作：优先徒手、优先和目标匹配的类型。 */
    private static Exercise pickFor(String part, PlanProfile p, int seed, List<String> used) {
        List<Exercise> best = new ArrayList<>();
        List<Exercise> fallback = new ArrayList<>();
        for (Exercise e : byPart(part)) {
            if (!p.gearAllowed(e.gear)) {
                continue;
            }
            if (p.goal == 3 && !"拉伸".equals(e.kind)) {
                continue;
            }
            if (p.goal != 3 && "拉伸".equals(e.kind)) {
                continue;
            }
            fallback.add(e);
            if (!e.needsGear()) {
                best.add(e);
            }
        }
        List<Exercise> pool;
        if (p.goal == 1 && (p.gearMask & ~PlanProfile.GEAR_BODY) != 0) {
            // 增肌且选了器械：优先编排器械动作
            List<Exercise> geared = new ArrayList<>();
            for (Exercise e : fallback) {
                if (e.needsGear()) {
                    geared.add(e);
                }
            }
            pool = !geared.isEmpty() ? geared : fallback;
        } else {
            pool = !best.isEmpty() ? best : fallback;
        }
        if (pool.isEmpty()) {
            return null;
        }
        for (int k = 0; k < pool.size(); k++) {
            Exercise e = pool.get(Math.floorMod(seed + k, pool.size()));
            if (!used.contains(e.id)) {
                used.add(e.id);
                return e;
            }
        }
        return null; // 该部位在当前条件下已没有不重复的动作
    }

    public static int todayIndex(Calendar cal) {
        int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=周日
        return (dow + 5) % 7;
    }
}
