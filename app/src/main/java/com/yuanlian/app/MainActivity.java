package com.yuanlian.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String[] WEEK_LABEL = {"一", "二", "三", "四", "五", "六", "日"};

    private int todayIndex;
    private PlanProfile profile;
    private List<Data.Day> plan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_main);

        todayIndex = Data.todayIndex(Calendar.getInstance());

        ((TextView) findViewById(R.id.tv_date)).setText(
                new SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINA).format(Calendar.getInstance().getTime()));
        ((TextView) findViewById(R.id.tv_greeting)).setText(greeting());

        findViewById(R.id.btn_start).setOnClickListener(v -> startWorkout(todayIndex));
        findViewById(R.id.btn_library).setOnClickListener(v ->
                startActivity(new Intent(this, LibraryActivity.class)));
        View.OnClickListener custom = v -> startActivity(new Intent(this, PlanActivity.class));
        findViewById(R.id.custom_card).setOnClickListener(custom);
        findViewById(R.id.btn_custom).setOnClickListener(custom);
        findViewById(R.id.btn_plan).setOnClickListener(custom);

        TextView customTitle = findViewById(R.id.tv_custom_title);
        TextView customSub = findViewById(R.id.tv_custom_sub);
        TextView customBtn = findViewById(R.id.btn_custom);
        TextView planMore = findViewById(R.id.btn_plan);
        TextView library = findViewById(R.id.btn_library);
        Ui.font(customTitle, 600);
        Ui.font(customSub, 400);
        Ui.font(customBtn, 600);
        Ui.font(planMore, 500);

        library.setText("浏览全部 " + Data.all().size() + " 个动作");
        Ui.font(library, 600);
        library.setBackground(Ui.ripple(this,
                Ui.roundStroke(this, Ui.SURFACE, Ui.R_CARD, Ui.LINE), 0x22000000));
        customBtn.setBackground(Ui.ripple(this, Ui.round(this, Ui.SURFACE, Ui.R_PILL), 0x22000000));
    }

    @Override
    protected void onResume() {
        super.onResume();
        profile = Store.profile(this);
        plan = Data.weekPlan(profile);
        renderCustomCard();
        renderHero();
        renderStats();
        renderPlan();
    }

    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 6) {
            return "夜深了，早点睡";
        } else if (h < 11) {
            return "早上好，先动一动";
        } else if (h < 14) {
            return "中午好，活动一下";
        } else if (h < 19) {
            return "下午好，该练了";
        }
        return "晚上好，来一组";
    }

    // ---- 定制入口 ----

    private void renderCustomCard() {
        TextView title = findViewById(R.id.tv_custom_title);
        TextView sub = findViewById(R.id.tv_custom_sub);
        TextView btn = findViewById(R.id.btn_custom);

        findViewById(R.id.custom_card).setBackground(Ui.ripple(this,
                Ui.round(this, Ui.ACCENT_SOFT, Ui.R_CARD), 0x22000000));

        boolean custom = Store.hasProfile(this);
        if (custom) {
            title.setText("你的定制计划 · " + profile.goalLabel());
            sub.setText("每周 " + profile.days + " 练 · 每次 " + profile.minutes + " 分钟\n"
                    + profile.levelLabel() + " · " + profile.gearSummary());
            btn.setText("调整");
        } else {
            title.setText("按你的条件生成计划");
            sub.setText("目标、水平、器械、时长，30 秒排好一周");
            btn.setText("开始定制");
        }
    }

    // ---- 今日训练卡 ----

    private void renderHero() {
        View card = findViewById(R.id.hero_card);
        card.setBackground(Ui.ripple(this, Ui.gradientHero(this), 0x33FFFFFF));

        Data.Day day = plan.get(todayIndex);
        TextView title = findViewById(R.id.tv_hero_title);
        TextView meta = findViewById(R.id.tv_hero_meta);
        TextView start = findViewById(R.id.btn_start);
        TextView time = findViewById(R.id.tv_hero_time);
        TextView label = findViewById(R.id.tv_hero_label);

        Ui.font(title, 700);
        Ui.font(meta, 400);
        Ui.font(start, 700);
        Ui.font(time, 500);
        Ui.font(label, 500);

        String tag = Store.hasProfile(this) ? profile.goalLabel() : "推荐";
        label.setText(day.rest ? "恢复日 · " + tag : "今日训练 · " + tag);
        title.setText(day.title);
        if (day.rest) {
            meta.setText(day.count() + " 个拉伸 · " + day.focus);
            start.setText(day.count() == 0 ? "随便练练" : "开始拉伸");
        } else {
            meta.setText(day.count() + " 个动作 × " + day.rounds + " 轮 · " + day.focus);
            start.setText("开始训练");
        }
        time.setText(day.count() == 0 ? "" : "约 " + day.minutes() + " 分钟");
        start.setBackground(Ui.ripple(this, Ui.round(this, Ui.SURFACE, Ui.R_PILL), 0x22000000));
        if (!day.rest && Store.dates(this).contains(Store.key(Calendar.getInstance()))) {
            start.setText("再练一次");
        }

        PhotoView hero = findViewById(R.id.hero_photo);
        hero.setRadiusDp(20f);
        hero.setZoom(1f);
        if (!day.exercises.isEmpty()) {
            hero.setVisibility(View.VISIBLE);
            hero.bind(day.exercises.get(0));
            hero.setAnimated(true);
        } else {
            hero.setVisibility(View.GONE);
        }
    }

    // ---- 数据卡 ----

    private void renderStats() {
        LinearLayout row = findViewById(R.id.stats_row);
        row.removeAllViews();

        int streak = Store.streak(this);
        int week = Store.weekDone(this, todayIndex);
        int minutes = Store.totalMinutes(this);

        addStat(row, String.valueOf(streak), "连续天数", 0, true);
        addStat(row, week + "/" + profile.days, "本周完成", 8, false);
        addStat(row, String.valueOf(minutes), "累计分钟", 8, false);
    }

    private void addStat(LinearLayout row, String value, String label,
                         int marginLeftDp, boolean accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(accent ? Ui.gradientStreak(this) : Ui.round(this, Ui.SURFACE, Ui.R_CARD));
        int p = Ui.dpi(this, 16);
        card.setPadding(p, p, p, p);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dpi(this, marginLeftDp);
        row.addView(card, lp);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(accent ? Ui.SURFACE : Ui.INK);
        v.setTextSize(22);
        Ui.font(v, 700);
        card.addView(v);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(accent ? Ui.WHITE_80 : Ui.INK_SOFT);
        l.setTextSize(12);
        l.setPadding(0, Ui.dpi(this, 2), 0, 0);
        Ui.font(l, 400);
        card.addView(l);
    }

    // ---- 本周安排 ----

    private void renderPlan() {
        LinearLayout box = findViewById(R.id.plan_container);
        box.removeAllViews();

        Set<String> done = Store.dates(this);
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            Data.Day day = plan.get(i);
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
            boolean isDone = done.contains(Store.key(d));
            boolean isToday = i == todayIndex;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackground(Ui.ripple(this,
                    Ui.round(this, isToday ? Ui.ACCENT_SOFT : Ui.SURFACE, Ui.R_CARD), 0x18000000));
            int p = Ui.dpi(this, 12);
            item.setPadding(p, p, p, p);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = Ui.dpi(this, 9);
            }
            box.addView(item, lp);

            PhotoView photo = new PhotoView(this);
            photo.setRadiusDp(14f);
            photo.setZoom(1f);
            photo.setAnimated(false);
            if (!day.exercises.isEmpty()) {
                photo.bind(day.exercises.get(0));
            }
            item.addView(photo, new LinearLayout.LayoutParams(Ui.dpi(this, 52), Ui.dpi(this, 52)));

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = Ui.dpi(this, 13);
            item.addView(text, tlp);

            TextView title = new TextView(this);
            title.setText("周" + WEEK_LABEL[i] + " · " + day.title);
            title.setTextColor(day.rest ? Ui.INK_SOFT : Ui.INK);
            title.setTextSize(15);
            Ui.font(title, 600);
            text.addView(title);

            TextView sub = new TextView(this);
            if (day.rest) {
                sub.setText(day.focus + " · " + day.count() + " 个拉伸");
            } else {
                sub.setText(day.count() + " 个动作 × " + day.rounds + " 轮 · 约 "
                        + day.minutes() + " 分钟");
            }
            sub.setTextColor(Ui.INK_SOFT);
            sub.setTextSize(12);
            sub.setPadding(0, Ui.dpi(this, 3), 0, 0);
            Ui.font(sub, 400);
            text.addView(sub);

            TextView status = new TextView(this);
            status.setTextSize(12);
            Ui.font(status, 600);
            if (isDone) {
                status.setText("已完成");
                status.setTextColor(Ui.ACCENT);
            } else if (isToday && !day.rest) {
                status.setText("今天");
                status.setTextColor(Ui.ACCENT);
            } else if (day.rest) {
                status.setText("恢复");
                status.setTextColor(Ui.INK_SOFT);
            } else {
                status.setText("");
            }
            item.addView(status);

            final int index = i;
            item.setOnClickListener(v -> {
                if (!day.exercises.isEmpty()) {
                    startWorkout(index);
                }
            });
        }
    }

    private void startWorkout(int dayIndex) {
        Intent it = new Intent(this, WorkoutActivity.class);
        it.putExtra(WorkoutActivity.EXTRA_DAY, dayIndex);
        startActivity(it);
    }
}
