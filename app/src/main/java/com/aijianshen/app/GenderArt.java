package com.aijianshen.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 引导页性别的全身站姿剪影，纯 Path 绘制，不占素材。
 *
 * <p>两种体型靠轮廓区分：男性宽肩窄胯、四肢粗；女性窄肩细腰宽胯、四肢细。
 * 视口固定 100×160，按控件尺寸等比缩放并居中，所以放多大都不会变形。
 *
 * <p>躯干与腿都是填充路径而不是等宽线条 —— 用 stroke 画腿会变成两根柱子，
 * 换成带锥度的四边形才有「腿」的样子。
 */
public class GenderArt extends View {

    public static final int MALE = 0;
    public static final int FEMALE = 1;

    private static final float VW = 100f;
    private static final float VH = 160f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final Path legL = new Path();
    private final Path legR = new Path();
    private final RectF head = new RectF();

    private int kind = MALE;
    private int color = Ui.SLATE;

    public GenderArt(Context c) {
        this(c, null);
    }

    public GenderArt(Context c, AttributeSet attrs) {
        super(c, attrs);
        fill.setStyle(Paint.Style.FILL);
        limb.setStyle(Paint.Style.STROKE);
        limb.setStrokeCap(Paint.Cap.ROUND);
        setColor(color);
    }

    public void setKind(int kind) {
        this.kind = kind;
        rebuild();
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        fill.setColor(color);
        limb.setColor(color);
        invalidate();
    }

    private void rebuild() {
        body.reset();
        legL.reset();
        legR.reset();
        if (kind == MALE) {
            head.set(36, 4, 64, 32);

            // 肩 36 宽、腰 28、胯 34
            body.moveTo(32, 47);
            body.quadTo(30, 41, 39, 41);
            body.lineTo(61, 41);
            body.quadTo(70, 41, 68, 47);
            body.lineTo(64, 84);
            body.lineTo(67, 95);
            body.lineTo(33, 95);
            body.lineTo(36, 84);
            body.close();

            // 大腿 14 宽收到脚踝 9
            legL.moveTo(47, 92);
            legL.lineTo(33, 92);
            legL.lineTo(37, 152);
            legL.lineTo(46, 152);
            legL.close();
            legR.moveTo(53, 92);
            legR.lineTo(67, 92);
            legR.lineTo(63, 152);
            legR.lineTo(54, 152);
            legR.close();
        } else {
            head.set(37.5f, 5, 62.5f, 31);

            // 肩 26、腰 12、胯 34
            body.moveTo(37, 47);
            body.quadTo(35, 41, 43, 41);
            body.lineTo(57, 41);
            body.quadTo(65, 41, 63, 47);
            body.lineTo(56, 74);
            body.lineTo(67, 93);
            body.lineTo(33, 93);
            body.lineTo(44, 74);
            body.close();

            // 大腿 12 宽收到脚踝 7.5
            legL.moveTo(46, 90);
            legL.lineTo(34, 90);
            legL.lineTo(38, 152);
            legL.lineTo(45.5f, 152);
            legL.close();
            legR.moveTo(54, 90);
            legR.lineTo(66, 90);
            legR.lineTo(62, 152);
            legR.lineTo(54.5f, 152);
            legR.close();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (body.isEmpty()) {
            rebuild();
        }
        float s = Math.min(w / VW, h / VH);
        canvas.save();
        canvas.translate((w - VW * s) / 2f, (h - VH * s) / 2f);
        canvas.scale(s, s);

        canvas.drawOval(head, fill);
        canvas.drawPath(body, fill);
        canvas.drawPath(legL, fill);
        canvas.drawPath(legR, fill);

        // 手臂向外张开，与躯干之间留出缝隙，否则整团糊在一起
        limb.setStrokeWidth(kind == MALE ? 10.5f : 9f);
        if (kind == MALE) {
            canvas.drawLine(33, 46, 23, 90, limb);
            canvas.drawLine(67, 46, 77, 90, limb);
        } else {
            canvas.drawLine(37, 46, 28, 88, limb);
            canvas.drawLine(63, 46, 72, 88, limb);
        }
        canvas.restore();
    }
}
