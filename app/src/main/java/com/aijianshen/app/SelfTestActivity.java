package com.aijianshen.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 一分钟自测：三个动作各定一条曲线（胸背 / 腿臀 / 核心）。
 *
 * 为什么用「选区间」而不是输入数字：输入要弹键盘、要判断合不合规，
 * 三个动作就要打断三次；点一下最省事，也避免用户拿不准该填多少。
 */
public class SelfTestActivity extends Activity {

    private static final String[] TITLES = {"俯卧撑", "深蹲", "平板支撑"};
    private static final String[] UNITS = {"个", "个", "秒"};
    private static final String[] HINTS = {
            "标准姿势，连续做到动作变形为止",
            "大腿下到与地面平行，连续数个数",
            "撑到腰塌下来为止，记秒数"
    };
    /** 每一档就是 L1–L5，文案取区间，避免用户纠结「做 7 个算几级」。 */
    private static final String[][] CHOICES = {
            {"0–2", "3–7", "8–15", "16–25", "26+"},
            {"<10", "10–19", "20–34", "35–49", "50+"},
            {"<20", "20–39", "40–74", "75–119", "120+"}
    };
    private static final String[] CURVES = PlanProfile.LEVEL_PARTS;

    private PlanProfile profile;
    private final int[] picked = new int[3];
    private final TextView[] status = new TextView[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        profile = Store.profile(this).copy();
        for (int i = 0; i < picked.length; i++) {
            picked[i] = profile.levelOfSlot(i);
        }

        LinearLayout root = UiList.page(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "1 分钟自测", v -> finish()));
        root.addView(UiList.subtitle(this,
                "三个动作，各自定一条曲线。上肢弱、下肢强的人，不用再被平均。"));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, 0, p, Ui.dpi(this, 18));
        sc.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int i = 0; i < TITLES.length; i++) {
            body.addView(card(i), cardLp(i));
        }

        UiList.hint(this, body,
                "自测只影响秒数和轮次，不换动作；随时可以在「训练设置」里重测。");

        TextView done = UiList.primary(this, "保存");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 52));
        dlp.topMargin = Ui.dpi(this, 8);
        dlp.leftMargin = p;
        dlp.rightMargin = p;
        root.addView(done, dlp);
        done.setOnClickListener(v -> save());
    }

    private LinearLayout.LayoutParams cardLp(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, i == 0 ? 4 : 12);
        return lp;
    }

    private LinearLayout card(int i) {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 14);
        card.setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = new TextView(this);
        name.setText(TITLES[i] + " · " + UNITS[i]);
        name.setTextColor(Ui.INK);
        name.setTextSize(15);
        Ui.font(name, 600);
        head.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText("L" + picked[i]);
        badge.setTextColor(Ui.ACCENT);
        badge.setTextSize(13);
        badge.setGravity(Gravity.CENTER);
        Ui.font(badge, 700);
        status[i] = badge;
        head.addView(badge);

        TextView hint = new TextView(this);
        hint.setText(HINTS[i] + " · 对应「" + CURVES[i] + "」");
        hint.setTextColor(Ui.INK_SOFT);
        hint.setTextSize(12);
        hint.setPadding(0, Ui.dpi(this, 3), 0, Ui.dpi(this, 10));
        Ui.font(hint, 400);
        card.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row);
        for (int k = 0; k < 5; k++) {
            final int level = k + 1;
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(CHOICES[i][k]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(11.5f);
            chip.setIncludeFontPadding(false);
            chip.setTextColor(level == picked[i] ? Color.WHITE : Ui.INK_MUTED);
            chip.setBackground(level == picked[i]
                    ? Ui.round(this, Ui.ACCENT, 14f)
                    : Ui.ripple(this, Ui.roundStroke(this, 0x00000000, 14f, 0x1F000000),
                    0x22000000));
            chip.setOnClickListener(v -> {
                picked[idx] = level;
                renderChips(row, idx);
                status[idx].setText("L" + level);
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    0, Ui.dpi(this, 38), 1f);
            clp.leftMargin = k == 0 ? 0 : Ui.dpi(this, 3);
            clp.rightMargin = k == 4 ? 0 : Ui.dpi(this, 3);
            row.addView(chip, clp);
        }
        return card;
    }

    /** 只重画这一张卡的 5 个格子：整页重建会让滚动位置跳回去。 */
    private void renderChips(LinearLayout row, int idx) {
        for (int k = 0; k < row.getChildCount(); k++) {
            TextView chip = (TextView) row.getChildAt(k);
            boolean on = k + 1 == picked[idx];
            chip.setTextColor(on ? Color.WHITE : Ui.INK_MUTED);
            chip.setBackground(on
                    ? Ui.round(this, Ui.ACCENT, 14f)
                    : Ui.ripple(this, Ui.roundStroke(this, 0x00000000, 14f, 0x1F000000),
                    0x22000000));
        }
    }

    private void save() {
        Store.setPartLevels(this, picked[0], picked[1], picked[2]);
        Data.clearPlanCache();
        Toast.makeText(this, "已保存：胸背 L" + picked[0]
                + " · 腿臀 L" + picked[1] + " · 核心 L" + picked[2],
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
