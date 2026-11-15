package com.yuanlian.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 连接体脂秤：扫描 → 连接 → 实时读数 → 稳定后保存体重与体成分。
 *
 * <p>协议移植自 AntAF 的 {@code scale_reader.py}，秤本身只给体重与阻抗，
 * 体成分由 {@link BodyComp} 在本机估算。
 */
public class ScaleActivity extends Activity implements BleScale.Listener {

    private static final int REQ_BT = 71;

    private BleScale scale;
    private TextView stateText;
    private TextView weightText;
    private TextView hintText;
    private TextView savedText;
    private LinearLayout devices;

    private final Map<String, String> found = new LinkedHashMap<>();
    private long lastSavedAt;
    private float lastSavedKg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "连接体脂秤", v -> finish()));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 8), p, Ui.dpi(this, 24));
        sc.addView(body);

        body.addView(readoutCard());
        body.addView(deviceSection(), cardLp());
        UiList.hint(this, body,
                "测量时请光脚站稳，阻抗数据才会一起传过来；没有阻抗就只能记体重与 BMI。");

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(p, Ui.dpi(this, 12), p, Ui.dpi(this, 14));
        root.addView(actions);
        TextView rescan = UiList.primary(this, "搜索体重秤");
        actions.addView(rescan, UiList.lp(this, 50, 0));
        rescan.setOnClickListener(v -> startScan());

        scale = new BleScale(this, this);
        if (BleScale.hasPermission(this)) {
            startScan();
        } else {
            requestPermissions(BleScale.permissions(), REQ_BT);
        }
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 12);
        return lp;
    }

    private LinearLayout readoutCard() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 20);
        card.setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        stateText = new TextView(this);
        stateText.setText("准备中…");
        stateText.setTextColor(Ui.INK_SOFT);
        stateText.setTextSize(13);
        Ui.font(stateText, 500);
        head.addView(stateText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        IconView icon = new IconView(this);
        icon.setIcon(IconView.SCALE);
        icon.setColor(Ui.ACCENT);
        icon.setStrokeDp(1.8f);
        head.addView(icon, new LinearLayout.LayoutParams(
                Ui.dpi(this, 26), Ui.dpi(this, 26)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(0, Ui.dpi(this, 8), 0, 0);
        // 必须给满宽：WRAP_CONTENT 时宽度会按初始的 "--" 定死，
        // 后来 setText("73.9") 虽然重新测量，但约束收得太紧，只画得出第一个字符。
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        weightText = new TextView(this);
        weightText.setTextColor(Ui.INK);
        weightText.setTextSize(52);
        weightText.setIncludeFontPadding(false);
        weightText.setSingleLine(true);
        Ui.font(weightText, 800);
        // 数字和单位放在同一个 TextView 里，宽度固定占满整行。
        // 拆成「数字 + 单位」两个控件时，数字的宽度会被初始的 "--" 锁死，
        // setText("73.9") 之后只画得出前两位（真机上实测宽度停在 162px）。
        row.addView(weightText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setReadout(null);

        hintText = new TextView(this);
        hintText.setText("踩一下秤，数字会实时跟到这里。");
        hintText.setTextColor(Ui.INK_SOFT);
        hintText.setTextSize(12);
        hintText.setLineSpacing(Ui.dp(this, 3), 1f);
        hintText.setPadding(0, Ui.dpi(this, 8), 0, 0);
        Ui.font(hintText, 400);
        card.addView(hintText);

        savedText = new TextView(this);
        savedText.setTextColor(Ui.ACCENT);
        savedText.setTextSize(13);
        savedText.setLineSpacing(Ui.dp(this, 3), 1f);
        savedText.setPadding(0, Ui.dpi(this, 8), 0, 0);
        savedText.setVisibility(View.GONE);
        Ui.font(savedText, 600);
        card.addView(savedText);
        return card;
    }

    private LinearLayout deviceSection() {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 8);
        card.setPadding(p, p, p, p);
        devices = new LinearLayout(this);
        devices.setOrientation(LinearLayout.VERTICAL);
        card.addView(devices, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        renderDevices();
        return card;
    }

    private void renderDevices() {
        devices.removeAllViews();
        if (found.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 22),
                    Ui.dpi(this, 16), Ui.dpi(this, 24));
            devices.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            IconView icon = new IconView(this);
            icon.setIcon(IconView.SCALE);
            icon.setColor(0xFFC7CBD1);
            icon.setStrokeDp(1.6f);
            empty.addView(icon, new LinearLayout.LayoutParams(
                    Ui.dpi(this, 34), Ui.dpi(this, 34)));

            TextView title = new TextView(this);
            title.setText("还没有搜到设备");
            title.setTextColor(Ui.INK);
            title.setTextSize(14);
            title.setPadding(0, Ui.dpi(this, 10), 0, 0);
            Ui.font(title, 600);
            empty.addView(title);

            TextView sub = new TextView(this);
            sub.setText("秤在休眠，点搜索后踩一下秤。");
            sub.setTextColor(Ui.INK_SOFT);
            sub.setTextSize(12);
            sub.setGravity(Gravity.CENTER);
            sub.setLineSpacing(Ui.dp(this, 3), 1f);
            sub.setPadding(0, Ui.dpi(this, 6), 0, 0);
            Ui.font(sub, 400);
            empty.addView(sub);
            return;
        }
        for (Map.Entry<String, String> e : found.entrySet()) {
            final String address = e.getKey();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(Nav.press(this));
            row.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 14),
                    Ui.dpi(this, 12), Ui.dpi(this, 14));
            devices.addView(row);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(this);
            name.setText(e.getValue());
            name.setTextColor(Ui.INK);
            name.setTextSize(15);
            Ui.font(name, 600);
            texts.addView(name);

            TextView addr = new TextView(this);
            addr.setText(address);
            addr.setTextColor(Ui.INK_SOFT);
            addr.setTextSize(11);
            addr.setPadding(0, Ui.dpi(this, 2), 0, 0);
            Ui.font(addr, 400);
            texts.addView(addr);

            TextView go = new TextView(this);
            go.setText("连接");
            go.setTextColor(Ui.ACCENT);
            go.setTextSize(13);
            Ui.font(go, 600);
            row.addView(go);

            row.setOnClickListener(v -> {
                stateText.setText("正在连接 " + e.getValue() + "…");
                scale.connect(address);
            });
        }
    }

    private void startScan() {
        if (!BleScale.bluetoothOn()) {
            stateText.setText("系统蓝牙没打开");
            hintText.setText("请到系统设置里打开蓝牙，再回来重试。");
            return;
        }
        found.clear();
        renderDevices();
        savedText.setVisibility(View.GONE);
        setReadout(null);
        hintText.setText("搜索中，找到秤后点它的名字连接。");
        lastSavedAt = 0;
        scale.startScan();
    }

    // ---- BleScale 回调 ----

    @Override
    public void onFound(String name, String address, int rssi) {
        if (!found.containsKey(address)) {
            found.put(address, name);
            renderDevices();
        }
    }

    @Override
    public void onState(String state) {
        stateText.setText(state);
        // 连接阶段别让下面那行还写着「搜索中」；「没找到」交给下面的空态卡片说明，避免重复
        if (state.startsWith("正在连接") || state.startsWith("已连接")) {
            hintText.setText("秤连上后光脚站上去，数字会实时跟到这里。");
        } else if (state.startsWith("没有找到")) {
            hintText.setText("");
        }
    }

    /**
     * 画出读数：大号数字 + 小号单位。
     *
     * @param kg 公斤值；null 表示还没有读数，显示占位
     */
    private void setReadout(Float kg) {
        // 下秤后秤会归零并继续回传 0.0，按「没有读数」处理，免得界面上挂着 0.0 kg
        boolean empty = kg == null || Math.abs(kg) < 3f;
        String num = empty ? "--"
                : String.format(Locale.US, "%.1f", Store.kgToDisplay(this, kg));
        SpannableStringBuilder sb = new SpannableStringBuilder(num);
        int start = sb.length();
        sb.append("  ").append(Store.weightUnit(this));
        int end = sb.length();
        sb.setSpan(new AbsoluteSizeSpan(17, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(Ui.INK_SOFT), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        weightText.setText(sb);
    }

    @Override
    public void onLive(float kg) {
        setReadout(kg);
        // 稳定后的重复帧还会走 onLive，别把「测量完成」那句话顶掉
        if (System.currentTimeMillis() - lastSavedAt > 3000) {
            hintText.setText("站稳别动，等读数稳定…");
        }
    }

    @Override
    public void onStable(float kg, float impedance) {
        setReadout(kg);
        if (Math.abs(kg) < 3f) {
            // 空秤的稳定帧，别记成体重
            return;
        }

        long now = System.currentTimeMillis();
        // 一次称重过程会连着来好几帧稳定包（实测间隔 5~6 秒），
        // 窗口太短会把同一次测量记成三四条，30 秒内同重量只留一条。
        if (Math.abs(kg - lastSavedKg) < 0.05f && now - lastSavedAt < 30000) {
            return;
        }
        lastSavedKg = kg;
        lastSavedAt = now;

        Store.addWeight(this, Store.dateOf(now), kg);
        Store.addBodyRecord(this, now, kg, impedance, null, "", false);

        BodyComp.Result r = BodyComp.calculate(kg, impedance,
                Store.heightCm(this), Store.age(this), Store.isMale(this));
        StringBuilder sb = new StringBuilder();
        sb.append("已保存 ").append(String.format(Locale.US, "%.1f", kg)).append(" kg");
        if (impedance > 0) {
            sb.append(" · 阻抗 ").append(Math.round(impedance)).append(" Ω");
        }
        if (r.valid) {
            sb.append("\n体脂率 ").append(r.text("body_fat")).append("%")
                    .append(" · 肌肉量 ").append(r.text("muscle")).append(" kg")
                    .append(" · 水分 ").append(r.text("water")).append("%");
        } else {
            sb.append("\n补全身高与年龄后可以估算体成分。");
        }
        savedText.setText(sb.toString());
        savedText.setVisibility(View.VISIBLE);
        hintText.setText(impedance > 0 ? "测量完成" : "这次没有阻抗数据，只记录了体重");
        Toast.makeText(this, "已记录 " + String.format(Locale.US, "%.1f", kg) + " kg",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(String message) {
        stateText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ---- 权限 ----

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_BT) {
            return;
        }
        boolean ok = grantResults.length > 0;
        for (int g : grantResults) {
            if (g != PackageManager.PERMISSION_GRANTED) {
                ok = false;
            }
        }
        if (ok) {
            startScan();
        } else {
            stateText.setText("需要蓝牙权限才能读取体重秤");
            hintText.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? "到系统设置 → 应用 → 私人健身教练 → 附近设备，允许后重试。"
                    : "到系统设置 → 应用 → 私人健身教练 → 位置信息，允许后重试。");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scale != null) {
            scale.close();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
