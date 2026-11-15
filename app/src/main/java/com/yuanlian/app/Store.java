package com.yuanlian.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 纯本地记录：完成日期、累计分钟。不涉及任何网络与账号。 */
public final class Store {

    private static final String SP = "yuanlian";
    private static final String K_DATES = "done_dates";
    private static final String K_MINUTES = "total_minutes";
    private static final String K_LAST = "last_day";
    private static final String K_PROFILE = "plan_profile";

    private Store() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    public static String key(Calendar cal) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(cal.getTimeInMillis()));
    }

    public static void markDone(Context c, Calendar cal, int minutes) {
        Set<String> dates = new HashSet<>(sp(c).getStringSet(K_DATES, new HashSet<String>()));
        dates.add(key(cal));
        sp(c).edit()
                .putStringSet(K_DATES, dates)
                .putInt(K_MINUTES, sp(c).getInt(K_MINUTES, 0) + minutes)
                .putString(K_LAST, key(cal))
                .apply();
    }

    public static Set<String> dates(Context c) {
        return sp(c).getStringSet(K_DATES, new HashSet<String>());
    }

    public static int totalMinutes(Context c) {
        return sp(c).getInt(K_MINUTES, 0);
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
}
