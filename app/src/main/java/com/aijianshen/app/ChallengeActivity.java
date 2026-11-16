package com.aijianshen.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 挑战页：挑一个目标，按周结算，平时练的记录自动算进度。 */
public class ChallengeActivity extends Activity {

    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "挑战", v -> finish()));
        root.addView(UiList.subtitle(this,
                "选一个目标，按周结算。你平时练的记录会自动算进进度，不用另外打卡。"));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 4), p, Ui.dpi(this, 28));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (body == null) {
            return;
        }
        body.removeAllViews();
        Store.ChallengeState st = Store.challengeState(this);
        Challenges.Def active = st.active() ? Challenges.byId(st.id) : null;

        if (active != null) {
            Challenges.Progress pr = Challenges.progress(this, active, st.start);
            body.addView(activeCard(active, pr));
            UiList.label(this, body, "换个目标");
        } else {
            UiList.label(this, body, "选一个开始");
        }

        for (Challenges.Def d : Challenges.all()) {
            if (active != null && d.id.equals(active.id)) {
                continue;
            }
            body.addView(defCard(d, active != null));
        }

        UiList.hint(this, body,
                "进度按开始日期往后算，每周练够要求次数就算这一周通关；"
                        + "中途漏掉的周不会补，但不影响后面的周。");
    }

    /** 进行中的那张卡：进度条 + 本周 + 剩余天数 + 放弃。 */
    private View activeCard(Challenges.Def d, Challenges.Progress pr) {
        LinearLayout card = UiList.card(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dpi(this, 16);
        head.setPadding(p, p, p, 0);
        card.addView(head);

        head.addView(tile(d, 44));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 13);
        head.addView(texts, tlp);

        TextView name = new TextView(this);
        name.setText(d.name);
        name.setTextColor(Ui.INK);
        name.setTextSize(18);
        Ui.font(name, 800);
        texts.addView(name);

        TextView spec = new TextView(this);
        spec.setText(pr.done ? "已通关 · 全部 " + d.weeks + " 周达成" : d.spec());
        spec.setTextColor(Ui.INK_SOFT);
        spec.setTextSize(12);
        spec.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(spec, 400);
        texts.addView(spec);

        TextView state = new TextView(this);
        boolean expired = !pr.done && pr.daysLeft <= 0;
        state.setText(pr.done ? "完成" : (expired ? "已结束" : "进行中"));
        state.setTextSize(11);
        state.setGravity(Gravity.CENTER);
        state.setTextColor(pr.done ? 0xFFFFFFFF : (expired ? 0xFF8A93A0 : Ui.ACCENT));
        state.setBackground(Ui.round(this,
                pr.done ? Ui.GREEN : (expired ? 0xFFEFF1F4 : Ui.ACCENT_SOFT), 12f));
        Ui.font(state, 700);
        head.addView(state, new LinearLayout.LayoutParams(
                Ui.dpi(this, 56), Ui.dpi(this, 26)));

        // 进度格：一周一格，够次数就点亮
        LinearLayout bars = new LinearLayout(this);
        bars.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 7));
        blp.leftMargin = p;
        blp.rightMargin = p;
        blp.topMargin = Ui.dpi(this, 18);
        card.addView(bars, blp);
        for (int i = 0; i < d.weeks; i++) {
            View cell = new View(this);
            cell.setBackground(Ui.round(this,
                    i < pr.weeksDone ? d.color : 0xFFEDEFF3, 4f));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i > 0) {
                clp.leftMargin = Ui.dpi(this, 6);
            }
            bars.addView(cell, clp);
        }

        TextView detail = new TextView(this);
        detail.setText("第 " + (pr.currentWeek + 1) + "/" + d.weeks
                + " 周 · 本周 " + Math.min(pr.thisWeek, d.perWeek) + "/" + d.perWeek + " 次"
                + (pr.done ? "" : (pr.daysLeft > 0 ? " · 还剩 " + pr.daysLeft + " 天" : " · 已结束")));
        detail.setTextColor(Ui.INK_MUTED);
        detail.setTextSize(12.5f);
        detail.setPadding(p, Ui.dpi(this, 14), p, 0);
        Ui.font(detail, 500);
        card.addView(detail);

        // 到期未达标：主推「重新开始」，下面留一个安静的移除口
        TextView quit = new TextView(this);
        boolean finished = pr.done || expired;
        quit.setText(pr.done ? "再来一次" : (expired ? "重新开始" : "放弃这个挑战"));
        quit.setTextColor(finished ? Ui.ACCENT : Ui.INK_SOFT);
        quit.setTextSize(13);
        quit.setGravity(Gravity.CENTER);
        quit.setBackground(Ui.ripple(this,
                Ui.round(this, finished ? Ui.ACCENT_SOFT : Ui.CARD_SOFT, 20f), 0x14000000));
        Ui.font(quit, 600);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 40));
        qlp.leftMargin = p;
        qlp.rightMargin = p;
        qlp.topMargin = Ui.dpi(this, 14);
        qlp.bottomMargin = expired ? 0 : p;
        card.addView(quit, qlp);
        quit.setOnClickListener(v -> {
            if (finished) {
                start(d, false);
            } else {
                InfoDialog.confirm(this, "放弃这个挑战？",
                        "已完成的进度会清零。训练记录本身不受影响，随时可以重新开始。",
                        "放弃", Ui.RED, () -> {
                            Store.quitChallenge(this);
                            render();
                        });
            }
        });
        if (expired) {
            TextView remove = new TextView(this);
            remove.setText("不想要了，移除这条记录");
            remove.setTextColor(Ui.INK_MUTED);
            remove.setTextSize(11.5f);
            remove.setGravity(Gravity.CENTER);
            remove.setPadding(0, Ui.dpi(this, 10), 0, p);
            Ui.font(remove, 400);
            card.addView(remove);
            remove.setOnClickListener(v ->
                    InfoDialog.confirm(this, "移除这条挑战记录？",
                            "只清掉挑战进度，训练记录不受影响。",
                            "移除", Ui.RED, () -> {
                                Store.quitChallenge(this);
                                render();
                            }));
        }
        return card;
    }

    private View defCard(Challenges.Def d, boolean hasActive) {
        LinearLayout card = UiList.card(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 12);
        card.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dpi(this, 14);
        row.setPadding(p, p, p, p);
        row.setBackground(Nav.press(this));
        card.addView(row);

        row.addView(tile(d, 42));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 13);
        row.addView(texts, tlp);

        TextView name = new TextView(this);
        name.setText(d.name);
        name.setTextColor(Ui.INK);
        name.setTextSize(16);
        Ui.font(name, 700);
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText(d.tagline);
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(12);
        sub.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(sub, 400);
        texts.addView(sub);

        TextView spec = new TextView(this);
        spec.setText(d.spec());
        spec.setTextColor(Ui.INK_MUTED);
        spec.setTextSize(11);
        spec.setPadding(0, Ui.dpi(this, 4), 0, 0);
        Ui.font(spec, 500);
        texts.addView(spec);

        TextView go = new TextView(this);
        go.setText("开始");
        go.setTextColor(0xFFFFFFFF);
        go.setTextSize(13);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.ripple(this, Ui.round(this, d.color, 17f), 0x33FFFFFF));
        Ui.font(go, 700);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 62), Ui.dpi(this, 34));
        glp.leftMargin = Ui.dpi(this, 8);
        row.addView(go, glp);

        View.OnClickListener click = v -> {
            if (hasActive) {
                InfoDialog.confirm(this, "换成「" + d.name + "」？",
                        "同一时间只做一挑战，切换会放弃当前那个的进度。训练记录不受影响。",
                        "换掉", Ui.ACCENT, () -> start(d, true));
            } else {
                start(d, false);
            }
        };
        go.setOnClickListener(click);
        row.setOnClickListener(click);
        return card;
    }

    private FrameLayout tile(Challenges.Def d, int sizeDp) {
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, d.color, sizeDp * 0.32f));
        IconView ic = new IconView(this);
        ic.setIcon(d.icon);
        ic.setColor(0xFFFFFFFF);
        int inner = Math.round(sizeDp * 0.54f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, inner), Ui.dpi(this, inner), Gravity.CENTER));
        tile.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dpi(this, sizeDp), Ui.dpi(this, sizeDp)));
        return tile;
    }

    private void start(Challenges.Def d, boolean replace) {
        if (replace) {
            Store.quitChallenge(this);
        }
        Store.startChallenge(this, d.id, System.currentTimeMillis());
        Toast.makeText(this, "已开始：" + d.name, Toast.LENGTH_SHORT).show();
        render();
    }
}
