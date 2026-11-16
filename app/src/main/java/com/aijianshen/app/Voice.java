package com.aijianshen.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** 系统 TTS 语音口令；没有语音包时保持静默，不影响训练。 */
public final class Voice implements TextToSpeech.OnInitListener {

    public interface Ready {
        void onReady(boolean ok, String status);
    }

    private TextToSpeech tts;
    private boolean ready;
    private String status = "正在准备语音";
    private final Ready callback;

    public Voice(Context ctx, Ready callback) {
        this.callback = callback;
        tts = new TextToSpeech(ctx.getApplicationContext(), this);
    }

    @Override
    public void onInit(int code) {
        if (code != TextToSpeech.SUCCESS || tts == null) {
            status = "系统语音引擎不可用";
            report();
            return;
        }
        int res = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        if (res == TextToSpeech.LANG_MISSING_DATA
                || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            res = tts.setLanguage(Locale.CHINA);
        }
        ready = res != TextToSpeech.LANG_MISSING_DATA
                && res != TextToSpeech.LANG_NOT_SUPPORTED;
        status = ready ? "系统中文语音已就绪" : "缺少中文语音包";
        if (ready) {
            tts.setSpeechRate(1.02f);
        }
        report();
    }

    private void report() {
        if (callback != null) {
            callback.onReady(ready, status);
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String status() {
        return status;
    }

    public void speak(String text) {
        if (!ready || tts == null || text == null || text.isEmpty()) {
            return;
        }
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aijianshen");
        } catch (Throwable ignored) {
        }
    }

    public void shutdown() {
        ready = false;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
    }
}
