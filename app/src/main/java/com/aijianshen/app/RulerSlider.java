package com.aijianshen.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** 引导页的横向刻度选择器：浅蓝轨道 + 圆点 + 蓝色选中环。 */
public class RulerSlider extends View {

    public interface OnIndexChanged {
        void onChanged(int index);
    }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotOn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    /** 左右留白：绘制与触摸必须用同一个值，否则点到第 2 格会跳到第 3 格。 */
    private static final float SIDE_DP = 14f;
    private int count = 5;
    private int index = 0;
    private OnIndexChanged listener;

    public RulerSlider(Context c) {
        this(c, null);
    }

    public RulerSlider(Context c, AttributeSet attrs) {
        super(c, attrs);
        track.setStyle(Paint.Style.FILL);
        track.setColor(0xFFEDF2FE);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(0xFFD5D8DE);
        knob.setStyle(Paint.Style.FILL);
        knob.setColor(Ui.ACCENT);
        knobInner.setStyle(Paint.Style.FILL);
        knobInner.setColor(Ui.SURFACE);
        active.setStyle(Paint.Style.FILL);
        active.setColor(Ui.ACCENT);
        dotOn.setStyle(Paint.Style.FILL);
        dotOn.setColor(0xB3FFFFFF);
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
        invalidate();
    }

    public int index() {
        return index;
    }

    public void setIndex(int index) {
        this.index = Math.max(0, Math.min(count - 1, index));
        invalidate();
    }

    public void setOnIndexChanged(OnIndexChanged l) {
        listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float side = Ui.dp(getContext(), SIDE_DP);
        float trackH = Ui.dp(getContext(), 20);
        float top = (h - trackH) / 2f;
        box.set(side, top, w - side, top + trackH);
        canvas.drawRoundRect(box, trackH / 2f, trackH / 2f, track);

        float step = count <= 1 ? 0 : (w - side * 2) / (count - 1);
        float knobX = side + step * index;
        // 已选段填充，明确「选到了哪儿」，而不是只看一个小圆环
        if (index > 0) {
            RectF fill = new RectF(box.left, box.top, knobX, box.bottom);
            canvas.drawRoundRect(fill, trackH / 2f, trackH / 2f, active);
        }
        for (int i = 0; i < count; i++) {
            float x = side + step * i;
            float cy = h / 2f;
            if (i == index) {
                canvas.drawCircle(x, cy, Ui.dp(getContext(), 7.6f), knob);
                canvas.drawCircle(x, cy, Ui.dp(getContext(), 4.4f), knobInner);
            } else {
                canvas.drawCircle(x, cy, Ui.dp(getContext(), 2.8f), i < index ? dotOn : dot);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN
                || e.getAction() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float side = Ui.dp(getContext(), SIDE_DP);
            float usable = Math.max(1f, getWidth() - side * 2);
            int i = Math.round((e.getX() - side) / usable * (count - 1));
            i = Math.max(0, Math.min(count - 1, i));
            if (i != index) {
                index = i;
                invalidate();
                if (listener != null) {
                    listener.onChanged(index);
                }
            }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }
}
