package com.aijianshen.app;

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
    private LinearLayout body;
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
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 4), p, Ui.dpi(this, 24));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        render();
    }

    /** 从「3 分钟接上」练完回来要立刻看到新数字，所以整页按当前记录重建。 */
    @Override
    protected void onResume() {
        super.onResume();
        if (body != null) {
            render();
        }
    }

    private void render() {
        body.removeAllViews();
        body.addView(streakHero(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(calendarCard(), cardLp());
        Store.Streak st = Store.streakInfo(this);
        String note;
        if (st.absent > 0) {
            note = "连续天数留了 " + Store.GRACE_DAYS + " 天宽限：中间停一两天不清零，"
                    + "日历上会用浅灰点标出来。今天动 3 分钟就能接上。";
        } else {
            note = "连续天数从今天往前计算；停一两天也不会清零，"
                    + "日历上会用浅灰点标出停过的那几天。";
        }
        UiList.hint(this, body, note);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 14);
        return lp;
    }

    private FrameLayout streakHero() {
        Store.Streak st = Store.streakInfo(this);
        int best = Store.bestStreak(this);
        // 真的断了就先给最长纪录，别让页面第一个数字是 0
        boolean down = st.days == 0 && best > 0;
        int hero = down ? best : st.days;

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
        cap.setText(down ? "最长连续" : (st.absent > 0 ? "还接得上" : "当前连续"));
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
        n.setText(String.valueOf(hero));
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
        tip.setText(tipText(st, down));
        tip.setTextColor(Ui.WHITE_80);
        tip.setTextSize(12);
        tip.setLineSpacing(Ui.dp(this, 2), 1f);
        Ui.font(tip, 400);
        left.addView(tip);

        // 断过的人最需要的是「现在就能动一下」，而不是一句总结
        if (st.absent > 0 && st.idle >= 0) {
            TextView go = new TextView(this);
            go.setText("3 分钟接上  ›");
            go.setTextColor(Ui.ACCENT);
            go.setTextSize(13);
            go.setGravity(Gravity.CENTER);
            go.setIncludeFontPadding(false);
            go.setPadding(Ui.dpi(this, 16), 0, Ui.dpi(this, 16), 0);
            go.setBackground(Ui.ripple(this,
                    Ui.round(this, Color.WHITE, 18f), 0x22000000));
            Ui.font(go, 700);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 36));
            glp.topMargin = Ui.dpi(this, 12);
            left.addView(go, glp);
            go.setOnClickListener(v -> Rescue.start(this));
        }

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(this, 0x2EFFFFFF, 24f));
        box.addView(badge, new LinearLayout.LayoutParams(
                Ui.dpi(this, 92), Ui.dpi(this, 92)));
        LinearLayout badgeBox = new LinearLayout(this);
        badgeBox.setOrientation(LinearLayout.VERTICAL);
        badgeBox.setGravity(Gravity.CENTER);
        badge.addView(badgeBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView bestView = new TextView(this);
        // 当前就是历史最好时，徽章改说宽限天数，免得同一个数字出现两次。
        // 但从没练过的人（idle < 0）不该看到「2 天宽限」——他连 1 天都没有，宽限无从谈起。
        boolean showGrace = st.idle >= 0 && (down || best <= st.days);
        bestView.setText(showGrace ? String.valueOf(Store.GRACE_DAYS) : String.valueOf(best));
        bestView.setTextColor(Color.WHITE);
        bestView.setTextSize(26);
        bestView.setIncludeFontPadding(false);
        bestView.setGravity(Gravity.CENTER);
        Ui.font(bestView, 800);
        badgeBox.addView(bestView);
        TextView bestLabel = new TextView(this);
        bestLabel.setText(showGrace ? "天宽限" : "最长连续 / 天");
        bestLabel.setTextColor(Ui.WHITE_60);
        bestLabel.setTextSize(10);
        bestLabel.setMaxLines(1);
        bestLabel.setGravity(Gravity.CENTER);
        Ui.font(bestLabel, 400);
        badgeBox.addView(bestLabel);
        return card;
    }

    private String tipText(Store.Streak st, boolean down) {
        if (st.idle < 0) {
            return "完成今天的一次训练，从这里开始。";
        }
        if (down) {
            return "空了 " + st.idle + " 天。今天动 3 分钟，不用从头算。";
        }
        if (st.absent > 0) {
            int left = st.tillBreak();
            return left <= 0 ? "今天再不练就断了，3 分钟也算数。"
                    : "再停 " + left + " 天就断了，今天接上就好。";
        }
        return st.todayDone ? "今天已经动过了，保持住。" : "今天还没练，现在开始正好。";
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
        // 固定 104dp 宽在系统字体放大时会折成「2026 年 11 / 月」两行，
        // 改成按内容撑开、单行不折，宽度不够时用省略号兜底，箭头位置才稳。
        monthLabel.setSingleLine(true);
        monthLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        monthLabel.setMinWidth(Ui.dpi(this, 104));
        Ui.font(monthLabel, 700);
        head.addView(monthLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 36)));

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
        Store.Streak st = Store.streakInfo(this);

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
            boolean gap = !ok && !isToday && !future && withinGap(day, today, st);
            row.addView(cell(d, day, key, ok, isToday, future, gap), cellLp(slot));
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

    /** 这一天是不是宽限期里空掉的那几天：最近一次训练之后、今天之前。 */
    private boolean withinGap(Calendar day, Calendar today, Store.Streak st) {
        if (st.idle <= 1 || st.broken()) {
            return false;
        }
        long diff = dayStart(today) - dayStart(day);
        return diff > 0 && diff < st.idle * 86400000L;
    }

    private long dayStart(Calendar c) {
        Calendar x = (Calendar) c.clone();
        x.set(Calendar.HOUR_OF_DAY, 0);
        x.set(Calendar.MINUTE, 0);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    private TextView cell(int d, Calendar day, String key, boolean ok,
                          boolean today, boolean future, boolean gap) {
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
        } else if (gap) {
            tv.setTextColor(0xFF7E8792);
            tv.setBackground(Ui.round(this, 0xFFEDEFF4, 14f));
            Ui.font(tv, 500);
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
