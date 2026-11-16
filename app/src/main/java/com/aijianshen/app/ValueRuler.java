package com.aijianshen.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

/**
 * 横向刻度尺：指针固定在中央，拖动刻度改数值，松手吸附到刻度。
 *
 * <p>与 {@link RulerSlider} 的区别：那个是「固定 N 个圆点里挑一个」，这个是有起止区间的
 * 连续数值，用于身高体重年龄这类需要精确到个位甚至 0.1 的场合。
 *
 * <p>刻度分长短两级：{@code minorStep} 一根短刻度，{@code majorStep} 一根长刻度并带数字。
 * 两者可以互质（比如体重短刻度 0.5kg、长刻度 5kg），数值精度另有 {@code step} 决定。
 */
public class ValueRuler extends View {

    public interface OnValueChanged {
        void onChanged(float value);
    }

    /** 动画种类，决定 computeScroll 里的正负号与收尾动作。 */
    private static final int ANIM_NONE = 0;
    private static final int ANIM_FLING = 1;
    private static final int ANIM_SNAP = 2;

    private final Paint minorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Scroller scroller;

    private float min = 0f;
    private float max = 100f;
    /** 取值精度，也是吸附步长。 */
    private float step = 1f;
    private float minorStep = 1f;
    private float majorStep = 5f;
    /** 每一单位值占多少 dp，越大越「松散」。 */
    private float dpPerUnit = 12f;

    /** 连续当前值，拖动过程中是小数，吸附后落在 step 的整数倍上。 */
    private float cur = 0f;
    private float unitPx = 1f;

    private int animKind = ANIM_NONE;
    private float animBase = 0f;

    private float lastX;
    private boolean dragging;
    private VelocityTracker tracker;
    private OnValueChanged listener;
    private float lastNotified = Float.NaN;

    public ValueRuler(Context c) {
        this(c, null);
    }

    public ValueRuler(Context c, AttributeSet attrs) {
        super(c, attrs);
        minorPaint.setStyle(Paint.Style.FILL);
        minorPaint.setColor(0xFFD5D8DE);
        majorPaint.setStyle(Paint.Style.FILL);
        majorPaint.setColor(0xFFB4B9C2);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(Ui.INK_SOFT);
        labelPaint.setTextSize(Ui.dp(c, 12));
        labelPaint.setTypeface(Ui.typeface(c, 500));
        pointerPaint.setColor(Ui.ACCENT);
        fadePaint.setShader(new android.graphics.LinearGradient(
                0, 0, 0, 1, 0x00000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP));
        scroller = new Scroller(c);
        scroller.setFriction(0.028f);
    }

    /**
     * @param min        最小值
     * @param max        最大值
     * @param step       精度（吸附步长）
     * @param minorStep  短刻度间隔
     * @param majorStep  长刻度间隔（同时也是数字间隔）
     * @param dpPerUnit  每单位占多少 dp
     * @param value      初始值
     */
    public void configure(float min, float max, float step, float minorStep,
                          float majorStep, float dpPerUnit, float value) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.minorStep = minorStep;
        this.majorStep = majorStep;
        this.dpPerUnit = dpPerUnit;
        this.cur = clamp(value);
        this.lastNotified = Float.NaN;
        invalidate();
    }

    public void setOnValueChanged(OnValueChanged l) {
        listener = l;
    }

    /** 外部设值（例如体脂秤读数），不触发回调，避免和调用方来回打乒乓。 */
    public void setValue(float value, boolean animate) {
        float target = clamp(Math.round(value / step) * step);
        float delta = Math.abs(target - cur);
        if (!animate || delta < step * 0.5f) {
            scroller.forceFinished(true);
            animKind = ANIM_NONE;
            cur = target;
            invalidate();
            return;
        }
        scroller.forceFinished(true);
        animBase = cur;
        animKind = ANIM_SNAP;
        scroller.startScroll(0, 0, Math.round((target - cur) * unitPx()), 0,
                (int) Math.min(600f, 180f + delta * 4f));
        postInvalidateOnAnimation();
    }

    public float value() {
        return Math.round(cur / step) * step;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        unitPx = Ui.dp(getContext(), dpPerUnit);
    }

    private float unitPx() {
        if (unitPx <= 1f) {
            unitPx = Ui.dp(getContext(), dpPerUnit);
        }
        return unitPx;
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float upx = unitPx();
        float minorLen = Ui.dp(getContext(), 7);
        float majorLen = Ui.dp(getContext(), 14);
        float baseline = majorLen + Ui.dp(getContext(), 18);

        // 只画可见区间，左右各多留一格避免边缘跳动
        float leftV = cur - cx / upx - minorStep;
        float rightV = cur + cx / upx + minorStep;
        int i0 = (int) Math.floor(leftV / minorStep);
        int i1 = (int) Math.ceil(rightV / minorStep);

        for (int i = i0; i <= i1; i++) {
            float v = i * minorStep;
            if (v < min - 0.001f || v > max + 0.001f) {
                continue;
            }
            float x = cx + (v - cur) * upx;
            if (x < -majorLen || x > w + majorLen) {
                continue;
            }
            boolean major = Math.abs(v - Math.round(v / majorStep) * majorStep)
                    < minorStep * 0.01f;
            float len = major ? majorLen : minorLen;
            float half = Ui.dp(getContext(), major ? 1.1f : 0.8f);
            canvas.drawRect(x - half, 0, x + half, len, major ? majorPaint : minorPaint);
            if (major) {
                canvas.drawText(number(v), x, baseline, labelPaint);
            }
        }

        // 指针：中央竖线压在所有刻度之上
        float pw = Ui.dp(getContext(), 2.4f);
        float ph = Ui.dp(getContext(), 20);
        canvas.drawRoundRect(cx - pw / 2f, -Ui.dp(getContext(), 2), cx + pw / 2f, ph,
                pw / 2f, pw / 2f, pointerPaint);
    }

    private String number(float v) {
        if (Math.abs(v - Math.round(v)) < 0.01f) {
            return String.valueOf(Math.round(v));
        }
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                animKind = ANIM_NONE;
                dragging = true;
                lastX = e.getX();
                if (tracker == null) {
                    tracker = VelocityTracker.obtain();
                }
                tracker.clear();
                tracker.addMovement(e);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return true;
                }
                float dx = e.getX() - lastX;
                lastX = e.getX();
                cur = clamp(cur - dx / unitPx());
                notifyChanged();
                invalidate();
                if (tracker != null) {
                    tracker.addMovement(e);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                float vx = 0f;
                if (tracker != null) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    vx = tracker.getXVelocity();
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (Math.abs(vx) > 420f) {
                    animBase = cur;
                    animKind = ANIM_FLING;
                    scroller.fling(0, 0, (int) vx, 0,
                            Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, 0, 0);
                    postInvalidateOnAnimation();
                } else {
                    snap();
                }
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    /** 吸附到最近的刻度。 */
    private void snap() {
        float target = clamp(Math.round(cur / step) * step);
        float deltaPx = (target - cur) * unitPx();
        if (Math.abs(deltaPx) < 0.6f) {
            cur = target;
            notifyChanged();
            invalidate();
            return;
        }
        animBase = cur;
        animKind = ANIM_SNAP;
        scroller.forceFinished(true);
        scroller.startScroll(0, 0, Math.round(deltaPx), 0, 210);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (animKind == ANIM_NONE) {
            return;
        }
        if (scroller.computeScrollOffset()) {
            float moved = scroller.getCurrX() / unitPx();
            float next = clamp(animBase + (animKind == ANIM_FLING ? -moved : moved));
            boolean hitEdge = next <= min || next >= max;
            cur = next;
            notifyChanged();
            invalidate();
            if (hitEdge && Math.abs(moved) > 0.5f) {
                // 撞到区间边界就不再滑，直接进吸附
                scroller.forceFinished(true);
            }
            postInvalidateOnAnimation();
            return;
        }
        int kind = animKind;
        animKind = ANIM_NONE;
        if (kind == ANIM_FLING) {
            snap();
        } else {
            cur = clamp(Math.round(cur / step) * step);
            notifyChanged();
            invalidate();
        }
    }

    private void notifyChanged() {
        float rounded = Math.round(cur / step) * step;
        if (Float.isNaN(lastNotified) || Math.abs(rounded - lastNotified) > step * 0.5f) {
            lastNotified = rounded;
            if (listener != null) {
                listener.onChanged(rounded);
            }
        }
    }
}
