package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 训练提醒列表：可以放多条，比如早上 7:30 一条、晚上 20:00 一条。 */
public class ReminderActivity extends Activity {

    private LinearLayout listBox;
    private TextView addBtn;
    private LinearLayout body;
    private final int[] tileColors = {Ui.ORANGE, Ui.BLUE_TILE, Ui.PURPLE, Ui.TEAL, Ui.GREEN};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "训练提醒", v -> finish()));
        root.addView(UiList.subtitle(this,
                "可以设多条：早上提醒一次、晚上再提醒一次都行。到点发本地通知，不联网。"));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, 0, p, Ui.dpi(this, 24));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(listBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        UiList.hint(this, body,
                "提醒只保存在本机。系统省电策略较严时，通知可能延迟几分钟到达；"
                        + "关掉对应的提醒就会取消。");

        addBtn = UiList.primary(this, "添加提醒");
        TextView add = addBtn;
        root.addView(add, UiList.lp(this, 52, 8));
        ((LinearLayout.LayoutParams) add.getLayoutParams()).leftMargin = p;
        ((LinearLayout.LayoutParams) add.getLayoutParams()).rightMargin = p;
        add.setOnClickListener(v -> {
            if (Store.reminders(this).size() >= MAX) {
                Toast.makeText(this, "最多同时开 " + MAX + " 条提醒", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, ReminderEditActivity.class));
        });
    }

    /** 提醒最多同时排 MAX 条——闹钟槽位有限，超了会静默失效。 */
    private static final int MAX = ReminderScheduler.SLOTS;

    /** 程序里 setChecked 也会触发监听，用这个开关挡住，避免无限递归。 */
    private boolean syncingToggle;

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (listBox == null) {
            return;
        }
        listBox.removeAllViews();
        List<Store.Reminder> list = Store.reminders(this);
        if (list.isEmpty()) {
            LinearLayout empty = UiList.card(this);
            listBox.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView t = new TextView(this);
            t.setText("还没有提醒");
            t.setTextColor(Ui.INK);
            t.setTextSize(16);
            Ui.font(t, 600);
            int ep = Ui.dpi(this, 18);
            t.setPadding(ep, ep, ep, Ui.dpi(this, 4));
            empty.addView(t);
            TextView s = new TextView(this);
            s.setText("加一条，到点它会叫你一声。建议放在你大概率有空的时间。");
            s.setTextColor(Ui.INK_SOFT);
            s.setTextSize(13);
            s.setPadding(ep, 0, ep, ep);
            Ui.font(s, 400);
            empty.addView(s);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            final Store.Reminder r = list.get(i);
            LinearLayout card = UiList.card(this);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                clp.topMargin = Ui.dpi(this, 12);
            }
            listBox.addView(card, clp);

            UiList.Row row = UiList.row(this, card,
                    IconView.CLOCK, tileColors[i % tileColors.length],
                    r.timeText(), r.daysText(), true,
                    v -> edit(r.id),
                    (b, on) -> {
                        if (syncingToggle) {
                            return;
                        }
                        r.on = on;
                        Store.upsertReminder(this, r);
                        ReminderScheduler.schedule(this);
                        card.setAlpha(on ? 1f : 0.62f);
                    });
            syncingToggle = true;
            row.toggle.setChecked(r.on);
            syncingToggle = false;
            card.setAlpha(r.on ? 1f : 0.62f);
            final String id = r.id;
            card.setOnLongClickListener(v -> {
                InfoDialog.confirm(this, "删除这条提醒？",
                        "删除后不会再按 " + r.timeText() + " 通知你，其它提醒不受影响。",
                        "删除", Ui.RED, () -> {
                            Store.deleteReminder(this, id);
                            ReminderScheduler.schedule(this);
                            render();
                        });
                return true;
            });
        }

        TextView tip = new TextView(this);
        tip.setText("点按编辑，长按删除");
        tip.setTextColor(Ui.INK_SOFT);
        tip.setTextSize(11);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, Ui.dpi(this, 12), 0, 0);
        Ui.font(tip, 400);
        listBox.addView(tip);

        if (addBtn != null) {
            boolean full = list.size() >= MAX;
            addBtn.setText(full ? "最多 " + MAX + " 条，删一条再加" : "添加提醒");
            addBtn.setAlpha(full ? 0.55f : 1f);
        }
    }

    private void edit(String id) {
        startActivity(new Intent(this, ReminderEditActivity.class)
                .putExtra(ReminderEditActivity.EXTRA_ID, id));
    }
}
