package com.yuanlian.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 最近七天的训练分钟折线图，替代原包体重图的位置。 */
public class QuickChartView extends View {

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path area = new Path();
    private final RectF bubbleBox = new RectF();
    private int[] values = new int[7];

    public QuickChartView(Context c) {
        this(c, null);
    }

    public QuickChartView(Context c, AttributeSet attrs) {
        super(c, attrs);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Ui.dp(c, 1));
        grid.setColor(0xFFECEDF0);
        grid.setPathEffect(new DashPathEffect(new float[]{Ui.dp(c, 3), Ui.dp(c, 4)}, 0f));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2.4f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(Ui.ACCENT);
        fill.setStyle(Paint.Style.FILL);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Ui.ACCENT);
        dotInner.setStyle(Paint.Style.FILL);
        dotInner.setColor(Ui.SURFACE);
        bubble.setStyle(Paint.Style.FILL);
        bubble.setColor(0xFF2B3038);
        label.setColor(Ui.SURFACE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Ui.typeface(c, 600));
        label.setTextSize(Ui.dp(c, 10.5f));
    }

    public void setValues(int[] data) {
        values = data == null ? new int[7] : data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float left = Ui.dp(getContext(), 4);
        float right = w - Ui.dp(getContext(), 4);
        float top = Ui.dp(getContext(), 22);
        float bottom = h - Ui.dp(getContext(), 8);
        int max = 1;
        for (int v : values) {
            max = Math.max(max, v);
        }
        max = (int) Math.ceil(max * 1.25f);

        for (int i = 0; i < 4; i++) {
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, grid);
        }

        int n = Math.max(1, values.length);
        float step = n == 1 ? 0 : (right - left) / (n - 1);
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = left + step * i;
            ys[i] = bottom - (bottom - top) * (values[i] / (float) max);
        }

        path.reset();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            path.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }
        area.set(path);
        area.lineTo(xs[n - 1], bottom);
        area.lineTo(xs[0], bottom);
        area.close();
        fill.setShader(new LinearGradient(0, top, 0, bottom,
                new int[]{0x33004AFF, 0x00004AFF}, null, Shader.TileMode.CLAMP));
        canvas.drawPath(area, fill);
        canvas.drawPath(path, line);

        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], ys[i], Ui.dp(getContext(), 3.4f), dot);
            canvas.drawCircle(xs[i], ys[i], Ui.dp(getContext(), 1.5f), dotInner);
        }

        int last = n - 1;
        String txt = values[last] + " 分";
        float tw = label.measureText(txt);
        float bw = tw + Ui.dp(getContext(), 16);
        float bh = Ui.dp(getContext(), 22);
        float bx = Math.max(left, Math.min(right - bw, xs[last] - bw / 2f));
        float by = Math.max(0, ys[last] - bh - Ui.dp(getContext(), 9));
        bubbleBox.set(bx, by, bx + bw, by + bh);
        canvas.drawRoundRect(bubbleBox, bh / 2f, bh / 2f, bubble);
        float baseline = by + bh / 2f - (label.ascent() + label.descent()) / 2f;
        canvas.drawText(txt, bx + bw / 2f, baseline, label);
    }
}
