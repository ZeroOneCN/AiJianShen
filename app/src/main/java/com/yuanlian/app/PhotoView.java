package com.yuanlian.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 动作演示。
 *
 * 静态图为 960x960，列表按采样率解码；详情页和训练页播放 640x640 动态 WebP
 * （AnimatedImageDrawable，Android 9+；8.x 自动退回静态图）。
 *
 * 每张图在画面里的人物大小并不一致（原片留白差别很大），所以离线算了一份
 * 「主体框」索引 assets/boxes.json，绘制时按框裁切对齐——同一张卡片里，
 * 不同动作看起来就是同一个人、同一个取景距离。
 */
public class PhotoView extends View {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    /** 归一化的主体框 l,t,r,b；缺失时按整图处理。 */
    private static final Map<String, float[]> BOX = new HashMap<>();
    private static final float[] FULL = {0f, 0f, 1f, 1f};
    private static boolean boxLoaded;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final Path borderPath = new Path();
    private final RectF box = new RectF();
    private final RectF dst = new RectF();

    private Bitmap still;
    private AnimatedImageDrawable anim;
    private String key = "";
    private int requestedSample = -1;
    private boolean animated = true;
    private float radiusDp = Ui.R_CARD;
    private float aspect = 0f;
    private float zoom = 1f;
    private float progress = -1f;
    private int bgColor = 0xFFFFFFFF;
    private int trackColor = 0x33000000;
    private int outlineColor = 0;
    private float outlineDp = 1.5f;

    public PhotoView(Context c) {
        this(c, null);
    }

    public PhotoView(Context c, AttributeSet attrs) {
        this(c, attrs, 0);
    }

    public PhotoView(Context c, AttributeSet attrs, int def) {
        super(c, attrs, def);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(Ui.dp(getContext(), 3));
        trackPaint.setColor(trackColor);
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeWidth(Ui.dp(getContext(), 3));
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setColor(Ui.ACCENT);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    // ---- 对外接口 ----

    public void bind(Exercise e) {
        bind(e == null ? "" : e.img);
    }

    public void bind(String img) {
        String k = img == null ? "" : img;
        if (k.equals(key)) {
            return;
        }
        stopAnim();
        key = k;
        still = null;
        requestedSample = -1;
        bgColor = 0xFFFFFFFF;
        invalidate();
        load();
    }

    /** 视图尺寸已知后才决定解码采样率。 */
    private void load() {
        if (key.isEmpty() || getWidth() <= 0) {
            return;
        }
        requestedSample = getWidth() > Ui.dpi(getContext(), 220) ? 1 : 2;
        requestStill(key, requestedSample);
        if (animated) {
            requestAnim(key);
        }
    }

    /** 关掉动图后固定显示缩略图，用于列表。 */
    public void setAnimated(boolean on) {
        if (animated == on) {
            return;
        }
        animated = on;
        if (!on) {
            stopAnim();
        } else if (!key.isEmpty() && getWidth() > 0) {
            requestAnim(key);
        }
        invalidate();
    }

    public void setRadiusDp(float dp) {
        radiusDp = dp;
        invalidate();
    }

    /** 放大裁切：小尺寸缩略图里人太小，按倍数推近看主体。 */
    public void setZoom(float z) {
        zoom = Math.max(1f, z);
        invalidate();
    }

    public void setProgress(float p) {
        progress = p;
        invalidate();
    }

    /** 固定宽高比，交给父布局的高度就不用猜了。 */
    public void setAspect(float a) {
        aspect = a;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (aspect > 0f) {
            int w = MeasureSpec.getSize(widthSpec);
            if (w > 0) {
                setMeasuredDimension(w, Math.round(w / aspect));
                return;
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    public void setTrackColor(int color) {
        trackColor = color;
        trackPaint.setColor(color);
        invalidate();
    }

    /** 一圈细描边，用于把浅底演示图压在彩色背景上时收边。 */
    public void setOutline(int color, float widthDp) {
        outlineColor = color;
        outlineDp = widthDp;
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(Ui.dp(getContext(), widthDp));
        outlinePaint.setColor(color);
        invalidate();
    }

    // ---- 解码 ----

    private void requestStill(final String img, final int sample) {
        final String ck = img + "#" + sample;
        Bitmap hit = CACHE.get(ck);
        if (hit != null) {
            if (img.equals(key)) {
                still = hit;
                invalidate();
            }
            return;
        }
        POOL.execute(() -> {
            final Bitmap bm = decodeStill(img, sample);
            if (bm == null) {
                return;
            }
            CACHE.put(ck, bm);
            MAIN.post(() -> {
                if (img.equals(key)) {
                    still = bm;
                    invalidate();
                }
            });
        });
    }

    private Bitmap decodeStill(String img, int sample) {
        AssetManager am = getContext().getAssets();
        try (InputStream is = am.open("img/" + img + ".webp")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            // 大图用 8888 保住线条过渡，缩略图用 565 控制内存
            o.inPreferredConfig = sample > 1 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeStream(is, null, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private void requestAnim(final String img) {
        if (Build.VERSION.SDK_INT < 28) {
            return;
        }
        POOL.execute(() -> {
            AnimatedImageDrawable d = decodeAnim(img);
            if (d == null) {
                return;
            }
            MAIN.post(() -> {
                if (!img.equals(key) || !animated) {
                    return;
                }
                stopAnim();
                anim = d;
                anim.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                if (isAttachedToWindow()) {
                    anim.start();
                }
                invalidate();
            });
        });
    }

    private AnimatedImageDrawable decodeAnim(String img) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(
                    getContext().getAssets(), "anim/" + img + ".webp");
            Drawable d = ImageDecoder.decodeDrawable(src);
            return d instanceof AnimatedImageDrawable ? (AnimatedImageDrawable) d : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private void stopAnim() {
        if (anim != null) {
            anim.stop();
            anim = null;
        }
    }

    // ---- 绘制 ----

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float r = Ui.dp(getContext(), radiusDp);
        box.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(box, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clipPath);

        bgPaint.setColor(bgColor);
        canvas.drawRect(box, bgPaint);

        if (anim != null) {
            fitContent(boxOf(key), dst);
            anim.setBounds(Math.round(dst.left), Math.round(dst.top),
                    Math.round(dst.right), Math.round(dst.bottom));
            anim.draw(canvas);
            postInvalidateOnAnimation();
        } else if (still != null) {
            bmpPaint.setAlpha(255);
            fitContent(boxOf(key), dst);
            canvas.drawBitmap(still, null, dst, bmpPaint);
        }
        canvas.restore();

        if (progress >= 0f) {
            drawBorder(canvas, r);
        }
        if (outlineColor != 0) {
            float inset = outlinePaint.getStrokeWidth() / 2f;
            RectF rf = new RectF(inset, inset, w - inset, h - inset);
            borderPath.reset();
            borderPath.addRoundRect(rf, r - inset, r - inset, Path.Direction.CW);
            canvas.drawPath(borderPath, outlinePaint);
        }
    }

    /** 按主体框等比居中：整图按「框」去凑视图，多出来的部分被圆角裁掉。 */
    private void fitContent(float[] b, RectF out) {
        float vw = getWidth();
        float vh = getHeight();
        float bw = Math.max(0.05f, b[2] - b[0]);
        float bh = Math.max(0.05f, b[3] - b[1]);
        float s = Math.min(vw / bw, vh / bh) * zoom;
        float cx = (b[0] + b[2]) / 2f * s;
        float cy = (b[1] + b[3]) / 2f * s;
        float l = vw / 2f - cx;
        float t = vh / 2f - cy;
        out.set(l, t, l + s, t + s);
    }

    /** 主体框一次性读进内存（38 KB，1324 条）。 */
    private static synchronized void loadBoxes(Context c) {
        if (boxLoaded) {
            return;
        }
        boxLoaded = true;
        try (InputStream is = c.getAssets().open("boxes.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bo.write(buf, 0, n);
            }
            JSONObject root = new JSONObject(bo.toString("UTF-8"));
            Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String k = it.next();
                org.json.JSONArray a = root.optJSONArray(k);
                if (a == null || a.length() < 4) {
                    continue;
                }
                BOX.put(k, new float[]{
                        (float) a.optDouble(0, 0),
                        (float) a.optDouble(1, 0),
                        (float) a.optDouble(2, 1),
                        (float) a.optDouble(3, 1)});
            }
        } catch (Throwable ignored) {
            // 索引缺失就退回整图等比，不影响显示
        }
    }

    private float[] boxOf(String img) {
        if (!boxLoaded) {
            loadBoxes(getContext());
        }
        float[] b = BOX.get(img);
        return b == null ? FULL : b;
    }

    private void drawBorder(Canvas canvas, float r) {
        float inset = trackPaint.getStrokeWidth() / 2f;
        RectF rf = new RectF(inset, inset, getWidth() - inset, getHeight() - inset);
        borderPath.reset();
        borderPath.addRoundRect(rf, r, r, Path.Direction.CW);
        canvas.drawPath(borderPath, trackPaint);
        if (progress <= 0f) {
            return;
        }
        PathMeasure pm = new PathMeasure(borderPath, false);
        float len = pm.getLength();
        float done = Math.max(1f, len * Math.min(1f, progress));
        android.graphics.DashPathEffect dash =
                new android.graphics.DashPathEffect(new float[]{done, len}, 0f);
        barPaint.setPathEffect(dash);
        canvas.drawPath(borderPath, barPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (anim != null) {
            anim.start();
        }
        if (key.isEmpty()) {
            return;
        }
        if (getWidth() > 0 && requestedSample < 0) {
            load();
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        int need = w > Ui.dpi(getContext(), 220) ? 1 : 2;
        if (!key.isEmpty() && requestedSample != need) {
            still = null;
            requestedSample = -1;
            load();
        }
    }
}
