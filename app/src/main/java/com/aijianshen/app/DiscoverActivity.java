package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;

/** 发现页：大图推荐、精选训练与按部位入口。 */
public class DiscoverActivity extends Activity {

    private int todayIndex;
    private PlanProfile profile;
    private List<Data.Day> plan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_discover);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        TextView title = findViewById(R.id.tv_title);
        Ui.font(title, 800);

        IconView search = findViewById(R.id.icon_search);
        search.setIcon(IconView.SEARCH);
        search.setColor(Ui.INK);
        IconView history = findViewById(R.id.icon_history);
        history.setIcon(IconView.CLOCK);
        history.setColor(Ui.INK);
        findViewById(R.id.btn_search).setOnClickListener(v ->
                startActivity(new Intent(this, LibraryActivity.class)));
        findViewById(R.id.btn_history).setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));

        TextView all = findViewById(R.id.btn_all);
        Ui.font(all, 600);
        all.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));

        ((FrameLayout) findViewById(R.id.nav_holder)).addView(
                Nav.bar(this, Nav.TAB_DISCOVER),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        profile = Store.profile(this);
        plan = Data.weekPlan(profile);
        todayIndex = Data.todayIndex(Calendar.getInstance());

        renderHero();
        renderFeatured();
        renderBanner();
        renderParts();
    }

    private void renderHero() {
        int idx = featuredIndex(todayIndex);
        Data.Day day = plan.get(idx);
        FrameLayout hero = findViewById(R.id.hero);
        hero.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        hero.setClipToOutline(true);

        PhotoView photo = findViewById(R.id.hero_photo);
        photo.setRadiusDp(20f);
        photo.setAnimated(true);
        if (!day.exercises.isEmpty()) {
            photo.bind(day.exercises.get(0));
        } else {
            hero.setBackground(Ui.gradientCard(this, Ui.ACCENT_2, Ui.ACCENT, 20f));
        }

        View scrim = findViewById(R.id.hero_scrim);
        scrim.setBackground(Ui.gradientVertical(this, 0x00000000, 0xC2000000, 20f));

        TextView title = findViewById(R.id.hero_title);
        title.setText(day.rest ? "松一松，\n也是训练的一部分" : "今天，\n先动十分钟");
        Ui.font(title, 800);
        TextView sub = findViewById(R.id.hero_sub);
        sub.setText(day.rest
                ? day.count() + " 个拉伸动作，把肩背和髋部慢慢打开。"
                : day.count() + " 个动作 · 约 " + day.minutes() + " 分钟，不用器械也能开始。");
        Ui.font(sub, 400);

        int finalIdx = idx;
        hero.setOnClickListener(v -> openDay(finalIdx));
    }

    private int featuredIndex(int start) {
        for (int k = 0; k < 7; k++) {
            int i = (start + k) % 7;
            if (!plan.get(i).rest && !plan.get(i).exercises.isEmpty()) {
                return i;
            }
        }
        return start;
    }

    private void renderFeatured() {
        LinearLayout box = findViewById(R.id.featured);
        // 整块做成一张圆角白卡：三行共用一张卡，行与行之间用缩进分隔线，
        // clipToOutline 才能把首尾两行被切掉的直角裁干净。
        box.setBackground(Ui.round(this, Ui.SURFACE, Ui.R_CARD));
        box.setClipToOutline(true);
        int added = 0;
        for (int k = 0; k < 7 && added < 3; k++) {
            int idx = (todayIndex + k) % 7;
            Data.Day day = plan.get(idx);
            if (day.rest || day.exercises.isEmpty()) {
                continue;
            }
            box.addView(featuredRow(day, idx, added == 2));
            added++;
        }
        if (added == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有可用的训练，先去动作库看看。");
            empty.setTextColor(Ui.INK_SOFT);
            empty.setTextSize(13);
            Ui.font(empty, 400);
            box.addView(empty);
        }
    }

    private View featuredRow(Data.Day day, int idx, boolean last) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // 卡片底色由外层统一给，这里只留水波纹，否则行自带的白色直角会盖住圆角
        row.setBackground(Ui.ripple(this, Ui.round(this, 0x00000000, 0f), 0x14000000));
        row.setPadding(0, Ui.dpi(this, 12), 0, Ui.dpi(this, 12));
        row.setClickable(true);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PhotoView photo = new PhotoView(this);
        photo.setRadiusDp(16f);
        photo.setAnimated(false);
        photo.bind(day.exercises.get(0));
        row.addView(photo, new LinearLayout.LayoutParams(
                Ui.dpi(this, 84), Ui.dpi(this, 84)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 14);
        tlp.rightMargin = Ui.dpi(this, 8);
        row.addView(text, tlp);

        TextView name = new TextView(this);
        name.setText("第 " + (idx + 1) + " 天 · " + day.title);
        name.setTextColor(Ui.INK);
        name.setTextSize(17);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.font(name, 700);
        text.addView(name);

        TextView sub = new TextView(this);
        sub.setText(day.count() + " 个动作 · 约 " + day.minutes() + " 分钟 · 能力 "
                + profile.levelLabel());
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setPadding(0, Ui.dpi(this, 4), 0, 0);
        Ui.font(sub, 400);
        text.addView(sub);

        IconView chevron = new IconView(this);
        chevron.setIcon(IconView.CHEVRON);
        chevron.setColor(0xFFC7CBD1);
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dpi(this, 18), Ui.dpi(this, 18)));

        View divider = new View(this);
        divider.setBackgroundColor(0xFFEDEEF1);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 1)));
        dlp.leftMargin = Ui.dpi(this, 98);
        wrap.addView(divider, dlp);
        if (last) {
            divider.setVisibility(View.GONE);
        }

        row.setOnClickListener(v -> openDay(idx));
        return wrap;
    }

    private void renderBanner() {
        FrameLayout banner = findViewById(R.id.banner);
        banner.setBackground(Ui.round(this, Ui.ACCENT, 20f));
        banner.setClipToOutline(true);
        View scrim = findViewById(R.id.banner_scrim);
        scrim.setBackground(Ui.gradientVertical(this, 0x00000000, 0xB8000000, 20f));

        int count = 0;
        int minutes = 0;
        int recovery = -1;
        for (int i = 0; i < 7; i++) {
            Data.Day d = plan.get(i);
            if (!d.rest) {
                count++;
                minutes += d.minutes();
            } else if (recovery < 0) {
                recovery = i;
            }
        }
        if (minutes == 0) {
            minutes = 15;
        }

        PhotoView photo = findViewById(R.id.banner_photo);
        photo.setRadiusDp(20f);
        photo.setAnimated(false);
        Data.Day source = plan.get(recovery >= 0 ? recovery : todayIndex);
        if (!source.exercises.isEmpty()) {
            photo.bind(source.exercises.get(0));
        }
        if (source.rest) {
            scrim.setBackground(Ui.gradientVertical(this, 0x33000000, 0xD9000000, 20f));
        }

        TextView title = findViewById(R.id.banner_title);
        title.setText(profile.goalLabel() + "计划\n已经排好了");
        Ui.font(title, 800);
        TextView sub = findViewById(R.id.banner_sub);
        sub.setText(count + " 个训练日 · 平均 " + Math.max(1, minutes / Math.max(1, count))
                + " 分钟 · 点击调整");
        Ui.font(sub, 400);
        banner.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));
    }

    private void renderParts() {
        LinearLayout row = findViewById(R.id.part_row);
        String[] parts = new String[Data.parts().size() + 1];
        parts[0] = "全身";
        for (int i = 0; i < Data.parts().size(); i++) {
            parts[i + 1] = Data.parts().get(i);
        }
        for (String part : parts) {
            TextView chip = new TextView(this);
            chip.setText(part);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(Ui.INK_MUTED);
            chip.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 10),
                    Ui.dpi(this, 18), Ui.dpi(this, 10));
            chip.setBackground(Ui.ripple(this,
                    Ui.round(this, Ui.SURFACE, 19f), 0x14000000));
            Ui.font(chip, 500);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dpi(this, 8);
            row.addView(chip, lp);
            chip.setOnClickListener(v -> {
                Intent it = new Intent(this, LibraryActivity.class);
                if (!"全身".equals(part)) {
                    it.putExtra(LibraryActivity.EXTRA_PART, part);
                }
                startActivity(it);
            });
        }
    }

    private void openDay(int idx) {
        Intent it = new Intent(this, DayDetailActivity.class);
        it.putExtra(DayDetailActivity.EXTRA_DAY, idx);
        startActivity(it);
    }
}
