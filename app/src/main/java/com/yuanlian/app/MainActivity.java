package com.yuanlian.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private LinearLayout weekStrip;
    private LinearLayout challengeRow;
    private LinearLayout focusRow;
    private LinearLayout planContainer;
    private TextView tvWeekProgress;
    private TextView tvCoach;
    private TextView tvStreak;

    private int flameIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_main);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        todayIndex = Data.todayIndex(Calendar.getInstance());
        bindHeader();
        bindStatic();
        ((FrameLayout) findViewById(R.id.nav_holder)).addView(
                Nav.bar(this, Nav.TAB_WORKOUT),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!Store.hasProfile(this) && savedInstanceState == null) {
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    startActivity(new Intent(this, PlanActivity.class)
                            .putExtra(PlanActivity.EXTRA_ONBOARDING, true)), 350);
        }
    }

    private void bindHeader() {
        TextView title = findViewById(R.id.tv_title);
        title.setText("圆练教练");
        Ui.font(title, 800);

        flameIcon = IconView.FLAME;
        LinearLayout chip = findViewById(R.id.streak_chip);
        chip.setBackground(Ui.ripple(this, Ui.round(this, Ui.CREAM, Ui.R_PILL), 0x22000000));
        IconView flame = findViewById(R.id.icon_flame);
        flame.setIcon(IconView.FLAME);
        flame.setColor(Ui.RED);
        tvStreak = findViewById(R.id.tv_streak);
        tvStreak.setTextColor(Ui.CREAM_INK);
        Ui.font(tvStreak, 700);
        chip.setOnClickListener(v -> startActivity(new Intent(this, StreakActivity.class)));
    }

    private void bindStatic() {
        LinearLayout search = findViewById(R.id.search_bar);
        search.setBackground(Ui.ripple(this, Ui.round(this, Ui.SEARCH, 23f), 0x14000000));
        IconView searchIcon = findViewById(R.id.icon_search);
        searchIcon.setIcon(IconView.SEARCH);
        searchIcon.setColor(Ui.INK_SOFT);
        TextView searchText = findViewById(R.id.tv_search);
        searchText.setText("搜索动作、部位或英文名");
        Ui.font(searchText, 400);
        search.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));

        LinearLayout weekCard = findViewById(R.id.week_card);
        weekCard.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        TextView weekTitle = findViewById(R.id.tv_week_title);
        weekTitle.setText("本周目标");
        Ui.font(weekTitle, 700);
        tvWeekProgress = findViewById(R.id.tv_week_progress);
        Ui.font(tvWeekProgress, 700);
        IconView pencil = findViewById(R.id.icon_pencil);
        pencil.setIcon(IconView.PENCIL);
        pencil.setColor(Ui.INK);
        pencil.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        weekStrip = findViewById(R.id.week_strip);

        LinearLayout coach = findViewById(R.id.coach_row);
        coach.setBackground(Ui.round(this, Ui.CARD_SOFT, 16f));
        IconView coachIcon = findViewById(R.id.icon_coach);
        coachIcon.setIcon(IconView.DUMBBELL);
        coachIcon.setColor(Ui.ACCENT);
        tvCoach = findViewById(R.id.tv_coach);
        Ui.font(tvCoach, 500);

        TextView more = findViewById(R.id.btn_more_challenge);
        more.setText("更多  ›");
        more.setTextColor(Ui.ACCENT);
        Ui.font(more, 600);
        more.setOnClickListener(v -> startActivity(new Intent(this, DiscoverActivity.class)));

        challengeRow = findViewById(R.id.challenge_row);
        focusRow = findViewById(R.id.focus_row);
        planContainer = findViewById(R.id.plan_container);

        TextView edit = findViewById(R.id.btn_edit_plan);
        edit.setTextColor(Ui.ACCENT);
        Ui.font(edit, 600);
        edit.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));

        TextView library = findViewById(R.id.btn_library);
        library.setText("浏览全部 " + Data.all().size() + " 个动作");
        library.setBackground(Ui.ripple(this,
                Ui.roundStroke(this, Ui.SURFACE, Ui.R_CARD, Ui.LINE), 0x18000000));
        Ui.font(library, 600);
        library.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        profile = Store.profile(this);
        plan = Data.weekPlan(profile);
        todayIndex = Data.todayIndex(Calendar.getInstance());
        renderStreak();
        renderWeekGoal();
        renderCoach();
        renderChallenges();
        renderFocus();
        renderPlan();
    }

    private void renderStreak() {
        int streak = Store.streak(this);
        tvStreak.setText(streak + " 天");
    }

    private void renderWeekGoal() {
        int done = Store.weekDone(this, todayIndex);
        int target = Math.max(1, profile.days);
        tvWeekProgress.setText(done + "/" + target);

        weekStrip.removeAllViews();
        Calendar cal = Calendar.getInstance();
        Set<String> dates = Store.dates(this);
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
            boolean today = i == todayIndex;
            boolean ok = dates.contains(Store.key(d));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            weekStrip.addView(col, lp);

            TextView num = new TextView(this);
            num.setText(String.valueOf(d.get(Calendar.DAY_OF_MONTH)));
            num.setTextSize(15);
            num.setGravity(Gravity.CENTER);
            num.setIncludeFontPadding(false);
            num.setTextColor(today ? Ui.ACCENT : (ok ? Ui.INK : Ui.INK_SOFT));
            Ui.font(num, today || ok ? 700 : 500);
            if (today) {
                num.setBackground(Ui.roundStroke(this, 0x00000000, 18f, Ui.ACCENT_RING));
            }
            col.addView(num, new LinearLayout.LayoutParams(Ui.dpi(this, 36), Ui.dpi(this, 36)));

            View dot = new View(this);
            dot.setBackground(Ui.round(this, ok ? Ui.ACCENT : 0x00000000, 3f));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 5), Ui.dpi(this, 5));
            dlp.topMargin = Ui.dpi(this, 4);
            col.addView(dot, dlp);
        }
    }

    private void renderCoach() {
        boolean doneToday = Store.dates(this).contains(Store.key(Calendar.getInstance()));
        Data.Day today = plan.get(todayIndex);
        String copy;
        if (doneToday) {
            copy = "今天已经练完了。做几组拉伸，给身体一点收尾。";
        } else if (today.rest) {
            copy = "今天是恢复日。慢慢拉开肩背，明天状态会更好。";
        } else if (!Store.hasProfile(this)) {
            copy = "先花半分钟告诉我你的目标，我来排好这一周。";
        } else {
            copy = "欢迎回来！今天安排的是「" + today.title + "」，跟着节奏来就好。";
        }
        tvCoach.setText(copy);
    }

    private void renderChallenges() {
        challengeRow.removeAllViews();
        int[] order = new int[7];
        int n = 0;
        for (int k = 0; k < 7 && n < 7; k++) {
            int i = (todayIndex + k) % 7;
            if (!plan.get(i).rest) {
                order[n++] = i;
            }
        }
        if (n == 0) {
            order[n++] = todayIndex;
        }
        int added = 0;
        for (int k = 0; k < n && added < 2; k++) {
            int idx = order[k];
            boolean blue = added == 0;
            challengeRow.addView(challengeCard(plan.get(idx), idx,
                    blue ? 0xFF0068FF : Ui.GREEN,
                    blue ? Ui.ACCENT : Ui.GREEN_2,
                    blue ? "按你的条件" : "换个节奏"), challengeLp());
            added++;
        }
        int rest = -1;
        for (int i = 0; i < 7; i++) {
            if (plan.get(i).rest) {
                rest = i;
                break;
            }
        }
        if (rest >= 0 && added < 3) {
            challengeRow.addView(challengeCard(plan.get(rest), rest,
                    0xFF6B7C8E, 0xFF3F4E5C, "恢复日"), challengeLp());
        }
    }

    private LinearLayout.LayoutParams challengeLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 318), Ui.dpi(this, 256));
        lp.rightMargin = Ui.dpi(this, 12);
        return lp;
    }

    private View challengeCard(Data.Day day, int dayIndex, int start, int end, String badge) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.gradientCard(this, start, end, Ui.R_HERO));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(v -> openDay(dayIndex));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 16);
        content.setPadding(p, p, p, p);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 20)));

        TextView badgeView = new TextView(this);
        badgeView.setText(badge);
        badgeView.setTextColor(Ui.CREAM_INK);
        badgeView.setTextSize(11);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setPadding(Ui.dpi(this, 10), Ui.dpi(this, 4),
                Ui.dpi(this, 10), Ui.dpi(this, 4));
        badgeView.setBackground(Ui.round(this, Ui.CREAM, 11f));
        Ui.font(badgeView, 600);
        top.addView(badgeView);

        TextView title = new TextView(this);
        title.setText(day.rest ? "恢复与拉伸" : day.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setLineSpacing(Ui.dp(this, 1.5f), 1f);
        title.setMaxLines(2);
        Ui.font(title, 800);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dpi(this, 8);
        tlp.rightMargin = Ui.dpi(this, 88);
        content.addView(title, tlp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.topMargin = Ui.dpi(this, 10);
        content.addView(grid, glp);
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        grid.addView(row1, rowLp);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = Ui.dpi(this, 7);
        grid.addView(row2, row2Lp);

        addInfo(row1, IconView.CALENDAR, "每日时间",
                day.rest ? day.minutes() + " 分钟" : Math.max(1, day.minutes()) + " 分钟");
        addInfo(row1, IconView.CHART, "难度",
                Store.hasProfile(this) ? profile.levelLabel() : "新手");
        addInfo(row2, IconView.TARGET, "重点部位",
                day.rest ? "全身放松" : shortFocus(day.focus));
        addInfo(row2, IconView.CHECK, "器械",
                Store.hasProfile(this) ? shortGear(profile.gearSummary()) : "徒手");

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout cta = new LinearLayout(this);
        cta.setOrientation(LinearLayout.HORIZONTAL);
        cta.setGravity(Gravity.CENTER_VERTICAL);
        cta.setBackground(Ui.round(this, Ui.SURFACE, 21f));
        cta.setPadding(Ui.dpi(this, 16), 0, Ui.dpi(this, 6), 0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 42));
        clp.topMargin = Ui.dpi(this, 8);
        content.addView(cta, clp);

        TextView go = new TextView(this);
        go.setText(day.rest ? "开始拉伸" : "开始训练");
        go.setTextColor(Ui.ACCENT);
        go.setTextSize(14);
        go.setGravity(Gravity.CENTER);
        Ui.font(go, 700);
        cta.addView(go, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout arrow = new FrameLayout(this);
        arrow.setBackground(Ui.round(this, Ui.ACCENT, 15f));
        cta.addView(arrow, new LinearLayout.LayoutParams(Ui.dpi(this, 30), Ui.dpi(this, 30)));
        IconView arrowIcon = new IconView(this);
        arrowIcon.setIcon(IconView.ARROW);
        arrowIcon.setColor(Color.WHITE);
        arrowIcon.setStrokeDp(2.2f);
        arrow.addView(arrowIcon, new FrameLayout.LayoutParams(
                Ui.dpi(this, 15), Ui.dpi(this, 15), Gravity.CENTER));

        cta.setOnClickListener(v -> startWorkout(dayIndex));

        PhotoView photo = new PhotoView(this);
        photo.setRadiusDp(38f);
        photo.setAnimated(false);
        photo.setOutline(0x55FFFFFF, 1.5f);
        if (!day.exercises.isEmpty()) {
            photo.bind(day.exercises.get(0));
        }
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                Ui.dpi(this, 76), Ui.dpi(this, 76));
        plp.gravity = Gravity.RIGHT | Gravity.TOP;
        plp.topMargin = Ui.dpi(this, 12);
        plp.rightMargin = Ui.dpi(this, 14);
        card.addView(photo, plp);
        photo.setOnClickListener(v -> openDay(dayIndex));

        return card;
    }

    private void addInfo(LinearLayout row, int icon, String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(cell, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, 0x2EFFFFFF, 8f));
        cell.addView(tile, new LinearLayout.LayoutParams(
                Ui.dpi(this, 26), Ui.dpi(this, 26)));
        IconView ic = new IconView(this);
        ic.setIcon(icon);
        ic.setColor(Color.WHITE);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 15), Ui.dpi(this, 15), Gravity.CENTER));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 7);
        cell.addView(texts, tlp);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(12.5f);
        v.setMaxLines(1);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.font(v, 700);
        texts.addView(v);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.WHITE_60);
        l.setTextSize(10);
        Ui.font(l, 400);
        texts.addView(l);
    }

    private String shortFocus(String focus) {
        if (focus == null || focus.isEmpty()) {
            return "全身";
        }
        int i = focus.indexOf('+');
        return i > 0 ? focus.substring(0, i).trim() : focus;
    }

    private String shortGear(String gear) {
        if (gear == null || gear.isEmpty()) {
            return "徒手";
        }
        return gear.contains("+") ? gear.substring(0, gear.indexOf('+')).trim() : gear;
    }

    private void renderFocus() {
        focusRow.removeAllViews();
        String[] parts = new String[Data.parts().size() + 1];
        parts[0] = "全身";
        for (int i = 0; i < Data.parts().size(); i++) {
            parts[i + 1] = Data.parts().get(i);
        }
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean on = i == 0;
            TextView chip = new TextView(this);
            chip.setText(part);
            chip.setTextSize(15);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 9),
                    Ui.dpi(this, 18), Ui.dpi(this, 9));
            chip.setTextColor(on ? Ui.ACCENT : Ui.INK_MUTED);
            chip.setBackground(on
                    ? Ui.roundStroke(this, 0x00000000, 19f, Ui.ACCENT)
                    : Ui.round(this, Ui.CARD_SOFT, 19f));
            Ui.font(chip, on ? 600 : 500);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dpi(this, 8);
            focusRow.addView(chip, lp);
            chip.setOnClickListener(v -> {
                Intent it = new Intent(this, LibraryActivity.class);
                if (!"全身".equals(part)) {
                    it.putExtra(LibraryActivity.EXTRA_PART, part);
                }
                startActivity(it);
            });
        }
    }

    private void renderPlan() {
        planContainer.removeAllViews();
        Set<String> done = Store.dates(this);
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            Data.Day day = plan.get(i);
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
            boolean isDone = done.contains(Store.key(d));
            boolean isToday = i == todayIndex;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.ripple(this, Ui.round(this, Ui.SURFACE, 16f), 0x18000000));
            int p = Ui.dpi(this, 10);
            row.setPadding(p, p, Ui.dpi(this, 14), p);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = Ui.dpi(this, 8);
            }
            planContainer.addView(row, lp);

            PhotoView photo = new PhotoView(this);
            photo.setRadiusDp(12f);
            photo.setAnimated(false);
            if (!day.exercises.isEmpty()) {
                photo.bind(day.exercises.get(0));
            }
            row.addView(photo, new LinearLayout.LayoutParams(
                    Ui.dpi(this, 48), Ui.dpi(this, 48)));

            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = Ui.dpi(this, 12);
            row.addView(text, tlp);

            TextView title = new TextView(this);
            title.setText("周" + WEEK_LABEL[i] + " · " + day.title);
            title.setTextColor(day.rest ? Ui.INK_MUTED : Ui.INK);
            title.setTextSize(14.5f);
            Ui.font(title, 600);
            text.addView(title);

            TextView sub = new TextView(this);
            if (day.rest) {
                sub.setText(day.focus + " · " + day.count() + " 个拉伸");
            } else {
                sub.setText(day.count() + " 个动作 · 约 " + day.minutes() + " 分钟");
            }
            sub.setTextColor(Ui.INK_SOFT);
            sub.setTextSize(12);
            sub.setPadding(0, Ui.dpi(this, 3), 0, 0);
            Ui.font(sub, 400);
            text.addView(sub);

            TextView status = new TextView(this);
            status.setTextSize(11);
            Ui.font(status, 600);
            if (isDone) {
                status.setText("已完成");
                status.setTextColor(Ui.ACCENT);
            } else if (isToday && !day.rest) {
                status.setText("今天");
                status.setTextColor(Ui.ACCENT);
                status.setBackground(Ui.round(this, Ui.ACCENT_SOFT, 10f));
                status.setPadding(Ui.dpi(this, 9), Ui.dpi(this, 4),
                        Ui.dpi(this, 9), Ui.dpi(this, 4));
            } else if (day.rest) {
                status.setText("恢复");
                status.setTextColor(Ui.INK_SOFT);
            } else {
                status.setText("");
            }
            row.addView(status);

            final int dayIdx = i;
            row.setOnClickListener(v -> openDay(dayIdx));
        }
    }

    private void openDay(int dayIndex) {
        Intent it = new Intent(this, DayDetailActivity.class);
        it.putExtra(DayDetailActivity.EXTRA_DAY, dayIndex);
        startActivity(it);
    }

    private void startWorkout(int dayIndex) {
        Intent it = new Intent(this, WorkoutActivity.class);
        it.putExtra(WorkoutActivity.EXTRA_DAY, dayIndex);
        startActivity(it);
    }
}
