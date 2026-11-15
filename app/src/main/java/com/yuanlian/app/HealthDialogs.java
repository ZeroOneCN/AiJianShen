package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/** 体重和身高的录入弹窗，报告页、结果页和通用设置共用。 */
public final class HealthDialogs {

    private HealthDialogs() {
    }

    public static void recordWeight(Activity a, Runnable after) {
        recordWeight(a, after, null);
    }

    /**
     * 记录体重。
     *
     * @param onKg 保存成功后的回调，参数为千克值；用于同时写一条带时间戳的体成分记录
     */
    public static void recordWeight(Activity a, Runnable after,
                                    java.util.function.DoubleConsumer onKg) {
        boolean lb = Store.isLb(a);
        String unit = lb ? "lb" : "kg";
        Dialog dialog = base(a, "记录体重", "只保存在本机，用于趋势和 BMI。");

        EditText input = input(a, lb ? "例如 165" : "例如 75");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        float latest = Store.latestWeightKg(a);
        if (latest > 0) {
            input.setText(String.format(Locale.US, "%.1f", Store.kgToDisplay(a, latest)));
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 52));
        ilp.topMargin = Ui.dpi(a, 16);
        LinearLayout root = (LinearLayout) dialog.findViewById(R.id.root);
        root.addView(input, ilp);

        TextView unitLabel = new TextView(a);
        unitLabel.setText("单位：" + unit);
        unitLabel.setTextColor(Ui.INK_SOFT);
        unitLabel.setTextSize(12);
        unitLabel.setPadding(0, Ui.dpi(a, 6), 0, 0);
        Ui.font(unitLabel, 400);
        root.addView(unitLabel);

        addActions(a, dialog, root, "保存", () -> {
            float v;
            try {
                v = Float.parseFloat(input.getText().toString().trim());
            } catch (Exception e) {
                v = 0;
            }
            float kg = Store.displayToKg(a, v);
            if (kg < 25 || kg > 300) {
                Toast.makeText(a, "请填写合理的体重", Toast.LENGTH_SHORT).show();
                return;
            }
            Store.addWeight(a, Store.key(Calendar.getInstance()), kg);
            if (onKg != null) {
                onKg.accept(kg);
            }
            if (after != null) {
                after.run();
            }
        });
        show(a, dialog);
    }

    public static void setHeight(Activity a, Runnable after) {
        Dialog dialog = base(a, "设置身高", "身高只用于计算 BMI，保存在本机。");
        LinearLayout root = (LinearLayout) dialog.findViewById(R.id.root);
        EditText cm = null;
        EditText feet = null;
        EditText inch = null;
        float current = Store.heightCm(a);

        if (Store.isFt(a)) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 52));
            rlp.topMargin = Ui.dpi(a, 16);
            root.addView(row, rlp);

            feet = input(a, "英尺");
            feet.setInputType(InputType.TYPE_CLASS_NUMBER);
            row.addView(feet, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f));

            inch = input(a, "英寸");
            inch.setInputType(InputType.TYPE_CLASS_NUMBER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            ilp.leftMargin = Ui.dpi(a, 10);
            row.addView(inch, ilp);

            if (current > 0) {
                float inches = current / 2.54f;
                int f = (int) (inches / 12);
                int i = Math.round(inches - f * 12);
                feet.setText(String.valueOf(f));
                inch.setText(String.valueOf(i));
            }
        } else {
            cm = input(a, "例如 170");
            cm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if (current > 0) {
                cm.setText(String.format(Locale.US, "%.0f", current));
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 52));
            ilp.topMargin = Ui.dpi(a, 16);
            root.addView(cm, ilp);
        }

        final EditText fCm = cm;
        final EditText fFeet = feet;
        final EditText fInch = inch;
        addActions(a, dialog, root, "保存", () -> {
            float value;
            if (fCm != null) {
                try {
                    value = Float.parseFloat(fCm.getText().toString().trim());
                } catch (Exception e) {
                    value = 0;
                }
            } else {
                float f;
                float i;
                try {
                    f = Float.parseFloat(fFeet.getText().toString().trim());
                } catch (Exception e) {
                    f = 0;
                }
                try {
                    i = Float.parseFloat(fInch.getText().toString().trim());
                } catch (Exception e) {
                    i = 0;
                }
                value = (f * 12 + i) * 2.54f;
            }
            if (value < 100 || value > 250) {
                Toast.makeText(a, "请填写合理的身高", Toast.LENGTH_SHORT).show();
                return;
            }
            Store.setHeightCm(a, value);
            if (after != null) {
                after.run();
            }
        });
        show(a, dialog);
    }

    private static EditText input(Activity a, String hint) {
        EditText input = new EditText(a);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextSize(17);
        input.setTextColor(Ui.INK);
        input.setHintTextColor(Ui.INK_SOFT);
        input.setBackground(Ui.round(a, Ui.SEARCH, 16f));
        input.setPadding(Ui.dpi(a, 16), Ui.dpi(a, 12), Ui.dpi(a, 16), Ui.dpi(a, 12));
        Ui.font(input, 500);
        return input;
    }

    private static Dialog base(Activity a, String titleText, String subText) {
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(a);
        root.setId(R.id.root);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(a, Ui.SURFACE, 24f));
        int p = Ui.dpi(a, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(a);
        title.setText(titleText);
        title.setTextColor(Ui.INK);
        title.setTextSize(20);
        Ui.font(title, 800);
        root.addView(title);

        TextView sub = new TextView(a);
        sub.setText(subText);
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dpi(a, 5), 0, 0);
        Ui.font(sub, 400);
        root.addView(sub);

        dialog.setContentView(root);
        return dialog;
    }

    private static void addActions(Activity a, Dialog dialog, LinearLayout root,
                                   String primary, Runnable save) {
        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 48));
        alp.topMargin = Ui.dpi(a, 20);
        root.addView(actions, alp);

        TextView cancel = new TextView(a);
        cancel.setText("取消");
        cancel.setTextColor(Ui.INK_MUTED);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancel, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp.rightMargin = Ui.dpi(a, 10);
        actions.addView(cancel, clp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView ok = new TextView(a);
        ok.setText(primary);
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(15);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(a, Ui.gradientBluePill(a), 0x33FFFFFF));
        Ui.font(ok, 700);
        actions.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.6f));
        ok.setOnClickListener(v -> {
            save.run();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        });
    }

    private static void show(Activity a, Dialog dialog) {
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
            w.setLayout(a.getResources().getDisplayMetrics().widthPixels - Ui.dpi(a, 48),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
