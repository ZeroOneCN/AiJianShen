package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** “换成别的动作”底部面板：同部位优先，可搜索、切部位。 */
public final class ReplaceSheet {

    public interface OnPick {
        void onPick(Exercise e);
    }

    private ReplaceSheet() {
    }

    public static void show(Activity a, Exercise current, OnPick onPick) {
        Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SURFACE);
        float r = Ui.dp(a, 28);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(bg);
        int p = Ui.dpi(a, 18);
        root.setPadding(p, Ui.dpi(a, 10), p, Ui.dpi(a, 12));

        View handle = new View(a);
        handle.setBackground(Ui.round(a, 0xFFE2E5EA, 3f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                Ui.dpi(a, 42), Ui.dpi(a, 5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dpi(a, 16);
        root.addView(handle, hlp);

        TextView title = new TextView(a);
        title.setText("换成别的动作");
        title.setTextColor(Ui.INK);
        title.setTextSize(21);
        Ui.font(title, 800);
        root.addView(title);

        TextView sub = new TextView(a);
        sub.setText("当前：" + current.name);
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dpi(a, 4), 0, Ui.dpi(a, 12));
        Ui.font(sub, 400);
        root.addView(sub);

        LinearLayout search = new LinearLayout(a);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.round(a, Ui.SEARCH, 21f));
        search.setPadding(Ui.dpi(a, 14), 0, Ui.dpi(a, 14), 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 44));
        root.addView(search, slp);
        IconView icon = new IconView(a);
        icon.setIcon(IconView.SEARCH);
        icon.setColor(Ui.INK_SOFT);
        icon.setStrokeDp(2f);
        search.addView(icon, new LinearLayout.LayoutParams(Ui.dpi(a, 18), Ui.dpi(a, 18)));
        EditText et = new EditText(a);
        et.setHint("搜索动作或部位");
        et.setTextSize(14);
        et.setTextColor(Ui.INK);
        et.setHintTextColor(Ui.INK_SOFT);
        et.setBackgroundColor(Color.TRANSPARENT);
        et.setSingleLine(true);
        Ui.font(et, 400);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        elp.leftMargin = Ui.dpi(a, 8);
        search.addView(et, elp);

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, Ui.dpi(a, 12), 0, Ui.dpi(a, 4));
        hs.addView(chips);
        root.addView(hs);

        Set<String> cats = new LinkedHashSet<>();
        cats.add(current.part);
        cats.addAll(Data.parts());
        cats.add("全部");

        ListView list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final String[] part = {current.part};
        final List<Exercise> shown = new ArrayList<>();
        list.setAdapter(new Adapter(a, shown));

        List<TextView> chipViews = new ArrayList<>();
        for (String c : cats) {
            TextView chip = new TextView(a);
            chip.setText(c);
            chip.setTextSize(12);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dpi(a, 14), Ui.dpi(a, 8), Ui.dpi(a, 14), Ui.dpi(a, 8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = Ui.dpi(a, 8);
            chips.addView(chip, clp);
            chipViews.add(chip);
            chip.setTag(c);
            chip.setOnClickListener(v -> {
                part[0] = (String) v.getTag();
                paintChips(chipViews, part[0]);
                fill(a, list, shown, part[0], et.getText().toString(), current);
            });
        }
        paintChips(chipViews, part[0]);

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a1, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a1, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fill(a, list, shown, part[0], s.toString(), current);
            }
        });

        list.setOnItemClickListener((parent, view, position, id) -> {
            Exercise pick = shown.get(position);
            dialog.dismiss();
            onPick.onPick(pick);
        });

        fill(a, list, shown, part[0], "", current);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        dialog.show();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.82f));
        }
    }

    private static void paintChips(List<TextView> chips, String selected) {
        for (TextView chip : chips) {
            boolean on = String.valueOf(chip.getTag()).equals(selected);
            chip.setTextColor(on ? Color.WHITE : Ui.INK_SOFT);
            chip.setBackground(Ui.round(chip.getContext(), on ? Ui.ACCENT : Ui.SURFACE,
                    Ui.R_CHIP));
            Ui.font(chip, on ? 600 : 400);
        }
    }

    private static void fill(Activity a, ListView list, List<Exercise> shown,
                             String part, String keyword, Exercise current) {
        shown.clear();
        for (Exercise e : Data.search(keyword, part)) {
            if (!e.id.equals(current.id)) {
                shown.add(e);
            }
        }
        ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
    }

    private static class Adapter extends BaseAdapter {
        private final Activity a;
        private final List<Exercise> items;

        Adapter(Activity a, List<Exercise> items) {
            this.a = a;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dpi(a, 8), 0, Ui.dpi(a, 8));
                row.setBackground(Nav.press(a));

                PhotoView photo = new PhotoView(a);
                photo.setRadiusDp(14f);
                photo.setAnimated(false);
                photo.setId(R.id.photo);
                row.addView(photo, new LinearLayout.LayoutParams(
                        Ui.dpi(a, 62), Ui.dpi(a, 62)));

                LinearLayout texts = new LinearLayout(a);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tlp.leftMargin = Ui.dpi(a, 12);
                row.addView(texts, tlp);

                TextView name = new TextView(a);
                name.setId(R.id.tv_name);
                name.setTextColor(Ui.INK);
                name.setTextSize(16);
                Ui.font(name, 600);
                texts.addView(name);

                TextView sub = new TextView(a);
                sub.setId(R.id.tv_sub);
                sub.setTextColor(Ui.INK_SOFT);
                sub.setTextSize(12);
                sub.setPadding(0, Ui.dpi(a, 3), 0, 0);
                Ui.font(sub, 400);
                texts.addView(sub);

                FrameLayout pick = new FrameLayout(a);
                pick.setBackground(Ui.round(a, Ui.ACCENT_SOFT, 15f));
                row.addView(pick, new LinearLayout.LayoutParams(
                        Ui.dpi(a, 30), Ui.dpi(a, 30)));
                IconView plus = new IconView(a);
                plus.setIcon(IconView.PLUS);
                plus.setColor(Ui.ACCENT);
                plus.setStrokeDp(2f);
                pick.addView(plus, new FrameLayout.LayoutParams(
                        Ui.dpi(a, 15), Ui.dpi(a, 15), Gravity.CENTER));
            }

            Exercise e = items.get(position);
            PhotoView photo = row.findViewById(R.id.photo);
            photo.bind(e);
            TextView name = row.findViewById(R.id.tv_name);
            name.setText(e.name);
            TextView sub = row.findViewById(R.id.tv_sub);
            sub.setText((e.target.isEmpty() ? e.part : e.target)
                    + " · " + e.kind + (e.needsGear() ? " · 需" + e.gear : ""));
            return row;
        }
    }
}
