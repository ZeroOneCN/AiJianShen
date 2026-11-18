package com.aijianshen.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 报告页：本周统计、历史、连续天数与训练量折线。 */
public class ReportActivity extends Activity {

    private static final String[] WEEK_LABEL = {"一", "二", "三", "四", "五", "六", "日"};

    private LinearLayout weekStrip;
    private LinearLayout chartLabels;
    private TextView tvStreak;
    private TextView tvTotal;
    private TextView tvAvg;
    private TextView tvMax;
    private QuickChartView chart;
    private WeightChartView weightChart;
    private TextView tvWeight;
    private TextView tvWeightMax;
    private TextView tvWeightMin;
    private TextView tvBmi;
    private int todayIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_report);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        TextView title = findViewById(R.id.tv_title);
        Ui.font(title, 800);

        LinearLayout statCard = findViewById(R.id.stat_card);
        statCard.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        LinearLayout historyCard = findViewById(R.id.history_card);
        historyCard.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        LinearLayout volumeCard = findViewById(R.id.volume_card);
        volumeCard.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        LinearLayout weightCard = findViewById(R.id.weight_card);
        weightCard.setBackground(Ui.round(this, Ui.SURFACE, 20f));

        TextView weightLog = findViewById(R.id.btn_weight_log);
        weightLog.setBackground(Ui.ripple(this, Ui.gradient(this, Ui.ACCENT, Ui.R_PILL),
                0x33FFFFFF));
        Ui.font(weightLog, 600);
        weightLog.setOnClickListener(v -> HealthDialogs.recordWeight(this, this::renderWeight));

        TextView bodyComp = findViewById(R.id.btn_body_comp);
        Ui.font(bodyComp, 600);
        bodyComp.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, BodyActivity.class)));

        tvWeight = findViewById(R.id.tv_weight);
        tvWeightMax = findViewById(R.id.tv_weight_max);
        tvWeightMin = findViewById(R.id.tv_weight_min);
        tvBmi = findViewById(R.id.tv_bmi);
        weightChart = findViewById(R.id.weight_chart);
        Ui.font(tvWeight, 800);
        Ui.font(tvWeightMax, 500);
        Ui.font(tvWeightMin, 500);
        Ui.font(tvBmi, 400);
        tvBmi.setOnClickListener(v -> HealthDialogs.setHeight(this, this::renderWeight));

        TextView all = findViewById(R.id.btn_all_records);
        Ui.font(all, 600);
        all.setOnClickListener(v -> showRecords());

        TextView log = findViewById(R.id.btn_log);
        log.setBackground(Ui.ripple(this, Ui.gradient(this, Ui.ACCENT, Ui.R_PILL), 0x33FFFFFF));
        Ui.font(log, 600);
        log.setOnClickListener(v -> showLog());

        IconView flame = findViewById(R.id.icon_flame);
        flame.setIcon(IconView.FLAME);
        flame.setColor(Ui.RED);
        tvStreak = findViewById(R.id.tv_streak);
        Ui.font(tvStreak, 800);

        weekStrip = findViewById(R.id.week_strip);
        chartLabels = findViewById(R.id.chart_labels);
        tvTotal = findViewById(R.id.tv_total);
        Ui.font(tvTotal, 800);
        tvAvg = findViewById(R.id.tv_avg);
        tvMax = findViewById(R.id.tv_max);
        Ui.font(tvAvg, 500);
        Ui.font(tvMax, 500);
        chart = findViewById(R.id.chart);

        ((FrameLayout) findViewById(R.id.nav_holder)).addView(
                Nav.bar(this, Nav.TAB_REPORT),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        todayIndex = Data.todayIndex(Calendar.getInstance());
        render();
    }

    private void render() {
        int sessions = Store.daysTrained(this);
        int minutes = Store.totalMinutes(this);
        int kcal = Store.totalKcal(this);

        fillStat(findViewById(R.id.stat_col_1), IconView.TROPHY, String.valueOf(sessions), "运动");
        fillStat(findViewById(R.id.stat_col_2), IconView.FLAME, String.valueOf(kcal), "千卡");
        fillStat(findViewById(R.id.stat_col_3), IconView.CLOCK, String.valueOf(minutes), "分钟");

        tvStreak.setText(Store.streak(this) + " 天");

        weekStrip.removeAllViews();
        Calendar cal = Calendar.getInstance();
        Set<String> dates = Store.dates(this);
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(todayIndex - i));
            boolean today = i == todayIndex;
            boolean done = dates.contains(Store.key(d));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            weekStrip.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(this);
            label.setText("周" + WEEK_LABEL[i]);
            label.setTextSize(11);
            label.setTextColor(Ui.INK_SOFT);
            Ui.font(label, 400);
            col.addView(label);

            TextView num = new TextView(this);
            num.setText(String.valueOf(d.get(Calendar.DAY_OF_MONTH)));
            num.setTextSize(15);
            num.setGravity(Gravity.CENTER);
            num.setIncludeFontPadding(false);
            num.setTextColor(today ? Ui.ACCENT : (done ? Ui.INK : Ui.INK_SOFT));
            Ui.font(num, today || done ? 700 : 500);
            if (today) {
                num.setBackground(Ui.roundStroke(this, 0x00000000, 18f, Ui.ACCENT_RING));
            }
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 36), Ui.dpi(this, 36));
            nlp.topMargin = Ui.dpi(this, 5);
            col.addView(num, nlp);

            View dot = new View(this);
            dot.setBackground(Ui.round(this, done ? Ui.ACCENT : 0x00000000, 3f));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 5), Ui.dpi(this, 5));
            dlp.topMargin = Ui.dpi(this, 3);
            col.addView(dot, dlp);
        }

        int[] week = Store.weekMinutes(this);
        int total = 0;
        int max = 0;
        for (int v : week) {
            total += v;
            max = Math.max(max, v);
        }
        tvTotal.setText(total + " 分钟");
        tvAvg.setText("日均 " + Math.round(total / 7f) + " 分钟");
        tvMax.setText("最长 " + max + " 分钟");

        renderWeight();

        int[] recent = Store.recentMinutes(this);
        chart.setValues(recent);
        chartLabels.removeAllViews();
        SimpleDateFormat fmt = new SimpleDateFormat("M/d", Locale.CHINA);
        for (int i = 0; i < 7; i++) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -(6 - i));
            TextView l = new TextView(this);
            l.setText(fmt.format(new Date(d.getTimeInMillis())));
            l.setTextSize(10);
            l.setGravity(Gravity.CENTER);
            l.setTextColor(i == 6 ? Ui.INK : Ui.INK_SOFT);
            Ui.font(l, i == 6 ? 600 : 400);
            chartLabels.addView(l, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        renderHeat();
        renderParts();
    }

    /** 近 12 周热力图 + 本周与上周的对比。 */
    private void renderHeat() {
        LinearLayout card = findViewById(R.id.heat_card);
        card.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        card.removeAllViews();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        TextView title = new TextView(this);
        title.setText("训练热力图");
        title.setTextColor(Ui.INK);
        title.setTextSize(17);
        Ui.font(title, 700);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView span = new TextView(this);
        span.setText("近 12 周");
        span.setTextColor(Ui.INK_SOFT);
        span.setTextSize(12);
        Ui.font(span, 400);
        head.addView(span);

        HeatmapView map = new HeatmapView(this);
        String[] keys = new String[84];
        int[] mins = new int[84];
        java.util.Map<String, Integer> byDay = Store.dayMinutes(this);
        Calendar walk = Calendar.getInstance();
        int dow = (walk.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        walk.add(Calendar.DAY_OF_MONTH, -dow - 11 * 7);
        for (int i = 0; i < 84; i++) {
            keys[i] = Store.key(walk);
            Integer m = byDay.get(keys[i]);
            mins[i] = m == null ? 0 : m;
            walk.add(Calendar.DAY_OF_MONTH, 1);
        }
        map.setData(keys, mins);
        map.setOnDayClick((key, m) -> {
            if (m <= 0) {
                Toast.makeText(this, key + " 没有训练记录", Toast.LENGTH_SHORT).show();
            } else {
                InfoDialog.show(this, key, "当天训练 " + m + " 分钟。");
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dpi(this, 14);
        card.addView(map, mlp);

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dpi(this, 8);
        card.addView(legend, llp);

        TextView less = new TextView(this);
        less.setText("少");
        less.setTextColor(Ui.INK_SOFT);
        less.setTextSize(10.5f);
        Ui.font(less, 400);
        legend.addView(less, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int[] levels = {Ui.CARD_SOFT, 0xFFD9EDE4, 0xFF95C9B4, 0xFF4FA184, Ui.GREEN};
        for (int color : levels) {
            View sq = new View(this);
            sq.setBackground(Ui.round(this, color, 3f));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 11), Ui.dpi(this, 11));
            slp.leftMargin = Ui.dpi(this, 3);
            legend.addView(sq, slp);
        }

        TextView more = new TextView(this);
        more.setText("多");
        more.setTextColor(Ui.INK_SOFT);
        more.setTextSize(10.5f);
        more.setGravity(Gravity.RIGHT);
        Ui.font(more, 400);
        legend.addView(more, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int[] now = weekStats(0);
        int[] prev = weekStats(1);
        LinearLayout cmp = new LinearLayout(this);
        cmp.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dpi(this, 14);
        card.addView(cmp, clp);
        cmp.addView(cmpCol("本周", now, Ui.INK), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        cmp.addView(cmpCol("上周", prev, Ui.INK_SOFT), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    /** [分钟, 次数, 千卡]，offset=0 本周，1 上周。 */
    private int[] weekStats(int offset) {
        Calendar mon = Calendar.getInstance();
        int dow = (mon.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        mon.add(Calendar.DAY_OF_MONTH, -dow - offset * 7);
        Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < 7; i++) {
            keys.add(Store.key(mon));
            mon.add(Calendar.DAY_OF_MONTH, 1);
        }
        int minutes = 0;
        int count = 0;
        int kcal = 0;
        for (Store.Session s : Store.sessions(this)) {
            if (!keys.contains(s.date)) {
                continue;
            }
            minutes += s.minutes;
            count++;
            kcal += s.kcal > 0 ? s.kcal : s.minutes * 6;
        }
        return new int[]{minutes, count, kcal};
    }

    private LinearLayout cmpCol(String label, int[] stats, int color) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.INK_SOFT);
        l.setTextSize(12);
        Ui.font(l, 400);
        col.addView(l);
        TextView v = new TextView(this);
        v.setText(stats[0] + " 分钟 · " + stats[1] + " 次");
        v.setTextColor(color);
        v.setTextSize(16);
        v.setIncludeFontPadding(false);
        v.setPadding(0, Ui.dpi(this, 2), 0, 0);
        Ui.font(v, 700);
        col.addView(v);
        TextView k = new TextView(this);
        k.setText(stats[2] + " 千卡");
        k.setTextColor(Ui.INK_SOFT);
        k.setTextSize(11.5f);
        Ui.font(k, 400);
        col.addView(k);
        return col;
    }

    /** 本周练到的部位分布（按训练记录里的动作 id 统计）。 */
    private void renderParts() {
        LinearLayout card = findViewById(R.id.part_card);
        card.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        card.removeAllViews();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        TextView title = new TextView(this);
        title.setText("本周练到的部位");
        title.setTextColor(Ui.INK);
        title.setTextSize(17);
        Ui.font(title, 700);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Calendar mon = Calendar.getInstance();
        int dow = (mon.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        mon.add(Calendar.DAY_OF_MONTH, -dow);
        Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < 7; i++) {
            keys.add(Store.key(mon));
            mon.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 先统计再画标题：这里要的是「本周」练过几个动作，
        // 不能再用个人纪录表的累计数，否则卡片标题和右侧数字对不上。
        java.util.Map<String, Integer> byPart = new java.util.LinkedHashMap<>();
        Set<String> weekIds = new java.util.HashSet<>();
        for (Store.Session s : Store.sessions(this)) {
            if (!keys.contains(s.date) || s.ids == null || s.ids.isEmpty()) {
                continue;
            }
            for (String id : s.ids.split(",")) {
                Exercise e = Data.byId(id);
                if (e == null || e.part == null) {
                    continue;
                }
                weekIds.add(e.id);
                Integer old = byPart.get(e.part);
                byPart.put(e.part, old == null ? 1 : old + 1);
            }
        }

        TextView moves = new TextView(this);
        moves.setText(weekIds.isEmpty() ? "本周还没练" : "本周 " + weekIds.size() + " 个动作");
        moves.setTextColor(Ui.INK_SOFT);
        moves.setTextSize(12);
        Ui.font(moves, 400);
        head.addView(moves);

        if (byPart.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("这周还没有按动作统计的记录。下次训练完成后，这里会显示各部位练了几组。");
            empty.setTextColor(Ui.INK_SOFT);
            empty.setTextSize(12.5f);
            empty.setLineSpacing(Ui.dp(this, 3), 1f);
            empty.setPadding(0, Ui.dpi(this, 12), 0, 0);
            Ui.font(empty, 400);
            card.addView(empty);
            return;
        }

        List<java.util.Map.Entry<String, Integer>> list =
                new java.util.ArrayList<>(byPart.entrySet());
        java.util.Collections.sort(list, (a, b) -> b.getValue() - a.getValue());
        int max = Math.max(1, list.get(0).getValue());

        for (int i = 0; i < list.size() && i < 6; i++) {
            java.util.Map.Entry<String, Integer> e = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dpi(this, 10);
            card.addView(row, rlp);

            TextView name = new TextView(this);
            name.setText(e.getKey());
            name.setTextColor(Ui.INK);
            name.setTextSize(13);
            Ui.font(name, 500);
            row.addView(name, new LinearLayout.LayoutParams(
                    Ui.dpi(this, 42), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout track = new LinearLayout(this);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setBackground(Ui.round(this, Ui.CARD_SOFT, 4f));
            row.addView(track, new LinearLayout.LayoutParams(0,
                    Ui.dpi(this, 8), 1f));

            int color = partColor(e.getKey());
            View fill = new View(this);
            fill.setBackground(Ui.round(this, color, 4f));
            track.addView(fill, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(0.8f, e.getValue() * 100f / max)));
            track.addView(new View(this), new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(0.8f, (max - e.getValue()) * 100f / max)));

            TextView num = new TextView(this);
            num.setText(e.getValue() + " 组");
            num.setTextColor(Ui.INK_SOFT);
            num.setTextSize(12);
            num.setGravity(Gravity.RIGHT);
            Ui.font(num, 500);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 48), ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.leftMargin = Ui.dpi(this, 10);
            row.addView(num, nlp);
        }
    }

    private int partColor(String part) {
        switch (part) {
            case "核心":
                return Ui.ORANGE;
            case "腿臀":
                return Ui.GREEN;
            case "胸":
                return Ui.BLUE_TILE;
            case "背":
                return Ui.PURPLE;
            case "肩":
                return Ui.TEAL;
            case "手臂":
                return Ui.RED;
            case "有氧":
                return Ui.ACCENT;
            default:
                return Ui.SLATE;
        }
    }

    private void renderWeight() {
        boolean lb = Store.isLb(this);
        float kg = Store.latestWeightKg(this);
        tvWeight.setText(kg > 0
                ? Store.num(Store.kgToDisplay(this, kg)) + " " + (lb ? "lb" : "kg")
                : "--");

        List<Store.WeightEntry> list = Store.weights(this);
        float max = 0;
        float min = 0;
        for (Store.WeightEntry e : list) {
            float v = Store.kgToDisplay(this, e.kg);
            max = max == 0 ? v : Math.max(max, v);
            min = min == 0 ? v : Math.min(min, v);
        }
        tvWeightMax.setText("最高 " + (max > 0 ? Store.num(max) : "--"));
        tvWeightMin.setText("最低 " + (min > 0 ? Store.num(min) : "--"));

        float bmi = Store.bmi(this);
        if (kg <= 0) {
            tvBmi.setText("记录体重后，这里会显示 BMI 与趋势。");
        } else if (bmi <= 0) {
            tvBmi.setText("点这里设置身高，计算 BMI →");
        } else {
            tvBmi.setText(String.format(Locale.CHINA, "BMI %.1f · %s · 身高 %s",
                    bmi, Store.bmiLabel(bmi), Store.formatHeight(this, Store.heightCm(this))));
        }
        weightChart.setData(list, lb ? "lb" : "kg");
    }

    private void fillStat(LinearLayout col, int icon, String value, String label) {
        col.removeAllViews();
        FrameLayout box = new FrameLayout(this);
        box.setBackground(getDrawable(R.drawable.bg_soft_circle));
        col.addView(box, new LinearLayout.LayoutParams(Ui.dpi(this, 40), Ui.dpi(this, 40)));
        IconView ic = new IconView(this);
        ic.setIcon(icon);
        ic.setColor(Ui.ACCENT);
        box.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 22), Ui.dpi(this, 22), Gravity.CENTER));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Ui.INK);
        v.setTextSize(29);
        v.setGravity(Gravity.CENTER);
        v.setIncludeFontPadding(false);
        Ui.font(v, 800);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Ui.dpi(this, 10);
        col.addView(v, vlp);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.INK_SOFT);
        l.setTextSize(13);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dpi(this, 5), 0, 0);
        Ui.font(l, 400);
        col.addView(l);
    }

    private void showLog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 24f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("记录今天的训练");
        title.setTextColor(Ui.INK);
        title.setTextSize(20);
        Ui.font(title, 800);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("手动补记分钟数，只保存在这台设备上。");
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dpi(this, 5), 0, 0);
        Ui.font(sub, 400);
        root.addView(sub);

        EditText input = new EditText(this);
        input.setHint("例如 15");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(16);
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.INK_SOFT);
        input.setBackground(Ui.round(this, Ui.SEARCH, 16f));
        input.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 12),
                Ui.dpi(this, 16), Ui.dpi(this, 12));
        Ui.font(input, 500);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = Ui.dpi(this, 16);
        root.addView(input, ilp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        alp.topMargin = Ui.dpi(this, 18);
        root.addView(actions, alp);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(Ui.INK_MUTED);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancel, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp.rightMargin = Ui.dpi(this, 10);
        actions.addView(cancel, clp);

        TextView save = new TextView(this);
        save.setText("保存");
        save.setTextColor(Ui.SURFACE);
        save.setTextSize(15);
        save.setGravity(Gravity.CENTER);
        save.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        Ui.font(save, 700);
        actions.addView(save, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.6f));

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            int min;
            try {
                min = Integer.parseInt(input.getText().toString().trim());
            } catch (Exception e) {
                min = 0;
            }
            if (min <= 0) {
                Toast.makeText(this, "填一个大于 0 的分钟数", Toast.LENGTH_SHORT).show();
                return;
            }
            Store.markDone(this, Calendar.getInstance(), Math.min(300, min));
            dialog.dismiss();
            render();
            Toast.makeText(this, "已记录 " + Math.min(300, min) + " 分钟", Toast.LENGTH_SHORT).show();
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
            w.setLayout(getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 48),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /** 删除一条训练记录前的确认框。 */
    private void confirmDelete(Dialog listDialog, Store.Session s) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 24f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("删除这条记录？");
        title.setTextColor(Ui.INK);
        title.setTextSize(19);
        Ui.font(title, 800);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(s.title + " · " + s.minutes + " 分钟，删除后当天与累计分钟会一起扣回。");
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        sub.setPadding(0, Ui.dpi(this, 6), 0, 0);
        Ui.font(sub, 400);
        root.addView(sub);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        alp.topMargin = Ui.dpi(this, 18);
        root.addView(actions, alp);

        TextView cancel = new TextView(this);
        cancel.setText("保留");
        cancel.setTextColor(Ui.INK_MUTED);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancel, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp.rightMargin = Ui.dpi(this, 10);
        actions.addView(cancel, clp);

        TextView del = new TextView(this);
        del.setText("删除");
        del.setTextColor(Color.WHITE);
        del.setTextSize(15);
        del.setGravity(Gravity.CENTER);
        del.setBackground(Ui.ripple(this, Ui.round(this, Ui.RED, 24f), 0x33FFFFFF));
        Ui.font(del, 700);
        actions.addView(del, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        cancel.setOnClickListener(v -> dialog.dismiss());
        del.setOnClickListener(v -> {
            boolean ok = Store.deleteSession(this, s.ts);
            dialog.dismiss();
            if (ok) {
                listDialog.dismiss();
                render();
                Toast.makeText(this, "已删除这条记录", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "这条记录已经不在了", Toast.LENGTH_SHORT).show();
            }
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
            w.setLayout(getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 64),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showRecords() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 24f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("所有记录");
        title.setTextColor(Ui.INK);
        title.setTextSize(20);
        Ui.font(title, 800);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("长按某条记录可以删除，当天的分钟数会一起扣回。");
        tip.setTextColor(Ui.INK_SOFT);
        tip.setTextSize(12);
        tip.setPadding(0, Ui.dpi(this, 5), 0, 0);
        Ui.font(tip, 400);
        root.addView(tip);

        List<Store.Session> list = Store.sessions(this);

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sc.addView(box);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dpi(this, 14);
        root.addView(sc, slp);

        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有训练记录，完成一次训练就会出现在这里。");
            empty.setTextColor(Ui.INK_SOFT);
            empty.setTextSize(14);
            empty.setLineSpacing(Ui.dp(this, 3), 1f);
            Ui.font(empty, 400);
            box.addView(empty);
        } else {
            SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat dayFmt = new SimpleDateFormat("M 月 d 日", Locale.CHINA);
            SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
            String[] feels = {"太轻松", "刚刚好", "太吃力"};
            for (Store.Session s : list) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dpi(this, 11), 0, Ui.dpi(this, 11));
                box.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                row.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView name = new TextView(this);
                name.setText(s.title == null || s.title.isEmpty() ? "训练" : s.title);
                name.setTextColor(Ui.INK);
                name.setTextSize(14);
                Ui.font(name, 600);
                texts.addView(name);

                String when = s.date;
                try {
                    Date d = s.ts > 0 ? new Date(s.ts) : parse.parse(s.date);
                    if (d != null) {
                        when = s.ts > 0 ? dayFmt.format(d) + " " + timeFmt.format(d)
                                : dayFmt.format(d);
                    }
                } catch (Exception ignored) {
                }
                if (s.feel >= 0) {
                    when += " · " + feels[Math.min(2, s.feel)];
                }
                TextView sub = new TextView(this);
                sub.setText(when);
                sub.setTextColor(Ui.INK_SOFT);
                sub.setTextSize(12);
                sub.setPadding(0, Ui.dpi(this, 2), 0, 0);
                Ui.font(sub, 400);
                texts.addView(sub);

                TextView min = new TextView(this);
                min.setText(s.minutes + " 分钟");
                min.setTextColor(Ui.ACCENT);
                min.setTextSize(13);
                Ui.font(min, 600);
                row.addView(min, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setOnLongClickListener(v -> {
                    confirmDelete(dialog, s);
                    return true;
                });

                View divider = new View(this);
                divider.setBackgroundColor(0xFFEFEFF2);
                box.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        }

        TextView close = new TextView(this);
        close.setText("关闭");
        close.setTextColor(Ui.INK_MUTED);
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(close, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        clp.topMargin = Ui.dpi(this, 14);
        root.addView(close, clp);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        if (!list.isEmpty()) {
            slp.height = Math.min(list.size() * Ui.dpi(this, 64), Ui.dpi(this, 340));
        }
        dialog.show();
        if (w != null) {
            int width = getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 48);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
