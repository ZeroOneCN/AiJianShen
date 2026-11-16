package com.aijianshen.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // 闹钟可能属于已经删掉的那条提醒，发通知前先确认它还在
        String id = intent == null ? null : intent.getStringExtra(ReminderScheduler.EXTRA_ID);
        if (id == null || id.isEmpty()) {
            if (!Store.anyReminderOn(context)) {
                return;
            }
        } else {
            Store.Reminder r = Store.reminderById(context, id);
            if (r == null || !r.on) {
                ReminderScheduler.schedule(context);
                return;
            }
        }
        ReminderScheduler.notifyNow(context);
        // 单次闹钟触发后重新排下一次，保持每日/每周循环。
        ReminderScheduler.schedule(context);
    }
}
