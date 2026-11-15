package com.yuanlian.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 训练倒计时圆环：灰色轨道 + 蓝色进度 + 中间大数字。 */
public class TimerRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path checkPath = new Path();
    private float progress = 1f;
    private int seconds = 30;
    private int ringColor = Ui.ACCENT;
    private boolean done;

    public TimerRingView(Context c) {
        this(c, null);
    }

    public TimerRingView(Context c, AttributeSet attrs) {
        super(c, attrs);
        float w = Ui.dp(c, 8);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(0xFFE9EAEE);
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeWidth(w);
        bar.setStrokeCap(Paint.Cap.ROUND);
        bar.setColor(ringColor);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(Ui.ACCENT);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Ui.INK);
        text.setTypeface(Ui.typeface(c, 800));
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public void setSeconds(int s) {
        seconds = Math.max(0, s);
        invalidate();
    }

    public void setRingColor(int color) {
        ringColor = color;
        bar.setColor(color);
        invalidate();
    }

    public void setDone(boolean value) {
        done = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float size = Math.min(w, h);
        float inset = Ui.dp(getContext(), 5);
        box.set(inset, inset, size - inset, size - inset);
        canvas.drawArc(box, 0f, 360f, false, track);
        if (progress > 0f) {
            canvas.drawArc(box, -90f, 360f * progress, false, bar);
        }
        if (done) {
            tick.setStrokeWidth(size * 0.055f);
            checkPath.reset();
            checkPath.moveTo(size * 0.36f, size * 0.51f);
            checkPath.lineTo(size * 0.46f, size * 0.61f);
            checkPath.lineTo(size * 0.65f, size * 0.40f);
            canvas.drawPath(checkPath, tick);
            return;
        }
        text.setTextSize(size * 0.38f);
        float baseline = size / 2f - (text.ascent() + text.descent()) / 2f;
        canvas.drawText(String.valueOf(seconds), size / 2f, baseline, text);
    }
}
