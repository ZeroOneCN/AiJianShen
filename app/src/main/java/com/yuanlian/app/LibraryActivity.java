package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class LibraryActivity extends Activity {

    public static final String EXTRA_PART = "part";

    private LinearLayout chipBox;
    private ListView listView;
    private EditText etSearch;
    private TextView tvCount;

    private String selectedPart = "全部";
    private final List<TextView> chipViews = new ArrayList<>();
    private final List<Exercise> shown = new ArrayList<>();
    private ExerciseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_library);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        chipBox = findViewById(R.id.chips);
        listView = findViewById(R.id.list);
        etSearch = findViewById(R.id.et_search);
        tvCount = findViewById(R.id.tv_count);

        TextView title = findViewById(R.id.tv_title);
        Ui.font(title, 800);
        IconView back = findViewById(R.id.icon_back);
        back.setIcon(IconView.BACK);
        back.setColor(Ui.INK);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        LinearLayout searchBar = findViewById(R.id.search_bar);
        searchBar.setBackground(Ui.round(this, Ui.SEARCH, 23f));
        IconView searchIcon = findViewById(R.id.icon_search);
        searchIcon.setIcon(IconView.SEARCH);
        searchIcon.setColor(Ui.INK_SOFT);

        Ui.font(tvCount, 500);
        Ui.font(etSearch, 400);
        etSearch.setBackgroundColor(Color.TRANSPARENT);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render();
            }
        });

        List<String> cats = new ArrayList<>();
        cats.add("全部");
        cats.addAll(Data.parts());
        for (String c : cats) {
            addChip(c);
        }

        String part = getIntent().getStringExtra(EXTRA_PART);
        if (part != null && cats.contains(part)) {
            selectedPart = part;
        } else {
            selectedPart = "全部";
        }

        adapter = new ExerciseAdapter(this);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> showDetail(shown.get(position)));

        render();
    }

    private void addChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setGravity(Gravity.CENTER);
        int lr = Ui.dpi(this, 16);
        int tb = Ui.dpi(this, 9);
        chip.setPadding(lr, tb, lr, tb);
        Ui.font(chip, 600);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dpi(this, 8);
        chipBox.addView(chip, lp);

        chip.setTag(label);
        chipViews.add(chip);
        chip.setOnClickListener(v -> {
            selectedPart = (String) v.getTag();
            render();
        });
    }

    private void paintChips() {
        for (TextView chip : chipViews) {
            boolean on = String.valueOf(chip.getTag()).equals(selectedPart);
            chip.setTextColor(on ? Ui.SURFACE : Ui.INK_SOFT);
            chip.setBackground(Ui.round(this, on ? Ui.ACCENT : Ui.SURFACE, Ui.R_CHIP));
        }
    }

    private void render() {
        paintChips();
        shown.clear();
        shown.addAll(Data.search(etSearch.getText().toString(), selectedPart));
        adapter.notifyDataSetChanged();
        tvCount.setText(shown.size() + " 个动作");
        listView.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 动作详情：动图与白色面板融为一体，滚动机要，底部固定开练按钮。 */
    private void showDetail(Exercise e) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 28f));
        root.setClipToOutline(true);

        // 演示图本身是纯白底，和弹窗同色，去掉描边和卡片边界
        PhotoView photo = new PhotoView(this);
        photo.setRadiusDp(0f);
        photo.setAspect(1.26f);
        photo.setAnimated(true);
        photo.setZoom(1f);
        photo.bind(e);
        root.addView(photo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cap = new TextView(this);
        cap.setText("循环演示");
        cap.setTextColor(Ui.INK_SOFT);
        cap.setTextSize(10);
        cap.setGravity(Gravity.END);
        cap.setPadding(Ui.dpi(this, 22), Ui.dpi(this, 4), Ui.dpi(this, 22), 0);
        Ui.font(cap, 400);
        root.addView(cap);

        ScrollView sc = new ScrollView(this);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 22);
        box.setPadding(p, Ui.dpi(this, 2), p, Ui.dpi(this, 18));
        sc.addView(box);
        root.addView(sc);

        TextView title = new TextView(this);
        title.setText(e.name);
        title.setTextColor(Ui.INK);
        title.setTextSize(22);
        Ui.font(title, 700);
        box.addView(title);

        TextView en = new TextView(this);
        en.setText(e.nameEn);
        en.setTextColor(Ui.INK_SOFT);
        en.setTextSize(12);
        en.setPadding(0, Ui.dpi(this, 4), 0, 0);
        Ui.font(en, 400);
        box.addView(en);

        FlowLayout tags = new FlowLayout(this);
        tags.setGap(8, 8);
        LinearLayout.LayoutParams tagsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagsLp.topMargin = Ui.dpi(this, 14);
        box.addView(tags, tagsLp);
        addTag(tags, e.part);
        if (!e.target.isEmpty()) {
            addTag(tags, e.target);
        }
        if (!e.kind.isEmpty()) {
            addTag(tags, e.kind);
        }
        addTag(tags, e.needsGear() ? e.gear : "徒手");

        if (e.needsGear()) {
            TextView gear = new TextView(this);
            gear.setText("需要 " + e.gear + " · 定制计划里可以排除器械动作");
            gear.setTextColor(Ui.ACCENT);
            gear.setTextSize(12);
            gear.setLineSpacing(Ui.dpi(this, 3), 1f);
            int gp = Ui.dpi(this, 12);
            gear.setPadding(gp, gp, gp, gp);
            gear.setBackground(Ui.round(this, Ui.ACCENT_SOFT, 14f));
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            glp.topMargin = Ui.dpi(this, 14);
            Ui.font(gear, 500);
            box.addView(gear, glp);
        }

        section(box, "动作要领");
        for (int i = 0; i < e.steps.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dpi(this, 10);
            box.addView(row, rlp);

            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextColor(Ui.ACCENT);
            num.setTextSize(11);
            num.setGravity(Gravity.CENTER);
            num.setBackground(Ui.round(this, Ui.ACCENT_SOFT, 12f));
            Ui.font(num, 700);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    Ui.dpi(this, 24), Ui.dpi(this, 24));
            nlp.topMargin = Ui.dpi(this, 1);
            nlp.rightMargin = Ui.dpi(this, 10);
            row.addView(num, nlp);

            TextView step = new TextView(this);
            step.setText(e.steps.get(i));
            step.setTextColor(Ui.INK);
            step.setTextSize(14);
            step.setLineSpacing(Ui.dpi(this, 5), 1f);
            Ui.font(step, 400);
            row.addView(step, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 10), Ui.dpi(this, 18), Ui.dpi(this, 18));
        root.addView(footer);

        TextView close = new TextView(this);
        close.setText("关闭");
        close.setTextColor(Ui.INK_MUTED);
        close.setTextSize(14);
        close.setGravity(Gravity.CENTER);
        Ui.font(close, 600);
        footer.addView(close, new LinearLayout.LayoutParams(0, Ui.dpi(this, 50), 1f));

        TextView start = new TextView(this);
        start.setText("练这个动作");
        start.setTextColor(Ui.SURFACE);
        start.setTextSize(15);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ripple(this, Ui.gradientPill(this), 0x33FFFFFF));
        Ui.font(start, 700);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dpi(this, 50), 1.6f);
        slp.leftMargin = Ui.dpi(this, 10);
        footer.addView(start, slp);

        close.setOnClickListener(v -> dialog.dismiss());
        start.setOnClickListener(v -> {
            dialog.dismiss();
            Intent it = new Intent(this, WorkoutActivity.class);
            it.putExtra(WorkoutActivity.EXTRA_SINGLE, e.name);
            startActivity(it);
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        dialog.show();
        if (w != null) {
            int width = getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 32);
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.88f);
            w.setLayout(width, height);
        }
    }

    private void section(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Ui.INK);
        tv.setTextSize(15);
        tv.setPadding(0, Ui.dpi(this, 18), 0, 0);
        Ui.font(tv, 700);
        parent.addView(tv);
    }

    private void addTag(ViewGroup parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(Ui.ACCENT);
        int lr = Ui.dpi(this, 12);
        int tb = Ui.dpi(this, 6);
        t.setPadding(lr, tb, lr, tb);
        t.setBackground(Ui.round(this, Ui.ACCENT_SOFT, Ui.R_PILL));
        Ui.font(t, 600);
        parent.addView(t, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 数据量不大，但仍走回收复用，滚动不掉帧。 */
    private class ExerciseAdapter extends BaseAdapter {

        private final LayoutInflater inflater;

        ExerciseAdapter(Context ctx) {
            inflater = LayoutInflater.from(ctx);
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.item_exercise, parent, false);
            }
            Exercise e = shown.get(position);

            TextView name = v.findViewById(R.id.tv_name);
            TextView sub = v.findViewById(R.id.tv_sub);
            TextView chevron = v.findViewById(R.id.tv_chevron);

            PhotoView photo = v.findViewById(R.id.photo);
            if (photo != null) {
                photo.setRadiusDp(14f);
                photo.setZoom(1f);
                photo.setAnimated(false);
                photo.bind(e);
            }

            name.setText(e.name);
            Ui.font(name, 600);
            Ui.textSize(name, 16f);
            Ui.font(sub, 400);

            String s = (e.target.isEmpty() ? "" : e.target + " · ") + e.kind
                    + (e.needsGear() ? " · 需" + e.gear : "") + " · " + e.seconds + " 秒";
            sub.setText(s);
            Ui.font(sub, 400);

            chevron.setText("›");
            Ui.font(chevron, 400);

            v.setBackground(Ui.ripple(LibraryActivity.this,
                    Ui.round(LibraryActivity.this, Ui.SURFACE, Ui.R_CARD), 0x22000000));
            return v;
        }
    }
}
