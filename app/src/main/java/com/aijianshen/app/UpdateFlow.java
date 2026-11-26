package com.aijianshen.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

/**
 * 更新流程：检查 → 提示 → 下载 → 交给系统安装器。
 *
 * <p>两个入口：首页冷启动时静默检查一次（6 小时内不重复），设置页「检查更新」手动查。
 * 自动检查失败不打扰用户——没主动问，就不该弹错误框。
 *
 * <p>强制更新（服务端 forceUpdate，或本机 versionCode 低于 minSupportedVersionCode）
 * 的弹窗不可取消、没有「稍后」，返回键也关不掉，这是文档要求的行为。
 */
public final class UpdateFlow {

    private static final String SP = "ota";
    private static final String K_LAST_CHECK = "last_check";
    private static final String K_SKIPPED = "skipped_code";
    private static final String K_AWAIT_PERM = "await_install_permission";
    /** 上次检查到「必须更新」的版本号。有它就不再受检查间隔限制，重启后立刻再弹。 */
    private static final String K_KNOWN_FORCE = "known_force_code";

    /** 自动检查间隔。手动检查不受限。 */
    private static final long AUTO_GAP = 6 * 60 * 60 * 1000L;

    /** 自动检查到的新版本，等界面到前台再弹，避免弹在引导页后面。 */
    private static UpdateChecker.Info pending;
    private static boolean checking;

    private UpdateFlow() {
    }

    // ---- 入口 ----

    /** 首页冷启动调一次。失败静默，只记时间。 */
    public static void checkOnLaunch(final Activity a) {
        final Context app = a.getApplicationContext();
        // 上次装完留下的安装包：已经不是新版本了就删掉，别一直占着缓存
        new Thread(() -> {
            File cached = UpdateChecker.cachedApk(app);
            if (cached.exists() && !UpdateChecker.isNewer(app, cached)) {
                UpdateChecker.clearCache(app);
            }
        }).start();

        if (checking) {
            return;
        }
        // 已知有强制更新时不再等那 6 小时：用户上次可能把下载失败关掉了，
        // 重启后要让他重新面对这件事，而不是在旧版本里安稳待一天。
        boolean urgent = sp(app).getInt(K_KNOWN_FORCE, 0) > UpdateChecker.versionCode(app);
        if (!urgent
                && System.currentTimeMillis() - sp(app).getLong(K_LAST_CHECK, 0) < AUTO_GAP) {
            return;
        }
        checking = true;
        new Thread(() -> {
            UpdateChecker.Info info = null;
            try {
                info = UpdateChecker.check(app);
            } catch (UpdateChecker.Failure ignored) {
                // 没网、服务器抽风都不该在启动时糊用户一脸
            } finally {
                checking = false;
            }
            if (info == null) {
                return;
            }
            stamp(app);
            if (!info.hasUpdate) {
                return;
            }
            // 用户主动跳过的版本不再自动弹；强制更新例外
            if (!info.forced() && info.versionCode == sp(app).getInt(K_SKIPPED, 0)) {
                return;
            }
            pending = info;
            final UpdateChecker.Info found = info;
            new Handler(Looper.getMainLooper()).post(() -> showPending(a, found));
        }).start();
    }

    /** 手动检查：结果无论如何都给一句明确的话。 */
    public static void checkManual(final Activity a, final TextView status) {
        if (checking) {
            return;
        }
        checking = true;
        if (status != null) {
            status.setText("正在检查…");
        }
        new Thread(() -> {
            UpdateChecker.Info info = null;
            String err = null;
            try {
                info = UpdateChecker.check(a.getApplicationContext());
            } catch (UpdateChecker.Failure f) {
                err = f.getMessage();
            } finally {
                checking = false;
            }
            final UpdateChecker.Info fi = info;
            final String fe = err;
            a.runOnUiThread(() -> {
                if (a.isFinishing() || a.isDestroyed()) {
                    return;
                }
                String current = UpdateChecker.versionName(a);
                if (fe != null) {
                    if (status != null) {
                        status.setText("当前 " + current + " · 上次检查失败");
                    }
                    Toast.makeText(a, fe, Toast.LENGTH_LONG).show();
                    return;
                }
                stamp(a);
                if (status != null) {
                    status.setText(fi.hasUpdate
                            ? "发现 " + fi.versionName + "，点这里下载"
                            : "当前 " + current + " · 已是最新");
                }
                if (fi.hasUpdate) {
                    showUpdateDialog(a, fi);
                } else {
                    Toast.makeText(a, "已经是最新版本", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /** 设置页那一行的副标题。 */
    public static String statusText(Context c) {
        String current = UpdateChecker.versionName(c);
        long last = sp(c).getLong(K_LAST_CHECK, 0);
        if (last <= 0) {
            return "当前 " + current + " · 还没检查过";
        }
        return "当前 " + current + " · " + ago(System.currentTimeMillis() - last);
    }

    /** MainActivity / SettingsActivity 的 onResume 都要调：从「允许安装未知应用」回来时续装。 */
    public static void resumePendingInstall(Activity a) {
        if (!sp(a).getBoolean(K_AWAIT_PERM, false)) {
            return;
        }
        File apk = UpdateChecker.cachedApk(a);
        if (!UpdateChecker.isNewer(a, apk)) {
            sp(a).edit().putBoolean(K_AWAIT_PERM, false).apply();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !a.getPackageManager().canRequestPackageInstalls()) {
            return;
        }
        install(a, apk);
    }

    // ---- 提示弹窗 ----

    private static void showPending(Activity a, UpdateChecker.Info info) {
        if (pending != info || a.isFinishing() || a.isDestroyed()) {
            return;
        }
        pending = null;
        showUpdateDialog(a, info);
    }

    private static void showUpdateDialog(final Activity a, final UpdateChecker.Info info) {
        final boolean force = info.forced();
        if (force) {
            sp(a).edit().putInt(K_KNOWN_FORCE, info.versionCode).apply();
        }

        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(!force);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(a, Ui.SURFACE, 26f));
        int p = Ui.dpi(a, 22);
        root.setPadding(p, p, p, p);

        // 头部：图标 + 标题 + 版本行
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout tile = new FrameLayout(a);
        tile.setBackground(Ui.round(a, Ui.ACCENT_SOFT, 13f));
        head.addView(tile, new LinearLayout.LayoutParams(Ui.dpi(a, 44), Ui.dpi(a, 44)));
        IconView ic = new IconView(a);
        ic.setIcon(IconView.REFRESH);
        ic.setColor(Ui.ACCENT);
        tile.addView(ic, new FrameLayout.LayoutParams(
                Ui.dpi(a, 24), Ui.dpi(a, 24), Gravity.CENTER));

        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = Ui.dpi(a, 13);
        head.addView(names, nlp);

        TextView title = new TextView(a);
        title.setText(force ? "需要更新才能继续" : "发现新版本 " + info.versionName);
        title.setTextColor(Ui.INK);
        title.setTextSize(19);
        title.setIncludeFontPadding(false);
        Ui.font(title, 800);
        names.addView(title);

        TextView sub = new TextView(a);
        sub.setText(versionLine(a, info));
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(12);
        sub.setPadding(0, Ui.dpi(a, 3), 0, 0);
        Ui.font(sub, 400);
        names.addView(sub);

        // 更新内容
        TextView label = new TextView(a);
        label.setText("更新内容");
        label.setTextColor(Ui.INK);
        label.setTextSize(13);
        Ui.font(label, 700);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dpi(a, 18);
        root.addView(label, llp);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.round(a, Ui.CARD_SOFT, 14f));
        int bp = Ui.dpi(a, 12);
        box.setPadding(bp, bp, bp, bp);

        // 更新日志长度不可控，套一层 ScrollView 再给个高度上限：长日志能滑，
        // 不会把按钮顶到屏幕外面去
        ScrollView logScroll = new ScrollView(a);
        logScroll.setVerticalScrollBarEnabled(false);
        logScroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dpi(a, 8);
        root.addView(logScroll, blp);

        String[] lines = info.lines();
        if (lines.length == 0) {
            lines = new String[]{"这次更新主要是稳定性修复。"};
        }
        for (String line : lines) {
            TextView tv = new TextView(a);
            tv.setText("· " + line);
            tv.setTextColor(Ui.INK_MUTED);
            tv.setTextSize(13);
            tv.setLineSpacing(Ui.dp(a, 3), 1f);
            Ui.font(tv, 400);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (box.getChildCount() > 0) {
                tlp.topMargin = Ui.dpi(a, 6);
            }
            box.addView(tv, tlp);
        }

        if (force) {
            // 强制更新必须说清「为什么关不掉」，否则用户只会觉得这是流氓弹窗
            TextView note = new TextView(a);
            String why = info.reason != null && !info.reason.trim().isEmpty()
                    ? info.reason.trim()
                    : "这一版修的是必须修的问题，装好之后才能继续用。";
            note.setText(why);
            note.setTextColor(Ui.CREAM_INK);
            note.setTextSize(12.5f);
            note.setLineSpacing(Ui.dp(a, 3), 1f);
            note.setBackground(Ui.round(a, Ui.CREAM, 12f));
            int np = Ui.dpi(a, 11);
            note.setPadding(np, np, np, np);
            Ui.font(note, 500);
            LinearLayout.LayoutParams nlp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp2.topMargin = Ui.dpi(a, 12);
            root.addView(note, nlp2);
        }

        // 按钮
        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 48));
        btnLp.topMargin = Ui.dpi(a, 20);
        root.addView(buttons, btnLp);

        if (!force) {
            TextView later = new TextView(a);
            later.setText("稍后");
            later.setTextColor(Ui.INK_MUTED);
            later.setTextSize(15);
            later.setGravity(Gravity.CENTER);
            later.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x14000000));
            Ui.font(later, 600);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            clp.rightMargin = Ui.dpi(a, 10);
            buttons.addView(later, clp);
            later.setOnClickListener(v -> {
                // 记下跳过的版本，免得每次启动都拿同一个弹窗烦人；手动检查仍然能查
                sp(a).edit().putInt(K_SKIPPED, info.versionCode).apply();
                dialog.dismiss();
            });
        }

        TextView go = new TextView(a);
        go.setText("立即更新");
        go.setTextColor(Color.WHITE);
        go.setTextSize(15);
        go.setGravity(Gravity.CENTER);
        go.setBackground(Ui.ripple(a, Ui.gradient(a, Ui.ACCENT, 24f), 0x33FFFFFF));
        Ui.font(go, 700);
        go.setOnClickListener(v -> {
            dialog.dismiss();
            startDownload(a, info);
        });
        buttons.addView(go, force
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        dialog.setContentView(root);
        applyWindow(a, dialog);
        // 弹窗已经量过一遍高度了，这里再给日志区封顶
        box.post(() -> {
            int maxH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.32f);
            if (box.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = box.getLayoutParams();
                lp.height = maxH;
                box.setLayoutParams(lp);
            }
        });
    }

    // ---- 下载 ----

    private static void startDownload(final Activity a, final UpdateChecker.Info info) {
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 下载中不许按返回键退出，只能按「取消」——否则用户以为没在下载，其实线程还在跑
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(a, Ui.SURFACE, 26f));
        int p = Ui.dpi(a, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(a);
        title.setText("正在下载 " + info.versionName);
        title.setTextColor(Ui.INK);
        title.setTextSize(18);
        Ui.font(title, 800);
        root.addView(title);

        TextView info1 = new TextView(a);
        info1.setText("下载完成后会直接打开系统安装器");
        info1.setTextColor(Ui.INK_SOFT);
        info1.setTextSize(12.5f);
        info1.setPadding(0, Ui.dpi(a, 5), 0, 0);
        Ui.font(info1, 400);
        root.addView(info1);

        ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        // 系统默认轨道在白卡上几乎看不见，给一档实色
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE4E7EC));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 8));
        blp.topMargin = Ui.dpi(a, 18);
        root.addView(bar, blp);

        final TextView percent = new TextView(a);
        percent.setText("0%");
        percent.setTextColor(Ui.INK);
        percent.setTextSize(13);
        percent.setPadding(0, Ui.dpi(a, 10), 0, 0);
        Ui.font(percent, 700);
        root.addView(percent);

        final TextView bytes = new TextView(a);
        bytes.setText(sizeText(0, info.fileSize));
        bytes.setTextColor(Ui.INK_SOFT);
        bytes.setTextSize(12);
        bytes.setPadding(0, Ui.dpi(a, 3), 0, 0);
        Ui.font(bytes, 400);
        root.addView(bytes);

        TextView cancelBtn = new TextView(a);
        cancelBtn.setText("取消");
        cancelBtn.setTextColor(Ui.INK_MUTED);
        cancelBtn.setTextSize(15);
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setBackground(Ui.ripple(a, Ui.round(a, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancelBtn, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(a, 44));
        clp.topMargin = Ui.dpi(a, 18);
        root.addView(cancelBtn, clp);

        final boolean[] cancelled = {false};
        cancelBtn.setOnClickListener(v -> {
            cancelled[0] = true;
            dialog.dismiss();
            if (info.forced()) {
                // 强制更新没法真的「取消」，只是把话说清楚
                Toast.makeText(a, "这一版需要装完才能继续使用", Toast.LENGTH_LONG).show();
                showUpdateDialog(a, info);
            }
        });

        dialog.setContentView(root);
        applyWindow(a, dialog);

        new Thread(() -> {
            final long start = System.currentTimeMillis();
            try {
                File apk = UpdateChecker.download(a.getApplicationContext(), info,
                        (done, total) -> a.runOnUiThread(() -> {
                            if (a.isFinishing() || a.isDestroyed() || !dialog.isShowing()) {
                                return;
                            }
                            int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                            bar.setProgress(total > 0 ? (int) (done * 1000 / total) : 0);
                            percent.setText(pct + "%");
                            bytes.setText(sizeText(done, total));
                        }),
                        () -> cancelled[0]);
                a.runOnUiThread(() -> {
                    if (a.isFinishing() || a.isDestroyed()) {
                        // 用户已经把界面关掉了。包留在缓存里，下次启动重新检查时会按需清理，
                        // 这里绝不能对着一个已经销毁的 Activity 弹窗（会 BadTokenException）。
                        return;
                    }
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    install(a, apk);
                });
            } catch (UpdateChecker.Failure f) {
                if (f.httpCode == UpdateChecker.CANCELED) {
                    return;
                }
                // 10 分钟签名过期是很常见的一种失败，这时候重试没用，必须重新检查
                String msg = f.httpCode == 410
                        ? "下载链接过期了，要重新检查一次才能拿到新链接。"
                        : f.getMessage() + "（耗时 "
                        + Math.max(1, (System.currentTimeMillis() - start) / 1000) + " 秒）";
                if (info.forced()) {
                    msg += "\n\n这一版必须装完才能继续使用，下次打开应用会再提示。";
                }
                final String body = msg;
                a.runOnUiThread(() -> {
                    if (a.isFinishing() || a.isDestroyed()) {
                        return;
                    }
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                    InfoDialog.confirm(a, "下载失败", body, "重试", Ui.ACCENT,
                            f.httpCode == 410
                                    ? () -> checkManual(a, null)
                                    : () -> startDownload(a, info));
                });
            }
        }).start();
    }

    // ---- 安装 ----

    static void install(Activity a, File apk) {
        if (apk == null || !apk.isFile()) {
            InfoDialog.show(a, "无法安装", "安装包不见了，请重新检查更新再下载一次。");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !a.getPackageManager().canRequestPackageInstalls()) {
            // 先记住这件事，用户从系统页面回来时 resumePendingInstall 会接着装
            sp(a).edit().putBoolean(K_AWAIT_PERM, true).apply();
            Toast.makeText(a, "在打开的页面里允许「安装未知应用」，返回后会自动继续",
                    Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                InfoDialog.show(a, "无法安装",
                        "这台设备没开放应用安装权限，需要手动处理。\n\n安装包位置：\n"
                                + apk.getAbsolutePath());
            }
            return;
        }
        sp(a).edit().putBoolean(K_AWAIT_PERM, false).apply();
        launchInstaller(a, apk);
    }

    private static void launchInstaller(Activity a, File apk) {
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setDataAndType(ApkProvider.uriFor(a), "application/vnd.android.package-archive");
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(it);
        } catch (ActivityNotFoundException e) {
            InfoDialog.show(a, "无法安装",
                    "系统里没找到能装 APK 的组件。安装包位置：\n" + apk.getAbsolutePath());
        }
    }

    // ---- 小工具 ----

    private static String versionLine(Activity a, UpdateChecker.Info info) {
        String from = UpdateChecker.versionName(a);
        StringBuilder sb = new StringBuilder();
        if (!from.isEmpty()) {
            sb.append(from).append(" → ");
        }
        sb.append(info.versionName);
        if (info.fileSize > 0) {
            sb.append(" · ").append(mb(info.fileSize));
        }
        return sb.toString();
    }

    private static String sizeText(long done, long total) {
        if (total <= 0) {
            return mb(done);
        }
        return mb(done) + " / " + mb(total);
    }

    private static String mb(long bytes) {
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1048576f);
    }

    private static String ago(long millis) {
        if (millis < 60_000L) {
            return "刚刚检查过";
        }
        if (millis < 3_600_000L) {
            return (millis / 60_000L) + " 分钟前检查过";
        }
        if (millis < 86_400_000L) {
            return (millis / 3_600_000L) + " 小时前检查过";
        }
        return (millis / 86_400_000L) + " 天前检查过";
    }

    private static void stamp(Context c) {
        sp(c).edit().putLong(K_LAST_CHECK, System.currentTimeMillis()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    private static void applyWindow(Activity a, Dialog dialog) {
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
            w.setLayout(a.getResources().getDisplayMetrics().widthPixels - Ui.dpi(a, 48),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
