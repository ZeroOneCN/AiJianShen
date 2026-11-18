package com.aijianshen.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

/**
 * 线性图标。
 *
 * 图形本身在 res/drawable/ic_*.xml 里，是 24 视口的 VectorDrawable（SVG 路径），
 * 这里只负责按常数取图、染色、铺满控件。换图标只要改 XML，不必再动 Java。
 */
public class IconView extends View {

    public static final int SEARCH = 1;
    public static final int CLOCK = 2;
    public static final int COMPASS = 3;
    public static final int CHART = 4;
    public static final int USER = 5;
    public static final int FLAME = 6;
    public static final int CHEVRON = 7;
    public static final int ARROW = 8;
    public static final int PENCIL = 9;
    public static final int CALENDAR = 10;
    public static final int TARGET = 11;
    public static final int CHECK = 12;
    public static final int DUMBBELL = 13;
    public static final int VIDEO = 14;
    public static final int MUSIC = 15;
    public static final int SETTINGS = 16;
    public static final int BACK = 17;
    public static final int THUMB_UP = 18;
    public static final int THUMB_DOWN = 19;
    public static final int SKIP_NEXT = 20;
    public static final int SKIP_PREV = 21;
    public static final int PLAY = 22;
    public static final int PAUSE = 23;
    public static final int BOLT = 24;
    public static final int SHARE = 25;
    public static final int STAR = 26;
    public static final int TRASH = 27;
    public static final int INFO = 28;
    public static final int CLOSE = 29;
    public static final int PLUS = 30;
    public static final int TROPHY = 31;
    public static final int REFRESH = 32;
    public static final int LEGS = 33;
    public static final int ARM = 34;
    public static final int CARDIO = 37;
    public static final int STRETCH = 38;
    public static final int SLIDERS = 39;
    public static final int SPEAKER = 40;
    public static final int SHIELD = 41;
    public static final int RACK = 42;
    public static final int WIFI = 43;
    public static final int SCALE = 44;
    public static final int MORE = 45;
    public static final int BOOKMARK = 46;
    public static final int SWAP = 47;

    private int icon = SEARCH;
    private int color = Ui.INK;
    private Drawable drawable;

    public IconView(Context c) {
        this(c, null);
    }

    public IconView(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    public void setIcon(int icon) {
        if (this.icon != icon) {
            this.icon = icon;
            drawable = null;
            // 给读屏软件一个可念的名字，别让它只会读「未标记」
            String label = labelOf(icon);
            if (label != null && getContentDescription() == null) {
                setContentDescription(label);
            }
        }
        invalidate();
    }

    private static String labelOf(int icon) {
        switch (icon) {
            case BACK: return "返回";
            case SEARCH: return "搜索";
            case REFRESH: return "同步";
            case CLOSE: return "关闭";
            case PLAY: return "播放";
            case PAUSE: return "暂停";
            case SKIP_NEXT: return "跳过";
            case SKIP_PREV: return "上一个";
            case ARROW: return "继续";
            case CHEVRON: return "展开";
            case PLUS: return "添加";
            case PENCIL: return "编辑";
            case TRASH: return "删除";
            case SHARE: return "分享";
            case CHECK: return "完成";
            case INFO: return "说明";
            case THUMB_UP: return "太轻松";
            case THUMB_DOWN: return "太吃力";
            case DUMBBELL: return "训练";
            case FLAME: return "挑战";
            case TROPHY: return "成就";
            case TARGET: return "目标";
            case BOLT: return "冲刺";
            case STAR: return "收藏";
            case BOOKMARK: return "收藏";
            case SWAP: return "换一个";
            case MORE: return "更多";
            default: return null;
        }
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public int iconColor() {
        return color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (drawable == null) {
            int res = resOf(icon);
            if (res == 0) {
                return;
            }
            drawable = getContext().getDrawable(res);
            if (drawable == null) {
                return;
            }
        }
        // 保持图标为正方形并居中，避免在非正方形控件里被拉变形
        int side = Math.min(w, h);
        int left = (w - side) / 2;
        int top = (h - side) / 2;
        drawable.setBounds(left, top, left + side, top + side);
        drawable.setTint(color);
        drawable.draw(canvas);
    }

    private static int resOf(int icon) {
        switch (icon) {
            case CLOCK: return R.drawable.ic_clock;
            case COMPASS: return R.drawable.ic_compass;
            case CHART: return R.drawable.ic_chart;
            case USER: return R.drawable.ic_user;
            case FLAME: return R.drawable.ic_flame;
            case CHEVRON: return R.drawable.ic_chevron;
            case ARROW: return R.drawable.ic_arrow;
            case PENCIL: return R.drawable.ic_pencil;
            case CALENDAR: return R.drawable.ic_calendar;
            case TARGET: return R.drawable.ic_target;
            case CHECK: return R.drawable.ic_check;
            case DUMBBELL: return R.drawable.ic_dumbbell;
            case VIDEO: return R.drawable.ic_video;
            case MUSIC: return R.drawable.ic_music;
            case SETTINGS: return R.drawable.ic_settings;
            case BACK: return R.drawable.ic_back;
            case THUMB_UP: return R.drawable.ic_thumb_up;
            case THUMB_DOWN: return R.drawable.ic_thumb_down;
            case SKIP_NEXT: return R.drawable.ic_skip_next;
            case SKIP_PREV: return R.drawable.ic_skip_prev;
            case PLAY: return R.drawable.ic_play;
            case PAUSE: return R.drawable.ic_pause;
            case BOLT: return R.drawable.ic_bolt;
            case SHARE: return R.drawable.ic_share;
            case STAR: return R.drawable.ic_star;
            case TRASH: return R.drawable.ic_trash;
            case INFO: return R.drawable.ic_info;
            case CLOSE: return R.drawable.ic_close;
            case PLUS: return R.drawable.ic_plus;
            case TROPHY: return R.drawable.ic_trophy;
            case REFRESH: return R.drawable.ic_refresh;
            case LEGS: return R.drawable.ic_legs;
            case ARM: return R.drawable.ic_arm;
            case CARDIO: return R.drawable.ic_cardio;
            case STRETCH: return R.drawable.ic_stretch;
            case SLIDERS: return R.drawable.ic_sliders;
            case SPEAKER: return R.drawable.ic_speaker;
            case SHIELD: return R.drawable.ic_shield;
            case RACK: return R.drawable.ic_rack;
            case WIFI: return R.drawable.ic_wifi;
            case SCALE: return R.drawable.ic_scale;
            case MORE: return R.drawable.ic_more;
            case BOOKMARK: return R.drawable.ic_bookmark;
            case SWAP: return R.drawable.ic_swap;
            case SEARCH:
            default: return R.drawable.ic_search;
        }
    }
}
