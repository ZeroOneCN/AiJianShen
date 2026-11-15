package com.yuanlian.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 线性图标：全部用 Canvas 画，保持与原包一致的细线风格。 */
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

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private int icon = SEARCH;
    private int color = Ui.INK;
    private float strokeDp = 2f;

    public IconView(Context c) {
        this(c, null);
    }

    public IconView(Context c, AttributeSet attrs) {
        super(c, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public int iconColor() {
        return color;
    }

    public void setStrokeDp(float dp) {
        strokeDp = dp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float unit = Math.min(w, h) / 24f;
        canvas.save();
        canvas.translate((w - 24f * unit) / 2f, (h - 24f * unit) / 2f);
        canvas.scale(unit, unit);
        paint.setColor(color);
        paint.setStrokeWidth(strokeDp);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(null);
        path.reset();
        switch (icon) {
            case CLOCK:
                circle(canvas, 12, 12, 8.6f);
                line(canvas, 12, 6.8f, 12, 12.2f);
                line(canvas, 12, 12.2f, 15.6f, 14.2f);
                break;
            case COMPASS:
                circle(canvas, 12, 12, 9f);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(16f, 8f);
                path.lineTo(13.2f, 13.2f);
                path.lineTo(8f, 16f);
                path.lineTo(10.8f, 10.8f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case CHART:
                paint.setStyle(Paint.Style.FILL);
                roundRect(canvas, 4f, 12f, 7.4f, 20f, 1.4f);
                roundRect(canvas, 9.9f, 8f, 13.3f, 20f, 1.4f);
                roundRect(canvas, 15.8f, 4.5f, 19.2f, 20f, 1.4f);
                break;
            case USER:
                circle(canvas, 12, 8.2f, 3.7f);
                path.moveTo(4.6f, 20.2f);
                path.cubicTo(5.4f, 15.7f, 8.2f, 14f, 12f, 14f);
                path.cubicTo(15.8f, 14f, 18.6f, 15.7f, 19.4f, 20.2f);
                canvas.drawPath(path, paint);
                break;
            case FLAME:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(12.4f, 2.5f);
                path.cubicTo(12.8f, 6.2f, 15.2f, 7.4f, 16.8f, 9.8f);
                path.cubicTo(18.8f, 12.8f, 18.6f, 16.4f, 16.2f, 19f);
                path.cubicTo(14.8f, 20.5f, 12.9f, 21.4f, 11.4f, 21.4f);
                path.cubicTo(7.6f, 21.4f, 5.1f, 18.6f, 5.1f, 15.1f);
                path.cubicTo(5.1f, 12.2f, 6.7f, 10.3f, 8.5f, 8.4f);
                path.cubicTo(9.4f, 7.5f, 9.8f, 6.5f, 9.7f, 4.9f);
                path.cubicTo(10.5f, 5.6f, 11.1f, 6.3f, 11.5f, 7.2f);
                path.cubicTo(11.9f, 5.9f, 12.2f, 4.4f, 12.4f, 2.5f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case CHEVRON:
                polyline(canvas, 9.3f, 5.5f, 15.8f, 12f, 9.3f, 18.5f);
                break;
            case ARROW:
                line(canvas, 3.8f, 12f, 19.4f, 12f);
                polyline(canvas, 14.2f, 6.8f, 19.4f, 12f, 14.2f, 17.2f);
                break;
            case PENCIL:
                path.moveTo(4.2f, 19.8f);
                path.lineTo(5.1f, 15.8f);
                path.lineTo(15.8f, 5.1f);
                path.lineTo(18.9f, 8.2f);
                path.lineTo(8.2f, 18.9f);
                path.close();
                canvas.drawPath(path, paint);
                line(canvas, 14.2f, 6.7f, 17.3f, 9.8f);
                break;
            case CALENDAR:
                roundRect(canvas, 3.6f, 5.6f, 20.4f, 20.4f, 2.4f);
                line(canvas, 3.6f, 9.8f, 20.4f, 9.8f);
                line(canvas, 8f, 3.2f, 8f, 7.2f);
                line(canvas, 16f, 3.2f, 16f, 7.2f);
                paint.setStyle(Paint.Style.FILL);
                circleFilled(canvas, 8f, 13.6f, 1.1f);
                circleFilled(canvas, 12f, 13.6f, 1.1f);
                circleFilled(canvas, 16f, 13.6f, 1.1f);
                circleFilled(canvas, 8f, 17.2f, 1.1f);
                circleFilled(canvas, 12f, 17.2f, 1.1f);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case TARGET:
                circle(canvas, 12, 12, 8.6f);
                circle(canvas, 12, 12, 5.2f);
                paint.setStyle(Paint.Style.FILL);
                circleFilled(canvas, 12, 12, 2f);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case CHECK:
                polyline(canvas, 4.8f, 12.4f, 9.8f, 17.4f, 19.4f, 6.6f);
                break;
            case DUMBBELL:
                line(canvas, 8f, 12f, 16f, 12f);
                line(canvas, 5.2f, 7.6f, 5.2f, 16.4f);
                line(canvas, 7.8f, 9.4f, 7.8f, 14.6f);
                line(canvas, 18.8f, 7.6f, 18.8f, 16.4f);
                line(canvas, 16.2f, 9.4f, 16.2f, 14.6f);
                break;
            case VIDEO:
                roundRect(canvas, 3.4f, 6.8f, 14.2f, 17.2f, 2.2f);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(15.6f, 10.4f);
                path.lineTo(20.6f, 7.8f);
                path.lineTo(20.6f, 16.2f);
                path.lineTo(15.6f, 13.6f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case MUSIC:
                line(canvas, 9.4f, 17.4f, 9.4f, 6.6f);
                line(canvas, 9.4f, 6.6f, 19f, 4.6f);
                line(canvas, 19f, 4.6f, 19f, 15.2f);
                circle(canvas, 6.4f, 17.4f, 3f);
                circle(canvas, 16f, 15.2f, 3f);
                break;
            case SETTINGS:
                circle(canvas, 12, 12, 3.4f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    line(canvas,
                            12 + (float) Math.cos(a) * 5.4f,
                            12 + (float) Math.sin(a) * 5.4f,
                            12 + (float) Math.cos(a) * 7.4f,
                            12 + (float) Math.sin(a) * 7.4f);
                }
                break;
            case BACK:
                line(canvas, 20f, 12f, 5f, 12f);
                polyline(canvas, 10.4f, 6.6f, 5f, 12f, 10.4f, 17.4f);
                break;
            case THUMB_UP:
                drawThumb(canvas, false);
                break;
            case THUMB_DOWN:
                drawThumb(canvas, true);
                break;
            case SKIP_NEXT:
                line(canvas, 19.2f, 6.4f, 19.2f, 17.6f);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(5f, 6.4f);
                path.lineTo(5f, 17.6f);
                path.lineTo(15f, 12f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case SKIP_PREV:
                line(canvas, 4.8f, 6.4f, 4.8f, 17.6f);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(19f, 6.4f);
                path.lineTo(19f, 17.6f);
                path.lineTo(9f, 12f);
                path.close();
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case PLAY:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(7.5f, 4.8f);
                path.lineTo(19.5f, 12f);
                path.lineTo(7.5f, 19.2f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case PAUSE:
                paint.setStyle(Paint.Style.FILL);
                roundRect(canvas, 6.6f, 4.8f, 10.2f, 19.2f, 1.4f);
                roundRect(canvas, 13.8f, 4.8f, 17.4f, 19.2f, 1.4f);
                break;
            case BOLT:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(13.4f, 2f);
                path.lineTo(4.8f, 14f);
                path.lineTo(10.6f, 14f);
                path.lineTo(9.8f, 22f);
                path.lineTo(19.4f, 9.6f);
                path.lineTo(13.2f, 9.6f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case SHARE:
                circle(canvas, 18f, 5.2f, 2.4f);
                circle(canvas, 6f, 12f, 2.4f);
                circle(canvas, 18f, 18.8f, 2.4f);
                line(canvas, 8.2f, 10.9f, 15.8f, 6.4f);
                line(canvas, 8.2f, 13.1f, 15.8f, 17.6f);
                break;
            case STAR:
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(12f, 2.6f);
                path.lineTo(14.9f, 8.7f);
                path.lineTo(21.4f, 9.6f);
                path.lineTo(16.7f, 14.2f);
                path.lineTo(17.9f, 20.7f);
                path.lineTo(12f, 17.5f);
                path.lineTo(6.1f, 20.7f);
                path.lineTo(7.3f, 14.2f);
                path.lineTo(2.6f, 9.6f);
                path.lineTo(9.1f, 8.7f);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case TRASH:
                line(canvas, 4.2f, 6.6f, 19.8f, 6.6f);
                line(canvas, 9.4f, 6.6f, 9.4f, 3.8f);
                line(canvas, 14.6f, 6.6f, 14.6f, 3.8f);
                path.moveTo(6.2f, 6.6f);
                path.lineTo(7.1f, 20.4f);
                path.lineTo(16.9f, 20.4f);
                path.lineTo(17.8f, 6.6f);
                canvas.drawPath(path, paint);
                line(canvas, 10f, 10.2f, 10.4f, 17f);
                line(canvas, 14f, 10.2f, 13.6f, 17f);
                break;
            case INFO:
                circle(canvas, 12, 12, 9f);
                line(canvas, 12, 10.6f, 12, 16.4f);
                paint.setStyle(Paint.Style.FILL);
                circleFilled(canvas, 12, 7.6f, 1.1f);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case CLOSE:
                line(canvas, 6f, 6f, 18f, 18f);
                line(canvas, 18f, 6f, 6f, 18f);
                break;
            case PLUS:
                line(canvas, 12f, 5f, 12f, 19f);
                line(canvas, 5f, 12f, 19f, 12f);
                break;
            case TROPHY:
                path.moveTo(7.2f, 3.8f);
                path.lineTo(16.8f, 3.8f);
                path.lineTo(16.8f, 10f);
                path.cubicTo(16.8f, 13.5f, 14.6f, 15.4f, 12f, 15.4f);
                path.cubicTo(9.4f, 15.4f, 7.2f, 13.5f, 7.2f, 10f);
                path.close();
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(7.2f, 5.6f);
                path.cubicTo(3.6f, 5.6f, 3.6f, 10.4f, 7.4f, 11f);
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(16.8f, 5.6f);
                path.cubicTo(20.4f, 5.6f, 20.4f, 10.4f, 16.6f, 11f);
                canvas.drawPath(path, paint);
                line(canvas, 12f, 15.4f, 12f, 19f);
                line(canvas, 7.8f, 20.8f, 16.2f, 20.8f);
                break;
            case REFRESH:
                oval.set(4.4f, 4.4f, 19.6f, 19.6f);
                canvas.drawArc(oval, -55f, 260f, false, paint);
                polyline(canvas, 17.2f, 3.2f, 19.8f, 7.8f, 14.2f, 8.4f);
                break;
            case SEARCH:
            default:
                circle(canvas, 10.6f, 10.6f, 6.5f);
                line(canvas, 15.4f, 15.4f, 20.8f, 20.8f);
                break;
        }
        canvas.restore();
    }

    private void drawThumb(Canvas canvas, boolean down) {
        canvas.save();
        if (down) {
            canvas.translate(0, 24);
            canvas.scale(1f, -1f);
        }
        roundRect(canvas, 3.4f, 10f, 7.4f, 19.4f, 1.2f);
        path.moveTo(9f, 19.4f);
        path.lineTo(15.2f, 19.4f);
        path.cubicTo(16.4f, 19.4f, 17.4f, 18.6f, 17.7f, 17.4f);
        path.lineTo(18.8f, 12.3f);
        path.cubicTo(19f, 11.1f, 18.1f, 10f, 16.9f, 10f);
        path.lineTo(12.8f, 10f);
        path.lineTo(13.4f, 6.9f);
        path.cubicTo(13.6f, 5.9f, 12.9f, 5f, 11.9f, 5f);
        path.cubicTo(11.2f, 5f, 10.6f, 5.4f, 10.3f, 6f);
        path.lineTo(9f, 9.2f);
        path.close();
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    private void circle(Canvas canvas, float cx, float cy, float r) {
        canvas.drawCircle(cx, cy, r, paint);
    }

    private void circleFilled(Canvas canvas, float cx, float cy, float r) {
        canvas.drawCircle(cx, cy, r, paint);
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private void polyline(Canvas canvas, float... pts) {
        path.reset();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) {
            path.lineTo(pts[i], pts[i + 1]);
        }
        canvas.drawPath(path, paint);
    }

    private void roundRect(Canvas canvas, float l, float t, float r, float b, float rad) {
        oval.set(l, t, r, b);
        canvas.drawRoundRect(oval, rad, rad, paint);
    }
}
