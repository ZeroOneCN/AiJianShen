package com.yuanlian.app;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** 训练提醒：每天/每周固定时间，用本地通知提醒。 */
public class ReminderActivity extends Activity {

    private static final String[] DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private Store.Reminder reminder;
    private UiList.Row timeRow;
    private UiList.Row toggleRow;
    private UiList.Row permissionRow;
    private final TextView[] chips = new TextView[7];
    private boolean initializing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);
        reminder = Store.reminder(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "训练提醒", v -> finish()));
        root.addView(UiList.subtitle(this,
                "到点发一条本地通知，不联网，也不会读取你的联系人。"));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, 0, p, Ui.dpi(this, 20));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout card = UiList.card(this);
        body.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        toggleRow = UiList.row(this, card, IconView.CLOCK, Ui.ORANGE, "训练提醒",
                "开启后按下面的时间提醒你", false, null,
                (b, on) -> {
                    if (initializing) return;
                    reminder.on = on;
                    if (on) {
                        ensureNotificationPermission();
                    }
                    render();
                });
        toggleRow.toggle.setChecked(reminder.on);

        timeRow = UiList.row(this, card, IconView.CLOCK, Ui.BLUE_TILE, "提醒时间",
                reminder.timeText(), true, v -> pickTime());

        TextView repeat = new TextView(this);
        repeat.setText("重复");
        repeat.setTextColor(Ui.INK);
        repeat.setTextSize(16);
        repeat.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 18),
                Ui.dpi(this, 18), Ui.dpi(this, 10));
        Ui.font(repeat, 600);
        body.addView(repeat);

        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(DAY_LABELS[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    Ui.dpi(this, 44), 1f);
            if (i > 0) {
                lp.leftMargin = Ui.dpi(this, 7);
            }
            dayRow.addView(chip, lp);
            chip.setOnClickListener(v -> {
                reminder.days[idx] = !reminder.days[idx];
                renderDays();
            });
            chips[i] = chip;
        }
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = Ui.dpi(this, 16);
        dlp.rightMargin = Ui.dpi(this, 16);
        body.addView(dayRow, dlp);

        UiList.label(this, body, "通知");
        LinearLayout noteCard = UiList.card(this);
        body.addView(noteCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        permissionRow = UiList.row(this, noteCard, IconView.INFO, Ui.SLATE, "通知权限",
                notificationStatus(), false,
                v -> ensureNotificationPermission());
        UiList.row(this, noteCard, IconView.BOLT, Ui.GREEN, "试发一条",
                "立即收到一条本地提醒，用来检查权限", true,
                v -> {
                    ReminderScheduler.notifyNow(this);
                    Toast.makeText(this, "已发送测试提醒", Toast.LENGTH_SHORT).show();
                });

        UiList.hint(this, body,
                "提醒只保存在本机。如果系统省电策略较严，通知可能延迟几分钟到达；"
                        + "关闭训练提醒或卸载应用后会取消。");

        TextView save = UiList.primary(this, "保存提醒");
        root.addView(save, UiList.lp(this, 52, 8));
        ((LinearLayout.LayoutParams) save.getLayoutParams()).leftMargin = p;
        ((LinearLayout.LayoutParams) save.getLayoutParams()).rightMargin = p;
        save.setOnClickListener(v -> save());

        initializing = false;
        render();
    }

    private void render() {
        if (toggleRow == null) {
            return;
        }
        timeRow.sub.setText(reminder.timeText());
        timeRow.view.setAlpha(reminder.on ? 1f : 0.55f);
        if (permissionRow != null && permissionRow.sub != null) {
            permissionRow.sub.setText(notificationStatus());
        }
        renderDays();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void renderDays() {
        for (int i = 0; i < 7; i++) {
            boolean on = reminder.days[i];
            chips[i].setTextColor(on ? 0xFFFFFFFF : Ui.INK_SOFT);
            chips[i].setBackground(Ui.round(this, on ? Ui.ACCENT : Ui.SURFACE, 13f));
            Ui.font(chips[i], on ? 700 : 500);
        }
    }

    private void pickTime() {
        TimePickerDialog d = new TimePickerDialog(this, (view, hour, minute) -> {
            reminder.hour = hour;
            reminder.minute = minute;
            if (!reminder.on) {
                reminder.on = true;
                toggleRow.toggle.setChecked(true);
                ensureNotificationPermission();
            }
            render();
        }, reminder.hour, reminder.minute, true);
        d.show();
    }

    private String notificationStatus() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return "已允许，提醒会正常弹出";
        }
        return "未允许，点按开启系统通知权限";
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 120);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 120) {
            boolean ok = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!ok) {
                InfoDialog.show(this, "通知未开启",
                        "训练提醒已经保存在本机，但系统还没有允许私人健身教练发送通知。\n\n"
                                + "可以到系统设置 → 应用 → 私人健身教练 → 通知中手动允许，"
                                + "或者继续使用应用内的训练页。");
            }
            render();
        }
    }

    private void save() {
        if (reminder.on) {
            boolean any = false;
            for (boolean b : reminder.days) {
                any |= b;
            }
            if (!any) {
                Toast.makeText(this, "至少选择一个重复日", Toast.LENGTH_SHORT).show();
                return;
            }
            ensureNotificationPermission();
        }
        Store.saveReminder(this, reminder);
        ReminderScheduler.schedule(this);
        Toast.makeText(this, reminder.on
                ? String.format(Locale.CHINA, "已保存：%s · %s",
                reminder.timeText(), reminder.daysText())
                : "已关闭训练提醒", Toast.LENGTH_SHORT).show();
        finish();
    }
}
