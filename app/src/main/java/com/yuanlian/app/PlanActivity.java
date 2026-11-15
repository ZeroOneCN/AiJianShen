package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;

/** 分步定制向导：目标、重点、水平、频率、时长、器械。 */
public class PlanActivity extends Activity {

    public static final String EXTRA_ONBOARDING = "onboarding";

    private static final int STEP_GOAL = 0;
    private static final int STEP_FOCUS = 1;
    private static final int STEP_LEVEL = 2;
    private static final int STEP_DAYS = 3;
    private static final int STEP_MINUTES = 4;
    private static final int STEP_GEAR = 5;
    private static final int STEPS = 6;

    private static final String[][] SECTIONS = {
            {"01", "目标与重点部位"},
            {"01", "目标与重点部位"},
            {"02", "身体信息"},
            {"03", "时间安排"},
            {"03", "时间安排"},
            {"04", "训练条件"},
    };

    private static final String[] QUESTION = {
            "你这次最想改变什么？",
            "重点想练哪些部位？",
            "你的训练经验是？",
            "每周想练几次？",
            "每次留多少时间？",
            "你能用哪些器械？"
    };

    private PlanProfile profile;
    private int step = 0;
    private LinearLayout body;
    private LinearLayout progress;
    private TextView btnNext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_plan);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        profile = Store.profile(this).copy();
        body = findViewById(R.id.body);
        progress = findViewById(R.id.progress);
        btnNext = findViewById(R.id.btn_next);
        Ui.font(btnNext, 700);

        IconView back = findViewById(R.id.icon_back);
        back.setIcon(IconView.BACK);
        back.setColor(Ui.INK);
        findViewById(R.id.btn_back).setOnClickListener(v -> onBack());

        TextView no = findViewById(R.id.tv_section_no);
        Ui.font(no, 700);
        TextView title = findViewById(R.id.tv_section_title);
        Ui.font(title, 600);

        btnNext.setOnClickListener(v -> onNext());
        renderStep();
    }

    private void renderStep() {
        String[] section = SECTIONS[step];
        ((TextView) findViewById(R.id.tv_section_no)).setText(section[0]);
        ((TextView) findViewById(R.id.tv_section_title)).setText(section[1]);
        renderProgress();

        btnNext.setText(step == STEPS - 1 ? "生成我的计划" : "下一步");
        btnNext.setBackground(Ui.ripple(this,
                Ui.gradientBluePill(this),
                0x33FFFFFF));

        body.removeAllViews();
        TextView q = new TextView(this);
        q.setText(QUESTION[step]);
        q.setTextColor(Ui.INK);
        q.setTextSize(27);
        q.setGravity(Gravity.CENTER);
        q.setLineSpacing(Ui.dp(this, 2), 1f);
        Ui.font(q, 700);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = Ui.dpi(this, 6);
        qlp.bottomMargin = Ui.dpi(this, 22);
        body.addView(q, qlp);

        switch (step) {
            case STEP_GOAL:
                renderGoal();
                break;
            case STEP_FOCUS:
                renderFocus();
                break;
            case STEP_LEVEL:
                renderLevel();
                break;
            case STEP_DAYS:
                renderDays();
                break;
            case STEP_MINUTES:
                renderMinutes();
                break;
            case STEP_GEAR:
                renderGear();
                break;
            default:
                break;
        }

        body.startAnimation(android.view.animation.AnimationUtils.loadAnimation(
                this, android.R.anim.fade_in));
    }

    private void renderProgress() {
        progress.removeAllViews();
        for (int i = 0; i < STEPS; i++) {
            View seg = new View(this);
            seg.setBackground(Ui.round(this, i <= step ? Ui.ACCENT : 0xFFE9ECF1, 2f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.leftMargin = i == 0 ? 0 : Ui.dpi(this, 2);
            lp.rightMargin = i == STEPS - 1 ? 0 : Ui.dpi(this, 2);
            progress.addView(seg, lp);
        }
    }

    private void renderGoal() {
        for (int i = 0; i < PlanProfile.GOALS.length; i++) {
            final int idx = i;
            addOptionCard(PlanProfile.GOALS[i], PlanProfile.GOAL_HINTS[i],
                    goalIcon(i), goalColor(i), profile.goal == i, v -> {
                        profile.goal = idx;
                        renderStep();
                        handler.postDelayed(this::autoNext, 170);
                    });
        }
        addInfo("目标决定动作类型与节奏，随时可以调整。");
    }

    private void renderLevel() {
        String[] subs = {
                "刚开始，先建立稳定的训练习惯",
                "练过一阵，能跟上连续的动作节奏",
                "动作熟练，想要更强的刺激"
        };
        for (int i = 0; i < PlanProfile.LEVELS.length; i++) {
            final int idx = i;
            addOptionCard(PlanProfile.LEVELS[i], subs[i],
                    i == 0 ? IconView.USER : i == 1 ? IconView.CHART : IconView.TROPHY,
                    i == 0 ? Ui.GREEN : i == 1 ? Ui.ACCENT : Ui.PURPLE,
                    profile.level == i, v -> {
                        profile.level = idx;
                        renderStep();
                        handler.postDelayed(this::autoNext, 170);
                    });
        }
        addInfo("新手动作 30 秒、进阶 40 秒、高手 50 秒；组间休息也会按目标调整。");
    }

    private void renderFocus() {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        body.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = null;
        for (int i = 0; i < PlanProfile.FOCUS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rlp.topMargin = Ui.dpi(this, 9);
                }
                grid.addView(row, rlp);
            }
            final int idx = i;
            View cell = focusCell(PlanProfile.FOCUS[i], focusIcon(i), focusColor(i),
                    profile.focus[i], v -> toggleFocus(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 0) {
                lp.rightMargin = Ui.dpi(this, 5);
            } else {
                lp.leftMargin = Ui.dpi(this, 5);
            }
            row.addView(cell, lp);
        }
        addInfo("可以多选；选择「全身」会按完整动作池安排。");
    }

    private void toggleFocus(int idx) {
        if (idx == 0) {
            for (int i = 0; i < profile.focus.length; i++) {
                profile.focus[i] = i == 0;
            }
        } else {
            profile.focus[idx] = !profile.focus[idx];
            boolean any = false;
            for (int i = 1; i < profile.focus.length; i++) {
                any |= profile.focus[i];
            }
            profile.focus[0] = !any;
        }
        renderStep();
    }

    private void renderDays() {
        int[] values = PlanProfile.DAY_VALUES;
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == profile.days) {
                index = i;
            }
        }
        addPicker(IconView.CALENDAR, Ui.BLUE_TILE,
                profile.days + " 天", "每周训练",
                values.length, index, "偶尔", "更多",
                daysHint(profile.days), i -> {
                    profile.days = values[i];
                    return profile.days + " 天";
                });
        addInfo("训练日会尽量分散，避免连续两天练同一部位。");
    }

    private void renderMinutes() {
        int[] values = PlanProfile.MINUTE_VALUES;
        int index = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == profile.minutes) {
                index = i;
            }
        }
        addPicker(IconView.CLOCK, Ui.ORANGE,
                profile.minutes + " 分钟", "每次训练",
                values.length, index, "短一点", "充足",
                "按轮次凑到目标时长附近，不会硬拖时间。", i -> {
                    profile.minutes = values[i];
                    return profile.minutes + " 分钟";
                });
        addInfo("10 到 30 分钟都能安排，核心是动作质量和持续性。");
    }

    private void renderGear() {
        int[] bits = {PlanProfile.GEAR_BODY, PlanProfile.GEAR_HOME,
                PlanProfile.GEAR_BAR, PlanProfile.GEAR_GYM};
        String[] subs = {"始终保留，不需要任何器械",
                "家里常见的小器械", "单杠、双杠或长凳", "完整器械动作池"};
        int[] icons = {IconView.USER, IconView.DUMBBELL, IconView.RACK, IconView.TROPHY};
        int[] colors = {Ui.GREEN, Ui.BLUE_TILE, Ui.ORANGE, Ui.PURPLE};
        for (int i = 0; i < PlanProfile.GEAR_OPTIONS.length; i++) {
            final int idx = i;
            boolean on = (profile.gearMask & bits[i]) != 0;
            addOptionCard(PlanProfile.GEAR_OPTIONS[i], subs[i], icons[i], colors[i], on, v -> {
                if (idx == 0) {
                    return;
                }
                profile.gearMask ^= bits[idx];
                if (profile.gearMask == 0) {
                    profile.gearMask = PlanProfile.GEAR_BODY;
                }
                renderStep();
            });
        }
        addInfo("徒手动作始终保留，多选会扩大动作池。");
    }

    private void addOptionCard(String title, String sub, int icon, int color,
                               boolean selected, View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 13),
                Ui.dpi(this, 14), Ui.dpi(this, 13));
        card.setBackground(selected
                ? Ui.roundStroke(this, Ui.SURFACE, Ui.R_CARD, Ui.ACCENT)
                : Ui.round(this, Ui.SURFACE, Ui.R_CARD));
        card.setClickable(true);
        card.setOnClickListener(click);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dpi(this, 10);
        body.addView(card, lp);

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, Ui.withAlpha(color, 0.13f), 10f));
        card.addView(tile, new LinearLayout.LayoutParams(
                Ui.dpi(this, 38), Ui.dpi(this, 38)));
        IconView ic = new IconView(this);
        ic.setIcon(icon);
        ic.setColor(color);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 21), Ui.dpi(this, 21), Gravity.CENTER));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 13);
        card.addView(texts, tlp);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(selected ? Ui.ACCENT : Ui.INK);
        name.setTextSize(17);
        Ui.font(name, 600);
        texts.addView(name);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Ui.INK_SOFT);
        s.setTextSize(12);
        s.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(s, 400);
        texts.addView(s);

        FrameLayout check = new FrameLayout(this);
        check.setBackground(selected
                ? Ui.round(this, Ui.ACCENT, 11f)
                : Ui.roundStroke(this, 0x00000000, 11f, 0xFFD5D8DE));
        card.addView(check, new LinearLayout.LayoutParams(
                Ui.dpi(this, 22), Ui.dpi(this, 22)));
        if (selected) {
            IconView tick = new IconView(this);
            tick.setIcon(IconView.CHECK);
            tick.setColor(Color.WHITE);
            tick.setStrokeDp(2.4f);
            check.addView(tick, new FrameLayout.LayoutParams(
                    Ui.dpi(this, 13), Ui.dpi(this, 13), Gravity.CENTER));
        }
    }

    private View focusCell(String label, int icon, int color, boolean selected,
                           View.OnClickListener click) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        cell.setGravity(Gravity.CENTER_VERTICAL);
        cell.setPadding(Ui.dpi(this, 13), Ui.dpi(this, 14),
                Ui.dpi(this, 13), Ui.dpi(this, 14));
        cell.setBackground(selected
                ? Ui.roundStroke(this, Ui.SURFACE, Ui.R_CARD, Ui.ACCENT)
                : Ui.round(this, Ui.SURFACE, Ui.R_CARD));
        cell.setClickable(true);
        cell.setOnClickListener(click);

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, Ui.withAlpha(color, 0.13f), 9f));
        cell.addView(tile, new LinearLayout.LayoutParams(
                Ui.dpi(this, 32), Ui.dpi(this, 32)));
        IconView ic = new IconView(this);
        ic.setIcon(icon);
        ic.setColor(color);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 18), Ui.dpi(this, 18), Gravity.CENTER));

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(selected ? Ui.ACCENT : Ui.INK);
        name.setTextSize(15);
        name.setPadding(Ui.dpi(this, 9), 0, 0, 0);
        Ui.font(name, 600);
        cell.addView(name);
        return cell;
    }

    private interface ValueChanged {
        String onChanged(int index);
    }

    private void addPicker(int icon, int color, String initial, String caption,
                           int count, int index, String left, String right,
                           String hint, ValueChanged changed) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        int p = Ui.dpi(this, 18);
        card.setPadding(p, p, p, Ui.dpi(this, 14));
        body.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, Ui.withAlpha(color, 0.13f), 26f));
        card.addView(tile, new LinearLayout.LayoutParams(
                Ui.dpi(this, 52), Ui.dpi(this, 52)));
        IconView ic = new IconView(this);
        ic.setIcon(icon);
        ic.setColor(color);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 28), Ui.dpi(this, 28), Gravity.CENTER));

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams vrlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vrlp.topMargin = Ui.dpi(this, 10);
        card.addView(valueRow, vrlp);

        TextView value = new TextView(this);
        value.setText(initial);
        value.setTextColor(Ui.INK);
        value.setTextSize(38);
        value.setIncludeFontPadding(false);
        Ui.font(value, 800);
        valueRow.addView(value);

        TextView cap = new TextView(this);
        cap.setText(caption);
        cap.setTextColor(Ui.INK_SOFT);
        cap.setTextSize(13);
        cap.setPadding(Ui.dpi(this, 8), 0, 0, 0);
        Ui.font(cap, 400);
        valueRow.addView(cap);

        RulerSlider slider = new RulerSlider(this);
        slider.setCount(count);
        slider.setIndex(index);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 52));
        slp.topMargin = Ui.dpi(this, 4);
        card.addView(slider, slp);
        slider.setOnIndexChanged(i -> value.setText(changed.onChanged(i)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(labels, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView l = new TextView(this);
        l.setText(left);
        l.setTextColor(Ui.INK_SOFT);
        l.setTextSize(12);
        Ui.font(l, 400);
        labels.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView r = new TextView(this);
        r.setText(right);
        r.setTextColor(Ui.INK_SOFT);
        r.setTextSize(12);
        Ui.font(r, 400);
        labels.addView(r);

        TextView h = new TextView(this);
        h.setText(hint);
        h.setTextColor(Ui.INK_SOFT);
        h.setTextSize(12.5f);
        h.setGravity(Gravity.CENTER);
        h.setLineSpacing(Ui.dp(this, 2), 1f);
        h.setPadding(0, Ui.dpi(this, 10), 0, 0);
        Ui.font(h, 400);
        card.addView(h);
    }

    private void addInfo(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.round(this, Ui.CARD_SOFT, 16f));
        int p = Ui.dpi(this, 14);
        row.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 10);
        body.addView(row, lp);

        IconView icon = new IconView(this);
        icon.setIcon(IconView.BOLT);
        icon.setColor(Ui.ACCENT);
        row.addView(icon, new LinearLayout.LayoutParams(
                Ui.dpi(this, 20), Ui.dpi(this, 20)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Ui.INK_MUTED);
        tv.setTextSize(12.5f);
        tv.setLineSpacing(Ui.dp(this, 2), 1f);
        tv.setPadding(Ui.dpi(this, 10), 0, 0, 0);
        Ui.font(tv, 400);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void autoNext() {
        if (step == STEP_GOAL || step == STEP_LEVEL) {
            if (step < STEPS - 1) {
                step++;
                renderStep();
            }
        }
    }

    private void onNext() {
        if (step < STEPS - 1) {
            step++;
            renderStep();
        } else {
            finishPlan();
        }
    }

    private void onBack() {
        if (step > 0) {
            step--;
            renderStep();
        } else {
            finish();
        }
    }

    private void finishPlan() {
        Store.saveProfile(this, profile);
        showResult();
    }

    private void showResult() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 26f));
        int p = Ui.dpi(this, 24);
        root.setPadding(p, p, p, p);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(getDrawable(R.drawable.bg_soft_circle));
        root.addView(badge, new LinearLayout.LayoutParams(
                Ui.dpi(this, 70), Ui.dpi(this, 70)));
        IconView ic = new IconView(this);
        ic.setIcon(IconView.TROPHY);
        ic.setColor(Ui.ACCENT);
        ic.setStrokeDp(2f);
        badge.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 36), Ui.dpi(this, 36), Gravity.CENTER));

        TextView title = new TextView(this);
        title.setText("这一周，排好了");
        title.setTextColor(Ui.INK);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        Ui.font(title, 800);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dpi(this, 16);
        root.addView(title, tlp);

        TextView summary = new TextView(this);
        summary.setText("目标 · " + profile.goalLabel()
                + "\n每周 " + profile.days + " 练 · 每次 " + profile.minutes + " 分钟"
                + "\n" + focusSummary() + " · " + profile.gearSummary()
                + "\n\n计划只保存在这台设备上，随时可以回来调整。");
        summary.setTextColor(Ui.INK_MUTED);
        summary.setTextSize(13.5f);
        summary.setGravity(Gravity.CENTER);
        summary.setLineSpacing(Ui.dp(this, 4), 1f);
        Ui.font(summary, 400);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dpi(this, 10);
        root.addView(summary, slp);

        TextView start = new TextView(this);
        start.setText("开始今天的训练");
        start.setTextColor(Ui.SURFACE);
        start.setTextSize(16);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        Ui.font(start, 700);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 52));
        stlp.topMargin = Ui.dpi(this, 20);
        root.addView(start, stlp);
        start.setOnClickListener(v -> {
            dialog.dismiss();
            Intent it = new Intent(this, WorkoutActivity.class);
            it.putExtra(WorkoutActivity.EXTRA_DAY, Data.todayIndex(Calendar.getInstance()));
            startActivity(it);
            finish();
        });

        TextView remind = new TextView(this);
        remind.setText("设置训练提醒");
        remind.setTextColor(Ui.ACCENT);
        remind.setTextSize(14);
        remind.setGravity(Gravity.CENTER);
        remind.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT_SOFT, 24f),
                0x22000000));
        Ui.font(remind, 600);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 46));
        rlp.topMargin = Ui.dpi(this, 10);
        root.addView(remind, rlp);
        remind.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, ReminderActivity.class));
        });

        TextView later = new TextView(this);
        later.setText("稍后再练");
        later.setTextColor(Ui.INK_SOFT);
        later.setTextSize(14);
        later.setGravity(Gravity.CENTER);
        Ui.font(later, 500);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 44));
        llp.topMargin = Ui.dpi(this, 4);
        root.addView(later, llp);
        later.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        dialog.show();
        if (w != null) {
            w.setLayout(getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 56),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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
        return sb.length() == 0 ? "全身" : sb.toString();
    }

    private String daysHint(int days) {
        if (days <= 2) {
            return "隔几天动一次，先把习惯接上。";
        }
        if (days <= 4) {
            return "有训练也有恢复，适合大多数人的节奏。";
        }
        return "训练频率偏高，注意睡眠和恢复。";
    }

    private int goalIcon(int goal) {
        switch (goal) {
            case 0:
                return IconView.FLAME;
            case 1:
                return IconView.DUMBBELL;
            case 2:
                return IconView.TARGET;
            default:
                return IconView.STRETCH;
        }
    }

    private int goalColor(int goal) {
        switch (goal) {
            case 0:
                return Ui.RED;
            case 1:
                return Ui.ACCENT;
            case 2:
                return Ui.GREEN;
            default:
                return Ui.TEAL;
        }
    }

    private int focusIcon(int focus) {
        switch (focus) {
            case 0:
                return IconView.USER;
            case 1:
                return IconView.BOLT;
            case 2:
                return IconView.LEGS;
            case 3:
                return IconView.DUMBBELL;
            case 4:
                return IconView.ARM;
            default:
                return IconView.CARDIO;
        }
    }

    private int focusColor(int focus) {
        switch (focus) {
            case 0:
                return Ui.ACCENT;
            case 1:
                return Ui.ORANGE;
            case 2:
                return Ui.GREEN;
            case 3:
                return Ui.PURPLE;
            case 4:
                return Ui.TEAL;
            default:
                return Ui.RED;
        }
    }
}
