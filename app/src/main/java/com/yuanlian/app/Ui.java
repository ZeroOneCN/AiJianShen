package com.yuanlian.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * 圆润设计系统：颜色、圆角、字体、通用形状。
 * 视觉语言参考自常见圆角卡片式健身 App 的公开设计模式，素材与配色为本项目自有。
 */
public final class Ui {

    // ---- 颜色（取自设计规格：bgColor / colorDark / colorAccent / gray_888）----
    public static final int INK       = 0xFF1C212C;
    public static final int INK_SOFT  = 0xFF888888;
    public static final int INK_MUTED = 0xFF666666;
    public static final int BG        = 0xFFF8F8F8;
    public static final int SURFACE   = 0xFFFFFFFF;
    public static final int ACCENT    = 0xFF004AFF;
    /** 渐变浅端，横向 0°。 */
    public static final int ACCENT_2  = 0xFF0075FF;
    public static final int ACCENT_HI = 0xFF0B9EFF;
    public static final int ACCENT_SOFT = 0xFFE9EFFF;
    public static final int ACCENT_RING = 0xFFD9E4FF;
    public static final int SEARCH    = 0xFFF2F3F5;
    public static final int CARD_SOFT = 0xFFF4F5F7;
    public static final int CREAM     = 0xFFFCE7C8;
    public static final int CREAM_INK = 0xFF7A4B00;
    public static final int GREEN     = 0xFF2B8A6E;
    public static final int GREEN_2   = 0xFF3AA98C;
    public static final int GREEN_SOFT= 0xFFE7F5F0;
    public static final int RED       = 0xFFFF5B4A;
    public static final int ORANGE    = 0xFFF5A623;
    public static final int BLUE_TILE = 0xFF3E8BFF;
    public static final int TEAL      = 0xFF27B7B0;
    public static final int PURPLE    = 0xFF7D5CE6;
    public static final int SLATE     = 0xFF6C7A89;
    public static final int DARK_1    = 0xFF343434;
    public static final int DARK_2    = 0xFF0B0B0B;
    public static final int LINE      = 0xFFEBEBEB;
    public static final int WHITE_80  = 0xCCFFFFFF;
    public static final int WHITE_60  = 0x99FFFFFF;

    // ---- 圆角（dp）----
    public static final float R_HERO = 22.5f;
    public static final float R_CARD = 18.75f;
    public static final float R_CHIP = 17f;
    public static final float R_TILE = 13f;
    public static final float R_SHEET = 28f;
    public static final float R_PILL = 75f;

    // ---- 字号（sp）：正文 14/13/12，标题 20/21，数字 52 ----
    public static final float T_TITLE = 21f;
    public static final float T_HEAD = 20f;
    public static final float T_BODY = 14f;
    public static final float T_SUB = 13f;
    public static final float T_CAP = 12f;

    private static final Map<Integer, Typeface> TYPEFACES = new HashMap<>();

    private Ui() {
    }

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    /** 可变字体按字重取实例，Outfit 为 SIL OFL 授权。 */
    public static Typeface typeface(Context c, int weight) {
        Typeface cached = TYPEFACES.get(weight);
        if (cached != null) {
            return cached;
        }
        Typeface tf;
        try {
            AssetManager am = c.getAssets();
            // 只在可变字体轴上设字重；setWeight(int) 是 API 28 才有的，minSdk 26 不能用。
            tf = new Typeface.Builder(am, "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            // 中文走系统字体回退，需要带 bold 样式才会真正加粗。
            if (weight >= 600) {
                tf = Typeface.create(tf, Typeface.BOLD);
            }
        } catch (Throwable t) {
            tf = Typeface.DEFAULT;
        }
        TYPEFACES.put(weight, tf);
        return tf;
    }

    public static void font(TextView tv, int weight) {
        tv.setTypeface(typeface(tv.getContext(), weight));
    }

    /** 纯色圆角背景。 */
    public static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(Context c, int color, float radiusDp, int strokeColor) {
        GradientDrawable d = round(c, color, radiusDp);
        d.setStroke(Math.max(1, dpi(c, 1)), strokeColor);
        return d;
    }

    /** 主色渐变胶囊，用于主行动按钮：横向 0°，浅端在左。 */
    public static GradientDrawable gradientPill(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT_2, ACCENT});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, R_PILL));
        return d;
    }

    /** 首页大卡：与胶囊同一组渐变端点，圆角按大卡规格。 */
    public static GradientDrawable gradientHero(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT_2, ACCENT});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, R_HERO));
        return d;
    }

    /** 数据卡渐变：更亮的天蓝端，用于连续天数一类的强调块。 */
    public static GradientDrawable gradientStreak(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT_HI, ACCENT});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, R_CARD));
        return d;
    }

    /** 引导页的主行动按钮：深色渐变胶囊。 */
    public static GradientDrawable gradientDarkPill(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF3A3A3A, DARK_2});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, R_PILL));
        return d;
    }

    /** 蓝色实心渐变胶囊，和原包 CTA 同一条蓝。 */
    public static GradientDrawable gradientBluePill(Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT_2, ACCENT});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, R_PILL));
        return d;
    }

    public static GradientDrawable gradientCard(Context c, int start, int end, float radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable gradientVertical(Context c, int top, int bottom,
                                                    float radiusDp) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 卡片阴影：规格里最大 elevation 6dp，常规卡片用 2dp。 */
    public static void elevate(android.view.View v, float dp) {
        v.setElevation(dp(v.getContext(), dp));
    }

    public static void textSize(TextView tv, float sp) {
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** 给形状加上水波纹反馈。 */
    public static Drawable ripple(Context c, GradientDrawable content, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }
}
