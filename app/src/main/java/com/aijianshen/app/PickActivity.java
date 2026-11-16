package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 挑动作：给「我的训练」选一组动作，返回逗号分隔的 id。 */
public class PickActivity extends Activity {

    public static final String EXTRA_SELECTED = "picked_ids";

    private final List<Exercise> shown = new ArrayList<>();
    private final List<String> picked = new ArrayList<>();
    private ListView list;
    private EditText etSearch;
    private TextView tvCount;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private ExerciseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "选择动作", v -> finish()));

        // 已被选中的 id（编辑已有训练时带进来）
        String pre = getIntent().getStringExtra(EXTRA_SELECTED);
        if (pre != null && !pre.isEmpty()) {
            Set<String> set = new HashSet<>();
            for (String id : pre.split(",")) {
                if (!id.isEmpty() && set.add(id)) {
                    picked.add(id);
                }
            }
        }

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(Ui.round(this, Ui.SEARCH, Ui.R_CHIP));
        int sp = Ui.dpi(this, 12);
        searchBar.setPadding(sp, 0, sp, 0);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 44));
        int side = Ui.dpi(this, 20);
        sbLp.leftMargin = side;
        sbLp.rightMargin = side;
        sbLp.topMargin = Ui.dpi(this, 6);
        root.addView(searchBar, sbLp);

        IconView icon = new IconView(this);
        icon.setIcon(IconView.SEARCH);
        icon.setColor(Ui.INK_SOFT);
        icon.setStrokeDp(1.8f);
        searchBar.addView(icon, new LinearLayout.LayoutParams(
                Ui.dpi(this, 18), Ui.dpi(this, 18)));

        etSearch = new EditText(this);
        etSearch.setHint("搜索动作名或部位");
        etSearch.setHintTextColor(Ui.INK_SOFT);
        etSearch.setTextColor(Ui.INK);
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        etSearch.setBackground(null);
        etSearch.setPadding(Ui.dpi(this, 10), 0, 0, 0);
        Ui.font(etSearch, 400);
        searchBar.addView(etSearch, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                schedule(s.toString().trim());
            }
        });

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setClipToPadding(false);
        list.setPadding(side, Ui.dpi(this, 8), side, Ui.dpi(this, 8));
        list.setFocusable(false);
        list.setFocusableInTouchMode(false);
        adapter = new ExerciseAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackground(Ui.roundStroke(this, Ui.SURFACE, 0f, Ui.LINE));
        int bp = Ui.dpi(this, 16);
        bottom.setPadding(bp, Ui.dpi(this, 10), bp, Ui.dpi(this, 10));
        root.addView(bottom);

        tvCount = new TextView(this);
        tvCount.setTextColor(Ui.INK);
        tvCount.setTextSize(14);
        Ui.font(tvCount, 600);
        bottom.addView(tvCount, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView ok = new TextView(this);
        ok.setText("加入我的训练");
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(14.5f);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        Ui.font(ok, 700);
        bottom.addView(ok, new LinearLayout.LayoutParams(
                Ui.dpi(this, 132), Ui.dpi(this, 44)));
        ok.setOnClickListener(v -> submit());

        render("");
        paintCount();
    }

    private void schedule(String keyword) {
        if (pending != null) {
            handler.removeCallbacks(pending);
        }
        pending = () -> render(keyword);
        handler.postDelayed(pending, 120);
    }

    private void render(String keyword) {
        shown.clear();
        if (keyword.isEmpty()) {
            shown.addAll(Data.all());
        } else {
            shown.addAll(Data.search(keyword, ""));
        }
        adapter.notifyDataSetChanged();
    }

    private void paintCount() {
        tvCount.setText(picked.isEmpty() ? "还没选动作" : "已选 " + picked.size() + " 个");
    }

    private void toggle(String id) {
        if (picked.contains(id)) {
            picked.remove(id);
        } else {
            picked.add(id);
        }
        paintCount();
        adapter.notifyDataSetChanged();
    }

    private void submit() {
        if (picked.isEmpty()) {
            tvCount.setText("先选至少一个动作");
            return;
        }
        Intent it = new Intent();
        StringBuilder sb = new StringBuilder();
        for (String id : picked) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        it.putExtra(EXTRA_SELECTED, sb.toString());
        setResult(RESULT_OK, it);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (pending != null) {
            handler.removeCallbacks(pending);
        }
        super.onDestroy();
    }

    private class ExerciseAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Exercise getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            LinearLayout row;
            if (convert instanceof LinearLayout) {
                row = (LinearLayout) convert;
            } else {
                row = new LinearLayout(PickActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(Ui.ripple(PickActivity.this,
                        Ui.round(PickActivity.this, Ui.SURFACE, 14f), 0x22000000));
                int p = Ui.dpi(PickActivity.this, 12);
                row.setPadding(p, p, p, p);

                LinearLayout texts = new LinearLayout(PickActivity.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                row.addView(texts, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView name = new TextView(PickActivity.this);
                name.setTextSize(15);
                name.setTextColor(Ui.INK);
                Ui.font(name, 500);
                texts.addView(name, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView sub = new TextView(PickActivity.this);
                sub.setTextSize(11.5f);
                sub.setTextColor(Ui.INK_SOFT);
                Ui.font(sub, 400);
                texts.addView(sub);

                TextView tick = new TextView(PickActivity.this);
                tick.setTextSize(13);
                tick.setGravity(Gravity.CENTER);
                Ui.font(tick, 700);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        Ui.dpi(PickActivity.this, 26), Ui.dpi(PickActivity.this, 26));
                tlp.leftMargin = Ui.dpi(PickActivity.this, 10);
                row.addView(tick, tlp);

                row.setTag(new View[]{name, sub, tick});
            }

            Exercise e = shown.get(position);
            View[] views = (View[]) row.getTag();
            TextView name = (TextView) views[0];
            TextView sub = (TextView) views[1];
            TextView tick = (TextView) views[2];
            name.setText(e.name);
            StringBuilder sb = new StringBuilder(e.part == null ? "" : e.part);
            if (e.kind != null && !e.kind.isEmpty()) {
                sb.append(" · ").append(e.kind);
            }
            if (e.nameEn != null && !e.nameEn.isEmpty()) {
                sb.append(" · ").append(e.nameEn);
            }
            sub.setText(sb.toString());

            boolean on = picked.contains(e.id);
            tick.setText(on ? "✓" : "");
            tick.setTextColor(Color.WHITE);
            tick.setBackground(on
                    ? Ui.round(PickActivity.this, Ui.ACCENT, 13f)
                    : Ui.roundStroke(PickActivity.this, 0x00000000, 13f, Ui.LINE));

            row.setOnClickListener(v -> toggle(e.id));
            return row;
        }
    }
}
