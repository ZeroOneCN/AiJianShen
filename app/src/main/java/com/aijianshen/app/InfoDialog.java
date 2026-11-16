package com.aijianshen.app;

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

/** 简单说明弹窗：标题、正文、知道了。 */
public final class InfoDialog {

    private InfoDialog() {
    }

    public static void show(Activity a, String titleText, String message) {
        show(a, titleText, message, null, null, 0, false);
    }

    /** 确认弹窗：标题 + 正文 + 取消 / 主操作。 */
    public static void confirm(Activity a, String titleText, String message,
                               String primary, int primaryColor, Runnable action) {
        show(a, titleText, message, primary, "取消", primaryColor, true, action);
    }

    public static void show(Activity a, String titleText, String message,
                            String primary, String cancelText, int primaryColor,
                            boolean withCancel) {
        show(a, titleText, message, primary, cancelText, primaryColor, withCancel, null);
    }

    private static void show(Activity a, String titleText, String message,
                             String primary, String cancelText, int primaryColor,
                             boolean withCancel, Runnable action) {
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(a);
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

        ScrollView sc = new ScrollView(a);
        sc.setVerticalScrollBarEnabled(false);
        TextView body = new TextView(a);
        body.setText(message);
        body.setTextColor(Ui.INK_SOFT);
        body.setTextSize(14);
        body.setLineSpacing(Ui.dp(a, 4), 1f);
        int bp = Ui.dpi(a, 10);
        body.setPadding(0, bp, 0, 0);
        Ui.font(body, 400);
        sc.addView(body);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(sc, slp);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 48));
        blp.topMargin = Ui.dpi(a, 20);
        root.addView(buttons, blp);

        if (withCancel) {
            TextView cancel = new TextView(a);
            cancel.setText(cancelText == null ? "取消" : cancelText);
            cancel.setTextColor(Ui.INK_MUTED);
            cancel.setTextSize(15);
            cancel.setGravity(Gravity.CENTER);
            cancel.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x14000000));
            Ui.font(cancel, 600);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            clp.rightMargin = Ui.dpi(a, 10);
            buttons.addView(cancel, clp);
            cancel.setOnClickListener(v -> dialog.dismiss());
        }

        TextView ok = new TextView(a);
        ok.setText(primary == null ? "知道了" : primary);
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(15);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(a, withCancel
                ? Ui.round(a, primaryColor == 0 ? Ui.ACCENT : primaryColor, 24f)
                : Ui.gradientBluePill(a), 0x33FFFFFF));
        Ui.font(ok, 700);
        LinearLayout.LayoutParams olp = withCancel
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        buttons.addView(ok, olp);
        ok.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
            dialog.dismiss();
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
            int width = a.getResources().getDisplayMetrics().widthPixels - Ui.dpi(a, 48);
            int maxH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.7f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            root.post(() -> {
                if (root.getHeight() > maxH) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                            sc.getLayoutParams();
                    lp.height = maxH - Ui.dpi(a, 130);
                    sc.setLayoutParams(lp);
                    w.setLayout(width, maxH);
                }
            });
        }
    }
}
