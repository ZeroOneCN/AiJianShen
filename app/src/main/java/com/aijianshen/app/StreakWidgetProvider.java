package com.aijianshen.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * 桌面小部件：连续天数 + 本周进度 + 今天练什么。
 *
 * <p>不额外存数据，全部读现有的训练记录，所以练完立刻就能对上。
 * 系统最短只肯 30 分钟刷一次，所以练完由 {@link #refresh(Context)} 主动推一次。
 */
public class StreakWidgetProvider extends AppWidgetProvider {

    private static final int[] DOTS = {
            R.id.w_dot0, R.id.w_dot1, R.id.w_dot2, R.id.w_dot3,
            R.id.w_dot4, R.id.w_dot5, R.id.w_dot6
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = build(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    /** 训练完成、改计划、改目标之后调用，把已经放在桌面上的都刷一遍。 */
    public static void refresh(Context c) {
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(c);
            ComponentName name = new ComponentName(c, StreakWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(name);
            if (ids == null || ids.length == 0) {
                return;
            }
            RemoteViews views = build(c);
            for (int id : ids) {
                manager.updateAppWidget(id, views);
            }
        } catch (Exception ignored) {
            // 桌面没放小部件时什么都不用做，别让调用方因为刷新失败而崩
        }
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_card);

        int todayIndex = Data.todayIndex(Calendar.getInstance());
        Set<String> dates = Store.dates(c);
        Store.Streak streak = Store.streakInfo(c);
        int best = Store.bestStreak(c);
        // 断了的小部件不摆 0，摆最长纪录——0 只会让人把图标拖掉
        boolean down = streak.days == 0 && best > 0;
        v.setTextViewText(R.id.w_streak, String.valueOf(down ? best : streak.days));

        if (!Store.hasProfile(c) && dates.isEmpty()) {
            v.setTextViewText(R.id.w_streak_unit, "天");
            v.setTextViewText(R.id.w_week, "还没开始");
            v.setTextViewText(R.id.w_today, "点一下，先说说你的目标");
            for (int id : DOTS) {
                v.setInt(id, "setBackgroundResource", R.drawable.widget_dot_off);
            }
        } else {
            PlanProfile p = Store.profile(c);
            int done = Store.weekDone(c, todayIndex);
            int target = Math.max(1, p.days);
            v.setTextViewText(R.id.w_streak_unit, down ? "天最长" : "天连续");
            v.setTextViewText(R.id.w_week, "本周 " + done + "/" + target);

            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < DOTS.length; i++) {
                Calendar d = (Calendar) cal.clone();
                d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
                boolean ok = dates.contains(Store.key(d));
                v.setInt(DOTS[i], "setBackgroundResource",
                        ok ? R.drawable.widget_dot_on : R.drawable.widget_dot_off);
            }

            v.setTextViewText(R.id.w_today, todayLine(c, p, todayIndex));
        }

        Intent it = new Intent(c, MainActivity.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        return v;
    }

    /** 「今天：臀腿 · 16 分钟」/「今天：恢复日，做几组拉伸」。 */
    private static String todayLine(Context c, PlanProfile p, int todayIndex) {
        String text;
        try {
            Data.init(c);
            List<Data.Day> plan = Data.weekPlan(p);
            Data.Day day = plan.get(todayIndex);
            if (day.rest) {
                text = "今天：恢复日，做几组拉伸";
            } else {
                text = "今天：" + shortFocus(day.focus) + " · "
                        + Math.max(1, day.minutes()) + " 分钟";
            }
        } catch (Exception e) {
            text = "点一下打开今天这一课";
        }
        return text;
    }

    private static String shortFocus(String focus) {
        if (focus == null || focus.isEmpty()) {
            return "全身";
        }
        int i = focus.indexOf('+');
        return i > 0 ? focus.substring(0, i).trim() : focus;
    }
}
