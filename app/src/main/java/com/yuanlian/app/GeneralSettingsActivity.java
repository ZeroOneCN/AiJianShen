package com.yuanlian.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/** 通用设置：单位、身高、提醒、语言与本机数据。 */
public class GeneralSettingsActivity extends Activity {

    private UiList.Row weightUnitRow;
    private UiList.Row heightUnitRow;
    private UiList.Row heightRow;
    private UiList.Row reminderRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "通用设置", v -> finish()));
        root.addView(UiList.subtitle(this, "单位会用于报告、体重和 BMI 的显示。"));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, 0, p, Ui.dpi(this, 20));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        UiList.label(this, body, "身体数据");
        LinearLayout health = UiList.card(this);
        body.addView(health, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        weightUnitRow = UiList.row(this, health, IconView.CHART, Ui.BLUE_TILE,
                "重量单位", weightUnitText(), false, v -> chooseWeightUnit());
        heightUnitRow = UiList.row(this, health, IconView.USER, Ui.TEAL,
                "身高单位", heightUnitText(), false, v -> chooseHeightUnit());
        heightRow = UiList.row(this, health, IconView.CHART, Ui.GREEN,
                "身高", Store.formatHeight(this, Store.heightCm(this)), true,
                v -> HealthDialogs.setHeight(this,
                        () -> heightRow.sub.setText(
                                Store.formatHeight(this, Store.heightCm(this)))));

        UiList.label(this, body, "训练与提醒");
        LinearLayout train = UiList.card(this);
        body.addView(train, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        reminderRow = UiList.row(this, train, IconView.CLOCK, Ui.ORANGE,
                "训练提醒", reminderText(), false,
                v -> startActivity(new Intent(this, ReminderActivity.class)));
        UiList.row(this, train, IconView.SETTINGS, Ui.PURPLE,
                "训练设置", "倒计时、休息、声音、语音与音乐", true,
                v -> startActivity(new Intent(this, WorkoutSettingsActivity.class)));

        UiList.label(this, body, "其它");
        LinearLayout other = UiList.card(this);
        body.addView(other, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        UiList.row(this, other, IconView.COMPASS, Ui.SLATE, "语言",
                "简体中文 · 跟随系统", false,
                v -> InfoDialog.show(this, "语言",
                        "私人健身教练当前内置简体中文界面，会跟随系统的深色模式和字号设置。\n\n"
                                + "训练动作名称保留了英文原名，方便对照国外教程。"));
        UiList.row(this, other, IconView.INFO, Ui.SLATE, "本机数据",
                "不联网、无账号、无云端同步", true,
                v -> InfoDialog.show(this, "本机数据",
                        "训练记录、体重、提醒和动作替换都保存在本机 SharedPreferences。\n\n"
                                + "应用不申请网络权限，也没有登录和云端同步。"
                                + "卸载应用会同时删除这些数据。"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (reminderRow != null) {
            reminderRow.sub.setText(reminderText());
        }
        if (heightRow != null) {
            heightRow.sub.setText(Store.formatHeight(this, Store.heightCm(this)));
        }
    }

    private String weightUnitText() {
        return Store.isLb(this) ? "磅 (lb)" : "公斤 (kg)";
    }

    private String heightUnitText() {
        return Store.isFt(this) ? "英尺 / 英寸" : "厘米 (cm)";
    }

    private String reminderText() {
        Store.Reminder r = Store.reminder(this);
        if (!r.on) {
            return "未开启";
        }
        return r.timeText() + " · " + r.daysText();
    }

    private void chooseWeightUnit() {
        String[] labels = {"公斤 (kg)", "磅 (lb)"};
        int sel = Store.isLb(this) ? 1 : 0;
        ChoiceDialog.show(this, "重量单位", labels, sel, i -> {
            Store.setWeightUnit(this, i == 1 ? "lb" : "kg");
            weightUnitRow.sub.setText(weightUnitText());
        });
    }

    private void chooseHeightUnit() {
        String[] labels = {"厘米 (cm)", "英尺 / 英寸"};
        int sel = Store.isFt(this) ? 1 : 0;
        ChoiceDialog.show(this, "身高单位", labels, sel, i -> {
            Store.setHeightUnit(this, i == 1 ? "ft" : "cm");
            heightUnitRow.sub.setText(heightUnitText());
            heightRow.sub.setText(Store.formatHeight(this, Store.heightCm(this)));
        });
    }
}
