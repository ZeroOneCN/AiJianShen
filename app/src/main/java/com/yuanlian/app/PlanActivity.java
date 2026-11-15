package com.yuanlian.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 按用户条件生成一周计划：目标 / 水平 / 频率 / 时长 / 器械 / 重点部位。 */
public class PlanActivity extends Activity {

    private static final int G_GOAL = 0;
    private static final int G_LEVEL = 1;
    private static final int G_DAYS = 2;
    private static final int G_MINUTES = 3;
    private static final int G_GEAR = 4;
    private static final int G_FOCUS = 5;

    private static final String[] WEEK_LABEL = {"一", "二", "三", "四", "五", "六", "日"};

    private PlanProfile profile;
    private LinearLayout body;
    private TextView tvGoalHint;
    private TextView tvSummary;
    private final List<Chip> chips = new ArrayList<>();

    private static class Chip {
        final TextView view;
        final int group;
        final int index;

        Chip(TextView view, int group, int index) {
            this.view = view;
            this.group = group;
            this.index = index;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_plan);

        profile = Store.profile(this).copy();
        body = findViewById(R.id.plan_body);
        tvSummary = findViewById(R.id.tv_summary);
        TextView generate = findViewById(R.id.btn_generate);

        Ui.font(tvSummary, 400);
        Ui.font(generate, 700);
        generate.setBackground(Ui.ripple(this, Ui.gradientPill(this), 0x33FFFFFF));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_reset).setOnClickListener(v -> {
            profile = PlanProfile.defaults();
            buildBody();
            updateSummary();
        });
        generate.setOnClickListener(v -> {
            Store.saveProfile(this, profile);
            Toast.makeText(this, "已生成：每周 " + profile.days + " 练 · "
                    + profile.goalLabel(), Toast.LENGTH_SHORT).show();
            finish();
        });

        buildBody();
        updateSummary();
    }

    private void buildBody() {
        body.removeAllViews();
        chips.clear();
        tvGoalHint = null;

        addSection("你的目标", "决定动作类型与节奏", PlanProfile.GOALS, G_GOAL, true);
        addSection("训练水平", "新手 30 秒 · 进阶 40 秒 · 高手 50 秒",
                PlanProfile.LEVELS, G_LEVEL, true);
        addSection("每周训练", "训练日分散排开，其余安排拉伸恢复",
                PlanProfile.DAY_OPTIONS, G_DAYS, true);
        addSection("每次时长", "按轮次凑到目标时长附近",
                PlanProfile.MINUTE_OPTIONS, G_MINUTES, true);
        addSection("可用器械", "徒手动作始终保留，可多选",
                PlanProfile.GEAR_OPTIONS, G_GEAR, false);
        addSection("重点部位", "全身 = 不限制；也可以只练几个部位",
                PlanProfile.FOCUS, G_FOCUS, false);
        paintChips();
    }

    private void addSection(String title, String subtitle, String[] options,
                            int group, boolean single) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, Ui.R_CARD));
        int p = Ui.dpi(this, 18);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dpi(this, 12);
        body.addView(card, lp);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextColor(Ui.INK);
        head.setTextSize(15);
        Ui.font(head, 600);
        card.addView(head);

        TextView hint = new TextView(this);
        hint.setText(subtitle);
        hint.setTextColor(Ui.INK_SOFT);
        hint.setTextSize(12);
        hint.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(hint, 400);
        card.addView(hint);
        if (group == G_GOAL) {
            tvGoalHint = hint;
        }

        FlowLayout flow = new FlowLayout(this);
        flow.setGap(7, 8);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.dpi(this, 12);
        card.addView(flow, flp);

        for (int i = 0; i < options.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(options[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            int lr = Ui.dpi(this, 12);
            int tb = Ui.dpi(this, 9);
            chip.setPadding(lr, tb, lr, tb);
            Ui.font(chip, 600);

            FlowLayout.LayoutParams clp = new FlowLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flow.addView(chip, clp);

            final int idx = i;
            chip.setOnClickListener(v -> onChip(group, idx));
            chips.add(new Chip(chip, group, idx));
        }
    }

    private void onChip(int group, int index) {
        switch (group) {
            case G_GOAL:
                profile.goal = index;
                break;
            case G_LEVEL:
                profile.level = index;
                break;
            case G_DAYS:
                profile.days = PlanProfile.DAY_VALUES[index];
                break;
            case G_MINUTES:
                profile.minutes = PlanProfile.MINUTE_VALUES[index];
                break;
            case G_GEAR:
                if (index == 0) {
                    return; // 徒手始终保留
                }
                int bit = index == 1 ? PlanProfile.GEAR_HOME
                        : index == 2 ? PlanProfile.GEAR_BAR : PlanProfile.GEAR_GYM;
                profile.gearMask ^= bit;
                break;
            case G_FOCUS:
                if (index == 0) {
                    for (int i = 0; i < profile.focus.length; i++) {
                        profile.focus[i] = i == 0;
                    }
                } else {
                    profile.focus[index] = !profile.focus[index];
                    boolean any = false;
                    for (int i = 1; i < profile.focus.length; i++) {
                        any |= profile.focus[i];
                    }
                    profile.focus[0] = !any;
                }
                break;
        }
        paintChips();
        updateSummary();
    }

    private boolean selected(Chip chip) {
        switch (chip.group) {
            case G_GOAL:
                return profile.goal == chip.index;
            case G_LEVEL:
                return profile.level == chip.index;
            case G_DAYS:
                return profile.days == PlanProfile.DAY_VALUES[chip.index];
            case G_MINUTES:
                return profile.minutes == PlanProfile.MINUTE_VALUES[chip.index];
            case G_GEAR:
                if (chip.index == 0) {
                    return true;
                }
                int bit = chip.index == 1 ? PlanProfile.GEAR_HOME
                        : chip.index == 2 ? PlanProfile.GEAR_BAR : PlanProfile.GEAR_GYM;
                return (profile.gearMask & bit) != 0;
            case G_FOCUS:
                return profile.focus[chip.index];
            default:
                return false;
        }
    }

    private void paintChips() {
        for (Chip chip : chips) {
            boolean on = selected(chip);
            chip.view.setTextColor(on ? Ui.SURFACE : Ui.INK_MUTED);
            chip.view.setBackground(on
                    ? Ui.round(this, Ui.ACCENT, 20f)
                    : Ui.round(this, Ui.BG, 20f));
        }
        if (tvGoalHint != null) {
            tvGoalHint.setText(PlanProfile.GOAL_HINTS[profile.goal]);
        }
    }

    private void updateSummary() {
        Data.Day first = null;
        for (Data.Day d : Data.weekPlan(profile)) {
            if (!d.rest) {
                first = d;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(profile.goalLabel()).append(" · ").append(profile.levelLabel())
                .append(" · 每周 ").append(profile.days).append(" 练 · 每次 ")
                .append(profile.minutes).append(" 分钟");
        sb.append("\n重点 ").append(focusSummary()).append(" · ").append(profile.gearSummary());
        if (first != null) {
            sb.append(" · ").append(first.count()).append(" 个动作 × ")
                    .append(first.rounds).append(" 轮 · 约 ").append(first.minutes()).append(" 分钟");
        }
        tvSummary.setText(sb.toString());
    }

    private String focusSummary() {
        if (profile.focusAll()) {
            return "全身";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < PlanProfile.FOCUS.length; i++) {
            if (profile.focus[i]) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(PlanProfile.FOCUS[i]);
            }
        }
        return sb.toString();
    }
}
