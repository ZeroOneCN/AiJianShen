package com.yuanlian.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 纯本地记录：完成日期、累计分钟、体重、训练偏好与提醒。不涉及任何网络与账号。 */
public final class Store {

    private static final String SP = "yuanlian";
    private static final String K_DATES = "done_dates";
    private static final String K_MINUTES = "total_minutes";
    private static final String K_DAY_MINUTES = "day_minutes";
    private static final String K_LAST = "last_day";
    private static final String K_PROFILE = "plan_profile";
    private static final String K_WORKOUT = "workout_prefs";
    private static final String K_WEIGHTS = "weights";
    private static final String K_HEIGHT = "height_cm";
    private static final String K_WEIGHT_UNIT = "weight_unit";
    private static final String K_HEIGHT_UNIT = "height_unit";
    private static final String K_SESSIONS = "sessions";
    private static final String K_REMINDER = "reminder";
    private static final String K_OVERRIDES = "overrides";
    private static final String K_BODY = "body_records";
    private static final String K_AGE = "age";
    private static final String K_SEX = "sex";
    private static final String K_FAV = "favorites";
    private static final String K_PENDING = "pending_workout";

    private Store() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    /**
     * SimpleDateFormat 不是线程安全的，而局域网同步会在自己的线程里读数据；
     * 每个线程各持一个实例，避免为了线程安全反复 new（key() 每次界面刷新要调用几十次）。
     */
    private static final ThreadLocal<SimpleDateFormat> DAY_FMT =
            new ThreadLocal<SimpleDateFormat>() {
                @Override
                protected SimpleDateFormat initialValue() {
                    return new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                }
            };

    private static String formatDay(long millis) {
        return DAY_FMT.get().format(new Date(millis));
    }

    public static String key(Calendar cal) {
        return formatDay(cal.getTimeInMillis());
    }

    public static void markDone(Context c, Calendar cal, int minutes) {
        markDone(c, cal, minutes, "训练", 0);
    }

    public static void markDone(Context c, Calendar cal, int minutes, String title, int count) {
        markDone(c, cal, minutes, title, count, -1);
    }

    /**
     * @param kcal 本次消耗；-1 表示没有算（旧版补记），展示时按分钟数回退估算
     */
    public static void markDone(Context c, Calendar cal, int minutes, String title, int count,
                                int kcal) {
        Set<String> dates = new HashSet<>(sp(c).getStringSet(K_DATES, new HashSet<String>()));
        dates.add(key(cal));
        JSONObject dayMap = dayMap(c);
        try {
            dayMap.put(key(cal), dayMap.optInt(key(cal), 0) + minutes);
        } catch (Exception ignored) {
        }
        List<Session> log = sessions(c);
        Session s = new Session();
        s.date = key(cal);
        s.ts = System.currentTimeMillis();
        s.title = title == null || title.isEmpty() ? "训练" : title;
        s.minutes = minutes;
        s.count = count;
        s.kcal = kcal;
        s.feel = -1;
        log.add(0, s);
        while (log.size() > 400) {
            log.remove(log.size() - 1);
        }
        sp(c).edit()
                .putStringSet(K_DATES, dates)
                .putInt(K_MINUTES, sp(c).getInt(K_MINUTES, 0) + minutes)
                .putString(K_DAY_MINUTES, dayMap.toString())
                .putString(K_LAST, key(cal))
                .putString(K_SESSIONS, sessionsJson(log))
                .apply();
    }

    /** 结果页把感受写回最近一次训练。 */
    public static void setLastFeel(Context c, int feel) {
        List<Session> log = sessions(c);
        if (log.isEmpty()) {
            return;
        }
        log.get(0).feel = feel;
        sp(c).edit().putString(K_SESSIONS, sessionsJson(log)).apply();
    }

    private static JSONObject dayMap(Context c) {
        try {
            return new JSONObject(sp(c).getString(K_DAY_MINUTES, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static int minutesOn(Context c, Calendar cal) {
        return dayMap(c).optInt(key(cal), 0);
    }

    public static Set<String> dates(Context c) {
        return sp(c).getStringSet(K_DATES, new HashSet<String>());
    }

    public static int daysTrained(Context c) {
        return dates(c).size();
    }

    public static int totalMinutes(Context c) {
        return sp(c).getInt(K_MINUTES, 0);
    }

    /** 本周（周一起）每天的分钟数，长度 7。 */
    public static int[] weekMinutes(Context c) {
        int[] out = new int[7];
        Calendar cal = Calendar.getInstance();
        int today = Data.todayIndex(cal);
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(today - i));
            out[i] = minutesOn(c, d);
        }
        return out;
    }

    /** 最近 7 天（今天在最后）的分钟数。 */
    public static int[] recentMinutes(Context c) {
        int[] out = new int[7];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(6 - i));
            out[i] = minutesOn(c, d);
        }
        return out;
    }

    public static PlanProfile profile(Context c) {
        return PlanProfile.fromJson(sp(c).getString(K_PROFILE, ""));
    }

    public static boolean hasProfile(Context c) {
        return sp(c).contains(K_PROFILE);
    }

    public static void saveProfile(Context c, PlanProfile p) {
        sp(c).edit().putString(K_PROFILE, p.toJson().toString()).apply();
    }

    public static void clearRecords(Context c) {
        sp(c).edit()
                .remove(K_DATES)
                .remove(K_MINUTES)
                .remove(K_DAY_MINUTES)
                .remove(K_LAST)
                .remove(K_SESSIONS)
                .remove(K_FEELS_LEGACY)
                .apply();
    }

    private static final String K_FEELS_LEGACY = "last_feel";

    /** 从今天往前数连续完成的天数。 */
    public static int streak(Context c) {
        Set<String> dates = dates(c);
        if (dates.isEmpty()) {
            return 0;
        }
        Calendar cur = Calendar.getInstance();
        if (!dates.contains(key(cur))) {
            cur.add(Calendar.DAY_OF_YEAR, -1);
            if (!dates.contains(key(cur))) {
                return 0;
            }
        }
        int n = 0;
        while (dates.contains(key(cur))) {
            n++;
            cur.add(Calendar.DAY_OF_YEAR, -1);
        }
        return n;
    }

    /** 历史上最长的一段连续训练。 */
    public static int bestStreak(Context c) {
        List<String> list = new ArrayList<>(dates(c));
        if (list.isEmpty()) {
            return 0;
        }
        Collections.sort(list);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        int best = 0;
        int run = 0;
        Calendar prev = null;
        for (String s : list) {
            Date d;
            try {
                d = fmt.parse(s);
            } catch (Exception e) {
                continue;
            }
            if (d == null) {
                continue;
            }
            Calendar cur = Calendar.getInstance();
            cur.setTime(d);
            cur.set(Calendar.HOUR_OF_DAY, 12);
            cur.set(Calendar.MINUTE, 0);
            cur.set(Calendar.SECOND, 0);
            cur.set(Calendar.MILLISECOND, 0);
            if (prev != null) {
                Calendar next = (Calendar) prev.clone();
                next.add(Calendar.DAY_OF_YEAR, 1);
                run = next.get(Calendar.YEAR) == cur.get(Calendar.YEAR)
                        && next.get(Calendar.DAY_OF_YEAR) == cur.get(Calendar.DAY_OF_YEAR)
                        ? run + 1 : 1;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            prev = cur;
        }
        return best;
    }

    /** 本周（周一起）已完成的训练天数。 */
    public static int weekDone(Context c, int todayIndex) {
        Calendar cal = Calendar.getInstance();
        Set<String> dates = dates(c);
        int count = 0;
        for (int i = 0; i <= todayIndex; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
            if (dates.contains(key(d))) {
                count++;
            }
        }
        return count;
    }

    // ---- 训练会话日志 ----

    public static class Session {
        public String date = "";
        public long ts;
        public String title = "";
        public int minutes;
        public int count;
        /** 本次消耗（千卡）；-1 表示旧记录没有这个字段。 */
        public int kcal = -1;
        /** -1 未选择，0 太轻松，1 刚刚好，2 太吃力 */
        public int feel = -1;
    }

    public static List<Session> sessions(Context c) {
        List<Session> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(K_SESSIONS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Session s = new Session();
                s.date = o.optString("d", "");
                s.ts = o.optLong("t", 0);
                s.title = o.optString("n", "训练");
                s.minutes = o.optInt("m", 0);
                s.count = o.optInt("c", 0);
                s.kcal = o.optInt("k", -1);
                s.feel = o.optInt("f", -1);
                out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String sessionsJson(List<Session> list) {
        JSONArray arr = new JSONArray();
        for (Session s : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("d", s.date);
                o.put("t", s.ts);
                o.put("n", s.title);
                o.put("m", s.minutes);
                o.put("c", s.count);
                if (s.kcal > 0) {
                    o.put("k", s.kcal);
                }
                o.put("f", s.feel);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        return arr.toString();
    }

    /**
     * 累计消耗。新记录直接取训练时算好的值；旧记录没有卡路里字段，
     * 按每分钟 6 千卡的旧口径回退，保证升级后总数不会突然变小。
     */
    public static int totalKcal(Context c) {
        int sum = 0;
        for (Session s : sessions(c)) {
            sum += s.kcal > 0 ? s.kcal : s.minutes * 6;
        }
        return sum;
    }

    /** 删除一条训练记录，并把当天的分钟数与累计分钟一起扣回去。 */
    public static boolean deleteSession(Context c, long ts) {
        List<Session> log = sessions(c);
        Session target = null;
        for (Session s : log) {
            if (s.ts == ts) {
                target = s;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        log.remove(target);
        JSONObject dayMap = dayMap(c);
        String day = target.date;
        int left = Math.max(0, dayMap.optInt(day, 0) - target.minutes);
        Set<String> dates = new HashSet<>(sp(c).getStringSet(K_DATES, new HashSet<String>()));
        try {
            if (left > 0) {
                dayMap.put(day, left);
            } else {
                dayMap.remove(day);
                // 当天还有别的记录（或手动补记过）时不算空
                if (!hasSessionOn(log, day)) {
                    dates.remove(day);
                }
            }
        } catch (Exception ignored) {
        }
        int total = Math.max(0, sp(c).getInt(K_MINUTES, 0) - target.minutes);
        SharedPreferences.Editor ed = sp(c).edit()
                .putStringSet(K_DATES, dates)
                .putString(K_DAY_MINUTES, dayMap.toString())
                .putString(K_SESSIONS, sessionsJson(log))
                .putInt(K_MINUTES, total);
        if (day.equals(sp(c).getString(K_LAST, ""))) {
            ed.remove(K_LAST);
        }
        ed.apply();
        return true;
    }

    private static boolean hasSessionOn(List<Session> log, String day) {
        for (Session s : log) {
            if (day.equals(s.date)) {
                return true;
            }
        }
        return false;
    }

    // ---- 收藏的动作 ----

    public static Set<String> favoriteIds(Context c) {
        return new HashSet<>(sp(c).getStringSet(K_FAV, new HashSet<String>()));
    }

    public static boolean isFavorite(Context c, String id) {
        return id != null && sp(c).getStringSet(K_FAV, new HashSet<String>()).contains(id);
    }

    /** @return 收藏后的状态 */
    public static boolean toggleFavorite(Context c, String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        Set<String> ids = new HashSet<>(sp(c).getStringSet(K_FAV, new HashSet<String>()));
        boolean on;
        if (ids.contains(id)) {
            ids.remove(id);
            on = false;
        } else {
            ids.add(id);
            on = true;
        }
        sp(c).edit().putStringSet(K_FAV, ids).apply();
        return on;
    }

    // ---- 中断的训练：把进度留在本机，下次接着练 ----

    /** 一次没做完的训练。只存动作 id 与秒数，恢复时重新绑定动作数据。 */
    public static class Pending {
        public int dayIndex;
        public String title = "";
        public String single;
        /** 每一步的动作 id；休息步为空串。 */
        public List<String> ids = new ArrayList<>();
        public List<Integer> totals = new ArrayList<>();
        public List<Integer> remains = new ArrayList<>();
        public int index;
        public int elapsedSeconds;
        public int rounds = 1;
        public long savedAt;

        public boolean valid() {
            return !ids.isEmpty() && index >= 0 && index < ids.size();
        }

        /** 「核心燃脂 · 第 2/8 个动作」这类能直接放在首页的一句话。 */
        public String progressText() {
            int perRound = 0;
            for (String id : ids) {
                if (id != null && !id.isEmpty()) {
                    perRound++;
                }
            }
            String step = ids.get(index).isEmpty() ? "组间休息" : "动作";
            return title + " · 停在第 " + (index + 1) + "/" + ids.size() + " 步（" + step + "）";
        }
    }

    public static Pending pending(Context c) {
        String text = sp(c).getString(K_PENDING, "");
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(text);
            Pending p = new Pending();
            p.dayIndex = o.optInt("day", 0);
            p.title = o.optString("title", "");
            String single = o.optString("single", "");
            p.single = single.isEmpty() ? null : single;
            p.index = o.optInt("idx", 0);
            p.elapsedSeconds = o.optInt("el", 0);
            p.rounds = Math.max(1, o.optInt("r", 1));
            p.savedAt = o.optLong("ts", 0);
            JSONArray steps = o.optJSONArray("steps");
            for (int i = 0; steps != null && i < steps.length(); i++) {
                JSONArray one = steps.optJSONArray(i);
                if (one == null) {
                    continue;
                }
                p.ids.add(one.optString(0, ""));
                p.totals.add(one.optInt(1, 0));
                p.remains.add(one.optInt(2, 0));
            }
            return p.valid() ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasPending(Context c) {
        return pending(c) != null;
    }

    public static void savePending(Context c, Pending p) {
        if (p == null || !p.valid()) {
            clearPending(c);
            return;
        }
        JSONObject o = new JSONObject();
        JSONArray steps = new JSONArray();
        try {
            for (int i = 0; i < p.ids.size(); i++) {
                JSONArray one = new JSONArray();
                one.put(p.ids.get(i));
                one.put(p.totals.get(i));
                one.put(p.remains.get(i));
                steps.put(one);
            }
            o.put("day", p.dayIndex);
            o.put("title", p.title);
            o.put("single", p.single == null ? "" : p.single);
            o.put("idx", p.index);
            o.put("el", p.elapsedSeconds);
            o.put("r", p.rounds);
            o.put("ts", System.currentTimeMillis());
            o.put("steps", steps);
        } catch (Exception ignored) {
        }
        sp(c).edit().putString(K_PENDING, o.toString()).apply();
    }

    public static void clearPending(Context c) {
        sp(c).edit().remove(K_PENDING).apply();
    }

    // ---- 训练偏好 ----
    public static class WorkoutPrefs {
        public int prepSeconds = 3;
        /** 0 表示跟随计划。 */
        public int restSeconds = 0;
        public boolean sound = true;
        public boolean vibrate = true;
        public boolean voice = true;
        public boolean animate = true;
        public boolean musicAuto = true;
        public String musicUri = "";
        public String musicName = "";

        public static WorkoutPrefs load(Context c) {
            WorkoutPrefs p = new WorkoutPrefs();
            try {
                JSONObject o = new JSONObject(sp(c).getString(K_WORKOUT, "{}"));
                p.prepSeconds = o.optInt("prep", 3);
                p.restSeconds = o.optInt("rest", 0);
                p.sound = o.optBoolean("sound", true);
                p.vibrate = o.optBoolean("vibrate", true);
                p.voice = o.optBoolean("voice", true);
                p.animate = o.optBoolean("animate", true);
                p.musicAuto = o.optBoolean("musicAuto", true);
                p.musicUri = o.optString("musicUri", "");
                p.musicName = o.optString("musicName", "");
            } catch (Exception ignored) {
            }
            return p;
        }

        public void save(Context c) {
            JSONObject o = new JSONObject();
            try {
                o.put("prep", prepSeconds);
                o.put("rest", restSeconds);
                o.put("sound", sound);
                o.put("vibrate", vibrate);
                o.put("voice", voice);
                o.put("animate", animate);
                o.put("musicAuto", musicAuto);
                o.put("musicUri", musicUri);
                o.put("musicName", musicName);
            } catch (Exception ignored) {
            }
            sp(c).edit().putString(K_WORKOUT, o.toString()).apply();
        }

        public int restFor(int planRest) {
            return restSeconds > 0 ? restSeconds : planRest;
        }
    }

    // ---- 体重、身高与单位 ----

    public static class WeightEntry {
        public final String date;
        public final float kg;

        WeightEntry(String date, float kg) {
            this.date = date;
            this.kg = kg;
        }
    }

    /** 按日期升序。 */
    public static List<WeightEntry> weights(Context c) {
        List<WeightEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(K_WEIGHTS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String d = o.optString("d", "");
                float w = (float) o.optDouble("w", 0);
                if (!d.isEmpty() && w > 0) {
                    out.add(new WeightEntry(d, w));
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> a.date.compareTo(b.date));
        return out;
    }

    public static void addWeight(Context c, String date, float kg) {
        List<WeightEntry> list = weights(c);
        JSONArray arr = new JSONArray();
        boolean replaced = false;
        for (WeightEntry e : list) {
            float w = e.date.equals(date) ? kg : e.kg;
            if (e.date.equals(date)) {
                replaced = true;
            }
            JSONObject o = new JSONObject();
            try {
                o.put("d", e.date);
                o.put("w", Math.round(w * 10) / 10.0);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        if (!replaced) {
            JSONObject o = new JSONObject();
            try {
                o.put("d", date);
                o.put("w", Math.round(kg * 10) / 10.0);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        sp(c).edit().putString(K_WEIGHTS, arr.toString()).apply();
    }

    public static float latestWeightKg(Context c) {
        List<WeightEntry> list = weights(c);
        return list.isEmpty() ? 0f : list.get(list.size() - 1).kg;
    }

    public static float heightCm(Context c) {
        return sp(c).getFloat(K_HEIGHT, 0f);
    }

    public static void setHeightCm(Context c, float cm) {
        sp(c).edit().putFloat(K_HEIGHT, cm).apply();
    }

    // ---- 身体成分：年龄、性别与按时间戳的体重记录 ----

    public static int age(Context c) {
        return sp(c).getInt(K_AGE, 0);
    }

    public static void setAge(Context c, int age) {
        sp(c).edit().putInt(K_AGE, Math.max(0, Math.min(120, age))).apply();
    }

    /** 性别：true = 男。未设置时按男性处理（体成分公式需要）。 */
    public static boolean isMale(Context c) {
        return !"female".equals(sp(c).getString(K_SEX, "male"));
    }

    public static void setMale(Context c, boolean male) {
        sp(c).edit().putString(K_SEX, male ? "male" : "female").apply();
    }

    /** 一条体重记录：可由本机录入、蓝牙体脂秤测量，或从另一台手机同步而来。 */
    public static class BodyRecord {
        public final long ts;
        public final String date;
        public final float kg;
        /** 阻抗（欧姆），0 表示没有阻抗数据。 */
        public final float impedance;
        /** 按 BIA 公式算好的 18 项体成分（JSON），没有时为 null。 */
        public final String body;
        public final String userName;
        /** 是否由局域网同步而来。 */
        public final boolean synced;

        BodyRecord(long ts, String date, float kg, float impedance, String body,
                   String userName, boolean synced) {
            this.ts = ts;
            this.date = date;
            this.kg = kg;
            this.impedance = impedance;
            this.body = body;
            this.userName = userName;
            this.synced = synced;
        }

        public JSONObject bodyJson() {
            if (body == null || body.isEmpty()) {
                return null;
            }
            try {
                return new JSONObject(body);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** 按时间升序；最多保留 {@link #MAX_BODY_RECORDS} 条。 */
    public static List<BodyRecord> bodyRecords(Context c) {
        List<BodyRecord> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(K_BODY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long ts = o.optLong("ts", 0);
                float kg = (float) o.optDouble("kg", 0);
                if (ts <= 0 || kg <= 0) {
                    continue;
                }
                out.add(new BodyRecord(ts, o.optString("d", ""), kg,
                        (float) o.optDouble("imp", 0), o.optString("body", null),
                        o.optString("user", ""), o.optBoolean("sync", false)));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(a.ts, b.ts));
        return out;
    }

    private static final int MAX_BODY_RECORDS = 240;

    /** 写入一条记录；同时间戳覆盖（同步重复拉取不会重复入库）。 */
    public static void addBodyRecord(Context c, long ts, float kg, float impedance,
                                     String bodyJson, String userName, boolean synced) {
        if (ts <= 0 || kg <= 0) {
            return;
        }
        List<BodyRecord> list = bodyRecords(c);
        List<BodyRecord> next = new ArrayList<>();
        boolean replaced = false;
        for (BodyRecord r : list) {
            if (r.ts == ts) {
                next.add(new BodyRecord(ts, dateOf(ts), kg, impedance, bodyJson,
                        userName, synced));
                replaced = true;
            } else {
                next.add(r);
            }
        }
        if (!replaced) {
            next.add(new BodyRecord(ts, dateOf(ts), kg, impedance, bodyJson,
                    userName, synced));
        }
        Collections.sort(next, (a, b) -> Long.compare(a.ts, b.ts));
        while (next.size() > MAX_BODY_RECORDS) {
            next.remove(0);
        }
        JSONArray arr = new JSONArray();
        for (BodyRecord r : next) {
            JSONObject o = new JSONObject();
            try {
                o.put("ts", r.ts);
                o.put("d", r.date);
                o.put("kg", Math.round(r.kg * 100) / 100.0);
                o.put("imp", Math.round(r.impedance * 10) / 10.0);
                if (r.body != null && !r.body.isEmpty()) {
                    o.put("body", r.body);
                }
                if (r.userName != null && !r.userName.isEmpty()) {
                    o.put("user", r.userName);
                }
                o.put("sync", r.synced);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        sp(c).edit().putString(K_BODY, arr.toString()).apply();
    }

    public static String dateOf(long ts) {
        return formatDay(ts);
    }

    /** 供同步模块组装记录用（构造器是包内可见）。 */
    static WeightEntry weightOf(String date, float kg) {
        return new WeightEntry(date, kg);
    }

    static BodyRecord bodyRecordOf(long ts, float kg, float impedance, String body,
                                   String userName, boolean synced) {
        return new BodyRecord(ts, dateOf(ts), kg, impedance, body, userName, synced);
    }

    public static BodyRecord latestBodyRecord(Context c) {
        List<BodyRecord> list = bodyRecords(c);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    // ---- 手机间同步：快照合并 ----

    /** 每天的分钟数（日期 → 分钟）。 */
    public static Map<String, Integer> dayMinutes(Context c) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JSONObject map = dayMap(c);
        java.util.Iterator<String> keys = map.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            int v = map.optInt(k, 0);
            if (v > 0) {
                out.put(k, v);
            }
        }
        return out;
    }

    /**
     * 把另一台手机的快照合并进本机。规则：日期取并集、同一天分钟取较大值、
     * 训练明细按时间戳去重、体重与体成分按日期 / 时间戳去重；本机缺的档案才补。
     *
     * @return 新导入的体成分记录条数
     */
    /** 一次合并实际新增的内容，用来给用户一句能看懂的反馈。 */
    public static class MergeStats {
        public int days;
        public int sessions;
        public int weights;
        public int body;
        public boolean profile;

        public boolean nothing() {
            return days == 0 && sessions == 0 && weights == 0 && body == 0 && !profile;
        }

        /** 只有体成分条数，兼容同步响应里的 added 字段。 */
        public int total() {
            return body;
        }

        public String summary() {
            if (nothing()) {
                return "没有新数据，两台手机已经一致";
            }
            StringBuilder sb = new StringBuilder("新增");
            boolean first = true;
            if (days > 0) {
                sb.append(' ').append(days).append(" 个训练日");
                first = false;
            }
            if (weights > 0) {
                sb.append(first ? " " : "、").append(weights).append(" 条体重");
                first = false;
            }
            if (body > 0) {
                sb.append(first ? " " : "、").append(body).append(" 条体成分");
                first = false;
            }
            if (sessions > 0) {
                sb.append(first ? " " : "、").append(sessions).append(" 次训练明细");
            }
            if (profile && sb.toString().endsWith("新增")) {
                sb.append(" 身高年龄档案");
            }
            return sb.toString();
        }
    }

    public static MergeStats mergeSnapshot(Context c, Set<String> inDates,
                                           Map<String, Integer> inDayMinutes,
                                           List<Session> inSessions,
                                           List<WeightEntry> inWeights,
                                           List<BodyRecord> inBody,
                                           float inHeightCm, int inAge, Boolean inMale) {
        MergeStats stats = new MergeStats();

        if (inDates != null && !inDates.isEmpty()) {
            Set<String> dates = new HashSet<>(sp(c).getStringSet(K_DATES, new HashSet<String>()));
            int before = dates.size();
            dates.addAll(inDates);
            stats.days = Math.max(0, dates.size() - before);
            sp(c).edit().putStringSet(K_DATES, dates).apply();
        }

        if (inDayMinutes != null && !inDayMinutes.isEmpty()) {
            JSONObject map = dayMap(c);
            try {
                for (Map.Entry<String, Integer> e : inDayMinutes.entrySet()) {
                    int local = map.optInt(e.getKey(), 0);
                    int v = Math.max(local, e.getValue() == null ? 0 : e.getValue());
                    if (v > 0) {
                        map.put(e.getKey(), v);
                    }
                }
            } catch (Exception ignored) {
            }
            int sum = 0;
            java.util.Iterator<String> keys = map.keys();
            while (keys.hasNext()) {
                sum += map.optInt(keys.next(), 0);
            }
            int total = Math.max(sum, sp(c).getInt(K_MINUTES, 0));
            sp(c).edit().putString(K_DAY_MINUTES, map.toString())
                    .putInt(K_MINUTES, total).apply();
        }

        if (inSessions != null && !inSessions.isEmpty()) {
            List<Session> local = sessions(c);
            Set<Long> have = new HashSet<>();
            for (Session s : local) {
                have.add(s.ts);
            }
            for (Session s : inSessions) {
                if (s.ts > 0 && !have.contains(s.ts)) {
                    local.add(s);
                    have.add(s.ts);
                    stats.sessions++;
                }
            }
            Collections.sort(local, (a, b) -> Long.compare(b.ts, a.ts));
            sp(c).edit().putString(K_SESSIONS, sessionsJson(local)).apply();
        }

        if (inWeights != null && !inWeights.isEmpty()) {
            Set<String> localDates = new HashSet<>();
            for (WeightEntry e : weights(c)) {
                localDates.add(e.date);
            }
            for (WeightEntry e : inWeights) {
                if (!localDates.contains(e.date)) {
                    addWeight(c, e.date, e.kg);
                    localDates.add(e.date);
                    stats.weights++;
                }
            }
        }

        if (inBody != null && !inBody.isEmpty()) {
            Set<Long> localTs = new HashSet<>();
            for (BodyRecord r : bodyRecords(c)) {
                localTs.add(r.ts);
            }
            for (BodyRecord r : inBody) {
                if (!localTs.contains(r.ts)) {
                    addBodyRecord(c, r.ts, r.kg, r.impedance, r.body, r.userName, r.synced);
                    localTs.add(r.ts);
                    stats.body++;
                }
            }
        }

        if (heightCm(c) <= 0 && inHeightCm > 0) {
            setHeightCm(c, inHeightCm);
            stats.profile = true;
        }
        if (age(c) <= 0 && inAge > 0) {
            setAge(c, inAge);
            stats.profile = true;
        }
        if (inMale != null && age(c) > 0 && !sp(c).contains(K_SEX)) {
            setMale(c, inMale);
            stats.profile = true;
        }
        return stats;
    }

    public static String weightUnit(Context c) {
        return "lb".equals(sp(c).getString(K_WEIGHT_UNIT, "kg")) ? "lb" : "kg";
    }

    public static void setWeightUnit(Context c, String unit) {
        sp(c).edit().putString(K_WEIGHT_UNIT, "lb".equals(unit) ? "lb" : "kg").apply();
    }

    public static boolean isLb(Context c) {
        return "lb".equals(weightUnit(c));
    }

    public static float kgToDisplay(Context c, float kg) {
        return isLb(c) ? kg * 2.20462f : kg;
    }

    public static float displayToKg(Context c, float value) {
        return isLb(c) ? value / 2.20462f : value;
    }

    public static String heightUnit(Context c) {
        return "ft".equals(sp(c).getString(K_HEIGHT_UNIT, "cm")) ? "ft" : "cm";
    }

    public static void setHeightUnit(Context c, String unit) {
        sp(c).edit().putString(K_HEIGHT_UNIT, "ft".equals(unit) ? "ft" : "cm").apply();
    }

    public static boolean isFt(Context c) {
        return "ft".equals(heightUnit(c));
    }

    public static String formatHeight(Context c, float cm) {
        if (cm <= 0) {
            return "--";
        }
        if (isFt(c)) {
            float inches = cm / 2.54f;
            int ft = (int) (inches / 12);
            int in = Math.round(inches - ft * 12);
            if (in >= 12) {
                ft++;
                in = 0;
            }
            return ft + " 英尺 " + in + " 英寸";
        }
        return Math.round(cm) + " cm";
    }

    public static float bmi(Context c) {
        float h = heightCm(c);
        float w = latestWeightKg(c);
        if (h <= 0 || w <= 0) {
            return 0f;
        }
        float m = h / 100f;
        return w / (m * m);
    }

    public static String bmiLabel(float bmi) {
        if (bmi <= 0) {
            return "";
        }
        if (bmi < 18.5f) {
            return "偏轻";
        }
        if (bmi < 24f) {
            return "正常";
        }
        if (bmi < 28f) {
            return "偏重";
        }
        return "肥胖";
    }

    // ---- 本周动作替换 ----

    public static Map<String, String> overrides(Context c, int dayIndex) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            JSONObject root = new JSONObject(sp(c).getString(K_OVERRIDES, "{}"));
            JSONObject week = root.optJSONObject(weekStamp());
            JSONObject day = week == null ? null : week.optJSONObject(String.valueOf(dayIndex));
            if (day != null) {
                java.util.Iterator<String> it = day.keys();
                while (it.hasNext()) {
                    String from = it.next();
                    out.put(from, day.optString(from, ""));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void setOverride(Context c, int dayIndex, String fromId, String toId) {
        if (fromId == null || toId == null || fromId.equals(toId)) {
            return;
        }
        try {
            JSONObject root = new JSONObject(sp(c).getString(K_OVERRIDES, "{}"));
            JSONObject week = root.optJSONObject(weekStamp());
            if (week == null) {
                week = new JSONObject();
                root.put(weekStamp(), week);
            }
            JSONObject day = week.optJSONObject(String.valueOf(dayIndex));
            if (day == null) {
                day = new JSONObject();
                week.put(String.valueOf(dayIndex), day);
            }
            day.put(fromId, toId);
            sp(c).edit().putString(K_OVERRIDES, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void clearOverrides(Context c) {
        sp(c).edit().remove(K_OVERRIDES).apply();
    }

    private static String weekStamp() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.WEEK_OF_YEAR);
    }

    // ---- 训练提醒 ----

    public static class Reminder {
        public boolean on = false;
        public int hour = 20;
        public int minute = 0;
        public boolean[] days = {true, true, true, true, true, true, true};

        public static Reminder load(Context c) {
            Reminder r = new Reminder();
            try {
                JSONObject o = new JSONObject(sp(c).getString(K_REMINDER, "{}"));
                r.on = o.optBoolean("on", false);
                r.hour = Math.max(0, Math.min(23, o.optInt("h", 20)));
                r.minute = Math.max(0, Math.min(59, o.optInt("m", 0)));
                JSONArray arr = o.optJSONArray("days");
                if (arr != null && arr.length() == 7) {
                    for (int i = 0; i < 7; i++) {
                        r.days[i] = arr.optBoolean(i, true);
                    }
                }
            } catch (Exception ignored) {
            }
            return r;
        }

        public void save(Context c) {
            JSONObject o = new JSONObject();
            try {
                o.put("on", on);
                o.put("h", hour);
                o.put("m", minute);
                JSONArray arr = new JSONArray();
                for (boolean b : days) {
                    arr.put(b);
                }
                o.put("days", arr);
            } catch (Exception ignored) {
            }
            sp(c).edit().putString(K_REMINDER, o.toString()).apply();
        }

        public String timeText() {
            return String.format(Locale.CHINA, "%02d:%02d", hour, minute);
        }

        public String daysText() {
            String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
            int n = 0;
            for (boolean b : days) {
                if (b) {
                    n++;
                }
            }
            if (n == 7) {
                return "每天";
            }
            if (n == 0) {
                return "未选择";
            }
            StringBuilder sb = new StringBuilder("周");
            for (int i = 0; i < 7; i++) {
                if (days[i]) {
                    if (sb.length() > 1) {
                        sb.append("、");
                    }
                    sb.append(labels[i]);
                }
            }
            return sb.toString();
        }
    }

    public static Reminder reminder(Context c) {
        return Reminder.load(c);
    }

    public static void saveReminder(Context c, Reminder r) {
        r.save(c);
    }
}
