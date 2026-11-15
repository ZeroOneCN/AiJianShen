package com.yuanlian.app;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** 设置类页面的通用行、卡片与按钮，保持四栏导航一致的观感。 */
public final class UiList {

    private UiList() {
    }

    public static LinearLayout page(Activity a) {
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setId(R.id.root);
        return root;
    }

    public static LinearLayout topBar(Activity a, String title, View.OnClickListener back) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dpi(a, 20);
        bar.setPadding(p, Ui.dpi(a, 14), p, Ui.dpi(a, 6));

        FrameLayout btn = new FrameLayout(a);
        btn.setBackground(Ui.ripple(a, Ui.round(a, Ui.SURFACE, 20f), 0x22000000));
        btn.setClickable(true);
        bar.addView(btn, new LinearLayout.LayoutParams(Ui.dpi(a, 40), Ui.dpi(a, 40)));
        IconView ic = new IconView(a);
        ic.setIcon(IconView.BACK);
        ic.setColor(Ui.INK);
        ic.setStrokeDp(2f);
        btn.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(a, 20), Ui.dpi(a, 20), Gravity.CENTER));
        btn.setOnClickListener(back);

        TextView tv = new TextView(a);
        tv.setText(title);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(21);
        tv.setIncludeFontPadding(false);
        Ui.font(tv, 800);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(a, 12);
        bar.addView(tv, tlp);
        return bar;
    }

    public static TextView subtitle(Activity a, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(Ui.INK_SOFT);
        tv.setTextSize(13);
        tv.setLineSpacing(Ui.dp(a, 3), 1f);
        int p = Ui.dpi(a, 20);
        tv.setPadding(p, Ui.dpi(a, 2), p, Ui.dpi(a, 10));
        Ui.font(tv, 400);
        return tv;
    }

    public static LinearLayout card(Activity a) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(a, Ui.SURFACE, 20f));
        return card;
    }

    public static class Row {
        public final LinearLayout view;
        public final TextView sub;
        public final Switch toggle;

        Row(LinearLayout view, TextView sub, Switch toggle) {
            this.view = view;
            this.sub = sub;
            this.toggle = toggle;
        }
    }

    public static Row row(Activity a, LinearLayout card, int icon, int color,
                          String title, String sub, boolean last,
                          View.OnClickListener click) {
        return row(a, card, icon, color, title, sub, last, click, null);
    }

    /** 返回这一行右侧的开关；不需要开关时 listener 传 null。 */
    public static Row row(Activity a, LinearLayout card, int icon, int color,
                          String title, String sub, boolean last,
                          View.OnClickListener click,
                          CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Nav.press(a));
        row.setClickable(true);
        row.setFocusable(true);
        int p = Ui.dpi(a, 14);
        row.setPadding(p, p, Ui.dpi(a, 16), p);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout tile = new FrameLayout(a);
        tile.setBackground(Ui.round(a, color, 11f));
        row.addView(tile, new LinearLayout.LayoutParams(Ui.dpi(a, 40), Ui.dpi(a, 40)));
        IconView ic = new IconView(a);
        ic.setIcon(icon);
        ic.setColor(Color.WHITE);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(a, 22), Ui.dpi(a, 22), Gravity.CENTER));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(a, 14);
        row.addView(texts, tlp);

        TextView name = new TextView(a);
        name.setText(title);
        name.setTextColor(Ui.INK);
        name.setTextSize(16);
        Ui.font(name, 500);
        texts.addView(name);

        TextView subView = null;
        if (sub != null && !sub.isEmpty()) {
            subView = new TextView(a);
            subView.setText(sub);
            subView.setTextColor(Ui.INK_SOFT);
            subView.setTextSize(12);
            subView.setPadding(0, Ui.dpi(a, 2), 0, 0);
            Ui.font(subView, 400);
            texts.addView(subView);
        }

        Switch sw = null;
        if (listener != null) {
            sw = new Switch(a);
            sw.setShowText(false);
            sw.setChecked(false);
            // 系统的开关轨迹自带半透明（实测只剩 26% 不透明度，开着也像半开）。
            // 轨道用 SRC 染色直接得到给的实色；滑块保持默认染色模式，白色圆点的投影才在。
            sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            sw.setTrackTintList(new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{Ui.ACCENT, 0xFFD7DBE2}));
            sw.setTrackTintMode(android.graphics.PorterDuff.Mode.SRC);
            sw.setOnCheckedChangeListener(listener);
            row.addView(sw, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dpi(a, 40)));
            row.setClickable(false);
        } else {
            IconView chevron = new IconView(a);
            chevron.setIcon(IconView.CHEVRON);
            chevron.setColor(0xFFC7CBD1);
            row.addView(chevron, new LinearLayout.LayoutParams(
                    Ui.dpi(a, 17), Ui.dpi(a, 17)));
        }

        if (click != null) {
            row.setOnClickListener(click);
            if (sw != null) {
                sw.setOnClickListener(click);
            }
        }
        card.addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!last) {
            View divider = new View(a);
            divider.setBackgroundColor(0xFFEFEFF2);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(a, 1)));
            dlp.leftMargin = Ui.dpi(a, 68);
            card.addView(divider, dlp);
        }
        return new Row(wrap, subView, sw);
    }

    public static void label(Activity a, LinearLayout parent, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(17);
        tv.setPadding(0, Ui.dpi(a, 22), 0, Ui.dpi(a, 10));
        Ui.font(tv, 700);
        parent.addView(tv);
    }

    public static void hint(Activity a, LinearLayout parent, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(Ui.INK_SOFT);
        tv.setTextSize(12);
        tv.setLineSpacing(Ui.dp(a, 3), 1f);
        tv.setPadding(Ui.dpi(a, 4), Ui.dpi(a, 12), Ui.dpi(a, 4), 0);
        Ui.font(tv, 400);
        parent.addView(tv);
    }

    public static TextView primary(Activity a, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Ui.ripple(a, Ui.gradientBluePill(a), 0x33FFFFFF));
        Ui.font(tv, 700);
        return tv;
    }

    public static TextView secondary(Activity a, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x22000000));
        Ui.font(tv, 600);
        return tv;
    }

    public static LinearLayout.LayoutParams lp(Activity a, int heightDp, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, heightDp));
        lp.topMargin = Ui.dpi(a, topMarginDp);
        return lp;
    }
}
