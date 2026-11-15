package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 单选设置项：单位、倒计时、休息时长等。 */
public final class ChoiceDialog {

    public interface OnChoose {
        void onChoose(int index);
    }

    private ChoiceDialog() {
    }

    public static void show(Activity a, String title, String[] labels, int selected,
                            OnChoose callback) {
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(a, Ui.SURFACE, 24f));
        int p = Ui.dpi(a, 22);
        root.setPadding(p, p, p, Ui.dpi(a, 14));

        TextView head = new TextView(a);
        head.setText(title);
        head.setTextColor(Ui.INK);
        head.setTextSize(20);
        Ui.font(head, 800);
        root.addView(head);

        ScrollView sc = new ScrollView(a);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dpi(a, 10), 0, 0);
        sc.addView(box);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Ui.ripple(a, Ui.round(a, on ? Ui.ACCENT_SOFT : 0x00000000, 14f),
                    0x14000000));
            row.setClickable(true);
            row.setPadding(Ui.dpi(a, 14), 0, Ui.dpi(a, 14), 0);

            TextView label = new TextView(a);
            label.setText(labels[i]);
            label.setTextColor(on ? Ui.ACCENT : Ui.INK);
            label.setTextSize(16);
            Ui.font(label, on ? 700 : 500);
            row.addView(label, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (on) {
                TextView check = new TextView(a);
                check.setText("✓");
                check.setTextColor(Ui.ACCENT);
                check.setTextSize(16);
                Ui.font(check, 700);
                row.addView(check);
            }

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 52));
            rlp.topMargin = Ui.dpi(a, 4);
            box.addView(row, rlp);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onChoose(idx);
            });
        }

        TextView cancel = new TextView(a);
        cancel.setText("取消");
        cancel.setTextColor(Ui.INK_MUTED);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancel, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 46));
        clp.topMargin = Ui.dpi(a, 14);
        root.addView(cancel, clp);
        cancel.setOnClickListener(v -> dialog.dismiss());

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
            int width = a.getResources().getDisplayMetrics().widthPixels - Ui.dpi(a, 56);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            int maxH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.74f);
            root.post(() -> {
                if (root.getHeight() > maxH) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                            sc.getLayoutParams();
                    lp.height = maxH - Ui.dpi(a, 120);
                    sc.setLayoutParams(lp);
                    w.setLayout(width, maxH);
                }
            });
        }
    }
}
