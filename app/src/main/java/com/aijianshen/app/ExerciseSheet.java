package com.aijianshen.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 动作学习卡：练之前先看清楚这一个动作怎么做、容易错在哪。
 *
 * 数据全部来自动作库自带的 steps / mistakes / breathingTip —— 这些字段一直在，
 * 之前只在动作库里躺着，日详情页点一下就直接开练了。
 */
public final class ExerciseSheet {

    public interface OnAction {
        /** 练这个（进入单动作训练，和原来的行为一致）。 */
        void onStart();

        /** 换成别的动作。 */
        void onReplace();
    }

    private ExerciseSheet() {
    }

    public static void show(Activity a, Exercise e, OnAction action) {
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        float r = Ui.dp(a, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(bg);
        int ph = Ui.dpi(a, 18);
        root.setPadding(ph, Ui.dpi(a, 10), ph, Ui.dpi(a, 16));

        View handle = new View(a);
        handle.setBackground(Ui.round(a, 0xFFE2E5EA, 3f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                Ui.dpi(a, 42), Ui.dpi(a, 5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dpi(a, 12);
        root.addView(handle, hlp);

        // 标题行：名称占满，右侧一个关闭
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(head);

        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        head.addView(names, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(a);
        title.setText(e.name);
        title.setTextColor(Ui.INK);
        title.setTextSize(21);
        Ui.font(title, 800);
        names.addView(title);

        TextView meta = new TextView(a);
        meta.setText(e.part + " · " + (e.needsGear() ? e.gear : "徒手"));
        meta.setTextColor(Ui.INK_SOFT);
        meta.setTextSize(12.5f);
        meta.setPadding(0, Ui.dpi(a, 3), 0, 0);
        Ui.font(meta, 400);
        names.addView(meta);

        FrameLayout close = new FrameLayout(a);
        close.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 16f), 0x22000000));
        close.setClickable(true);
        head.addView(close, new LinearLayout.LayoutParams(Ui.dpi(a, 32), Ui.dpi(a, 32)));
        IconView x = new IconView(a);
        x.setIcon(IconView.CLOSE);
        x.setColor(Ui.INK_SOFT);
        close.addView(x, new FrameLayout.LayoutParams(
                Ui.dpi(a, 14), Ui.dpi(a, 14), Gravity.CENTER));
        close.setOnClickListener(v -> dialog.dismiss());

        ScrollView sc = new ScrollView(a);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, Ui.dpi(a, 14), 0, 0);
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 首帧大图
        FrameLayout pic = new FrameLayout(a);
        pic.setBackground(Ui.round(a, Ui.CARD_SOFT, 20f));
        body.addView(pic, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 158)));
        PhotoView photo = new PhotoView(a);
        photo.setRadiusDp(20f);
        photo.setAnimated(false);
        photo.bind(e);
        pic.addView(photo, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 步骤
        List<String> steps = e.steps;
        if (steps != null && !steps.isEmpty()) {
            blockTitle(a, body, "怎么做", "按顺序跟着做，不用记");
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            body.addView(list);
            for (int i = 0; i < steps.size(); i++) {
                list.addView(stepRow(a, i + 1, steps.get(i)));
            }
        }

        // 常见错误
        List<String> mistakes = e.mistakes;
        if (mistakes != null && !mistakes.isEmpty()) {
            blockTitle(a, body, "容易错在这儿", "做的时候对着检查一下");
            LinearLayout warn = new LinearLayout(a);
            warn.setOrientation(LinearLayout.VERTICAL);
            warn.setBackground(Ui.round(a, Ui.ACCENT_SOFT, 16f));
            int wp = Ui.dpi(a, 13);
            warn.setPadding(wp, Ui.dpi(a, 10), wp, Ui.dpi(a, 10));
            body.addView(warn);
            for (String one : mistakes) {
                TextView t = new TextView(a);
                t.setText("· " + one);
                t.setTextColor(Ui.INK);
                t.setTextSize(13.5f);
                t.setLineSpacing(0f, 1.35f);
                t.setPadding(0, Ui.dpi(a, 3), 0, Ui.dpi(a, 3));
                Ui.font(t, 400);
                warn.addView(t);
            }
        }

        String breath = e.breathingTip();
        if (breath != null && !breath.isEmpty()) {
            TextView t = new TextView(a);
            t.setText("呼吸：" + breath);
            t.setTextColor(Ui.INK_SOFT);
            t.setTextSize(12.5f);
            t.setLineSpacing(0f, 1.35f);
            t.setPadding(0, Ui.dpi(a, 16), 0, Ui.dpi(a, 4));
            Ui.font(t, 400);
            body.addView(t);
        }

        // 底部两个动作
        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dpi(a, 16);
        root.addView(actions, alp);

        TextView replace = new TextView(a);
        replace.setText("换一个");
        replace.setTextColor(Ui.INK);
        replace.setTextSize(15);
        replace.setGravity(Gravity.CENTER);
        replace.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x22000000));
        Ui.font(replace, 600);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                0, Ui.dpi(a, 50), 1f);
        rlp.rightMargin = Ui.dpi(a, 10);
        actions.addView(replace, rlp);
        replace.setOnClickListener(v -> {
            dialog.dismiss();
            action.onReplace();
        });

        TextView start = new TextView(a);
        start.setText("练这个");
        start.setTextColor(Color.WHITE);
        start.setTextSize(15);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ripple(a, Ui.gradient(a, Ui.ACCENT, 24f), 0x33FFFFFF));
        Ui.font(start, 700);
        actions.addView(start, new LinearLayout.LayoutParams(
                0, Ui.dpi(a, 50), 1.6f));
        start.setOnClickListener(v -> {
            dialog.dismiss();
            action.onStart();
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        dialog.show();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.86f));
        }
    }

    private static void blockTitle(Activity a, LinearLayout parent, String title, String sub) {
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextColor(Ui.INK);
        t.setTextSize(15);
        t.setPadding(0, Ui.dpi(a, 18), 0, Ui.dpi(a, 2));
        Ui.font(t, 700);
        parent.addView(t);

        TextView s = new TextView(a);
        s.setText(sub);
        s.setTextColor(Ui.INK_SOFT);
        s.setTextSize(12);
        s.setPadding(0, 0, 0, Ui.dpi(a, 8));
        Ui.font(s, 400);
        parent.addView(s);
    }

    /** 数据里没有"常见错误"这一栏，那就把步骤里真正带约束的句子标出来。 */
    private static final String[] KEYS = {
            "保持", "不要", "避免", "注意", "缓慢", "慢慢", "收紧", "挺直", "控制"
    };

    private static CharSequence emphasize(String text) {
        SpannableString ss = new SpannableString(text);
        for (String key : KEYS) {
            int from = 0;
            while (true) {
                int i = text.indexOf(key, from);
                if (i < 0) {
                    break;
                }
                ss.setSpan(new StyleSpan(Typeface.BOLD), i, i + key.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from = i + key.length();
            }
        }
        return ss;
    }

    private static View stepRow(Activity a, int no, String text) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Context c = a;
        TextView num = new TextView(c);
        num.setText(String.valueOf(no));
        num.setTextColor(Ui.ACCENT);
        num.setTextSize(11);
        num.setGravity(Gravity.CENTER);
        num.setBackground(Ui.round(c, Ui.ACCENT_SOFT, 11f));
        Ui.font(num, 700);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                Ui.dpi(c, 20), Ui.dpi(c, 20));
        nlp.topMargin = Ui.dpi(c, 2);
        nlp.rightMargin = Ui.dpi(c, 10);
        row.addView(num, nlp);

        TextView t = new TextView(c);
        t.setText(emphasize(text));
        t.setTextColor(Ui.INK);
        t.setTextSize(14);
        t.setLineSpacing(0f, 1.4f);
        Ui.font(t, 400);
        row.addView(t, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dpi(c, 9);
        row.setLayoutParams(rlp);
        return row;
    }
}
