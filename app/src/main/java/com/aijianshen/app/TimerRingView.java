package com.aijianshen.app;

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
 * 进度弧是连续滑过去的；数字换值时逐位比对，只有内容变了的位做一次短滚动，
 * 其余位保持静止——59 倒数到 50 的过程中十位不动，只有个位在走。
 * 暂停 / 跳过 / +15 秒这类突变走同一条平滑通道，不会「咔」一下跳一格。
 */
public class TimerRingView extends View {

    private static final long DIGIT_MS = 210;
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

    /** 当前整串文本；换值时逐位比对，只有内容变了的位才滚。 */
    private String label = "";
    private String prevLabel = "";
    private int digit = -1;
    private long digitAt;

    /** 逐位布局的临时缓冲，避免每帧 new。 */
    private final char[] one = new char[1];
    private final float[] slot = new float[12];

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
        setTotal(totalMs);
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
        setTotal(totalMs);
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

    /** 总时长变了意味着显示格式可能从「秒」切到「M:SS」，数字得重建。 */
    private void setTotal(long total) {
        long t = Math.max(1L, total);
        if (t != totalMs) {
            totalMs = t;
            digit = -1;
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
        if (sec == digit) {
            return;
        }
        String cur = fmt(sec);
        boolean first = digit < 0 || label.isEmpty();
        prevLabel = first ? cur : label;
        label = cur;
        digit = sec;
        digitAt = first ? 0 : SystemClock.uptimeMillis();
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
        String cur = label;
        if (cur.isEmpty()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        boolean anim = digitAt > 0 && now - digitAt < DIGIT_MS && !cur.equals(prevLabel);
        float t = anim ? Math.min(1f, (now - digitAt) / (float) DIGIT_MS) : 1f;
        float e = anim ? smooth(t) : 1f;

        canvas.save();
        canvas.clipRect(size * 0.08f, size * 0.14f, size * 0.92f, size * 0.86f);
        if (anim && prevLabel.length() != cur.length()) {
            drawWhole(canvas, size, cur, prevLabel, e);
        } else {
            drawPerChar(canvas, size, cur, anim ? prevLabel : cur, anim, e);
        }
        canvas.restore();
        text.setAlpha(255);
    }

    /** 首尾都缓的 smoothstep：位移小的时候比减速插值更柔，不会一上来就窜一下。 */
    private static float smooth(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }

    /** 位数变了（9:59 → 10:00）：整串一起滚，逐位比对没有意义。 */
    private void drawWhole(Canvas canvas, float size, String cur, String prev, float e) {
        float ts = size * 0.38f;
        float limit = size * 0.74f;
        text.setTextSize(ts);
        float w = Math.max(text.measureText(prev), text.measureText(cur));
        if (w > limit) {
            text.setTextSize(ts * (limit / w));
        }
        float base = size / 2f - (text.ascent() + text.descent()) / 2f;
        float slide = size * 0.08f;
        text.setAlpha((int) (255 * Math.pow(1f - e, 1.35)));
        canvas.drawText(prev, size / 2f, base + slide * e, text);
        text.setAlpha((int) (255 * Math.pow(e, 1.15)));
        canvas.drawText(cur, size / 2f, base - slide * (1f - e), text);
    }

    /**
     * 逐位绘制：内容没变的位一像素都不动，只有变了的位滚动。
     * 方向向下——旧数字往下走、新数字从上方来，和「数字在减少」一致，
     * 51→50 时十位纹丝不动，个位像里程表的末位那样走一格。
     */
    private void drawPerChar(Canvas canvas, float size, String cur, String prev, boolean anim, float e) {
        float ts = size * 0.38f;
        float limit = size * 0.74f;
        float total = layout(cur, ts);
        if (total > limit) {
            total = layout(cur, ts * (limit / total));
        }
        float base = size / 2f - (text.ascent() + text.descent()) / 2f;
        float slide = size * 0.16f;
        // 旧的走得快一点、新的立得早一点：位移小而两字重叠时，中段不会糊成一团
        float outA = (float) Math.pow(1f - e, 2.6);
        float inA = (float) Math.pow(e, 0.5);
        float x = size / 2f - total / 2f;

        for (int i = 0; i < cur.length(); i++) {
            char c = cur.charAt(i);
            float cx = x + slot[i] / 2f;
            if (!anim || i >= prev.length() || prev.charAt(i) == c) {
                text.setAlpha(255);
                one[0] = c;
                canvas.drawText(one, 0, 1, cx, base, text);
            } else {
                text.setAlpha((int) (255 * outA));
                one[0] = prev.charAt(i);
                // 旧字基本原地淡出（只让 3 成位移），笔画不会和新字交叉成一团
                canvas.drawText(one, 0, 1, cx, base + slide * 0.3f * e, text);
                text.setAlpha((int) (255 * inA));
                one[0] = c;
                canvas.drawText(one, 0, 1, cx, base - slide * (1f - e), text);
            }
            x += slot[i];
        }
    }

    /** 铺一遍每个字符的槽宽（数字统一取最宽的那个，换值时整行不会左右抖），返回总宽。 */
    private float layout(String s, float ts) {
        text.setTextSize(ts);
        float num = 0f;
        for (int d = 0; d < 10; d++) {
            one[0] = (char) ('0' + d);
            num = Math.max(num, text.measureText(one, 0, 1));
        }
        float total = 0f;
        for (int i = 0; i < s.length() && i < slot.length; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                slot[i] = num;
            } else {
                one[0] = c;
                slot[i] = text.measureText(one, 0, 1);
            }
            total += slot[i];
        }
        return total;
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

    private String fmt(int sec) {
        if (sec < 0) {
            return "";
        }
        if (totalMs >= 60000L) {
            return String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60);
        }
        return String.valueOf(sec);
    }
}
