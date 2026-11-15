package com.yuanlian.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 成就：按本机记录算出的徽章墙。 */
public class AchievementsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "成就", v -> finish()));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 4), p, Ui.dpi(this, 24));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        List<Achievements.Badge> all = Achievements.all(this);
        int earned = 0;
        for (Achievements.Badge b : all) {
            if (b.earned()) {
                earned++;
            }
        }
        body.addView(hero(earned, all.size()));

        String group = null;
        LinearLayout card = null;
        for (int i = 0; i < all.size(); i++) {
            Achievements.Badge b = all.get(i);
            if (!b.group.equals(group)) {
                group = b.group;
                UiList.label(this, body, group);
                card = UiList.card(this);
                body.addView(card);
            }
            boolean last = i == all.size() - 1
                    || !all.get(i + 1).group.equals(group);
            card.addView(row(b, last));
        }

        UiList.hint(this, body, "徽章全部按这台设备上的记录计算，达成后不会消失。"
                + "「练过多少个动作」按个人纪录表统计，所以升级前练的动作不计入。");
    }

    private FrameLayout hero(int earned, int total) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.gradientStreak(this));
        card.setClipToOutline(true);
        int p = Ui.dpi(this, 22);
        card.setPadding(p, p, p, p);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(box);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        box.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cap = new TextView(this);
        cap.setText("已点亮");
        cap.setTextColor(Ui.WHITE_80);
        cap.setTextSize(13);
        Ui.font(cap, 500);
        left.addView(cap);

        LinearLayout num = new LinearLayout(this);
        num.setOrientation(LinearLayout.HORIZONTAL);
        num.setGravity(Gravity.BOTTOM);
        left.addView(num);
        TextView n = new TextView(this);
        n.setText(String.valueOf(earned));
        n.setTextColor(Color.WHITE);
        n.setTextSize(54);
        n.setIncludeFontPadding(false);
        Ui.font(n, 800);
        num.addView(n);
        TextView of = new TextView(this);
        of.setText(" / " + total);
        of.setTextColor(Ui.WHITE_60);
        of.setTextSize(18);
        of.setPadding(0, 0, 0, Ui.dpi(this, 9));
        Ui.font(of, 600);
        num.addView(of);

        String hint = Achievements.nextHint(this);
        TextView tip = new TextView(this);
        tip.setText(hint.isEmpty() ? "全部达成了，厉害。" : hint);
        tip.setTextColor(Ui.WHITE_80);
        tip.setTextSize(12);
        Ui.font(tip, 400);
        left.addView(tip);

        FrameLayout medal = new FrameLayout(this);
        medal.setBackground(Ui.round(this, 0x2EFFFFFF, 24f));
        box.addView(medal, new LinearLayout.LayoutParams(
                Ui.dpi(this, 82), Ui.dpi(this, 82)));
        IconView ic = new IconView(this);
        ic.setIcon(IconView.TROPHY);
        ic.setColor(Color.WHITE);
        ic.setStrokeDp(2f);
        medal.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 40), Ui.dpi(this, 40), Gravity.CENTER));
        return card;
    }

    private View row(Achievements.Badge b, boolean last) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dpi(this, 14);
        row.setPadding(p, p, Ui.dpi(this, 16), p);
        wrap.addView(row);

        boolean on = b.earned();
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, on ? b.color : Ui.CARD_SOFT, 11f));
        row.addView(tile, new LinearLayout.LayoutParams(
                Ui.dpi(this, 40), Ui.dpi(this, 40)));
        IconView ic = new IconView(this);
        ic.setIcon(b.icon);
        ic.setColor(on ? Color.WHITE : 0xFFB4BAC4);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 21), Ui.dpi(this, 21), Gravity.CENTER));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 13);
        row.addView(texts, tlp);

        TextView name = new TextView(this);
        name.setText(b.title);
        name.setTextColor(on ? Ui.INK : Ui.INK_MUTED);
        name.setTextSize(15.5f);
        Ui.font(name, on ? 700 : 500);
        texts.addView(name);

        TextView desc = new TextView(this);
        desc.setText(b.desc);
        desc.setTextColor(Ui.INK_SOFT);
        desc.setTextSize(12);
        desc.setPadding(0, Ui.dpi(this, 1), 0, Ui.dpi(this, 6));
        Ui.font(desc, 400);
        texts.addView(desc);

        texts.addView(bar(b));

        TextView state = new TextView(this);
        state.setText(b.progressText());
        state.setTextColor(on ? b.color : Ui.INK_MUTED);
        state.setTextSize(11.5f);
        state.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dpi(this, 10);
        Ui.font(state, on ? 700 : 500);
        row.addView(state, slp);

        if (!last) {
            View div = new View(this);
            div.setBackgroundColor(Ui.LINE);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 0.7f)));
            dlp.leftMargin = Ui.dpi(this, 67);
            wrap.addView(div, dlp);
        }
        return wrap;
    }

    private LinearLayout bar(Achievements.Badge b) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.round(this, Ui.CARD_SOFT, 3f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 5));
        bar.setLayoutParams(lp);

        float p = b.progress();
        View fill = new View(this);
        fill.setBackground(Ui.round(this, b.earned() ? b.color : Ui.ACCENT, 3f));
        bar.addView(fill, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.5f, p * 100f)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.5f, (1f - p) * 100f)));
        return bar;
    }
}
