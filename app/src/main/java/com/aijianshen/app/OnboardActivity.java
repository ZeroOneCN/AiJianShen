package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 新用户引导：性别 → 年龄 → 身高 → 体重，四步填完接定制计划。
 *
 * <p>顺序不是随便排的：{@link BleScale} 连接时要把身高、年龄、性别下发给秤才能打开阻抗位，
 * 所以这三个必须排在体重之前，否则用户站上秤只能拿到体重、拿不到阻抗，18 项体成分一个也算不出来。
 *
 * <p>每一步都能「稍后再说」，跳过时同样写 {@code onboard_done}，不会每次启动都弹。
 */
public class OnboardActivity extends Activity implements BleScale.Listener {

    private static final int STEP_GENDER = 0;
    private static final int STEP_AGE = 1;
    private static final int STEP_HEIGHT = 2;
    private static final int STEP_WEIGHT = 3;
    private static final int STEPS = 4;

    private static final int SOURCE_NONE = 0;
    private static final int SOURCE_SCALE = 1;
    private static final int SOURCE_MANUAL = 2;

    private static final int REQ_BT = 0x9A;

    private static final String[] TITLE = {
            "先认识一下你", "你今年多大", "你的身高是多少", "你现在的体重"
    };
    private static final String[] SUBTITLE = {
            "体脂率、基础代谢这些估算都要用到它",
            "年龄决定基础代谢与目标心率的基准",
            "身高用来算 BMI 与每日热量需求",
            "可以手动填，也可以直接连体脂秤"
    };

    private int step = STEP_GENDER;
    /** null 表示还没选性别。 */
    private Boolean male = null;
    private int age = 0;
    private float heightCm = 0f;
    private float weightKg = 0f;
    private float impedance = 0f;
    private int source = SOURCE_NONE;

    private LinearLayout body;
    private LinearLayout progress;
    private TextView btnNext;
    private ScrollView scroll;

    // ---- 体脂秤 ----
    private BleScale scale;
    private final Map<String, String> found = new LinkedHashMap<>();
    private LinearLayout deviceList;
    private TextView scaleState;
    private boolean connecting;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_onboard);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        body = findViewById(R.id.body);
        progress = findViewById(R.id.progress);
        scroll = findViewById(R.id.scroller);
        btnNext = findViewById(R.id.btn_next);
        Ui.font(btnNext, 700);

        IconView back = findViewById(R.id.icon_back);
        back.setIcon(IconView.BACK);
        back.setColor(Ui.INK);
        findViewById(R.id.btn_back).setOnClickListener(v -> back());
        findViewById(R.id.btn_back).setVisibility(View.INVISIBLE);

        TextView later = findViewById(R.id.btn_later);
        Ui.font(later, 500);
        later.setOnClickListener(v -> skip());

        btnNext.setOnClickListener(v -> next());

        // 中途退出再进来时接着上次填的值，别让用户重新来一遍
        age = Store.age(this);
        heightCm = Store.heightCm(this);

        renderStep();
    }

    // ---------------------------------------------------------------- 渲染

    private void renderStep() {
        renderProgress();
        findViewById(R.id.btn_back).setVisibility(step == STEP_GENDER ? View.INVISIBLE : View.VISIBLE);

        body.removeAllViews();
        // 上一屏的引用已经随视图一起丢掉，不清掉会让回调写到看不见的控件上
        deviceList = null;
        scaleState = null;
        previewRow = null;

        TextView q = new TextView(this);
        q.setText(TITLE[step]);
        q.setTextColor(Ui.INK);
        q.setTextSize(27);
        q.setGravity(Gravity.CENTER);
        q.setLineSpacing(Ui.dp(this, 2), 1f);
        Ui.font(q, 700);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qlp.topMargin = Ui.dpi(this, 6);
        body.addView(q, qlp);

        TextView sub = new TextView(this);
        sub.setText(SUBTITLE[step]);
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        Ui.font(sub, 400);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dpi(this, 8);
        slp.leftMargin = Ui.dpi(this, 8);
        slp.rightMargin = Ui.dpi(this, 8);
        body.addView(sub, slp);

        switch (step) {
            case STEP_GENDER:
                renderGender();
                break;
            case STEP_AGE:
                renderAge();
                break;
            case STEP_HEIGHT:
                renderHeight();
                break;
            case STEP_WEIGHT:
                renderWeight();
                break;
            default:
                break;
        }

        updateNextButton();
        body.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        scroll.scrollTo(0, 0);
    }

    private void renderProgress() {
        progress.removeAllViews();
        for (int i = 0; i < STEPS; i++) {
            View seg = new View(this);
            seg.setBackground(Ui.round(this, i <= step ? Ui.ACCENT : 0xFFE9ECF1, 2f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.leftMargin = i == 0 ? 0 : Ui.dpi(this, 2);
            lp.rightMargin = i == STEPS - 1 ? 0 : Ui.dpi(this, 2);
            progress.addView(seg, lp);
        }
    }

    private void updateNextButton() {
        boolean ready = step != STEP_WEIGHT || weightKg > 0f;
        if (step == STEP_WEIGHT) {
            btnNext.setText(ready ? "完成" : "继续");
        } else {
            btnNext.setText("继续");
        }
        btnNext.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        btnNext.setAlpha(ready ? 1f : 0.4f);
        btnNext.setEnabled(ready);
    }

    // ---- 第 1 步：性别 ----

    private void renderGender() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dpi(this, 42);
        body.addView(row, rlp);

        row.addView(genderCard(true, "男", Boolean.TRUE.equals(male)),
                cardLp(true));
        row.addView(genderCard(false, "女", Boolean.FALSE.equals(male)),
                cardLp(false));
    }

    private LinearLayout.LayoutParams cardLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (first) {
            lp.rightMargin = Ui.dpi(this, 6);
        } else {
            lp.leftMargin = Ui.dpi(this, 6);
        }
        return lp;
    }

    /**
     * 性别卡：圆形色底里放性别符号，选中时整块变蓝、符号反白。
     *
     * <p>符号是 res/drawable/ic_gender_male|female.xml（SVG 路径），不再画人体剪影——
     * 小尺寸下剪影的比例怎么调都容易失真，符号一眼就能认出。
     */
    private LinearLayout genderCard(boolean isMale, String name, boolean on) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(Ui.dpi(this, 8), Ui.dpi(this, 28),
                Ui.dpi(this, 8), Ui.dpi(this, 24));

        GradientDrawable shape = Ui.round(this, on ? Ui.ACCENT_SOFT : Ui.SURFACE, Ui.R_CARD);
        if (on) {
            shape.setStroke(Math.max(1, Ui.dpi(this, 2)), Ui.ACCENT);
        }
        card.setBackground(Ui.ripple(this, shape, 0x22000000));

        FrameLayout disc = new FrameLayout(this);
        // 76dp 的方块配 38dp 圆角，正好是一个圆
        disc.setBackground(Ui.round(this, on ? Ui.ACCENT : 0xFFF1F3F6, 38f));
        card.addView(disc, new LinearLayout.LayoutParams(
                Ui.dpi(this, 76), Ui.dpi(this, 76)));

        ImageView symbol = new ImageView(this);
        symbol.setImageResource(isMale ? R.drawable.ic_gender_male : R.drawable.ic_gender_female);
        symbol.setColorFilter(on ? Color.WHITE : 0xFF98A1AE);
        disc.addView(symbol, new FrameLayout.LayoutParams(
                Ui.dpi(this, 38), Ui.dpi(this, 38), Gravity.CENTER));

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(on ? Ui.ACCENT : Ui.INK);
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, Ui.dpi(this, 14), 0, 0);
        Ui.font(label, 700);
        // 必须显式给 MATCH_PARENT：不给 LayoutParams 时父容器的 gravity 落不到
        // 文字上，字会贴在卡片左下角
        card.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> {
            male = isMale;
            Store.setMale(this, male);
            renderStep();
            handler.postDelayed(this::next, 170);
        });
        return card;
    }

    // ---- 第 2 步：年龄 ----

    private void renderAge() {
        int init = age > 0 ? age : 26;
        TextView number = bigNumber(String.valueOf(init), "岁");
        addBigNumber(number);
        age = init;

        ValueRuler ruler = new ValueRuler(this);
        ruler.configure(12, 90, 1, 1, 5, 16f, init);
        ruler.setOnValueChanged(v -> {
            age = Math.round(v);
            setNumberText(number, String.valueOf(age));
        });
        addRuler(ruler);

        addHint("12–90 岁。「年龄」只用来估算基础代谢与体成分，不会影响动作推荐。");
    }

    // ---- 第 3 步：身高 ----

    private void renderHeight() {
        float init = heightCm > 0 ? heightCm : 172f;
        String shown = String.format(Locale.US, "%.0f", init);
        TextView number = bigNumber(shown, "cm");
        addBigNumber(number);
        heightCm = init;

        ValueRuler ruler = new ValueRuler(this);
        ruler.configure(120, 220, 1, 1, 10, 12f, init);
        ruler.setOnValueChanged(v -> {
            heightCm = Math.round(v);
            setNumberText(number, String.valueOf(Math.round(heightCm)));
        });
        addRuler(ruler);

        addHint("身高会影响 BMI、每日热量需求与体表面积。");
    }

    // ---- 第 4 步：体重 ----

    private void renderWeight() {
        if (source == SOURCE_NONE) {
            renderSourceCard(SOURCE_SCALE, IconView.SCALE,
                    "我有蚂蚁阿福体脂秤", "打开蓝牙，光脚站上秤就能读到体重与体成分");
            renderSourceCard(SOURCE_MANUAL, IconView.PENCIL,
                    "我手动录入", "没有秤或现在不方便称，直接填一个数");
            return;
        }

        // 选完就把两张卡收成一行，把纵向空间让给刻度和预览
        renderSourceHeader();

        if (source == SOURCE_SCALE) {
            renderScalePanel();
        } else {
            renderManualPanel();
        }

        if (weightKg > 0f) {
            renderPreview();
            scrollToEnd();
        } else if (source == SOURCE_SCALE) {
            addHint("还没读到体重。点右上角「稍后再说」可以先跳过，之后在设置里补。");
        }
    }

    private void renderSourceHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 20);
        body.addView(row, lp);

        TextView label = new TextView(this);
        label.setText(source == SOURCE_SCALE ? "体脂秤测量" : "手动录入");
        label.setTextColor(Ui.INK_SOFT);
        label.setTextSize(13);
        Ui.font(label, 400);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView change = new TextView(this);
        change.setText("更换方式");
        change.setTextColor(Ui.ACCENT);
        change.setTextSize(13);
        change.setPadding(Ui.dpi(this, 8), Ui.dpi(this, 4), 0, Ui.dpi(this, 4));
        Ui.font(change, 600);
        row.addView(change);
        change.setOnClickListener(v -> {
            source = SOURCE_NONE;
            weightKg = 0f;
            impedance = 0f;
            stopScale();
            renderStep();
        });
    }

    /** 展开面板后内容会超高，滚到底让用户看到刻度和预览。 */
    private void scrollToEnd() {
        scroll.postDelayed(() -> {
            View child = scroll.getChildAt(0);
            if (child != null) {
                scroll.smoothScrollTo(0, Math.max(0, child.getHeight() - scroll.getHeight()));
            }
        }, 140);
    }

    private void renderSourceCard(int kind, int icon, String title, String sub) {
        boolean on = source == kind;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 15),
                Ui.dpi(this, 16), Ui.dpi(this, 15));

        GradientDrawable shape = Ui.round(this, on ? Ui.ACCENT_SOFT : Ui.SURFACE, Ui.R_CARD);
        if (on) {
            shape.setStroke(Math.max(1, Ui.dpi(this, 2)), Ui.ACCENT);
        }
        card.setBackground(Ui.ripple(this, shape, 0x22000000));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(on ? Ui.ACCENT : Ui.INK);
        t.setTextSize(15);
        Ui.font(t, 700);
        texts.addView(t);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Ui.INK_SOFT);
        s.setTextSize(12);
        s.setLineSpacing(Ui.dp(this, 2), 1f);
        s.setPadding(0, Ui.dpi(this, 4), 0, 0);
        Ui.font(s, 400);
        texts.addView(s);

        IconView iv = new IconView(this);
        iv.setIcon(on ? IconView.CHECK : icon);
        iv.setColor(on ? Ui.ACCENT : Ui.INK_SOFT);
        card.addView(iv, new LinearLayout.LayoutParams(
                Ui.dpi(this, 24), Ui.dpi(this, 24)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 12);
        body.addView(card, lp);

        card.setOnClickListener(v -> {
            if (source == kind) {
                return;
            }
            source = kind;
            // 换一种取数方式就重新取，免得把上一种方式的值当成已记录
            weightKg = 0f;
            impedance = 0f;
            if (kind == SOURCE_MANUAL) {
                stopScale();
            }
            renderStep();
            if (kind == SOURCE_SCALE) {
                handler.postDelayed(this::beginScan, 120);
            }
        });
    }

    /** 手动录入：刻度尺 + BMI 实时反馈。 */
    private void renderManualPanel() {
        float init = weightKg > 0 ? weightKg : 65f;
        String shown = String.format(Locale.US, "%.1f", init);
        TextView number = bigNumber(shown, "kg");
        addBigNumber(number);
        weightKg = init;

        ValueRuler ruler = new ValueRuler(this);
        ruler.configure(30, 200, 0.1f, 0.5f, 5, 26f, init);
        ruler.setOnValueChanged(v -> {
            weightKg = Math.round(v * 10) / 10f;
            setNumberText(number, String.format(Locale.US, "%.1f", weightKg));
            refreshPreview();
        });
        addRuler(ruler);

        ruler.setValue(init, false);
        refreshPreview();
    }

    /** 体脂秤面板：说明 → 搜索 → 读数。 */
    private void renderScalePanel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, Ui.R_CARD));
        int p = Ui.dpi(this, 16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 14);
        body.addView(card, lp);

        TextView t1 = new TextView(this);
        t1.setText(weightKg > 0 ? "已记录" : "三步完成测量");
        t1.setTextColor(Ui.INK);
        t1.setTextSize(15);
        Ui.font(t1, 700);
        card.addView(t1);

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.setPadding(0, Ui.dpi(this, 10), 0, 0);
        card.addView(steps);
        String[] tips = {
                "1. 确认手机蓝牙已打开，秤放在硬地面",
                "2. 光脚双脚踩上秤，站稳别扶墙",
                "3. 数字稳定后自动带入，约 5 秒"
        };
        for (String tip : tips) {
            TextView tv = new TextView(this);
            tv.setText(tip);
            tv.setTextColor(Ui.INK_MUTED);
            tv.setTextSize(12);
            tv.setLineSpacing(Ui.dp(this, 3), 1f);
            tv.setPadding(0, Ui.dpi(this, 4), 0, 0);
            Ui.font(tv, 400);
            steps.addView(tv);
        }

        if (weightKg > 0f) {
            TextView ok = new TextView(this);
            ok.setText(String.format(Locale.US, "%.1f kg", weightKg)
                    + (impedance > 0
                    ? " · 阻抗 " + Math.round(impedance) + " Ω"
                    : " · 这次没有阻抗数据"));
            ok.setTextColor(impedance > 0 ? Ui.GREEN : Ui.INK_SOFT);
            ok.setTextSize(13);
            ok.setPadding(0, Ui.dpi(this, 12), 0, 0);
            Ui.font(ok, 600);
            card.addView(ok);
            return;
        }

        scaleState = new TextView(this);
        scaleState.setText(connecting ? "正在连接…" : "准备搜索附近的秤…");
        scaleState.setTextColor(Ui.INK_SOFT);
        scaleState.setTextSize(12);
        scaleState.setPadding(0, Ui.dpi(this, 12), 0, 0);
        Ui.font(scaleState, 400);
        card.addView(scaleState);

        deviceList = new LinearLayout(this);
        deviceList.setOrientation(LinearLayout.VERTICAL);
        deviceList.setPadding(0, Ui.dpi(this, 6), 0, 0);
        card.addView(deviceList);

        TextView rescan = new TextView(this);
        rescan.setText("开始搜索");
        rescan.setTextColor(Ui.ACCENT);
        rescan.setTextSize(14);
        rescan.setGravity(Gravity.CENTER);
        rescan.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT_SOFT, Ui.R_PILL), 0x22000000));
        Ui.font(rescan, 700);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 46));
        rlp.topMargin = Ui.dpi(this, 12);
        card.addView(rescan, rlp);
        rescan.setOnClickListener(v -> startScan());

        renderDeviceList();
        if (!BleScale.hasPermission(this)) {
            requestPermissions(BleScale.permissions(), REQ_BT);
        } else if (scale == null) {
            handler.postDelayed(this::beginScan, 150);
        }
    }

    private void renderDeviceList() {
        if (deviceList == null) {
            return;
        }
        deviceList.removeAllViews();
        if (found.isEmpty()) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            rows.add(new String[]{e.getKey(), e.getValue()});
        }
        // AFU 开头的排前面，其余按服务端给的顺序
        rows.sort((a, b) -> {
            boolean af = isAfu(a[1]);
            boolean bf = isAfu(b[1]);
            if (af == bf) {
                return 0;
            }
            return af ? -1 : 1;
        });

        for (String[] r : rows) {
            String address = r[0];
            String name = r[1];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Nav.press(this));
            row.setPadding(Ui.dpi(this, 10), Ui.dpi(this, 12),
                    Ui.dpi(this, 10), Ui.dpi(this, 12));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView n = new TextView(this);
            n.setText(name == null || name.isEmpty() ? "未命名设备" : name);
            n.setTextColor(Ui.INK);
            n.setTextSize(14);
            Ui.font(n, 600);
            texts.addView(n);

            TextView a = new TextView(this);
            a.setText(address);
            a.setTextColor(Ui.INK_SOFT);
            a.setTextSize(11);
            Ui.font(a, 400);
            texts.addView(a);

            TextView go = new TextView(this);
            go.setText("连接");
            go.setTextColor(Ui.ACCENT);
            go.setTextSize(13);
            Ui.font(go, 600);
            row.addView(go);

            row.setOnClickListener(v -> {
                // 秤的 0xD0 档案包要从 Store 取，先把这一步填的值落下去，
                // 否则下发的是 0 身高 / 0 年龄，功能位打不开，只出体重不出阻抗。
                syncProfile();
                if (scale == null) {
                    scale = new BleScale(this, this);
                }
                connecting = true;
                if (scaleState != null) {
                    scaleState.setText("正在连接 " + (name == null ? "" : name) + "…");
                }
                scale.connect(address);
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dpi(this, 2);
            deviceList.addView(row, rlp);
        }
    }

    private boolean isAfu(String name) {
        return name != null && name.toUpperCase(Locale.US).startsWith("AFU");
    }

    // ---- 通用小件 ----

    private TextView bigNumber(String value, String unit) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(52);
        tv.setIncludeFontPadding(false);
        tv.setGravity(Gravity.CENTER);
        Ui.font(tv, 700);
        // 用一个隐藏标签记住单位，回调时统一重建文本
        tv.setTag(unit);
        return tv;
    }

    private void addBigNumber(TextView number) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dpi(this, 22);
        body.addView(row, rlp);

        row.addView(number, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView unit = new TextView(this);
        unit.setText((String) number.getTag());
        unit.setTextColor(Ui.INK_SOFT);
        unit.setTextSize(15);
        unit.setIncludeFontPadding(false);
        unit.setPadding(Ui.dpi(this, 6), 0, 0, Ui.dpi(this, 10));
        Ui.font(unit, 600);
        row.addView(unit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 改数字时只换文本；单位是固定的，不必跟着动。 */
    private void setNumberText(TextView tv, String value) {
        tv.setText(value);
    }

    private void addRuler(ValueRuler ruler) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 60));
        lp.topMargin = Ui.dpi(this, 8);
        body.addView(ruler, lp);
    }

    private void addHint(String text) {
        UiList.hint(this, body, text);
    }

    // ---- BMI 预览 ----

    private LinearLayout previewRow;

    private void renderPreview() {
        previewRow = buildPreview();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 16);
        body.addView(previewRow, lp);
    }

    private void refreshPreview() {
        if (previewRow == null || weightKg <= 0f || heightCm <= 0f) {
            return;
        }
        previewRow.removeAllViews();
        fillPreview(previewRow);
    }

    private LinearLayout buildPreview() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        fillPreview(row);
        return row;
    }

    private void fillPreview(LinearLayout row) {
        float bmi = heightCm > 0 ? weightKg / (float) Math.pow(heightCm / 100.0, 2) : 0f;
        String grade;
        int color;
        if (bmi < 18.5f) {
            grade = "偏瘦";
            color = Ui.BLUE_TILE;
        } else if (bmi < 24f) {
            grade = "正常";
            color = Ui.GREEN;
        } else if (bmi < 28f) {
            grade = "偏胖";
            color = Ui.ORANGE;
        } else {
            grade = "肥胖";
            color = Ui.RED;
        }

        row.addView(previewCell("体重",
                String.format(Locale.US, "%.1f", weightKg), "kg", Ui.INK), cellLp(true));
        row.addView(previewCell("BMI",
                String.format(Locale.US, "%.1f", bmi), grade, color), cellLp(false));

        // 有阻抗才谈得上体脂率，否则只留两格
        if (impedance > 0 && heightCm > 0 && age > 0) {
            BodyComp.Result r = BodyComp.calculate(weightKg, impedance, heightCm, age,
                    Boolean.TRUE.equals(male));
            if (r.valid) {
                row.addView(previewCell("体脂率",
                        String.format(Locale.US, "%.1f", r.bodyFat), "%", Ui.INK), cellLp(false));
            }
        }
    }

    private LinearLayout.LayoutParams cellLp(boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (first) {
            lp.rightMargin = Ui.dpi(this, 5);
        } else {
            lp.leftMargin = Ui.dpi(this, 5);
        }
        return lp;
    }

    private LinearLayout previewCell(String label, String value, String unit, int color) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(Ui.round(this, Ui.SURFACE, 16f));
        cell.setPadding(Ui.dpi(this, 6), Ui.dpi(this, 14),
                Ui.dpi(this, 6), Ui.dpi(this, 14));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.BOTTOM);
        cell.addView(line);

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(21);
        v.setIncludeFontPadding(false);
        Ui.font(v, 700);
        line.addView(v);

        TextView u = new TextView(this);
        u.setText(unit);
        u.setTextColor(Ui.INK_SOFT);
        u.setTextSize(11);
        u.setIncludeFontPadding(false);
        u.setPadding(Ui.dpi(this, 3), 0, 0, Ui.dpi(this, 3));
        Ui.font(u, 500);
        line.addView(u);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(Ui.INK_SOFT);
        l.setTextSize(11);
        l.setPadding(0, Ui.dpi(this, 6), 0, 0);
        Ui.font(l, 400);
        cell.addView(l);
        return cell;
    }

    // ---------------------------------------------------------------- 体脂秤

    private void beginScan() {
        if (scale == null) {
            scale = new BleScale(this, this);
        }
        if (!BleScale.bluetoothOn()) {
            if (scaleState != null) {
                scaleState.setText("系统蓝牙没打开");
            }
            Toast.makeText(this, "请先在系统设置里打开蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }
        startScan();
    }

    private void startScan() {
        if (scale == null) {
            scale = new BleScale(this, this);
        }
        if (!BleScale.hasPermission(this)) {
            requestPermissions(BleScale.permissions(), REQ_BT);
            return;
        }
        found.clear();
        if (deviceList != null) {
            deviceList.removeAllViews();
        }
        if (scaleState != null) {
            scaleState.setText("正在搜索附近的秤，踩一下秤面会更快出现…");
        }
        scale.startScan();
    }

    private void stopScale() {
        if (scale != null) {
            scale.stopScan();
            scale.close();
            scale = null;
        }
    }

    @Override
    public void onFound(String name, String address, int rssi) {
        if (address == null || found.containsKey(address)) {
            return;
        }
        found.put(address, name);
        renderDeviceList();
    }

    @Override
    public void onState(String state) {
        if (scaleState != null && state != null) {
            scaleState.setText(state);
        }
    }

    @Override
    public void onLive(float kg) {
        if (scaleState != null && Math.abs(kg) > 3f) {
            scaleState.setText(String.format(Locale.US, "读数中… %.1f kg，站稳别动", kg));
        }
    }

    @Override
    public void onStable(float kg, float imp) {
        if (Math.abs(kg) < 3f) {
            // 空秤的稳定帧，不能当成体重
            return;
        }
        weightKg = Math.round(kg * 10) / 10f;
        impedance = imp;
        Store.addWeight(this, Store.dateOf(System.currentTimeMillis()), weightKg);
        if (imp > 0) {
            Store.addBodyRecord(this, System.currentTimeMillis(), weightKg, imp,
                    null, "", false);
        }
        stopScale();
        connecting = false;
        Toast.makeText(this, String.format(Locale.US, "已记录 %.1f kg", weightKg),
                Toast.LENGTH_SHORT).show();
        renderStep();
    }

    @Override
    public void onError(String message) {
        connecting = false;
        if (scaleState != null) {
            scaleState.setText(message == null ? "连接失败" : message);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT) {
            return;
        }
        boolean ok = grantResults.length > 0;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                ok = false;
            }
        }
        if (ok) {
            startScan();
        } else {
            Toast.makeText(this, "没有蓝牙权限就搜不到秤，可以改用手动录入",
                    Toast.LENGTH_LONG).show();
            if (scaleState != null) {
                scaleState.setText("缺少蓝牙权限，改用手动录入吧");
            }
        }
    }

    // ---------------------------------------------------------------- 导航

    private void next() {
        if (step == STEP_GENDER) {
            if (male == null) {
                male = Boolean.TRUE;
                Store.setMale(this, true);
            }
            step = STEP_AGE;
            renderStep();
            return;
        }
        if (step == STEP_AGE) {
            if (age <= 0) {
                age = 26;
            }
            Store.setAge(this, age);
            step = STEP_HEIGHT;
            renderStep();
            return;
        }
        if (step == STEP_HEIGHT) {
            if (heightCm <= 0) {
                heightCm = 172;
            }
            Store.setHeightCm(this, Math.round(heightCm));
            step = STEP_WEIGHT;
            renderStep();
            return;
        }
        // 体重：已经有数才允许往下走
        if (weightKg <= 0) {
            return;
        }
        syncProfile();
        Store.addWeight(this, Store.dateOf(System.currentTimeMillis()), weightKg);
        finishOnboard();
    }

    /** 把这一步填的性别 / 年龄 / 身高写进本地档案。 */
    private void syncProfile() {
        if (male != null) {
            Store.setMale(this, male);
        }
        if (age > 0) {
            Store.setAge(this, age);
        }
        if (heightCm > 0) {
            Store.setHeightCm(this, Math.round(heightCm));
        }
    }

    private void back() {
        if (step == STEP_GENDER) {
            return;
        }
        step--;
        renderStep();
    }

    private void skip() {
        if (step == STEP_WEIGHT) {
            stopScale();
        }
        finishOnboard();
    }

    private void finishOnboard() {
        Store.setOnboardDone(this);
        startActivity(new Intent(this, PlanActivity.class)
                .putExtra(PlanActivity.EXTRA_ONBOARDING, true));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (step > STEP_GENDER) {
            back();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopScale();
    }
}
