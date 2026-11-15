package com.yuanlian.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** 轻量自动换行容器，用于条件选择的胶囊组。 */
public class FlowLayout extends ViewGroup {

    private int gapX;
    private int gapY;

    public FlowLayout(Context c) {
        super(c);
    }

    public FlowLayout(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    public void setGap(int xDp, int yDp) {
        gapX = Ui.dpi(getContext(), xDp);
        gapY = Ui.dpi(getContext(), yDp);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int padL = getPaddingLeft();
        int padR = getPaddingRight();
        int avail = Math.max(0, width - padL - padR);
        int lineW = 0;
        int lineH = 0;
        int totalH = getPaddingTop() + getPaddingBottom();

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            if (lineW > 0 && lineW + gapX + cw > avail) {
                totalH += lineH + gapY;
                lineW = 0;
                lineH = 0;
            }
            lineW += (lineW == 0 ? 0 : gapX) + cw;
            lineH = Math.max(lineH, ch);
        }
        totalH += lineH;
        setMeasuredDimension(width, totalH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int padL = getPaddingLeft();
        int avail = Math.max(0, getWidth() - padL - getPaddingRight());
        int x = padL;
        int y = getPaddingTop();
        int lineH = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            if (x > padL && x + cw > padL + avail) {
                x = padL;
                y += lineH + gapY;
                lineH = 0;
            }
            child.layout(x, y, x + cw, y + ch);
            x += cw + gapX;
            lineH = Math.max(lineH, ch);
        }
    }
}
