package com.aijianshen.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

/** 训练提醒：AlarmManager + 本地通知，不联网。支持多条提醒，各自占一个闹钟槽位。 */
public final class ReminderScheduler {

    public static final String CHANNEL = "training_reminder";
    private static final int NOTIFY_ID = 1201;
    private static final int ALARM_BASE = 1210;
    /** 闹钟槽位上限；条数超过这个数就不再排，避免 requestCode 失控。 */
    private static final int SLOTS = 8;
    public static final String EXTRA_ID = "reminder_id";

    private ReminderScheduler() {
    }

    private static PendingIntent slot(Context c, int index, String id) {
        Intent it = new Intent(c, ReminderReceiver.class);
        it.putExtra(EXTRA_ID, id);
        // requestCode 用槽位下标，同一槽位换提醒时靠 FLAG_UPDATE_CURRENT 覆盖
        return PendingIntent.getBroadcast(c, ALARM_BASE + index, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            am.cancel(slot(c, i, ""));
        }
        List<Store.Reminder> list = Store.reminders(c);
        long now = System.currentTimeMillis();
        int used = 0;
        for (Store.Reminder r : list) {
            if (!r.on || r.dayCount() == 0) {
                continue;
            }
            long next = nextTrigger(r, now);
            if (next <= 0 || used >= SLOTS) {
                continue;
            }
            PendingIntent pi = slot(c, used, r.id);
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
            used++;
        }
        if (used > 0) {
            ensureChannel(c);
        }
    }

    public static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            am.cancel(slot(c, i, ""));
        }
    }

    /** 找到下一次符合星期与时间的触发点；没有选中任何星期时返回 -1。 */
    public static long nextTrigger(Store.Reminder r, long now) {
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(now);
        for (int add = 0; add <= 7; add++) {
            Calendar cand = (Calendar) base.clone();
            cand.add(Calendar.DAY_OF_YEAR, add);
            cand.set(Calendar.HOUR_OF_DAY, r.hour);
            cand.set(Calendar.MINUTE, r.minute);
            cand.set(Calendar.SECOND, 0);
            cand.set(Calendar.MILLISECOND, 0);
            int idx = (cand.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            if (!r.days[idx] || cand.getTimeInMillis() <= now) {
                continue;
            }
            return cand.getTimeInMillis();
        }
        return -1;
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL, "训练提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("到了训练时间，提醒你完成今天的动作");
        nm.createNotificationChannel(ch);
    }

    public static void notifyNow(Context c) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("该练一会儿了")
                .setContentText("今天的动作已经排好，花十几分钟动起来。")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setShowWhen(true);
        nm.notify(NOTIFY_ID, b.build());
    }
}
