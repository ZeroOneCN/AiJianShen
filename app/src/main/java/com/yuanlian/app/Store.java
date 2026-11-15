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

    private Store() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static String key(Calendar cal) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(cal.getTimeInMillis()));
    }

    public static void markDone(Context c, Calendar cal, int minutes) {
        markDone(c, cal, minutes, "训练", 0);
    }

    public static void markDone(Context c, Calendar cal, int minutes, String title, int count) {
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
                o.put("f", s.feel);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        return arr.toString();
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
