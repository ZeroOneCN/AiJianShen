package com.yuanlian.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Store.Reminder r = Store.reminder(context);
        if (!r.on) {
            return;
        }
        ReminderScheduler.notifyNow(context);
        // 单次闹钟在触发后自行排下一次，保持每日/每周循环。
        ReminderScheduler.schedule(context);
    }
}
