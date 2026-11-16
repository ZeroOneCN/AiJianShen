package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 设置页：本地档案、计划入口与本地数据管理。 */
public class SettingsActivity extends Activity {

    private TextView btnCustom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_settings);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        TextView title = findViewById(R.id.tv_title);
        Ui.font(title, 800);

        TextView footer = findViewById(R.id.tv_footer);
        footer.setText("私人健身教练 " + versionName() + " · 本地训练版");

        IconView user = findViewById(R.id.icon_user);
        user.setIcon(IconView.USER);
        user.setColor(Ui.ACCENT);
        user.setStrokeDp(2f);

        TextView name = findViewById(R.id.tv_name);
        Ui.font(name, 800);
        TextView sub = findViewById(R.id.tv_sub);
        Ui.font(sub, 400);

        IconView sync = findViewById(R.id.btn_sync);
        sync.setIcon(IconView.REFRESH);
        sync.setColor(Ui.ACCENT);
        sync.setOnClickListener(v -> Toast.makeText(this,
                "本地模式：所有记录只保存在这台设备上", Toast.LENGTH_SHORT).show());

        ((FrameLayout) findViewById(R.id.nav_holder)).addView(
                Nav.bar(this, Nav.TAB_SETTINGS),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        btnCustom = findViewById(R.id.btn_custom);
        btnCustom.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        Ui.font(btnCustom, 700);
        btnCustom.setOnClickListener(v -> startActivity(new Intent(this, PlanActivity.class)));

        findViewById(R.id.profile_row).setOnClickListener(v ->
                startActivity(new Intent(this, PlanActivity.class)));

        LinearLayout main = findViewById(R.id.card_main);
        main.setBackground(Ui.round(this, Ui.SURFACE, 20f));
        LinearLayout more = findViewById(R.id.card_more);
        more.setBackground(Ui.round(this, Ui.SURFACE, 20f));

        addRow(main, IconView.TARGET, Ui.GREEN, "定制计划",
                "按目标、时间与器械重新排一周", false,
                v -> startActivity(new Intent(this, PlanActivity.class)));
        addRow(main, IconView.DUMBBELL, Ui.BLUE_TILE, "动作库",
                Data.all().size() + " 个动作，可搜索、看要领", false,
                v -> startActivity(new Intent(this, LibraryActivity.class)));
        addRow(main, IconView.RACK, Ui.PURPLE, "我的训练",
                "自己挑动作存成模板，随时一键开练", false,
                v -> startActivity(new Intent(this, TemplatesActivity.class)));
        addRow(main, IconView.TROPHY, Ui.GREEN, "成就",
                "已点亮 " + Achievements.earned(this) + " 枚徽章", false,
                v -> startActivity(new Intent(this, AchievementsActivity.class)));
        addRow(main, IconView.SLIDERS, Ui.ORANGE, "训练设置",
                "预备、休息、语音、音乐与提示音", false,
                v -> startActivity(new Intent(this, WorkoutSettingsActivity.class)));
        addRow(main, IconView.SETTINGS, Ui.SLATE, "通用设置",
                "体重单位、身高与提醒", false,
                v -> startActivity(new Intent(this, GeneralSettingsActivity.class)));
        addRow(main, IconView.SPEAKER, Ui.PURPLE, "语音播报",
                "使用系统中文语音，可试听", false,
                v -> startActivity(new Intent(this, VoiceSettingsActivity.class)));
        addRow(main, IconView.CLOCK, Ui.RED, "训练提醒",
                "按固定时间发本地通知", false,
                v -> startActivity(new Intent(this, ReminderActivity.class)));
        addRow(main, IconView.WIFI, Ui.BLUE_TILE, "手机间同步",
                "换手机时把训练与体重数据搬过去", false,
                v -> startActivity(new Intent(this, SyncActivity.class)));
        addRow(main, IconView.SHARE, Ui.TEAL, "导出备份",
                "把全部记录写成一个 JSON 文件", false, v -> exportData());
        addRow(main, IconView.REFRESH, Ui.SLATE, "从备份恢复",
                "读取 JSON 文件，按并集合并，不会覆盖本机记录", false,
                v -> importData());
        addRow(main, IconView.SCALE, Ui.BLUE_TILE, "连接体脂秤",
                "蓝牙直连，读体重与体脂率", false,
                v -> startActivity(new Intent(this, ScaleActivity.class)));
        addRow(main, IconView.SHIELD, Ui.TEAL, "数据与隐私",
                "本机存储，网络仅用于手机间同步", false,
                v -> info("数据与隐私",
                        "训练记录、体重、身高、计划条件与训练偏好只保存在本机，没有账号体系，也不上传到任何服务器。\n\n蓝牙只用于连接你自己的体脂秤，读取重量与阻抗；体成分由本机按 BIA 公式估算。\n\n手机间同步是可选项：只有你主动开启同步服务或填写对方地址时，应用才会在同一 Wi-Fi 下与另一台手机交换数据；平时不发起任何网络请求。\n\n训练提醒使用系统本地通知，不需要联网，也不会读取联系人。\n\n卸载应用或在下方清除记录后，数据会被删除。"));
        addRow(main, IconView.TRASH, Ui.RED, "清除训练记录",
                "保留定制计划与个人数据，只删除训练记录", true,
                v -> confirmClear());

        addRow(more, IconView.SHARE, Ui.SLATE, "分享给朋友", null, false,
                v -> share());
        addRow(more, IconView.STAR, Ui.SLATE, "关于私人健身教练", "版本与说明", true,
                v -> info("关于私人健身教练",
                        "私人健身教练 " + versionName() + "\n\n训练记录、体重与计划条件只保存在本机，不需要账号，也不上传。\n\n本应用不包含原版应用的任何图片、插画、动画或文案。"));
    }

    /** 版本号从安装包读取，避免和 build.gradle 里写的版本对不上。 */
    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null || v.isEmpty() ? "1.8.3" : v;
        } catch (Exception e) {
            return "1.8.3";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        btnCustom.setText(Store.hasProfile(this) ? "调整我的计划" : "定制我的计划");
    }

    private void addRow(LinearLayout card, int icon, int tileColor, String title,
                        String sub, boolean last, View.OnClickListener click) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Nav.press(this));
        row.setClickable(true);
        row.setFocusable(true);
        int p = Ui.dpi(this, 14);
        row.setPadding(p, p, Ui.dpi(this, 16), p);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(this, tileColor, 11f));
        row.addView(tile, new LinearLayout.LayoutParams(
                Ui.dpi(this, 40), Ui.dpi(this, 40)));
        IconView ic = new IconView(this);
        ic.setIcon(icon);
        ic.setColor(Ui.SURFACE);
        ic.setStrokeDp(2f);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(this, 22), Ui.dpi(this, 22), Gravity.CENTER));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dpi(this, 14);
        row.addView(texts, tlp);

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Ui.INK);
        name.setTextSize(16);
        Ui.font(name, 500);
        texts.addView(name);

        if (sub != null) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextColor(Ui.INK_SOFT);
            s.setTextSize(12);
            s.setPadding(0, Ui.dpi(this, 2), 0, 0);
            Ui.font(s, 400);
            texts.addView(s);
        }

        IconView chevron = new IconView(this);
        chevron.setIcon(IconView.CHEVRON);
        chevron.setColor(0xFFC7CBD1);
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dpi(this, 17), Ui.dpi(this, 17)));

        row.setOnClickListener(click);
        card.addView(wrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!last) {
            View divider = new View(this);
            divider.setBackgroundColor(0xFFEFEFF2);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 1)));
            dlp.leftMargin = Ui.dpi(this, 68);
            card.addView(divider, dlp);
        }
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,
                "我在用私人健身教练：一个纯本地、不联网的徒手训练应用。动作库加一周定制计划，练完只记在本机。");
        startActivity(Intent.createChooser(send, "分享私人健身教练"));
    }

    private void confirmClear() {
        dialog("清除训练记录？",
                "会删除完成日期、分钟数与训练感受；定制计划、体重和身高会保留。此操作不能撤销。",
                "清除", Ui.RED, () -> {
                    Store.clearRecords(this);
                    Toast.makeText(this, "训练记录已清除", Toast.LENGTH_SHORT).show();
                });
    }

    private void info(String title, String message) {
        dialog(title, message, "知道了", Ui.ACCENT, null);
    }

    // ---- 备份：导出/恢复都走系统文件选择器，不碰任何网络 ----

    private static final int REQ_EXPORT = 2001;
    private static final int REQ_IMPORT = 2002;

    private void exportData() {
        Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("application/json");
        it.putExtra(Intent.EXTRA_TITLE,
                "yuanlian-" + Store.key(java.util.Calendar.getInstance()) + ".json");
        try {
            startActivityForResult(it, REQ_EXPORT);
        } catch (Exception e) {
            Toast.makeText(this, "这台设备没有可用的文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void importData() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("*/*");
        it.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/json", "text/plain", "application/octet-stream"});
        try {
            startActivityForResult(it, REQ_IMPORT);
        } catch (Exception e) {
            Toast.makeText(this, "这台设备没有可用的文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT && requestCode != REQ_IMPORT) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        android.net.Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) {
                    throw new java.io.IOException("无法写入");
                }
                os.write(LanSync.snapshot(this).toString()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
                Toast.makeText(this, "已导出，建议另存一份到电脑", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_IMPORT) {
            // 只读前 8 MB：备份就是一份几百字节的 JSON，超过这个量说明选错了文件，
            // 不设上限的话选个视频就会把整个文件读进内存崩掉。
            final int limit = 8 * 1024 * 1024;
            try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new java.io.IOException("无法读取");
                }
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > limit) {
                        Toast.makeText(this, "这个文件太大，不是本应用的备份",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    bos.write(buf, 0, n);
                }
                String text = new String(bos.toByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8);
                Store.MergeStats st = LanSync.merge(this, new org.json.JSONObject(text));
                if (st.nothing()) {
                    Toast.makeText(this, "没有新数据，本机已经包含了这份备份",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "已恢复：新增 " + st.days + " 个训练日、"
                            + st.sessions + " 条训练记录、" + st.weights + " 条体重",
                            Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "这份文件不是本应用的备份", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void dialog(String titleText, String message, String primary,
                        int primaryColor, Runnable action) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 24f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(Ui.INK);
        title.setTextSize(20);
        Ui.font(title, 800);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(message);
        body.setTextColor(Ui.INK_SOFT);
        body.setTextSize(14);
        body.setLineSpacing(Ui.dp(this, 4), 1f);
        Ui.font(body, 400);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dpi(this, 10);
        root.addView(body, blp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        rlp.topMargin = Ui.dpi(this, 20);
        root.addView(row, rlp);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(Ui.INK_MUTED);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancel, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp.rightMargin = Ui.dpi(this, 10);
        row.addView(cancel, clp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView ok = new TextView(this);
        ok.setText(primary);
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(15);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(this, Ui.round(this, primaryColor, 24f), 0x33FFFFFF));
        Ui.font(ok, 700);
        row.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ok.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            }
            dialog.dismiss();
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
            int width = getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 48);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
