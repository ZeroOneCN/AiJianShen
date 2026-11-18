package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/** 训练设置：预备/休息时长、声音、振动、语音、动态演示与本地音乐。 */
public class WorkoutSettingsActivity extends Activity {

    private static final int REQ_MUSIC = 701;

    private Store.WorkoutPrefs prefs;
    private UiList.Row prepRow;
    private UiList.Row restRow;
    private UiList.Row musicRow;
    private TextView clearMusic;
    private Voice testVoice;
    private boolean initializing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        prefs = Store.WorkoutPrefs.load(this);
        LinearLayout root = UiList.page(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "训练设置", v -> finish()));
        root.addView(UiList.subtitle(this,
                "这些设置只影响本机的跟练体验，不会上传。"));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, 0, p, Ui.dpi(this, 18));
        sc.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        UiList.label(this, body, "训练节奏");
        LinearLayout rhythm = UiList.card(this);
        body.addView(rhythm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        prepRow = UiList.row(this, rhythm, IconView.CLOCK, Ui.ACCENT,
                "预备倒计时", prepText(), false, v -> choosePrep());
        restRow = UiList.row(this, rhythm, IconView.CLOCK, Ui.ACCENT,
                "休息时长", restText(), true, v -> chooseRest());

        UiList.label(this, body, "声音与画面");
        LinearLayout media = UiList.card(this);
        body.addView(media, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        UiList.row(this, media, IconView.BOLT, Ui.ACCENT, "提示音",
                "开始、倒计时和切换动作时播放", false, null,
                (b, on) -> {
                    if (initializing) return;
                    prefs.sound = on;
                    prefs.save(this);
                }).toggle.setChecked(prefs.sound);
        UiList.row(this, media, IconView.REFRESH, Ui.ACCENT, "振动",
                "休息与完成时轻振一下", false, null,
                (b, on) -> {
                    if (initializing) return;
                    prefs.vibrate = on;
                    prefs.save(this);
                    if (on) {
                        WorkoutCues.vibrate(this, 40);
                    }
                }).toggle.setChecked(prefs.vibrate);
        UiList.row(this, media, IconView.SPEAKER, Ui.ACCENT, "语音口令",
                "用系统 TTS 播报预备、休息和完成", false, null,
                (b, on) -> {
                    if (initializing) return;
                    prefs.voice = on;
                    prefs.save(this);
                }).toggle.setChecked(prefs.voice);
        UiList.row(this, media, IconView.VIDEO, Ui.ACCENT, "动态演示",
                "训练页播放动作动图；关闭后显示静态图", true, null,
                (b, on) -> {
                    if (initializing) return;
                    prefs.animate = on;
                    prefs.save(this);
                }).toggle.setChecked(prefs.animate);

        TextView test = new TextView(this);
        test.setText("试听语音口令");
        test.setTextColor(Ui.ACCENT);
        test.setTextSize(13);
        test.setGravity(Gravity.CENTER);
        test.setPadding(0, Ui.dpi(this, 12), 0, Ui.dpi(this, 4));
        Ui.font(test, 600);
        test.setOnClickListener(v -> testVoice());
        body.addView(test);

        UiList.label(this, body, "训练音乐");
        LinearLayout music = UiList.card(this);
        body.addView(music, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        musicRow = UiList.row(this, music, IconView.MUSIC, Ui.ACCENT, "选择音乐",
                musicText(), false, v -> pickMusic());
        UiList.row(this, music, IconView.PLAY, Ui.ACCENT, "自动播放",
                "进入训练页后自动开始播放", true, null,
                (b, on) -> {
                    if (initializing) return;
                    prefs.musicAuto = on;
                    prefs.save(this);
                }).toggle.setChecked(prefs.musicAuto);

        clearMusic = new TextView(this);
        clearMusic.setText("清除已选音乐");
        clearMusic.setTextColor(Ui.INK_SOFT);
        clearMusic.setTextSize(12);
        clearMusic.setGravity(Gravity.CENTER);
        clearMusic.setPadding(0, Ui.dpi(this, 10), 0, 0);
        Ui.font(clearMusic, 500);
        clearMusic.setOnClickListener(v -> {
            deleteMusicFile();
            prefs.musicUri = "";
            prefs.musicName = "";
            prefs.save(this);
            musicRow.sub.setText(musicText());
            clearMusic.setVisibility(android.view.View.GONE);
            Toast.makeText(this, "已清除训练音乐", Toast.LENGTH_SHORT).show();
        });
        body.addView(clearMusic);
        clearMusic.setVisibility(prefs.musicUri.isEmpty()
                ? android.view.View.GONE : android.view.View.VISIBLE);

        UiList.hint(this, body,
                "选中的音乐会复制一份到应用内，删除原文件也不影响播放；不会上传。"
                        + "语音口令依赖系统 TTS 中文语音包。");

        TextView done = UiList.primary(this, "完成");
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 52));
        dlp.topMargin = Ui.dpi(this, 8);
        dlp.leftMargin = p;
        dlp.rightMargin = p;
        root.addView(done, dlp);
        done.setOnClickListener(v -> finish());
        initializing = false;
    }

    private String prepText() {
        return prefs.prepSeconds <= 0 ? "关闭，直接开始" : prefs.prepSeconds + " 秒";
    }

    private String restText() {
        if (prefs.restSeconds > 0) {
            return prefs.restSeconds + " 秒";
        }
        int plan = Store.profile(this).restSeconds();
        return "跟随计划（当前 " + plan + " 秒）";
    }

    private String musicText() {
        return prefs.musicUri.isEmpty() ? "未选择 · 点按从本机选一首"
                : prefs.musicName + " · 点按更换";
    }

    private void choosePrep() {
        String[] labels = {"关闭", "3 秒", "5 秒", "10 秒"};
        int[] values = {0, 3, 5, 10};
        int sel = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == prefs.prepSeconds) {
                sel = i;
            }
        }
        ChoiceDialog.show(this, "预备倒计时", labels, sel, i -> {
            prefs.prepSeconds = values[i];
            prefs.save(this);
            prepRow.sub.setText(prepText());
        });
    }

    private void chooseRest() {
        String[] labels = {"跟随计划", "10 秒", "20 秒", "30 秒", "45 秒", "60 秒"};
        int[] values = {0, 10, 20, 30, 45, 60};
        int sel = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == prefs.restSeconds) {
                sel = i;
            }
        }
        ChoiceDialog.show(this, "休息时长", labels, sel, i -> {
            prefs.restSeconds = values[i];
            prefs.save(this);
            restRow.sub.setText(restText());
        });
    }

    private void pickMusic() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("audio/*");
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(it, REQ_MUSIC);
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_MUSIC || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String name = uri.getLastPathSegment();
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    name = c.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            name = "已选音乐";
        }
        File target = new File(getFilesDir(), "workout_music" + audioExt(name));
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IllegalStateException("empty stream");
            }
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            target.delete();
            Toast.makeText(this, "读取这首音乐失败，换一首试试", Toast.LENGTH_SHORT).show();
            return;
        }
        File old = musicFile();
        if (old != null && !old.getAbsolutePath().equals(target.getAbsolutePath())) {
            old.delete();
        }
        prefs.musicUri = target.getAbsolutePath();
        prefs.musicName = name;
        prefs.save(this);
        musicRow.sub.setText(musicText());
        clearMusic.setVisibility(android.view.View.VISIBLE);
        Toast.makeText(this, "已选择：" + prefs.musicName, Toast.LENGTH_SHORT).show();
    }

    private String audioExt(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.matches("mp3|m4a|aac|wav|ogg|opus|flac")) {
                return "." + ext;
            }
        }
        return ".mp3";
    }

    private File musicFile() {
        String src = prefs.musicUri;
        if (src == null || src.isEmpty()) {
            return null;
        }
        if (src.startsWith("file://")) {
            return new File(Uri.parse(src).getPath());
        }
        if (src.startsWith("content://")) {
            return null;
        }
        return new File(src);
    }

    private void deleteMusicFile() {
        File f = musicFile();
        if (f != null && f.getParentFile() != null
                && f.getParentFile().equals(getFilesDir())) {
            f.delete();
        }
    }

    private void testVoice() {
        if (testVoice != null) {
            testVoice.speak("预备，开始。保持呼吸，动作慢一点。");
            return;
        }
        testVoice = new Voice(this, (ok, status) -> {
            if (!ok) {
                Toast.makeText(this, status, Toast.LENGTH_LONG).show();
                return;
            }
            testVoice.speak("预备，开始。保持呼吸，动作慢一点。");
        });
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (testVoice != null) {
                testVoice.speak("预备，开始。保持呼吸，动作慢一点。");
            }
        }, 1200);
    }

    @Override
    protected void onDestroy() {
        if (testVoice != null) {
            testVoice.shutdown();
            testVoice = null;
        }
        super.onDestroy();
    }
}
