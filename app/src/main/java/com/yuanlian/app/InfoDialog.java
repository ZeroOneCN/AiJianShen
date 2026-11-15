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

/** 简单说明弹窗：标题、正文、知道了。 */
public final class InfoDialog {

    private InfoDialog() {
    }

    public static void show(Activity a, String titleText, String message) {
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

        TextView ok = new TextView(a);
        ok.setText("知道了");
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(15);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(a, Ui.gradientBluePill(a), 0x33FFFFFF));
        Ui.font(ok, 700);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 48));
        olp.topMargin = Ui.dpi(a, 20);
        root.addView(ok, olp);
        ok.setOnClickListener(v -> dialog.dismiss());

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
