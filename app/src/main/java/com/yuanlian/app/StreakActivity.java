package com.yuanlian.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 连续训练：当前/最长连续天数与月历。 */
public class StreakActivity extends Activity {

    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};

    private final Calendar month = Calendar.getInstance();
    private LinearLayout grid;
    private TextView monthLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);
        month.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "训练连续", v -> finish()));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 4), p, Ui.dpi(this, 24));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(streakHero());
        body.addView(calendarCard(), cardLp());
        UiList.hint(this, body, "连续天数从今天往前计算；中间断一天也没关系，"
                + "历史记录会一直留在这台设备上。");
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 14);
        return lp;
    }

    private FrameLayout streakHero() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.gradientStreak(this));
        card.setClipToOutline(true);
        int p = Ui.dpi(this, 22);
        card.setPadding(p, p, p, p);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(box);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        box.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cap = new TextView(this);
        cap.setText("当前连续");
        cap.setTextColor(Ui.WHITE_80);
        cap.setTextSize(13);
        Ui.font(cap, 500);
        left.addView(cap);

        LinearLayout num = new LinearLayout(this);
        num.setOrientation(LinearLayout.HORIZONTAL);
        num.setGravity(Gravity.BOTTOM);
        num.setPadding(0, Ui.dpi(this, 2), 0, 0);
        left.addView(num);
        TextView n = new TextView(this);
        n.setText(String.valueOf(Store.streak(this)));
        n.setTextColor(Color.WHITE);
        n.setTextSize(58);
        n.setIncludeFontPadding(false);
        Ui.font(n, 800);
        num.addView(n);
        TextView unit = new TextView(this);
        unit.setText("天");
        unit.setTextColor(Ui.WHITE_80);
        unit.setTextSize(16);
        unit.setPadding(Ui.dpi(this, 6), 0, 0, Ui.dpi(this, 10));
        Ui.font(unit, 600);
        num.addView(unit);

        TextView tip = new TextView(this);
        int streak = Store.streak(this);
        tip.setText(streak > 0 ? "今天已经动过了，保持住。" : "完成今天的一次训练，从这里开始。");
        tip.setTextColor(Ui.WHITE_80);
        tip.setTextSize(12);
        Ui.font(tip, 400);
        left.addView(tip);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(this, 0x2EFFFFFF, 24f));
        box.addView(badge, new LinearLayout.LayoutParams(
                Ui.dpi(this, 92), Ui.dpi(this, 92)));
        LinearLayout badgeBox = new LinearLayout(this);
        badgeBox.setOrientation(LinearLayout.VERTICAL);
        badgeBox.setGravity(Gravity.CENTER);
        badge.addView(badgeBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView best = new TextView(this);
        best.setText(String.valueOf(Store.bestStreak(this)));
        best.setTextColor(Color.WHITE);
        best.setTextSize(26);
        best.setIncludeFontPadding(false);
        best.setGravity(Gravity.CENTER);
        Ui.font(best, 800);
        badgeBox.addView(best);
        TextView bestLabel = new TextView(this);
        bestLabel.setText("最长连续 / 天");
        bestLabel.setTextColor(Ui.WHITE_60);
        bestLabel.setTextSize(10);
        bestLabel.setGravity(Gravity.CENTER);
        Ui.font(bestLabel, 400);
        badgeBox.addView(bestLabel);
        return card;
    }

    private LinearLayout calendarCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 16);
        card.setPadding(p, p, p, Ui.dpi(this, 14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        TextView total = new TextView(this);
        total.setText("共 " + Store.daysTrained(this) + " 天 · "
                + Store.totalMinutes(this) + " 分钟");
        total.setTextColor(Ui.INK_SOFT);
        total.setTextSize(12);
        Ui.font(total, 400);
        head.addView(total, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView prev = arrow("‹");
        head.addView(prev, new LinearLayout.LayoutParams(
                Ui.dpi(this, 36), Ui.dpi(this, 36)));
        prev.setOnClickListener(v -> {
            month.add(Calendar.MONTH, -1);
            renderCalendar();
        });

        monthLabel = new TextView(this);
        monthLabel.setTextColor(Ui.INK);
        monthLabel.setTextSize(15);
        monthLabel.setGravity(Gravity.CENTER);
        Ui.font(monthLabel, 700);
        head.addView(monthLabel, new LinearLayout.LayoutParams(
                Ui.dpi(this, 104), Ui.dpi(this, 36)));

        TextView next = arrow("›");
        head.addView(next, new LinearLayout.LayoutParams(
                Ui.dpi(this, 36), Ui.dpi(this, 36)));
        next.setOnClickListener(v -> {
            month.add(Calendar.MONTH, 1);
            renderCalendar();
        });

        LinearLayout weeks = new LinearLayout(this);
        weeks.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = Ui.dpi(this, 12);
        card.addView(weeks, wlp);
        for (String s : WEEK) {
            TextView w = new TextView(this);
            w.setText(s);
            w.setTextColor(Ui.INK_SOFT);
            w.setTextSize(11);
            w.setGravity(Gravity.CENTER);
            Ui.font(w, 500);
            weeks.addView(w, new LinearLayout.LayoutParams(0,
                    Ui.dpi(this, 26), 1f));
        }

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        card.addView(grid);
        renderCalendar();
        return card;
    }

    private TextView arrow(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 18f), 0x22000000));
        Ui.font(tv, 500);
        return tv;
    }

    private void renderCalendar() {
        if (grid == null) {
            return;
        }
        monthLabel.setText(String.format(Locale.CHINA, "%d 年 %d 月",
                month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1));

        grid.removeAllViews();
        Calendar first = (Calendar) month.clone();
        int lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        Set<String> done = Store.dates(this);
        Calendar today = Calendar.getInstance();
        String todayKey = Store.key(today);

        LinearLayout row = null;
        int slot = 0;
        for (int i = 0; i < lead; i++) {
            if (slot == 0) {
                row = newRow();
            }
            row.addView(new View(this), cellLp(slot));
            slot++;
            if (slot == 7) {
                slot = 0;
            }
        }
        for (int d = 1; d <= days; d++) {
            if (slot == 0) {
                row = newRow();
            }
            Calendar day = (Calendar) first.clone();
            day.set(Calendar.DAY_OF_MONTH, d);
            String key = Store.key(day);
            boolean ok = done.contains(key);
            boolean isToday = key.equals(todayKey);
            boolean future = day.after(today);
            row.addView(cell(d, day, key, ok, isToday, future), cellLp(slot));
            slot++;
            if (slot == 7) {
                slot = 0;
            }
        }
        if (slot != 0 && row != null) {
            for (int i = slot; i < 7; i++) {
                row.addView(new View(this), cellLp(i));
            }
        }
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        grid.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48)));
        return row;
    }

    private LinearLayout.LayoutParams cellLp(int slot) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (slot > 0) {
            lp.leftMargin = Ui.dpi(this, 2);
        }
        return lp;
    }

    private TextView cell(int d, Calendar day, String key, boolean ok,
                          boolean today, boolean future) {
        TextView tv = new TextView(this);
        tv.setText(String.valueOf(d));
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        if (ok) {
            tv.setTextColor(Color.WHITE);
            tv.setBackground(Ui.round(this, Ui.ACCENT, 14f));
            Ui.font(tv, 700);
        } else if (today) {
            tv.setTextColor(Ui.ACCENT);
            tv.setBackground(Ui.roundStroke(this, 0x00000000, 14f, Ui.ACCENT_RING));
            Ui.font(tv, 700);
        } else {
            tv.setTextColor(future ? 0xFFC4C9D0 : Ui.INK_SOFT);
            Ui.font(tv, 400);
        }
        if (ok) {
            tv.setOnClickListener(v -> showDay(key, day));
        }
        return tv;
    }

    private void showDay(String key, Calendar day) {
        int minutes = Store.minutesOn(this, day);
        List<Store.Session> all = Store.sessions(this);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        int logged = 0;
        for (Store.Session s : all) {
            if (!key.equals(s.date)) {
                continue;
            }
            count++;
            logged += s.minutes;
            sb.append(count).append(". ").append(s.title)
                    .append(" · ").append(s.minutes).append(" 分钟");
            if (s.feel >= 0) {
                sb.append(" · ").append(new String[]{"太轻松", "刚刚好", "太吃力"}[Math.min(2, s.feel)]);
            }
            sb.append('\n');
        }
        if (sb.length() == 0) {
            sb.append("当天完成了 ").append(minutes).append(" 分钟训练。\n");
        }
        sb.append("\n当天合计 ").append(count > 0 ? logged : minutes).append(" 分钟。");
        InfoDialog.show(this, key, sb.toString());
    }
}
