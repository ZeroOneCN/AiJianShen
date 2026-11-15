package com.yuanlian.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 系统 TTS 语音口令设置。 */
public class VoiceSettingsActivity extends Activity {

    private Store.WorkoutPrefs prefs;
    private Voice voice;
    private TextView status;
    private TextView test;
    private boolean initializing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);
        prefs = Store.WorkoutPrefs.load(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);

        root.addView(UiList.topBar(this, "语音选择", v -> finish()));
        root.addView(UiList.subtitle(this,
                "用系统自带的文字转语音引擎，在预备、休息和完成时给出提示。"));

        LinearLayout card = UiList.card(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.dpi(this, 20);
        clp.rightMargin = Ui.dpi(this, 20);
        root.addView(card, clp);

        UiList.row(this, card, IconView.MUSIC, Ui.PURPLE, "语音口令",
                "关闭后只保留提示音和振动", true, null,
                (b, on) -> {
                    if (initializing) return;
                    prefs.voice = on;
                    prefs.save(this);
                    if (on) {
                        speakTest();
                    }
                }).toggle.setChecked(prefs.voice);
        initializing = false;

        TextView engine = new TextView(this);
        engine.setText("语音引擎");
        engine.setTextColor(Ui.INK);
        engine.setTextSize(16);
        engine.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 18),
                Ui.dpi(this, 18), 0);
        Ui.font(engine, 700);
        root.addView(engine);

        status = new TextView(this);
        status.setText("正在检测系统语音引擎…");
        status.setTextColor(Ui.INK_SOFT);
        status.setTextSize(13);
        status.setLineSpacing(Ui.dp(this, 3), 1f);
        status.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 6),
                Ui.dpi(this, 18), 0);
        Ui.font(status, 400);
        root.addView(status);

        test = new TextView(this);
        test.setText("试听一句");
        test.setTextColor(Ui.ACCENT);
        test.setTextSize(14);
        test.setGravity(Gravity.CENTER);
        test.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT_SOFT, 22f), 0x22000000));
        Ui.font(test, 600);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 46));
        tlp.leftMargin = Ui.dpi(this, 20);
        tlp.rightMargin = Ui.dpi(this, 20);
        tlp.topMargin = Ui.dpi(this, 18);
        root.addView(test, tlp);
        test.setOnClickListener(v -> speakTest());

        TextView note = new TextView(this);
        note.setText("如果提示缺少中文语音包，请到系统设置 → 文字转语音中安装中文语音数据，"
                + "或继续使用提示音与振动。语音只在训练页播放。");
        note.setTextColor(Ui.INK_SOFT);
        note.setTextSize(12);
        note.setLineSpacing(Ui.dp(this, 3), 1f);
        note.setPadding(Ui.dpi(this, 22), Ui.dpi(this, 16),
                Ui.dpi(this, 22), 0);
        Ui.font(note, 400);
        root.addView(note);

        voice = new Voice(this, (ok, text) -> status.setText(text));
    }

    private void speakTest() {
        if (voice == null) {
            return;
        }
        if (!voice.isReady()) {
            Toast.makeText(this, voice.status(), Toast.LENGTH_SHORT).show();
            return;
        }
        voice.speak("预备，开始。保持呼吸，动作慢一点。");
    }

    @Override
    protected void onDestroy() {
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        super.onDestroy();
    }
}
