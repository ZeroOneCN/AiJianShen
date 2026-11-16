package com.aijianshen.app;

import android.app.Activity;
import android.app.Dialog;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 体重与身体成分：本机手动记录 + 蓝牙体脂秤测量 + 手机间同步来的记录。
 *
 * <p>记录里带完整体成分时直接用测量结果；只有体重（或对方记录不完整）时，用同一套
 * BIA 公式按本机档案与兜底阻抗估算，并在页面上注明是估算值。数据全部留在本机。
 */
public class BodyActivity extends Activity {

    private TextView weightValue;
    private TextView weightUnit;
    private TextView weightMeta;
    private LinearLayout grid;
    private LinearLayout metrics;
    private LinearLayout metricsCardView;
    private LinearLayout history;
    private LinearLayout profileRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "体重与身体成分", v -> finish()));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 8), p, Ui.dpi(this, 24));
        sc.addView(body);

        body.addView(hero());
        body.addView(profileCard(), cardLp());
        body.addView(section("身体成分"));
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        body.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(metricsCard(), cardLp());
        body.addView(section("测量记录"));
        LinearLayout historyCard = UiList.card(this);
        historyCard.setPadding(Ui.dpi(this, 6), Ui.dpi(this, 6),
                Ui.dpi(this, 6), Ui.dpi(this, 6));
        history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        historyCard.addView(history, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(historyCard, cardLp());

        UiList.hint(this, body,
                "体成分是按 BIA 公式估算的参考值，不能作为医学诊断依据。");

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(p, Ui.dpi(this, 12), p, Ui.dpi(this, 14));
        root.addView(actions);

        TextView weigh = UiList.primary(this, "连接体脂秤称重");
        actions.addView(weigh, UiList.lp(this, 52, 0));
        weigh.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, ScaleActivity.class)));

        LinearLayout second = new LinearLayout(this);
        second.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 46));
        slp.topMargin = Ui.dpi(this, 8);
        actions.addView(second, slp);

        TextView manual = UiList.secondary(this, "手动记录");
        second.addView(manual, new LinearLayout.LayoutParams(0, Ui.dpi(this, 46), 1f));
        manual.setOnClickListener(v -> HealthDialogs.recordWeight(this, this::refresh,
                kg -> Store.addBodyRecord(this, System.currentTimeMillis(), (float) kg, 0, null,
                        "", false)));

        TextView sync = UiList.secondary(this, "手机间同步");
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, Ui.dpi(this, 46), 1f);
        mlp.leftMargin = Ui.dpi(this, 10);
        second.addView(sync, mlp);
        sync.setOnClickListener(v -> startActivity(
                new android.content.Intent(this, SyncActivity.class)));
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 12);
        return lp;
    }

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(18);
        tv.setPadding(0, Ui.dpi(this, 22), 0, Ui.dpi(this, 8));
        Ui.font(tv, 700);
        return tv;
    }

    // ---- 顶部体重卡 ----

    private LinearLayout hero() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 20);
        card.setPadding(p, p, p, Ui.dpi(this, 18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        TextView label = new TextView(this);
        label.setText("当前体重");
        label.setTextColor(Ui.INK_SOFT);
        label.setTextSize(13);
        Ui.font(label, 500);
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        IconView scale = new IconView(this);
        scale.setIcon(IconView.SCALE);
        scale.setColor(Ui.ACCENT);
        head.addView(scale, new LinearLayout.LayoutParams(
                Ui.dpi(this, 26), Ui.dpi(this, 26)));

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = Ui.dpi(this, 6);
        card.addView(valueRow, vlp);

        weightValue = new TextView(this);
        weightValue.setTextColor(Ui.INK);
        weightValue.setTextSize(46);
        weightValue.setIncludeFontPadding(false);
        Ui.font(weightValue, 800);
        valueRow.addView(weightValue);

        weightUnit = new TextView(this);
        weightUnit.setTextColor(Ui.INK_SOFT);
        weightUnit.setTextSize(17);
        weightUnit.setPadding(Ui.dpi(this, 6), 0, 0, Ui.dpi(this, 6));
        Ui.font(weightUnit, 600);
        valueRow.addView(weightUnit);

        weightMeta = new TextView(this);
        weightMeta.setTextColor(Ui.INK_SOFT);
        weightMeta.setTextSize(12);
        weightMeta.setLineSpacing(Ui.dp(this, 2), 1f);
        weightMeta.setPadding(0, Ui.dpi(this, 8), 0, 0);
        Ui.font(weightMeta, 400);
        card.addView(weightMeta);
        return card;
    }

    // ---- 身高 / 年龄 / 性别 ----

    private LinearLayout profileCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 16);
        card.setPadding(p, p, p, p);
        profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 14f),
                0x22000000));
        profileRow.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 12),
                Ui.dpi(this, 14), Ui.dpi(this, 12));
        card.addView(profileRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        profileRow.setOnClickListener(v -> editProfile());
        return card;
    }

    private void renderProfile() {
        if (profileRow == null) {
            return;
        }
        profileRow.removeAllViews();
        float h = Store.heightCm(this);
        int age = Store.age(this);
        cell(profileRow, "身高", h > 0 ? Store.formatHeight(this, h) : "未设置");
        cell(profileRow, "年龄", age > 0 ? age + " 岁" : "未设置");
        cell(profileRow, "性别", Store.isMale(this) ? "男" : "女");

        TextView edit = new TextView(this);
        edit.setText("编辑");
        edit.setTextColor(Ui.ACCENT);
        edit.setTextSize(13);
        Ui.font(edit, 600);
        profileRow.addView(edit);
    }

    private void cell(LinearLayout parent, String label, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        parent.addView(col, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.INK_SOFT);
        l.setTextSize(11);
        Ui.font(l, 400);
        col.addView(l);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Ui.INK);
        v.setTextSize(15);
        v.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(v, 600);
        col.addView(v);
    }

    private void editProfile() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 24f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("身体资料");
        title.setTextColor(Ui.INK);
        title.setTextSize(19);
        Ui.font(title, 700);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("用于估算体成分，只保存在本机。");
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(12);
        sub.setPadding(0, Ui.dpi(this, 6), 0, 0);
        Ui.font(sub, 400);
        root.addView(sub);

        EditText height = field("身高（cm）",
                Store.heightCm(this) > 0 ? Store.num(Store.heightCm(this)) : "");
        root.addView(height, fieldLp());
        EditText age = field("年龄（岁）", Store.age(this) > 0
                ? String.valueOf(Store.age(this)) : "");
        root.addView(age, fieldLp());

        LinearLayout sexRow = new LinearLayout(this);
        sexRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 46));
        srp.topMargin = Ui.dpi(this, 12);
        root.addView(sexRow, srp);
        final boolean[] male = {Store.isMale(this)};
        TextView boy = chip("男", male[0]);
        TextView girl = chip("女", !male[0]);
        sexRow.addView(boy, new LinearLayout.LayoutParams(0, Ui.dpi(this, 46), 1f));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, Ui.dpi(this, 46), 1f);
        glp.leftMargin = Ui.dpi(this, 10);
        sexRow.addView(girl, glp);
        boy.setOnClickListener(v -> {
            male[0] = true;
            paintChip(boy, true);
            paintChip(girl, false);
        });
        girl.setOnClickListener(v -> {
            male[0] = false;
            paintChip(boy, false);
            paintChip(girl, true);
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        alp.topMargin = Ui.dpi(this, 20);
        root.addView(actions, alp);

        TextView cancel = UiList.secondary(this, "取消");
        actions.addView(cancel, new LinearLayout.LayoutParams(0, Ui.dpi(this, 48), 1f));
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView save = UiList.primary(this, "保存");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dpi(this, 48), 1f);
        slp.leftMargin = Ui.dpi(this, 10);
        actions.addView(save, slp);
        save.setOnClickListener(v -> {
            float h = parse(height, Store.heightCm(this));
            int a = (int) parse(age, Store.age(this));
            if (h > 0) {
                Store.setHeightCm(this, h);
            }
            if (a > 0) {
                Store.setAge(this, a);
            }
            Store.setMale(this, male[0]);
            dialog.dismiss();
            refresh();
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

    private EditText field(String hint, String value) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(value);
        et.setHintTextColor(Ui.INK_SOFT);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setTextSize(16);
        et.setTextColor(Ui.INK);
        et.setBackground(Ui.round(this, Ui.SEARCH, 16f));
        et.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 12), Ui.dpi(this, 16), Ui.dpi(this, 12));
        return et;
    }

    private LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 10);
        return lp;
    }

    private TextView chip(String text, boolean on) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(15);
        Ui.font(tv, 600);
        paintChip(tv, on);
        return tv;
    }

    private void paintChip(TextView tv, boolean on) {
        tv.setTextColor(on ? Color.WHITE : Ui.INK);
        tv.setBackground(Ui.round(this, on ? Ui.ACCENT : Ui.SEARCH, 14f));
        Ui.font(tv, on ? 700 : 500);
    }

    private float parse(EditText et, float fallback) {
        try {
            String s = et.getText().toString().trim();
            return s.isEmpty() ? fallback : Float.parseFloat(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private LinearLayout needProfileCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 18);
        card.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("补全身高和年龄，才能估算体成分");
        title.setTextColor(Ui.INK);
        title.setTextSize(15);
        Ui.font(title, 600);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText("体脂率、肌肉量、水分率等指标都依赖身高与年龄；可以点下面的按钮填写，"
                + "也可以用蓝牙体脂秤称一次，或从另一台手机同步（会自动带入身体资料）。");
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(12.5f);
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        sub.setPadding(0, Ui.dpi(this, 6), 0, Ui.dpi(this, 12));
        Ui.font(sub, 400);
        card.addView(sub);

        TextView btn = UiList.secondary(this, "填写身体资料");
        card.addView(btn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 44)));
        btn.setOnClickListener(v -> editProfile());
        return card;
    }

    // ---- 四项主指标 ----

    private void renderGrid(BodyComp.Result r) {
        grid.removeAllViews();
        if (!r.valid) {
            grid.addView(needProfileCard(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }
        String[][] cells = {
                {"bmi", "BMI"}, {"body_fat", "体脂率"},
                {"muscle", "肌肉量"}, {"water", "体水分率"}
        };
        LinearLayout row = null;
        for (int i = 0; i < cells.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rlp.topMargin = Ui.dpi(this, 10);
                }
                grid.addView(row, rlp);
            }
            BodyComp.Item it = find(r, cells[i][0]);
            LinearLayout cell = UiList.card(this);
            int cp = Ui.dpi(this, 16);
            cell.setPadding(cp, cp, cp, Ui.dpi(this, 14));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 1) {
                clp.leftMargin = Ui.dpi(this, 10);
            }
            row.addView(cell, clp);

            // 名称和等级标签放同一行：各卡高度一致，标签不把卡撑高
            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            cell.addView(nameRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = new TextView(this);
            name.setText(cells[i][1]);
            name.setTextColor(Ui.INK_SOFT);
            name.setTextSize(12);
            Ui.font(name, 500);
            nameRow.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (it != null && !it.grade.isEmpty()) {
                TextView grade = new TextView(this);
                grade.setText(it.grade);
                grade.setTextColor(BodyComp.gradeColor(it.grade));
                grade.setTextSize(11);
                grade.setPadding(Ui.dpi(this, 8), 0, 0, 0);
                Ui.font(grade, 600);
                nameRow.addView(grade, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout valueRow = new LinearLayout(this);
            valueRow.setOrientation(LinearLayout.HORIZONTAL);
            valueRow.setGravity(Gravity.BOTTOM);
            valueRow.setPadding(0, Ui.dpi(this, 6), 0, 0);
            cell.addView(valueRow);

            TextView value = new TextView(this);
            value.setText(it == null ? "--" : it.text);
            value.setTextColor(Ui.INK);
            value.setTextSize(24);
            value.setIncludeFontPadding(false);
            Ui.font(value, 800);
            valueRow.addView(value);

            if (it != null && !it.unit.isEmpty()) {
                TextView unit = new TextView(this);
                unit.setText(it.unit);
                unit.setTextColor(Ui.INK_SOFT);
                unit.setTextSize(12);
                unit.setPadding(Ui.dpi(this, 4), 0, 0, Ui.dpi(this, 3));
                Ui.font(unit, 500);
                valueRow.addView(unit);
            }
        }
    }

    private BodyComp.Item find(BodyComp.Result r, String key) {
        for (BodyComp.Item it : r.items) {
            if (it.key.equals(key)) {
                return it;
            }
        }
        return null;
    }

    // ---- 其余 14 项 ----

    private LinearLayout metricsCard() {
        LinearLayout card = UiList.card(this);
        card.setPadding(Ui.dpi(this, 6), Ui.dpi(this, 6), Ui.dpi(this, 6), Ui.dpi(this, 6));
        metricsCardView = card;
        metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.VERTICAL);
        card.addView(metrics, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static final String[][] MORE = {
            {"visceral_fat", "内脏脂肪"}, {"sub_fat_pct", "皮下脂肪率"},
            {"fat_mass", "脂肪量"}, {"sub_fat_mass", "皮下脂肪量"},
            {"water_mass", "体水分量"}, {"protein_mass", "蛋白质含量"},
            {"protein_rate", "蛋白质占比"}, {"bone", "骨量"},
            {"bone_rate", "骨量占比"}, {"muscle_rate", "肌肉率"},
            {"skeletal_muscle", "骨骼肌量"}, {"skeletal_muscle_rate", "骨骼肌率"},
            {"bmr", "基础代谢"}, {"lean_mass", "去脂体重"}
    };

    private void renderMetrics(BodyComp.Result r) {
        metrics.removeAllViews();
        if (metricsCardView != null) {
            metricsCardView.setVisibility(View.VISIBLE);
        }
        if (!r.valid) {
            if (metricsCardView != null) {
                metricsCardView.setVisibility(View.GONE);
            }
            return;
        }
        // 没经过体脂秤：其余指标留空，给一张引导卡
        if (r.fallbackImpedance) {
            metrics.setPadding(0, Ui.dpi(this, 8), 0, Ui.dpi(this, 8));
            TextView blank = new TextView(this);
            blank.setText("肌肉量、骨量、水分等 14 项需要体脂秤测量。\n连接后这里会自动填满，手动记录的体重不受影响。");
            blank.setTextColor(Ui.INK_SOFT);
            blank.setTextSize(13);
            blank.setLineSpacing(Ui.dp(this, 3), 1f);
            blank.setGravity(Gravity.CENTER);
            Ui.font(blank, 400);
            metrics.addView(blank, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView go = new TextView(this);
            go.setText("连接体脂秤");
            go.setTextColor(Color.WHITE);
            go.setTextSize(14);
            go.setGravity(Gravity.CENTER);
            go.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
            Ui.font(go, 700);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 44));
            glp.topMargin = Ui.dpi(this, 14);
            metrics.addView(go, glp);
            go.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, ScaleActivity.class)));
            return;
        }
        metrics.setPadding(0, 0, 0, 0);
        for (int i = 0; i < MORE.length; i++) {
            BodyComp.Item it = find(r, MORE[i][0]);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 11),
                    Ui.dpi(this, 12), Ui.dpi(this, 11));
            metrics.addView(row);

            TextView name = new TextView(this);
            name.setText(MORE[i][1]);
            name.setTextColor(Ui.INK);
            name.setTextSize(14);
            Ui.font(name, 500);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (it != null && !it.grade.isEmpty()) {
                TextView grade = new TextView(this);
                grade.setText(it.grade);
                grade.setTextColor(BodyComp.gradeColor(it.grade));
                grade.setTextSize(11);
                grade.setPadding(0, 0, Ui.dpi(this, 10), 0);
                Ui.font(grade, 600);
                row.addView(grade);
            }

            TextView value = new TextView(this);
            value.setText(it == null ? "--" : it.text + (it.unit.isEmpty() ? ""
                    : " " + it.unit));
            value.setTextColor(Ui.INK);
            value.setTextSize(15);
            Ui.font(value, 700);
            row.addView(value);

            if (i < MORE.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(0xFFF1F2F4);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 1)));
                llp.leftMargin = Ui.dpi(this, 12);
                metrics.addView(line, llp);
            }
        }
    }

    // ---- 历史记录 ----

    private void renderHistory() {
        history.removeAllViews();
        List<Store.BodyRecord> list = Store.bodyRecords(this);
        int shown = 0;
        SimpleDateFormat fmt = new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA);
        for (int i = list.size() - 1; i >= 0 && shown < 8; i--, shown++) {
            Store.BodyRecord rec = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 13),
                    Ui.dpi(this, 12), Ui.dpi(this, 13));
            history.addView(row);

            TextView when = new TextView(this);
            when.setText(fmt.format(new Date(rec.ts)));
            when.setTextColor(Ui.INK_SOFT);
            when.setTextSize(12);
            Ui.font(when, 400);
            row.addView(when, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (rec.synced) {
                TextView tag = new TextView(this);
                tag.setText("已同步");
                tag.setTextColor(Ui.ACCENT);
                tag.setTextSize(10);
                tag.setPadding(Ui.dpi(this, 6), Ui.dpi(this, 2),
                        Ui.dpi(this, 6), Ui.dpi(this, 2));
                tag.setBackground(Ui.round(this, Ui.ACCENT_SOFT, 8f));
                Ui.font(tag, 600);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tlp.rightMargin = Ui.dpi(this, 8);
                row.addView(tag, tlp);
            }

            TextView kg = new TextView(this);
            kg.setText(Store.num(Store.kgToDisplay(this, rec.kg)) + " " +
                    (Store.isLb(this) ? "lb" : "kg"));
            kg.setTextColor(Ui.INK);
            kg.setTextSize(15);
            Ui.font(kg, 700);
            row.addView(kg);

            if (shown < 7 && i > 0) {
                View line = new View(this);
                line.setBackgroundColor(0xFFF1F2F4);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 1)));
                llp.leftMargin = Ui.dpi(this, 12);
                history.addView(line, llp);
            }
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有记录。点下方「连接体脂秤称重」，或手动记录一次。");
            empty.setTextColor(Ui.INK_SOFT);
            empty.setTextSize(13);
            empty.setLineSpacing(Ui.dp(this, 3), 1f);
            empty.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 16),
                    Ui.dpi(this, 14), Ui.dpi(this, 16));
            Ui.font(empty, 400);
            history.addView(empty);
        }
    }

    // ---- 数据装配 ----

    private void refresh() {
        Store.BodyRecord rec = Store.latestBodyRecord(this);
        float kg = rec != null ? rec.kg : Store.latestWeightKg(this);

        weightValue.setText(kg > 0
                ? Store.num(Store.kgToDisplay(this, kg)) : "--");
        weightUnit.setText(Store.isLb(this) ? "lb" : "kg");

        BodyComp.Result r = compute(rec, kg);
        if (kg <= 0) {
            weightMeta.setText("还没有体重记录。先记录一次，或从另一台手机同步。");
        } else if (rec != null && rec.body != null) {
            String who = rec.userName.isEmpty() ? "" : "（" + rec.userName + "）";
            weightMeta.setText(Store.dateOf(rec.ts)
                    + (hasCoreMetrics(rec.bodyJson()) ? " · 体脂秤测量" : " · 同步记录")
                    + who);
        } else if (rec != null) {
            weightMeta.setText(Store.dateOf(rec.ts) + " · 本机记录");
        } else {
            weightMeta.setText("本机记录");
        }
        if (kg > 0 && r.valid && r.fallbackImpedance) {
            weightMeta.append("\n体脂率按参考阻抗估算；肌肉量等其余指标需体脂秤测量。");
        }

        renderProfile();
        renderGrid(r);
        renderMetrics(r);
        renderHistory();
    }

    private BodyComp.Result compute(Store.BodyRecord rec, float kg) {
        if (rec != null && rec.body != null) {
            JSONObject o = rec.bodyJson();
            if (o != null && hasCoreMetrics(o)) {
                return BodyComp.fromJson(o);
            }
        }
        return BodyComp.calculate(kg, rec == null ? 0 : rec.impedance,
                Store.heightCm(this), Store.age(this), Store.isMale(this));
    }

    /**
     * 记录里的体成分是否完整。
     *
     * <p>手机间同步来的记录可能只带了体重（对方没填身高年龄、或是更早的版本），
     * 这种记录直接用会渲染成一片 0.00，所以缺核心指标时改用本机档案重算。
     */
    private boolean hasCoreMetrics(JSONObject o) {
        return o != null
                && o.optDouble("bmi", 0) > 0
                && o.optDouble("body_fat", 0) > 0
                && o.optDouble("weight", 0) > 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
