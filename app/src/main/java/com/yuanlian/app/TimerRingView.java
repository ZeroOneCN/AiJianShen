package com.yuanlian.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/**
 * 训练倒计时圆环：灰色轨道 + 彩色进度 + 中间大数字。
 *
 * 时间由内部逐帧推进（Choreographer），不再等外面 200ms 一次的整秒刷新：
 * 进度弧是连续滑过去的，数字换值时旧的向上淡出、新的从下方淡入。
 * 暂停 / 跳过 / +15 秒这类突变走同一条平滑通道，不会「咔」一下跳一格。
 */
public class TimerRingView extends View {

    private static final long DIGIT_MS = 150;
    private static final long CHECK_DELAY_MS = 80;
    private static final long CHECK_MS = 260;
    /** 单帧追赶系数：越大越跟手，越小越绵。 */
    private static final float FOLLOW = 0.30f;
    /** 目标值变化超过这个量才算「突变」，才需要平滑；正常倒计时零延迟。 */
    private static final float JUMP_MS = 400f;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path checkPath = new Path();
    private final DecelerateInterpolator ease = new DecelerateInterpolator(1.9f);

    /** 绘制用的剩余毫秒（可能正被平滑地追向 targetMs）。 */
    private float shownMs = 30000f;
    /** 逻辑上的剩余毫秒。 */
    private float targetMs = 30000f;
    private float lastTarget = 30000f;
    private boolean follow;
    private long totalMs = 30000L;
    private long deadline;
    private boolean live;

    /** 完成时环补满用的进度（0..1）。 */
    private float fill = 1f;

    private int ringColor = Ui.ACCENT;
    private boolean done;
    private long doneAt;

    private int digit = -1;
    private int prevDigit = -1;
    private long digitAt;

    private boolean posted;

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            posted = false;
            advance();
            if (wantsFrames()) {
                nudge();
            }
        }
    };

    public TimerRingView(Context c) {
        this(c, null);
    }

    public TimerRingView(Context c, AttributeSet attrs) {
        super(c, attrs);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(Ui.dp(c, 8));
        track.setColor(0xFFE9EAEE);
        bar.setStyle(Paint.Style.STROKE);
        bar.setStrokeWidth(Ui.dp(c, 8));
        bar.setStrokeCap(Paint.Cap.ROUND);
        bar.setColor(ringColor);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(Ui.ACCENT);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Ui.INK);
        text.setTypeface(Ui.typeface(c, 800));
    }

    // ---------------------------------------------------------------- 外部接口

    /** 从 now 起按 deadline 逐帧推进；totalMs 用来算进度弧的比例。 */
    public void setTime(long deadlineMs, long totalMs) {
        this.totalMs = Math.max(1L, totalMs);
        this.deadline = deadlineMs;
        done = false;
        if (!live) {
            live = true;
            targetMs = Math.max(0f, deadlineMs - SystemClock.elapsedRealtime());
            if (Math.abs(targetMs - shownMs) > JUMP_MS) {
                // 预备归零 → 动作开始：环从空补回满，别一帧跳上去
                follow = true;
            } else {
                shownMs = targetMs;
                follow = false;
            }
            lastTarget = targetMs;
            // 数字跟逻辑时间走，不跟着平滑后的环慢慢爬
            syncDigit(targetMs);
        }
        nudge();
    }

    /** 静态显示一个剩余量（暂停中、或外部手动指定），不再自行走表。 */
    public void setRemaining(long remainMs, long totalMs) {
        this.totalMs = Math.max(1L, totalMs);
        live = false;
        done = false;
        targetMs = Math.max(0f, remainMs);
        if (Math.abs(targetMs - shownMs) > 40f) {
            shownMs = targetMs;
        }
        lastTarget = targetMs;
        follow = false;
        syncDigit(targetMs);
        nudge();
    }

    /** 归零并停表（预备倒计时结束时的收尾）。 */
    public void clear() {
        live = false;
        done = false;
        follow = false;
        targetMs = 0f;
        lastTarget = 0f;
        shownMs = 0f;
        syncDigit(0f);
        nudge();
    }

    /** 训练完成：环从当前进度补满，随后淡入对勾。 */
    public void finish() {
        fill = totalMs <= 0 ? 0f : Math.max(0f, Math.min(1f, shownMs / totalMs));
        live = false;
        follow = false;
        targetMs = 0f;
        lastTarget = 0f;
        shownMs = 0f;
        syncDigit(0f);
        done = true;
        doneAt = SystemClock.uptimeMillis();
        nudge();
    }

    public void setRingColor(int color) {
        if (ringColor != color) {
            ringColor = color;
            nudge();
        }
    }

    // ---------------------------------------------------------------- 逐帧推进

    private void advance() {
        long now = SystemClock.uptimeMillis();
        if (live) {
            targetMs = Math.max(0f, deadline - SystemClock.elapsedRealtime());
        }
        if (Math.abs(targetMs - lastTarget) > JUMP_MS) {
            follow = true;
        }
        lastTarget = targetMs;
        if (follow) {
            shownMs += (targetMs - shownMs) * FOLLOW;
            if (Math.abs(targetMs - shownMs) < 6f) {
                shownMs = targetMs;
                follow = false;
            }
        } else {
            shownMs = targetMs;
        }
        if (shownMs < 0f) {
            shownMs = 0f;
        }
        syncDigit(targetMs);
        if (done && fill < 1f) {
            fill += (1f - fill) * 0.28f;
            if (1f - fill < 0.004f) {
                fill = 1f;
            }
        }
        invalidate();
    }

    private void syncDigit(float ms) {
        int sec = (int) Math.ceil(Math.max(0f, ms) / 1000.0);
        if (digit < 0) {
            digit = sec;
            prevDigit = sec;
            digitAt = 0;
        } else if (sec != digit) {
            prevDigit = digit;
            digit = sec;
            digitAt = SystemClock.uptimeMillis();
        }
    }

    private boolean wantsFrames() {
        if (posted || !isShown()) {
            return false;
        }
        if (live || follow) {
            return true;
        }
        if (done) {
            if (fill < 1f) {
                return true;
            }
            return SystemClock.uptimeMillis() - doneAt < CHECK_DELAY_MS + CHECK_MS;
        }
        return digitAt > 0 && SystemClock.uptimeMillis() - digitAt < DIGIT_MS;
    }

    private void nudge() {
        invalidate();
        if (posted || !wantsFrames()) {
            return;
        }
        posted = true;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        nudge();
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frame);
        posted = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE) {
            nudge();
        }
    }

    // ---------------------------------------------------------------- 绘制

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

        float p = done ? fill : (totalMs <= 0 ? 0f : Math.max(0f, Math.min(1f, shownMs / totalMs)));
        bar.setColor(ringColor);
        if (p > 0.004f) {
            canvas.drawArc(box, -90f, 360f * p, false, bar);
        }
        if (done) {
            drawCheck(canvas, size);
            return;
        }
        drawDigits(canvas, size);
    }

    private void drawDigits(Canvas canvas, float size) {
        long now = SystemClock.uptimeMillis();
        String cur = label(digit);
        String prev = label(prevDigit);
        boolean anim = digitAt > 0 && now - digitAt < DIGIT_MS && !cur.equals(prev);
        float t = anim ? (now - digitAt) / (float) DIGIT_MS : 1f;
        float e = ease.getInterpolation(t);

        text.setTextSize(size * 0.38f);
        float wide = text.measureText(cur);
        float limit = size * 0.74f;
        if (wide > limit) {
            // 「1:30」这种会顶到环上，按需缩一点
            text.setTextSize(size * 0.38f * (limit / wide));
        }
        float base = size / 2f - (text.ascent() + text.descent()) / 2f;
        float slide = size * 0.085f;

        canvas.save();
        canvas.clipRect(size * 0.08f, size * 0.14f, size * 0.92f, size * 0.86f);
        if (anim) {
            // 旧的先加速淡出、整体向上滑；新的压着它从下方顶上来
            float out = 1f - e;
            text.setAlpha((int) (255 * out * out));
            canvas.drawText(prev, size / 2f, base - slide * e, text);
            text.setAlpha((int) (255 * e));
            canvas.drawText(cur, size / 2f, base + slide * (1f - e), text);
        } else {
            text.setAlpha(255);
            canvas.drawText(cur, size / 2f, base, text);
        }
        canvas.restore();
        text.setAlpha(255);
    }

    private void drawCheck(Canvas canvas, float size) {
        long now = SystemClock.uptimeMillis();
        float t = (now - doneAt - CHECK_DELAY_MS) / (float) CHECK_MS;
        if (t <= 0f) {
            return;
        }
        float e = ease.getInterpolation(Math.min(1f, t));
        tick.setStrokeWidth(size * 0.055f);
        tick.setColor(ringColor);
        checkPath.reset();
        checkPath.moveTo(size * 0.36f, size * 0.51f);
        checkPath.lineTo(size * 0.46f, size * 0.61f);
        checkPath.lineTo(size * 0.65f, size * 0.40f);
        canvas.save();
        float s = 0.9f + 0.1f * e;
        canvas.scale(s, s, size / 2f, size / 2f);
        tick.setAlpha((int) (255 * e));
        canvas.drawPath(checkPath, tick);
        canvas.restore();
        tick.setAlpha(255);
    }

    private String label(int sec) {
        if (sec < 0) {
            return "";
        }
        if (totalMs >= 60000L) {
            return String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60);
        }
        return String.valueOf(sec);
    }
}
