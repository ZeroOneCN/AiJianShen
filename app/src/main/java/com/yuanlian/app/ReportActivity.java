package com.yuanlian.app;

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
        weightLog.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT, Ui.R_PILL),
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
        log.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT, Ui.R_PILL), 0x33FFFFFF));
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
    }

    private void renderWeight() {
        boolean lb = Store.isLb(this);
        float kg = Store.latestWeightKg(this);
        tvWeight.setText(kg > 0
                ? String.format(Locale.US, "%.1f %s",
                Store.kgToDisplay(this, kg), lb ? "lb" : "kg")
                : "--");

        List<Store.WeightEntry> list = Store.weights(this);
        float max = 0;
        float min = 0;
        for (Store.WeightEntry e : list) {
            float v = Store.kgToDisplay(this, e.kg);
            max = max == 0 ? v : Math.max(max, v);
            min = min == 0 ? v : Math.min(min, v);
        }
        tvWeightMax.setText("最高 " + (max > 0 ? String.format(Locale.US, "%.1f", max) : "--"));
        tvWeightMin.setText("最低 " + (min > 0 ? String.format(Locale.US, "%.1f", min) : "--"));

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
        ic.setStrokeDp(2f);
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
