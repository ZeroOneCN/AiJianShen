package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/** 单日计划详情：大图、统计、开始按钮与动作列表。 */
public class DayDetailActivity extends Activity {

    public static final String EXTRA_DAY = "day_index";

    private Data.Day day;
    private int dayIndex;
    private PhotoView hero;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_day_detail);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        PlanProfile profile = Store.profile(this);
        dayIndex = getIntent().getIntExtra(EXTRA_DAY, Data.todayIndex(Calendar.getInstance()));
        dayIndex = ((dayIndex % 7) + 7) % 7;
        List<Data.Day> plan = Data.weekPlan(profile);
        day = Data.withOverrides(plan.get(dayIndex), Store.overrides(this, dayIndex));

        hero = findViewById(R.id.hero_photo);
        hero.setRadiusDp(0);
        hero.setAnimated(true);
        if (!day.exercises.isEmpty()) {
            hero.bind(day.exercises.get(0));
        }

        FrameLayout back = findViewById(R.id.btn_back);
        back.setOnClickListener(v -> finish());
        IconView backIcon = findViewById(R.id.icon_back);
        backIcon.setIcon(IconView.BACK);
        backIcon.setColor(Ui.INK);

        FrameLayout menu = findViewById(R.id.btn_menu);
        menu.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
        IconView menuIcon = findViewById(R.id.icon_menu);
        menuIcon.setIcon(IconView.PENCIL);
        menuIcon.setColor(Ui.INK);

        TextView title = findViewById(R.id.tv_day);
        title.setText("第 " + (dayIndex + 1) + " 天");
        Ui.font(title, 800);

        TextView focus = findViewById(R.id.tv_focus);
        focus.setText((day.rest ? "恢复日 · " : profile.goalLabel() + "计划 · ") + day.focus);
        Ui.font(focus, 400);

        IconView clock = findViewById(R.id.icon_clock);
        clock.setIcon(IconView.CLOCK);
        clock.setColor(Ui.INK_MUTED);
        TextView minutes = findViewById(R.id.tv_minutes);
        minutes.setText("约 " + day.minutes() + " 分钟");
        Ui.font(minutes, 500);

        IconView flame = findViewById(R.id.icon_flame);
        flame.setIcon(IconView.FLAME);
        flame.setColor(Ui.ORANGE);
        TextView count = findViewById(R.id.tv_count);
        count.setText(day.count() + " 个动作" + (day.rounds > 1 ? " · " + day.rounds + " 轮" : ""));
        Ui.font(count, 500);

        TextView start = findViewById(R.id.btn_start);
        start.setBackground(Ui.ripple(this,
                Ui.gradientCard(this, 0xFF00C8FF, Ui.ACCENT, Ui.R_PILL), 0x33FFFFFF));
        Ui.font(start, 700);
        start.setOnClickListener(v -> startDay());

        TextView edit = findViewById(R.id.btn_edit);
        Ui.font(edit, 600);
        edit.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));

        list = findViewById(R.id.list);
        renderExercises();

        IconView tipIcon = findViewById(R.id.icon_tip);
        tipIcon.setIcon(IconView.BOLT);
        tipIcon.setColor(Ui.ACCENT);
        TextView tip = findViewById(R.id.tv_tip);
        tip.setText(day.rounds > 1
                ? "动作之间休息 " + day.restSeconds + " 秒，共 " + day.rounds
                + " 轮。跟不上就少做一轮，动作标准比速度重要。"
                : "把呼吸放慢，在能保持姿势的范围内完成每个动作。");
        Ui.font(tip, 400);
    }

    private void renderExercises() {
        list.removeAllViews();
        for (int i = 0; i < day.exercises.size(); i++) {
            list.addView(row(day.exercises.get(i), i == day.exercises.size() - 1));
        }
        if (!day.exercises.isEmpty()) {
            hero.bind(day.exercises.get(0));
        }
    }

    private void replace(Exercise e) {
        ReplaceSheet.show(this, e, pick -> {
            Store.setOverride(this, dayIndex, e.id, pick.id);
            day = Data.withOverrides(Data.weekPlan(Store.profile(this)).get(dayIndex),
                    Store.overrides(this, dayIndex));
            renderExercises();
            TextView count = findViewById(R.id.tv_count);
            count.setText(day.count() + " 个动作"
                    + (day.rounds > 1 ? " · " + day.rounds + " 轮" : ""));
            android.widget.Toast.makeText(this,
                    "已换成 " + pick.name + "，本周都按这个动作",
                    android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    private View row(Exercise e, boolean last) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.ripple(this, Ui.round(this, Ui.SURFACE, 0f), 0x14000000));
        int p = Ui.dpi(this, 12);
        row.setPadding(Ui.dpi(this, 2), p, Ui.dpi(this, 2), p);
        row.setClickable(true);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView drag = new TextView(this);
        drag.setText("≡");
        drag.setTextColor(0xFFC9CDD4);
        drag.setTextSize(19);
        drag.setGravity(Gravity.CENTER);
        row.addView(drag, new LinearLayout.LayoutParams(
                Ui.dpi(this, 22), ViewGroup.LayoutParams.MATCH_PARENT));

        PhotoView photo = new PhotoView(this);
        photo.setRadiusDp(14f);
        photo.setAnimated(false);
        photo.bind(e);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 84), Ui.dpi(this, 84));
        plp.leftMargin = Ui.dpi(this, 6);
        row.addView(photo, plp);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 14);
        row.addView(text, tlp);

        TextView name = new TextView(this);
        name.setText(e.name);
        name.setTextColor(Ui.INK);
        name.setTextSize(18.5f);
        name.setMaxLines(2);
        Ui.font(name, 700);
        text.addView(name);

        TextView sub = new TextView(this);
        sub.setText(day.seconds + " 秒"
                + (e.target.isEmpty() ? "" : " · " + e.target));
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dpi(this, 4), 0, 0);
        Ui.font(sub, 400);
        text.addView(sub);

        TextView change = new TextView(this);
        change.setText("换");
        change.setTextColor(Ui.ACCENT);
        change.setTextSize(12);
        change.setGravity(Gravity.CENTER);
        change.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT_SOFT, 18f),
                0x22000000));
        Ui.font(change, 600);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 36), Ui.dpi(this, 36));
        chlp.rightMargin = Ui.dpi(this, 6);
        row.addView(change, chlp);
        change.setOnClickListener(v -> replace(e));

        IconView chevron = new IconView(this);
        chevron.setIcon(IconView.CHEVRON);
        chevron.setColor(0xFFC9CDD4);
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dpi(this, 18), Ui.dpi(this, 18)));

        row.setOnClickListener(v -> {
            Intent it = new Intent(this, WorkoutActivity.class);
            it.putExtra(WorkoutActivity.EXTRA_SINGLE, e.name);
            startActivity(it);
        });

        View divider = new View(this);
        divider.setBackgroundColor(0xFFEFEFF2);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 1)));
        dlp.leftMargin = Ui.dpi(this, 112);
        wrap.addView(divider, dlp);
        if (last) {
            divider.setVisibility(View.GONE);
        }
        return wrap;
    }

    private void startDay() {
        Intent it = new Intent(this, WorkoutActivity.class);
        it.putExtra(WorkoutActivity.EXTRA_DAY, dayIndex);
        startActivity(it);
    }
}
