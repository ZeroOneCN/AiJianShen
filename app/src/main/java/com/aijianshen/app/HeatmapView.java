package com.aijianshen.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 训练热力图：近 12 周 × 7 天，每格一天，颜色按当天分钟数分档。
 * 只负责画，数据由外部 setData 传进来。
 */
public class HeatmapView extends View {

    public interface OnDayClick {
        void onDay(String key, int index);
    }

    private static final int COLS = 12;
    private static final int ROWS = 7;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int[] minutes = new int[COLS * ROWS];
    private String[] keys = new String[COLS * ROWS];
    private OnDayClick listener;
    private float side;
    private float gap;

    public HeatmapView(Context c) {
        super(c);
        gap = Ui.dp(c, 3.5f);
    }

    /** 从最旧到最新，共 COLS*ROWS 个。 */
    public void setData(String[] keys, int[] minutes) {
        if (keys == null || minutes == null
                || keys.length < COLS * ROWS || minutes.length < COLS * ROWS) {
            return;
        }
        this.keys = keys;
        this.minutes = minutes;
        invalidate();
    }

    public void setOnDayClick(OnDayClick l) {
        this.listener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int pad = getPaddingLeft() + getPaddingRight();
        side = (w - pad - gap * (COLS - 1)) / COLS;
        int h = (int) (side * ROWS + gap * (ROWS - 1))
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = getPaddingLeft();
        float top = getPaddingTop();
        for (int i = 0; i < COLS * ROWS; i++) {
            int col = i / ROWS;
            int row = i % ROWS;
            float x = left + col * (side + gap);
            float y = top + row * (side + gap);
            rect.set(x, y, x + side, y + side);
            paint.setColor(colorFor(minutes[i]));
            float r = Math.max(Ui.dp(getContext(), 3f), side * 0.22f);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }

    /** 0 / 1–9 / 10–19 / 20–34 / 35+ 分钟，五档。 */
    private int colorFor(int m) {
        if (m <= 0) {
            return Ui.CARD_SOFT;
        }
        if (m < 10) {
            return 0xFFD9EDE4;
        }
        if (m < 20) {
            return 0xFF95C9B4;
        }
        if (m < 35) {
            return 0xFF4FA184;
        }
        return Ui.GREEN;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null || side <= 0) {
            return super.onTouchEvent(e);
        }
        // View 默认不消费 DOWN，父容器就不会再把 UP 派发过来，点击等于完全没反应。
        // 这里主动吃掉 DOWN，UP 时再判断落在哪一格；滑动仍由外层 ScrollView 拦截。
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float x = e.getX() - getPaddingLeft();
        float y = e.getY() - getPaddingTop();
        if (x < 0 || y < 0) {
            return false;
        }
        // 落在格子之间的缝里也算旁边那一格：格子只有 70 来像素宽，
        // 缝占了近三成面积，精确到像素才响应会让人觉得「点不动」。
        int col = (int) (x / (side + gap));
        int row = (int) (y / (side + gap));
        if (col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return false;
        }
        int index = col * ROWS + row;
        if (keys[index] == null) {
            return false;
        }
        performClick();
        listener.onDay(keys[index], minutes[index]);
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
