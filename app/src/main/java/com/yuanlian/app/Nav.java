package com.yuanlian.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 底部四栏导航与系统栏适配。锻炼 / 发现 / 报告 / 设置。 */
public final class Nav {

    public static final int TAB_WORKOUT = 0;
    public static final int TAB_DISCOVER = 1;
    public static final int TAB_REPORT = 2;
    public static final int TAB_SETTINGS = 3;

    private static final String[] LABELS = {"锻炼", "发现", "报告", "设置"};
    private static final int[] ICONS = {
            IconView.DUMBBELL, IconView.COMPASS, IconView.CHART, IconView.USER
    };
    private static final Class<?>[] TARGETS = {
            MainActivity.class, DiscoverActivity.class, ReportActivity.class, SettingsActivity.class
    };

    private Nav() {
    }

    public static void chrome(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        View decor = w.getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
    }

    /** 把状态栏和导航栏高度补到根布局上，targetSdk 36 的全面屏也安全。 */
    public static void insets(final View root) {
        final int pl = root.getPaddingLeft();
        final int pt = root.getPaddingTop();
        final int pr = root.getPaddingRight();
        final int pb = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, wi) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = wi.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = wi.getSystemWindowInsetTop();
                bottom = wi.getSystemWindowInsetBottom();
            }
            v.setPadding(pl, pt + top, pr, pb + bottom);
            return wi;
        });
        root.requestApplyInsets();
    }

    public static LinearLayout bar(final Activity a, int selected) {
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(0xFFFCFCFD);

        View divider = new View(a);
        divider.setBackgroundColor(0xFFEDEEF1);
        wrap.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(a, 1))));

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 64)));

        for (int i = 0; i < LABELS.length; i++) {
            final int tab = i;
            boolean on = i == selected;
            LinearLayout item = new LinearLayout(a);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackground(press(a));
            item.setClickable(true);
            item.setFocusable(true);

            IconView icon = new IconView(a);
            icon.setIcon(ICONS[i]);
            icon.setColor(on ? Ui.ACCENT : Ui.INK_SOFT);
            icon.setStrokeDp(on ? 2.2f : 2f);
            item.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dpi(a, 24), Ui.dpi(a, 24)));

            TextView label = new TextView(a);
            label.setText(LABELS[i]);
            label.setTextSize(11);
            label.setTextColor(on ? Ui.ACCENT : Ui.INK_SOFT);
            Ui.font(label, on ? 600 : 400);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = Ui.dpi(a, 2);
            item.addView(label, llp);

            item.setOnClickListener(v -> {
                if (tab == selected) {
                    return;
                }
                Intent it = new Intent(a, TARGETS[tab]);
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                a.startActivity(it);
                a.overridePendingTransition(0, 0);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            row.addView(item, lp);
        }
        return wrap;
    }

    public static RippleDrawable press(Activity a) {
        return new RippleDrawable(ColorStateList.valueOf(0x14000000), null,
                new ColorDrawable(Color.WHITE));
    }
}
