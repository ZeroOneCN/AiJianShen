package com.aijianshen.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 训练页的要领区。
 *
 * 两种模式：
 * - 整段：休息、预备、结算时给一句话；
 * - 分步：动作进行中把 steps 逐条列出来，当前这步高亮、已过的淡出。
 *
 * 分步模式最多同时显示 3 行。多一行就会把图区挤矮，进度环被顶出屏幕——
 * 这个页面的高度预算已经很紧了。
 */
public class StepTips extends LinearLayout {

    private static final int MAX_ROWS = 3;
    /** 每步短于这个秒数就不做高亮：闪得太快反而让人分神。 */
    private static final int MIN_SEG = 4;

    private final List<TextView> texts = new ArrayList<>();
    private final List<View> bars = new ArrayList<>();
    private List<String> showing = new ArrayList<>();
    private boolean stepsMode;
    private int highlighted = -1;

    public StepTips(Context c) {
        super(c);
        setOrientation(VERTICAL);
    }

    /** XML 里 inflate 必须有这个构造器，否则 LayoutInflater 找不到会直接抛 InflateException。 */
    public StepTips(Context c, android.util.AttributeSet attrs) {
        super(c, attrs);
        setOrientation(VERTICAL);
    }

    public StepTips(Context c, android.util.AttributeSet attrs, int defStyleAttr) {
        super(c, attrs, defStyleAttr);
        setOrientation(VERTICAL);
    }

    /** 整段模式。休息、预备倒计时、结算提示都走这里。 */
    public void setText(CharSequence text) {
        stepsMode = false;
        highlighted = -1;
        showing.clear();
        removeAllViews();
        texts.clear();
        bars.clear();

        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(12.5f);
        tv.setTextColor(Ui.INK_SOFT);
        tv.setLineSpacing(0f, 1.32f);
        Ui.font(tv, 400);
        addView(tv, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        texts.add(tv);
        bars.add(null);
    }

    /**
     * 分步模式。
     *
     * @param steps      动作要领，来源是 Exercise.steps
     * @param active     当前进行到第几条（0 基）
     * @param segSeconds 每条分到的秒数；太短就不做高亮
     */
    public void setSteps(List<String> steps, int active, int segSeconds) {
        if (steps == null || steps.isEmpty()) {
            setText("");
            return;
        }
        int size = steps.size();
        boolean canHighlight = segSeconds >= MIN_SEG && size >= 2;
        int act = Math.max(0, Math.min(size - 1, active));

        int start;
        int end;
        if (size <= MAX_ROWS) {
            start = 0;
            end = size;
        } else if (!canHighlight) {
            // 不做高亮时固定停在开头，别让窗口随着时间滑走
            start = 0;
            end = MAX_ROWS;
        } else {
            // 窗口跟着当前步滑动，始终让当前步落在中间那行
            start = Math.max(0, Math.min(size - MAX_ROWS, act - 1));
            end = start + MAX_ROWS;
        }

        List<String> want = new ArrayList<>(steps.subList(start, end));
        if (!stepsMode || !want.equals(showing)) {
            rebuild(want);
            showing = want;
            stepsMode = true;
        }
        applyHighlight(canHighlight ? act - start : -1);
    }

    private void rebuild(List<String> want) {
        removeAllViews();
        texts.clear();
        bars.clear();
        // 行是新建的，竖条默认不可见：缓存必须一起清掉，
        // 否则 applyHighlight 会以为"已经高亮过了"而直接 return，新行永远不亮。
        highlighted = -1;
        Context c = getContext();
        for (int i = 0; i < want.size(); i++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            View bar = new View(c);
            bar.setBackground(Ui.round(c, Ui.ACCENT, 1.5f));
            bar.setVisibility(View.INVISIBLE);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    Ui.dpi(c, 3), Ui.dpi(c, 14));
            blp.rightMargin = Ui.dpi(c, 9);
            row.addView(bar, blp);

            TextView tv = new TextView(c);
            tv.setText(want.get(i));
            tv.setTextSize(12.5f);
            tv.setLineSpacing(0f, 1.32f);
            row.addView(tv, new LinearLayout.LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rlp.topMargin = Ui.dpi(c, 5);
            }
            addView(row, rlp);
            texts.add(tv);
            bars.add(bar);
        }
    }

    private void applyHighlight(int idx) {
        if (idx == highlighted) {
            return;
        }
        highlighted = idx;
        for (int i = 0; i < texts.size(); i++) {
            TextView tv = texts.get(i);
            View bar = bars.get(i);
            if (bar != null) {
                bar.setVisibility(i == idx ? View.VISIBLE : View.INVISIBLE);
            }
            if (i == idx) {
                tv.setTextColor(Ui.INK);
                tv.setAlpha(1f);
                Ui.font(tv, 600);
            } else if (idx >= 0 && i < idx) {
                tv.setTextColor(Ui.INK_SOFT);
                tv.setAlpha(0.45f);
                Ui.font(tv, 400);
            } else {
                tv.setTextColor(Ui.INK_SOFT);
                tv.setAlpha(1f);
                Ui.font(tv, 400);
            }
        }
    }
}
