package com.aijianshen.app;

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
import android.widget.Toast;

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

        if (savedInstanceState == null) {
            if (needsOnboard()) {
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                        startActivity(new Intent(this, OnboardActivity.class)), 350);
            } else if (!Store.hasProfile(this)) {
                new Handler(Looper.getMainLooper()).postDelayed(() ->
                        startActivity(new Intent(this, PlanActivity.class)
                                .putExtra(PlanActivity.EXTRA_ONBOARDING, true)), 350);
            }
        }
    }

    /**
     * 是否该走首次引导。
     *
     * <p>四个条件同时成立才算新用户：没走过引导、没填身高、没体重记录、没训练记录。
     * 这样装了新版但已经在用的老用户不会被突然弹一脸引导。
     */
    private boolean needsOnboard() {
        if (Store.onboardDone(this)) {
            return false;
        }
        boolean fresh = Store.heightCm(this) <= 0
                && Store.weights(this).isEmpty()
                && Store.sessions(this).isEmpty();
        if (!fresh) {
            // 已经有数据就是老用户，直接记成走过引导，省得每次启动重算
            Store.setOnboardDone(this);
        }
        return fresh;
    }

    private void bindHeader() {
        TextView title = findViewById(R.id.tv_title);
        title.setText("爱健身");
        Ui.font(title, 800);

        flameIcon = IconView.FLAME;
        LinearLayout chip = findViewById(R.id.streak_chip);
        chip.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT_SOFT, Ui.R_PILL), 0x22000000));
        IconView flame = findViewById(R.id.icon_flame);
        flame.setIcon(IconView.FLAME);
        flame.setColor(Ui.ACCENT);
        tvStreak = findViewById(R.id.tv_streak);
        tvStreak.setTextColor(Ui.ACCENT);
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
        more.setOnClickListener(v -> openChallenges());

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
        renderQuick();
        renderFocus();
        renderPlan();
    }

    /** 快捷入口：5 分钟微训练、我的训练、成就。 */
    private void renderQuick() {
        LinearLayout box = findViewById(R.id.quick_card);
        box.removeAllViews();
        LinearLayout card = UiList.card(this);
        box.addView(card);

        int tpl = Store.templates(this).size();
        UiList.row(this, card, IconView.BOLT, Ui.ACCENT, "5 分钟微训练",
                "随机 4 个徒手动作，一个动作 60 秒，不挑场地",
                false, v -> startQuick());
        UiList.row(this, card, IconView.DUMBBELL, Ui.ACCENT, "我的训练",
                tpl > 0 ? tpl + " 个自己编排的训练，点一下开练"
                        : "挑几个动作存成模板，以后一键开练", false,
                v -> startActivity(new Intent(this, TemplatesActivity.class)));
        UiList.row(this, card, IconView.TROPHY, Ui.ACCENT, "成就",
                "已点亮 " + Achievements.earned(this) + " 枚，看看下一个差多少", true,
                v -> startActivity(new Intent(this, AchievementsActivity.class)));
        box.setVisibility(View.VISIBLE);
    }

    /** 随机挑几个不用器械的力量/核心动作，凑一段五分钟。 */
    private void startQuick() {
        List<Exercise> pool = new java.util.ArrayList<>();
        for (Exercise e : Data.all()) {
            boolean bare = e.gear == null || e.gear.isEmpty();
            boolean strength = "力量".equals(e.kind) || "核心".equals(e.kind);
            if (bare && strength) {
                pool.add(e);
            }
        }
        java.util.Collections.shuffle(pool);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, pool.size()); i++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(pool.get(i).id);
        }
        Intent it = new Intent(this, WorkoutActivity.class);
        it.putExtra(WorkoutActivity.EXTRA_IDS, sb.toString());
        it.putExtra(WorkoutActivity.EXTRA_ROUNDS, 1);
        it.putExtra(WorkoutActivity.EXTRA_SECONDS, 60);
        it.putExtra(WorkoutActivity.EXTRA_REST, 15);
        it.putExtra(WorkoutActivity.EXTRA_TITLE, "5 分钟微训练");
        startActivity(it);
    }

    /** 断签状态下不给「0 天」，给最长纪录 + 今天就能做完的入口。 */
    private void renderStreak() {
        Store.Streak s = Store.streakInfo(this);
        int best = Store.bestStreak(this);
        if (s.days > 0) {
            tvStreak.setText(s.days + " 天");
        } else if (best > 0) {
            tvStreak.setText("最长 " + best + " 天");
        } else {
            // 从没练过时别写「开始」：那看着像主按钮，点进去却是连续页，容易误点。
            // chip 上配着火苗图标，写「0 天」语义更准。
            tvStreak.setText("0 天");
        }
    }

    /**
     * 3 分钟接上：优先用今天课表里的动作，让「接上」和计划对得上；
     * 具体拼装在 {@link Rescue}，连续页也用同一套。
     */
    private void startRescue() {
        Rescue.start(this);
    }

    private void renderWeekGoal() {
        int done = Store.weekDone(this, todayIndex);
        int target = Math.max(1, profile.days);
        tvWeekProgress.setText(done + "/" + target);

        weekStrip.removeAllViews();
        Calendar cal = Calendar.getInstance();
        Set<String> dates = Store.dates(this);
        Store.Streak streak = Store.streakInfo(this);
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
            boolean today = i == todayIndex;
            boolean ok = dates.contains(Store.key(d));
            // 宽限期内的空档：不是「没练」，是「还差一点就断了」，用浅灰点单独区分
            int back = todayIndex - i;
            boolean gap = !ok && back > 0 && back < streak.idle && !streak.broken();

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
            dot.setBackground(Ui.round(this,
                    ok ? Ui.ACCENT : (gap ? 0xFFBFC6D2 : 0x00000000), 3f));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 5), Ui.dpi(this, 5));
            dlp.topMargin = Ui.dpi(this, 4);
            col.addView(dot, dlp);
        }
    }

    private void renderCoach() {
        boolean doneToday = Store.dates(this).contains(Store.key(Calendar.getInstance()));
        Data.Day today = plan.get(todayIndex);
        Store.Pending pending = Store.pending(this);
        // 只要有没做完的存档就给入口。之前还要求「今天没练完」，
        // 结果是今天练过一次、又开了一次没做完的情况下，存档再也回不去了。
        boolean resume = pending != null;
        Store.Streak streak = Store.streakInfo(this);
        // 断签救援排在「今天练什么」前面：断了的人不缺计划，缺的是一个立刻能做完的动作
        boolean rescue = !resume && !doneToday && streak.absent > 0;

        LinearLayout coach = findViewById(R.id.coach_row);
        IconView coachIcon = findViewById(R.id.icon_coach);
        String copy;
        if (resume) {
            // 上次没练完：把这一条变成「继续」入口，比让他自己在计划里找更直接
            int left = Math.max(1, pending.ids.size() - pending.index);
            copy = "上次「" + pending.title + "」还差一点，点这里接着练（剩 " + left + " 步）";
            coachIcon.setIcon(IconView.PLAY);
        } else if (rescue) {
            String part = Store.lastPart(this);
            if (streak.broken()) {
                copy = "已经空了 " + streak.idle + " 天。不用从头算，动 3 分钟就回来了。";
            } else if (part != null) {
                copy = "上次练到" + part + "，今天 3 分钟就能接上，不算断。";
            } else {
                copy = "今天 3 分钟就能接上，不算断。";
            }
            coachIcon.setIcon(IconView.PLAY);
        } else if (doneToday) {
            copy = "今天已经练完了。做几组拉伸，给身体一点收尾。";
            coachIcon.setIcon(IconView.DUMBBELL);
        } else if (today.rest) {
            copy = "今天是恢复日。慢慢拉开肩背，明天状态会更好。";
            coachIcon.setIcon(IconView.DUMBBELL);
        } else if (!Store.hasProfile(this)) {
            copy = "先花半分钟告诉我你的目标，我来排好这一周。";
            coachIcon.setIcon(IconView.DUMBBELL);
        } else {
            copy = "欢迎回来！今天安排的是「" + today.title + "」，跟着节奏来就好。";
            coachIcon.setIcon(IconView.DUMBBELL);
        }
        coachIcon.setColor(Ui.ACCENT);
        tvCoach.setText(copy);
        if (resume) {
            coach.setOnClickListener(v -> startActivity(new Intent(this, WorkoutActivity.class)
                    .putExtra(WorkoutActivity.EXTRA_RESUME, true)));
        } else if (rescue) {
            coach.setOnClickListener(v -> startRescue());
        } else {
            coach.setOnClickListener(null);
        }
        coach.setClickable(resume || rescue);
    }

    /**
     * 挑战区：正在做的那一个占一张卡，直接显示第几周、本周几次、还剩几天；
     * 没开始的按目标列出来，一键就能开始。进度全部来自平时的训练记录，不用另外打卡。
     */
    private void renderChallenges() {
        challengeRow.removeAllViews();
        Store.ChallengeState st = Store.challengeState(this);
        Challenges.Def active = st.active() ? Challenges.byId(st.id) : null;
        if (active != null) {
            challengeRow.addView(
                    activeChallengeCard(active, Challenges.progress(this, active, st.start)),
                    challengeLp());
        }
        for (Challenges.Def d : Challenges.all()) {
            if (active != null && d.id.equals(active.id)) {
                continue;
            }
            challengeRow.addView(goalChallengeCard(d), challengeLp());
        }
    }

    private LinearLayout.LayoutParams challengeLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 300), Ui.dpi(this, 172));
        lp.rightMargin = Ui.dpi(this, 12);
        return lp;
    }

    /** 进行中：名字 + 第几周 + 本周几次 + 剩余天数 + 一周一格的进度条。 */
    private View activeChallengeCard(Challenges.Def d, Challenges.Progress pr) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.gradientCard(this, d.color, shade(d.color, 0.70f), Ui.R_HERO));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(v -> openChallenges());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 13);
        content.setPadding(p, p, p, p);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        boolean again = pr.done || pr.daysLeft == 0;
        TextView badge = new TextView(this);
        badge.setText(pr.done ? "已通关" : (again ? "已结束" : "进行中"));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11);
        badge.setIncludeFontPadding(false);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dpi(this, 9), 0, Ui.dpi(this, 9), 0);
        badge.setBackground(Ui.round(this, 0x33FFFFFF, 10f));
        Ui.font(badge, 700);
        content.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 20)));

        TextView title = new TextView(this);
        title.setText(d.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.font(title, 800);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dpi(this, 8);
        tlp.rightMargin = Ui.dpi(this, 62);
        content.addView(title, tlp);

        TextView meta = new TextView(this);
        meta.setText(progressLine(d, pr));
        meta.setTextColor(0xE6FFFFFF);
        meta.setTextSize(11.5f);
        meta.setIncludeFontPadding(false);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.font(meta, 500);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dpi(this, 5);
        mlp.rightMargin = Ui.dpi(this, 6);
        content.addView(meta, mlp);

        // 一周一格，够次数就点亮
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 6));
        blp.topMargin = Ui.dpi(this, 11);
        content.addView(bars, blp);
        for (int i = 0; i < d.weeks; i++) {
            View cell = new View(this);
            cell.setBackground(Ui.round(this,
                    i < pr.weeksDone ? 0xFFFFFFFF : 0x42FFFFFF, 3f));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                clp.leftMargin = Ui.dpi(this, 5);
            }
            bars.addView(cell, clp);
        }

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Runnable action = again ? this::restartChallenge : () -> practice(todayIndex);
        content.addView(again
                        ? ctaBar("再来一次", 0x33FFFFFF, Color.WHITE,
                        0x3DFFFFFF, Color.WHITE, action)
                        : ctaBar("练一次", 0xFFFFFFFF, Ui.INK,
                        Ui.ACCENT_SOFT, Ui.ACCENT, action),
                ctaLp());
        card.addView(tile(d, 48, 0x33FFFFFF), tileLp());
        return card;
    }

    /** 还没开始的挑战：目标规格 + 一句话说明 + 开始。 */
    private View goalChallengeCard(Challenges.Def d) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.roundStroke(this, Ui.SURFACE, Ui.R_HERO, Ui.LINE));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(v -> openChallenges());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 13);
        content.setPadding(p, p, p, p);
        card.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView badge = new TextView(this);
        badge.setText(d.spec());
        badge.setTextColor(d.color);
        badge.setTextSize(11);
        badge.setIncludeFontPadding(false);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dpi(this, 9), 0, Ui.dpi(this, 9), 0);
        badge.setBackground(Ui.round(this, Ui.withAlpha(d.color, 0.12f), 10f));
        Ui.font(badge, 700);
        content.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(this, 20)));

        TextView title = new TextView(this);
        title.setText(d.name);
        title.setTextColor(Ui.INK);
        title.setTextSize(21);
        title.setIncludeFontPadding(false);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.font(title, 800);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dpi(this, 8);
        tlp.rightMargin = Ui.dpi(this, 62);
        content.addView(title, tlp);

        TextView sub = new TextView(this);
        sub.setText(d.tagline);
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(11.5f);
        sub.setIncludeFontPadding(false);
        sub.setMaxLines(1);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.font(sub, 400);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dpi(this, 5);
        slp.rightMargin = Ui.dpi(this, 6);
        content.addView(sub, slp);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(ctaBar("开始挑战", d.color, Color.WHITE,
                        0x3DFFFFFF, Color.WHITE, () -> startChallenge(d)), ctaLp());
        card.addView(tile(d, 48, d.color), tileLp());
        return card;
    }

    /** 卡片右下角的通用按钮：左边文字，右边一个圆底箭头。 */
    private LinearLayout ctaBar(String label, int pill, int labelColor,
                                int arrowBg, int arrowColor, Runnable action) {
        LinearLayout cta = new LinearLayout(this);
        cta.setOrientation(LinearLayout.HORIZONTAL);
        cta.setGravity(Gravity.CENTER_VERTICAL);
        cta.setBackground(Ui.ripple(this, Ui.round(this, pill, 19f), 0x22000000));
        cta.setPadding(Ui.dpi(this, 15), 0, Ui.dpi(this, 5), 0);
        cta.setClickable(true);

        TextView go = new TextView(this);
        go.setText(label);
        go.setTextColor(labelColor);
        go.setTextSize(14);
        go.setIncludeFontPadding(false);
        go.setGravity(Gravity.CENTER);
        Ui.font(go, 700);
        cta.addView(go, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout dot = new FrameLayout(this);
        dot.setBackground(Ui.round(this, arrowBg, 14f));
        cta.addView(dot, new LinearLayout.LayoutParams(
                Ui.dpi(this, 28), Ui.dpi(this, 28)));
        IconView arrow = new IconView(this);
        arrow.setIcon(IconView.ARROW);
        arrow.setColor(arrowColor);
        dot.addView(arrow, new FrameLayout.LayoutParams(
                Ui.dpi(this, 14), Ui.dpi(this, 14), Gravity.CENTER));

        cta.setOnClickListener(v -> action.run());
        return cta;
    }

    private LinearLayout.LayoutParams ctaLp() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 38));
    }

    private FrameLayout.LayoutParams tileLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dpi(this, 48), Ui.dpi(this, 48));
        lp.gravity = Gravity.RIGHT | Gravity.TOP;
        lp.topMargin = Ui.dpi(this, 13);
        lp.rightMargin = Ui.dpi(this, 13);
        return lp;
    }

    private FrameLayout tile(Challenges.Def d, int sizeDp, int bg) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, bg, sizeDp * 0.31f));
        IconView ic = new IconView(this);
        ic.setIcon(d.icon);
        ic.setColor(Color.WHITE);
        int inner = Math.round(sizeDp * 0.52f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, inner), Ui.dpi(this, inner), Gravity.CENTER));
        return tile;
    }

    /** 「第 2/3 周 · 本周 2/3 次 · 还剩 12 天」。 */
    private String progressLine(Challenges.Def d, Challenges.Progress pr) {
        if (pr.done) {
            return "全部 " + d.weeks + " 周达成，共 " + (d.weeks * d.perWeek) + " 次训练";
        }
        if (pr.daysLeft == 0) {
            return d.weeks + " 周走完，有 " + (d.weeks - pr.weeksDone) + " 周没达标";
        }
        return "第 " + (pr.currentWeek + 1) + "/" + d.weeks + " 周 · 本周 "
                + Math.min(pr.thisWeek, d.perWeek) + "/" + d.perWeek + " 次 · 还剩 "
                + pr.daysLeft + " 天";
    }

    /** 挑战卡上的「练一次」：今天有课就直接开练，休息日先看拉伸清单。 */
    private void practice(int dayIndex) {
        if (plan.get(dayIndex).rest) {
            openDay(dayIndex);
        } else {
            startWorkout(dayIndex);
        }
    }

    private void startChallenge(Challenges.Def d) {
        Store.startChallenge(this, d.id, System.currentTimeMillis());
        Toast.makeText(this, "已开始「" + d.name + "」", Toast.LENGTH_SHORT).show();
        renderChallenges();
    }

    private void restartChallenge() {
        Challenges.Def d = Challenges.byId(Store.challengeState(this).id);
        if (d != null) {
            Store.startChallenge(this, d.id, System.currentTimeMillis());
            Toast.makeText(this, "重新开始「" + d.name + "」", Toast.LENGTH_SHORT).show();
            renderChallenges();
        }
    }

    private void openChallenges() {
        startActivity(new Intent(this, ChallengeActivity.class));
    }

    /** 渐变深端：把颜色按比例压暗，用来做同色系的右端。 */
    private static int shade(int color, float factor) {
        int r = Math.min(255, Math.round(Color.red(color) * factor));
        int g = Math.min(255, Math.round(Color.green(color) * factor));
        int b = Math.min(255, Math.round(Color.blue(color) * factor));
        return Color.rgb(r, g, b);
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
