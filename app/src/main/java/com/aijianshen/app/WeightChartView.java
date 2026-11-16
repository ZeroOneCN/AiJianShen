package com.aijianshen.app;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 体重趋势：折线 + 面积渐变 + 最新值气泡。 */
public class WeightChartView extends View {

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubble = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Float> values = new ArrayList<>();
    private final List<String> dates = new ArrayList<>();
    private String unit = "kg";

    public WeightChartView(Context c) {
        this(c, null);
    }

    public WeightChartView(Context c, AttributeSet attrs) {
        super(c, attrs);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2.4f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(Ui.ACCENT);

        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Ui.dp(c, 1));
        grid.setColor(0xFFE9EBEF);
        grid.setPathEffect(new DashPathEffect(new float[]{Ui.dp(c, 4), Ui.dp(c, 5)}, 0));

        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Ui.ACCENT);

        text.setTextSize(Ui.dp(c, 10));
        text.setColor(Ui.INK_SOFT);

        bubble.setStyle(Paint.Style.FILL);
        bubble.setColor(0xFF1F2733);
    }

    public void setData(List<Store.WeightEntry> list, String unit) {
        values.clear();
        dates.clear();
        this.unit = unit == null ? "kg" : unit;
        int start = Math.max(0, list.size() - 10);
        for (int i = start; i < list.size(); i++) {
            Store.WeightEntry e = list.get(i);
            values.add(Store.isLb(getContext()) ? e.kg * 2.20462f : e.kg);
            dates.add(e.date);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float left = Ui.dp(getContext(), 34);
        float right = w - Ui.dp(getContext(), 10);
        float top = Ui.dp(getContext(), 22);
        float bottom = h - Ui.dp(getContext(), 20);

        if (values.isEmpty()) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Ui.INK_SOFT);
            canvas.drawText("记录一次体重，这里会出现趋势", w / 2f,
                    (top + bottom) / 2f, text);
            return;
        }

        float min = values.get(0);
        float max = values.get(0);
        for (float v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        // 刻度落在 step 的整数倍上：4 条线正好 3 格，顶线取 ≤(中点+1.5格) 的整数倍，
        // 这样标签永远是 75.4/75.2/75.0/74.8 这类值，不会出现 75.3/75.1 跳数。
        float spread = max - min;
        float step = niceStep(Math.max(0.15f, spread * 0.4f));
        float mid = (min + max) / 2f;
        float topTick = (float) Math.ceil((mid + step * 1.5f) / step) * step;
        max = topTick;
        min = topTick - step * 3f;
        float span = max - min;

        text.setColor(Ui.INK_SOFT);
        text.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 3; i++) {
            float y = top + (bottom - top) * i / 3f;
            canvas.drawLine(left, y, right, y, grid);
            float v = max - span * i / 3f;
            canvas.drawText(String.format(Locale.US, "%.1f", v), left - Ui.dp(getContext(), 6),
                    y + Ui.dp(getContext(), 3.5f), text);
        }

        int n = values.size();
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = n == 1 ? (left + right) / 2f
                    : left + (right - left) * i / (float) (n - 1);
            ys[i] = bottom - (values.get(i) - min) / span * (bottom - top);
        }

        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            path.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i]);
        }

        if (n > 1) {
            Path area = new Path(path);
            area.lineTo(xs[n - 1], bottom);
            area.lineTo(xs[0], bottom);
            area.close();
            fill.setShader(new LinearGradient(0, top, 0, bottom,
                    new int[]{0x33004AFF, 0x00004AFF}, null, Shader.TileMode.CLAMP));
            canvas.drawPath(area, fill);
            canvas.drawPath(path, line);
        }

        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], ys[i], Ui.dp(getContext(), i == n - 1 ? 4.2f : 3f), dot);
        }

        if (n > 1) {
            // 最新值气泡（只有一个点时数值在卡片上方已经写了大字，不用再浮一个）
            String last = String.format(Locale.US, "%.1f", values.get(n - 1));
            text.setTextAlign(Paint.Align.CENTER);
            float tw = text.measureText(last);
            float bw = tw + Ui.dp(getContext(), 20);
            float bh = Ui.dp(getContext(), 24);
            float bx = Math.min(Math.max(xs[n - 1], left + bw / 2), right - bw / 2);
            float by = Math.max(top - Ui.dp(getContext(), 2), ys[n - 1] - Ui.dp(getContext(), 18) - bh);
            RectF r = new RectF(bx - bw / 2, by, bx + bw / 2, by + bh);
            canvas.drawRoundRect(r, bh / 2, bh / 2, bubble);
            text.setColor(0xFFFFFFFF);
            canvas.drawText(last, bx, by + bh * 0.68f, text);
        }

        // 日期：单点居中，多点分列两端
        text.setColor(Ui.INK_SOFT);
        if (n == 1) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(shortDate(dates.get(0)), (left + right) / 2f,
                    h - Ui.dp(getContext(), 5), text);
        } else {
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(shortDate(dates.get(0)), left, h - Ui.dp(getContext(), 5), text);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(shortDate(dates.get(n - 1)), right, h - Ui.dp(getContext(), 5), text);
        }
    }

    /** 把任意刻度间距归到 0.2 / 0.5 / 1 / 2 / 5 这类「整」数上。 */
    private float niceStep(float raw) {
        float[] steps = {0.2f, 0.5f, 1f, 2f, 5f, 10f, 20f, 50f};
        for (float st : steps) {
            if (raw <= st) {
                return st;
            }
        }
        return 100f;
    }

    private String shortDate(String key) {
        try {
            SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = parse.parse(key);
            if (d != null) {
                return new SimpleDateFormat("M/d", Locale.CHINA).format(d);
            }
        } catch (Exception ignored) {
        }
        return key;
    }
}
