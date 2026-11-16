package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

/** 练后结果：本次统计、周进度、感受、体重与提醒。 */
public class ResultActivity extends Activity {

    public static final String EXTRA_DAY = "day_index";
    public static final String EXTRA_SINGLE = "single_exercise";
    public static final String EXTRA_TITLE = "session_title";
    public static final String EXTRA_MINUTES = "session_minutes";
    public static final String EXTRA_COUNT = "session_count";
    public static final String EXTRA_KCAL = "session_kcal";
    /** 本次刷新了几个动作的个人纪录。 */
    public static final String EXTRA_PR = "session_pr";

    private static final String[] WEEK_LABEL = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] FEEL_LABELS = {"太轻松", "刚刚好", "太吃力"};
    private static final int[] FEEL_ICONS = {IconView.BOLT, IconView.CHECK, IconView.FLAME};

    private int dayIndex;
    private String single;
    private String title;
    private int minutes;
    private int count;
    private int kcal;
    private int pr;

    private final LinearLayout[] feelCells = new LinearLayout[3];
    private final IconView[] feelIcons = new IconView[3];
    private final TextView[] feelTexts = new TextView[3];
    private int feel = -1;
    /** 点第一下之前的难度档位，改主意时以它为准。 */
    private int feelBaseLevel = -1;
    private TextView feelTip;

    private TextView weightValue;
    private TextView weightMax;
    private TextView weightMin;
    private TextView bmiText;
    private TextView reminderSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        // Android 13+：返回键回首页而不是留在结果页
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    (OnBackInvokedCallback) this::goHome);
        }

        dayIndex = getIntent().getIntExtra(EXTRA_DAY, Data.todayIndex(Calendar.getInstance()));
        single = getIntent().getStringExtra(EXTRA_SINGLE);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        minutes = Math.max(1, getIntent().getIntExtra(EXTRA_MINUTES, 1));
        count = Math.max(1, getIntent().getIntExtra(EXTRA_COUNT, 1));
        kcal = getIntent().getIntExtra(EXTRA_KCAL, -1);
        pr = Math.max(0, getIntent().getIntExtra(EXTRA_PR, 0));
        if (kcal < 0) {
            // 旧入口或测试数据：按分钟数粗算，和报告页的回退口径一致
            kcal = Kcal.rough(minutes);
        }
        if (title == null || title.isEmpty()) {
            title = single == null ? "今日训练" : single;
        }

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 12), p, Ui.dpi(this, 26));
        sc.addView(body);

        // 内容比一屏长，底部压一层由底色渐隐的遮罩，让人知道还能往下滑
        FrameLayout holder = new FrameLayout(this);
        holder.addView(sc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View fade = new View(this);
        fade.setBackground(Ui.gradientVertical(this, 0x00F8F8F8, Ui.BG, 0f));
        fade.setClickable(false);
        holder.addView(fade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 26), Gravity.BOTTOM));
        root.addView(holder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(hero());
        body.addView(weekCard(), cardLp());
        body.addView(section("感觉怎么样"));
        body.addView(feelCard(), cardLp());
        body.addView(section("身体数据"));
        body.addView(weightCard(), cardLp());
        body.addView(section("保持节奏"));
        body.addView(reminderCard(), cardLp());
        UiList.hint(this, body, "本次结果和感受只保存在本机。");

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(p, Ui.dpi(this, 14), p, Ui.dpi(this, 14));
        root.addView(actions);

        TextView done = UiList.primary(this, "完成");
        actions.addView(done, UiList.lp(this, 52, 0));
        done.setOnClickListener(v -> goHome());

        TextView again = UiList.secondary(this,
                single == null ? "再做一次" : "再练这个动作");
        actions.addView(again, UiList.lp(this, 46, 8));
        again.setOnClickListener(v -> restart());

        // 记录已经落盘了，把桌面上的小部件一起刷新，不用等它自己半小时后更新
        StreakWidgetProvider.refresh(this);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 10);
        return lp;
    }

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(18);
        tv.setPadding(0, Ui.dpi(this, 22), 0, Ui.dpi(this, 2));
        Ui.font(tv, 700);
        return tv;
    }

    private FrameLayout hero() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.gradientHero(this));
        card.setClipToOutline(true);
        int p = Ui.dpi(this, 22);
        card.setPadding(p, p, p, p);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = new TextView(this);
        badge.setText(single == null ? "本次训练已完成" : "动作已完成");
        badge.setTextColor(Ui.CREAM_INK);
        badge.setTextSize(11);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dpi(this, 10), Ui.dpi(this, 4),
                Ui.dpi(this, 10), Ui.dpi(this, 4));
        badge.setBackground(Ui.round(this, Ui.CREAM, 11f));
        Ui.font(badge, 600);
        // 胶囊按文字收窄，右侧给角标圆留出位置，避免被压在下面
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp2.rightMargin = Ui.dpi(this, 66);
        content.addView(badge, blp2);

        TextView big = new TextView(this);
        boolean goal = weekReached();
        big.setText(goal ? "本周目标达成！" : "今天也完成了");
        big.setTextColor(Color.WHITE);
        big.setTextSize(30);
        big.setLineSpacing(Ui.dp(this, 2), 1f);
        big.setPadding(0, Ui.dpi(this, 12), Ui.dpi(this, 60), 0);
        Ui.font(big, 800);
        content.addView(big);

        TextView sub = new TextView(this);
        sub.setText(title + " · " + minutes + " 分钟 · " + count + " 个动作"
                + (pr > 0 ? " · " + pr + " 项新纪录" : ""));
        sub.setTextColor(Ui.WHITE_80);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dpi(this, 6), 0, 0);
        Ui.font(sub, 400);
        content.addView(sub);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dpi(this, 18);
        content.addView(stats, slp);
        stat(stats, String.valueOf(minutes), "分钟");
        stat(stats, String.valueOf(count), "个动作");
        stat(stats, String.valueOf(kcal), "千卡");

        FrameLayout bubble = new FrameLayout(this);
        bubble.setBackground(Ui.round(this, 0x2EFFFFFF, 30f));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                Ui.dpi(this, 60), Ui.dpi(this, 60));
        blp.gravity = Gravity.RIGHT | Gravity.TOP;
        card.addView(bubble, blp);
        IconView trophy = new IconView(this);
        trophy.setIcon(goal ? IconView.TROPHY : IconView.CHECK);
        trophy.setColor(Color.WHITE);
        bubble.addView(trophy, new FrameLayout.LayoutParams(
                Ui.dpi(this, 30), Ui.dpi(this, 30), Gravity.CENTER));
        return card;
    }

    private void stat(LinearLayout row, String value, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        row.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(21);
        v.setIncludeFontPadding(false);
        Ui.font(v, 800);
        cell.addView(v);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.WHITE_60);
        l.setTextSize(11);
        l.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(l, 400);
        cell.addView(l);
    }

    private boolean weekReached() {
        int target = Math.max(1, Store.profile(this).days);
        return Store.weekDone(this, Data.todayIndex(Calendar.getInstance())) >= target;
    }

    private LinearLayout weekCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 18);
        card.setPadding(p, p, p, Ui.dpi(this, 16));

        int target = Math.max(1, Store.profile(this).days);
        int done = Store.weekDone(this, Data.todayIndex(Calendar.getInstance()));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);
        TextView left = new TextView(this);
        left.setText("本周目标");
        left.setTextColor(Ui.INK);
        left.setTextSize(17);
        Ui.font(left, 700);
        head.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = new TextView(this);
        right.setText(done + " / " + target + " 天");
        right.setTextColor(Ui.ACCENT);
        right.setTextSize(14);
        Ui.font(right, 700);
        head.addView(right);

        ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(target);
        bar.setProgress(Math.min(target, done));
        bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFEDF0F4));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 7));
        blp.topMargin = Ui.dpi(this, 12);
        card.addView(bar, blp);

        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dpi(this, 12);
        card.addView(days, dlp);

        int today = Data.todayIndex(Calendar.getInstance());
        Calendar cal = Calendar.getInstance();
        Set<String> dates = Store.dates(this);
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(today - i));
            boolean isToday = i == today;
            boolean ok = dates.contains(Store.key(d));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            days.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(this);
            label.setText(WEEK_LABEL[i]);
            label.setTextColor(isToday ? Ui.ACCENT : Ui.INK_SOFT);
            label.setTextSize(10);
            Ui.font(label, isToday ? 700 : 400);
            col.addView(label);

            TextView dot = new TextView(this);
            dot.setText(ok ? "✓" : String.valueOf(d.get(Calendar.DAY_OF_MONTH)));
            dot.setTextSize(11);
            dot.setGravity(Gravity.CENTER);
            dot.setIncludeFontPadding(false);
            dot.setTextColor(ok ? Color.WHITE : (isToday ? Ui.ACCENT : Ui.INK_SOFT));
            dot.setBackground(Ui.round(this, ok ? Ui.ACCENT
                    : (isToday ? 0x00000000 : 0x00000000), 15f));
            if (isToday && !ok) {
                dot.setBackground(Ui.roundStroke(this, 0x00000000, 15f, Ui.ACCENT_RING));
            }
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 30), Ui.dpi(this, 30));
            nlp.topMargin = Ui.dpi(this, 4);
            col.addView(dot, nlp);
        }
        return card;
    }

    private LinearLayout feelCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 14);
        card.setPadding(p, p, p, p);
        TextView q = new TextView(this);
        q.setText("今天的强度合适吗？");
        q.setTextColor(Ui.INK);
        q.setTextSize(15);
        Ui.font(q, 600);
        card.addView(q);
        TextView tip = new TextView(this);
        feelTip = tip;
        tip.setText("现在是「" + Store.profile(this).levelLabel()
                + "」。觉得轻松会加一档，吃力会降一档，下周生效。");
        tip.setTextColor(Ui.INK_SOFT);
        tip.setTextSize(12);
        tip.setPadding(0, Ui.dpi(this, 3), 0, Ui.dpi(this, 10));
        Ui.font(tip, 400);
        card.addView(tip);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Ui.dpi(this, 12), 0, Ui.dpi(this, 10));
            cell.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 16f),
                    0x22000000));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                lp.leftMargin = Ui.dpi(this, 8);
            }
            row.addView(cell, lp);
            IconView ic = new IconView(this);
            ic.setIcon(FEEL_ICONS[i]);
            ic.setColor(Ui.INK_SOFT);
            cell.addView(ic, new LinearLayout.LayoutParams(
                    Ui.dpi(this, 24), Ui.dpi(this, 24)));
            TextView t = new TextView(this);
            t.setText(FEEL_LABELS[i]);
            t.setTextColor(Ui.INK_SOFT);
            t.setTextSize(12);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dpi(this, 6), 0, 0);
            Ui.font(t, 500);
            // 纵向 LinearLayout 的默认参数是 MATCH_PARENT 宽，文字会在整格里左对齐，
            // 父容器的 gravity=CENTER 就失效了。这里显式给宽度 + 居中。
            cell.addView(t, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            cell.setOnClickListener(v -> selectFeel(idx));
            feelCells[i] = cell;
            feelIcons[i] = ic;
            feelTexts[i] = t;
        }
        return card;
    }

    private void selectFeel(int idx) {
        feel = idx;
        for (int i = 0; i < 3; i++) {
            boolean on = i == idx;
            feelCells[i].setBackground(on
                    ? Ui.roundStroke(this, Ui.ACCENT_SOFT, 16f, Ui.ACCENT)
                    : Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 16f), 0x22000000));
            feelIcons[i].setColor(on ? Ui.ACCENT : Ui.INK_SOFT);
            feelTexts[i].setTextColor(on ? Ui.ACCENT : Ui.INK_SOFT);
            Ui.font(feelTexts[i], on ? 700 : 500);
        }
        Store.setLastFeel(this, idx);

        // 第一次点的时候记下原来的档位，之后再改主意都以它为准，
        // 这样「太轻松 → 太吃力」不会把档位来回拉扯。
        if (feelBaseLevel < 0) {
            feelBaseLevel = Store.profile(this).level;
        }
        int delta = idx == 0 ? 1 : (idx == 2 ? -1 : 0);
        Store.setLevel(this, feelBaseLevel + delta);
        String now = Store.profile(this).levelLabel();
        if (feelTip != null) {
            feelTip.setText("现在是「" + now + "」。觉得轻松会加一档，吃力会降一档，下周生效。");
        }
        Toast.makeText(this, idx == 1 ? "已记录：刚刚好"
                : "已记录：" + FEEL_LABELS[idx] + "，下周按「" + now + "」安排",
                Toast.LENGTH_SHORT).show();
    }

    private LinearLayout weightCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 18);
        card.setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);
        TextView titleView = new TextView(this);
        titleView.setText("体重");
        titleView.setTextColor(Ui.INK);
        titleView.setTextSize(17);
        Ui.font(titleView, 700);
        head.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView record = new TextView(this);
        record.setText("记录");
        record.setTextColor(Color.WHITE);
        record.setTextSize(13);
        record.setGravity(Gravity.CENTER);
        record.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT, 20f), 0x33FFFFFF));
        Ui.font(record, 600);
        head.addView(record, new LinearLayout.LayoutParams(
                Ui.dpi(this, 66), Ui.dpi(this, 34)));
        record.setOnClickListener(v -> HealthDialogs.recordWeight(this, this::renderWeight));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dpi(this, 12);
        card.addView(row, rlp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView label = new TextView(this);
        label.setText("当前");
        label.setTextColor(Ui.INK_SOFT);
        label.setTextSize(12);
        Ui.font(label, 400);
        left.addView(label);
        weightValue = new TextView(this);
        weightValue.setTextColor(Ui.INK);
        weightValue.setTextSize(28);
        weightValue.setIncludeFontPadding(false);
        weightValue.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(weightValue, 800);
        left.addView(weightValue);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        row.addView(right);
        weightMax = new TextView(this);
        weightMax.setTextColor(Ui.INK_SOFT);
        weightMax.setTextSize(12);
        Ui.font(weightMax, 500);
        right.addView(weightMax);
        weightMin = new TextView(this);
        weightMin.setTextColor(Ui.INK_SOFT);
        weightMin.setTextSize(12);
        weightMin.setPadding(0, Ui.dpi(this, 5), 0, 0);
        Ui.font(weightMin, 500);
        right.addView(weightMin);

        bmiText = new TextView(this);
        bmiText.setTextColor(Ui.INK_SOFT);
        bmiText.setTextSize(12);
        bmiText.setLineSpacing(Ui.dp(this, 3), 1f);
        bmiText.setPadding(0, Ui.dpi(this, 10), 0, 0);
        Ui.font(bmiText, 400);
        bmiText.setOnClickListener(v -> HealthDialogs.setHeight(this, this::renderWeight));
        card.addView(bmiText);

        renderWeight();
        return card;
    }

    private void renderWeight() {
        boolean lb = Store.isLb(this);
        float kg = Store.latestWeightKg(this);
        float unit = Store.kgToDisplay(this, kg);
        if (kg > 0) {
            weightValue.setText(Store.num(unit) + " " + (lb ? "lb" : "kg"));
        } else {
            weightValue.setText("--");
        }
        java.util.List<Store.WeightEntry> list = Store.weights(this);
        float max = 0;
        float min = 0;
        for (Store.WeightEntry e : list) {
            float v = Store.kgToDisplay(this, e.kg);
            max = max == 0 ? v : Math.max(max, v);
            min = min == 0 ? v : Math.min(min, v);
        }
        weightMax.setText("最高 " + (max > 0 ? Store.num(max) : "--"));
        weightMin.setText("最低 " + (min > 0 ? Store.num(min) : "--"));
        float bmi = Store.bmi(this);
        if (kg <= 0) {
            bmiText.setText("记录体重后，可以在这里看到 BMI。");
        } else if (bmi <= 0) {
            bmiText.setText("点这里设置身高，计算 BMI →");
        } else {
            bmiText.setText(String.format(Locale.CHINA, "BMI %.1f · %s · 身高 %s",
                    bmi, Store.bmiLabel(bmi), Store.formatHeight(this, Store.heightCm(this))));
        }
    }

    private LinearLayout reminderCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 14);
        card.setPadding(p, p, p, p);
        UiList.Row row = UiList.row(this, card, IconView.CLOCK, Ui.ORANGE,
                "训练提醒", reminderText(), true,
                v -> startActivity(new Intent(this, ReminderActivity.class)));
        reminderSub = row.sub;
        return card;
    }

    private String reminderText() {
        String s = Store.reminderSummary(this);
        return "未开启".equals(s) ? "设置一个固定的训练时间" : s + " · 点按修改";
    }

    private void restart() {
        Intent it = new Intent(this, WorkoutActivity.class);
        String ids = getIntent().getStringExtra(WorkoutActivity.EXTRA_IDS);
        if (ids != null && !ids.isEmpty()) {
            it.putExtra(WorkoutActivity.EXTRA_IDS, ids);
            it.putExtra(WorkoutActivity.EXTRA_ROUNDS,
                    getIntent().getIntExtra(WorkoutActivity.EXTRA_ROUNDS, 1));
            it.putExtra(WorkoutActivity.EXTRA_SECONDS,
                    getIntent().getIntExtra(WorkoutActivity.EXTRA_SECONDS, 0));
            it.putExtra(WorkoutActivity.EXTRA_REST,
                    getIntent().getIntExtra(WorkoutActivity.EXTRA_REST, -1));
            it.putExtra(WorkoutActivity.EXTRA_TITLE, title);
        } else if (single != null) {
            it.putExtra(WorkoutActivity.EXTRA_SINGLE, single);
        } else {
            it.putExtra(WorkoutActivity.EXTRA_DAY, dayIndex);
        }
        startActivity(it);
        finish();
    }

    private void goHome() {
        Intent it = new Intent(this, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(it);
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (reminderSub != null) {
            reminderSub.setText(reminderText());
        }
        if (weightValue != null) {
            renderWeight();
        }
    }
}
