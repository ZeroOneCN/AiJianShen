package com.aijianshen.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 系统 TTS 语音口令；没有语音包时保持静默，不影响训练。
 *
 * 有的机器默认引擎（常见是 Google 的）只装了英文数据，
 * 而系统里另有一个支持中文的引擎（如网内厂商自带的那套）。
 * 所以默认引擎设不上中文时，会按系统给出的引擎列表依次再试一遍，
 * 全都失败才降级成静默，由画面把要领补回来。
 */
public final class Voice implements TextToSpeech.OnInitListener {

    private static final String TAG = "aijianshen-voice";

    public interface Ready {
        void onReady(boolean ok, String status);
    }

    private final Context ctx;
    private final Ready callback;
    private TextToSpeech tts;
    private boolean ready;
    private String status = "正在准备语音";
    /** 默认引擎不支持中文时，待试的引擎名；null 表示还没开始找。 */
    private List<String> fallback;
    private int fallbackAt;

    public Voice(Context ctx, Ready callback) {
        this.ctx = ctx.getApplicationContext();
        this.callback = callback;
        tts = new TextToSpeech(this.ctx, this);
    }

    @Override
    public void onInit(int code) {
        if (code == TextToSpeech.SUCCESS && tts != null && tryChinese()) {
            ready = true;
            status = "系统中文语音已就绪";
            String engine;
            try {
                engine = label(defaultEngine());
                tts.setSpeechRate(1.02f);
            } catch (Throwable t) {
                engine = "-";
            }
            android.util.Log.i(TAG, "ready engine=" + engine);
            report();
            return;
        }
        // 这个引擎不行，换下一个可能是中文的
        if (tryNextEngine()) {
            return;
        }
        ready = false;
        status = code == TextToSpeech.SUCCESS ? "缺少中文语音包" : "系统语音引擎不可用";
        android.util.Log.i(TAG, "unavailable code=" + code + " tried=" + (fallback == null ? 0 : fallback.size()));
        report();
    }

    /** 给当前引擎设中文，返回是否真的能用。 */
    private boolean tryChinese() {
        try {
            int res = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (missing(res)) {
                res = tts.setLanguage(Locale.CHINA);
            }
            android.util.Log.i(TAG, "engine=" + label(defaultEngine())
                    + " langRes=" + res + " locales=" + locales());
            return !missing(res);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean missing(int res) {
        return res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED;
    }

    /** 换一个候选引擎；候选试完返回 false。 */
    private boolean tryNextEngine() {
        if (fallback == null) {
            fallback = engineCandidates();
            String cur = defaultEngine();
            if (cur != null) {
                fallback.remove(cur);
            }
            fallbackAt = 0;
        }
        while (fallbackAt < fallback.size()) {
            String name = fallback.get(fallbackAt++);
            try {
                release();
                tts = new TextToSpeech(ctx, this, name);
                android.util.Log.i(TAG, "switch to " + name);
                return true;
            } catch (Throwable ignored) {
                // 这个引擎起不来，继续下一个
            }
        }
        return false;
    }

    /**
     * 系统里所有 TTS 引擎。
     * 先问 TTS 服务要，但默认引擎初始化失败时 {@code getEngines()} 是空的，
     * 这时退回到包管理器查谁声明了 TTS 服务——国行机器上 Google 那套常常绑不上，
     * 而厂商自带的引擎是好的，不查这一层就永远试不到它。
     */
    private List<String> engineCandidates() {
        List<String> names = new ArrayList<>();
        try {
            List<TextToSpeech.EngineInfo> all = tts == null ? null : tts.getEngines();
            if (all != null) {
                for (TextToSpeech.EngineInfo e : all) {
                    if (e != null && e.name != null && !names.contains(e.name)) {
                        names.add(e.name);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        if (!names.isEmpty()) {
            return names;
        }
        try {
            List<ResolveInfo> list = ctx.getPackageManager().queryIntentServices(
                    new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
            if (list != null) {
                for (ResolveInfo ri : list) {
                    String pkg = ri.serviceInfo == null ? null : ri.serviceInfo.packageName;
                    if (pkg != null && !names.contains(pkg)) {
                        names.add(pkg);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        android.util.Log.i(TAG, "candidates=" + names);
        return names;
    }

    private String defaultEngine() {
        try {
            return tts == null ? null : tts.getDefaultEngine();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 引擎名太长，日志里只要短标识。 */
    private static String label(String engine) {
        if (engine == null) {
            return "-";
        }
        return engine.length() > 40 ? engine.substring(engine.length() - 40) : engine;
    }

    private String locales() {
        try {
            return tts.getAvailableLanguages() == null ? "0" : String.valueOf(tts.getAvailableLanguages().size());
        } catch (Throwable t) {
            return "?";
        }
    }

    private void release() {
        if (tts == null) {
            return;
        }
        try {
            tts.stop();
            tts.shutdown();
        } catch (Throwable ignored) {
        }
        tts = null;
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
        say(text, TextToSpeech.QUEUE_FLUSH);
    }

    /**
     * 排队播报，接在当前这句话后面念完，不打断。
     * 要领这种「动作名之后接着说两条」的场景必须用它：
     * FLUSH 会把刚念到一半的动作名掐掉，听感是卡顿和重复。
     */
    public void speakNext(String text) {
        say(text, TextToSpeech.QUEUE_ADD);
    }

    private void say(String text, int mode) {
        if (!ready || tts == null || text == null || text.isEmpty()) {
            return;
        }
        try {
            tts.speak(text, mode, null, "aijianshen");
        } catch (Throwable ignored) {
        }
    }

    /** 换动作、暂停时把还没念完的旧口令清掉，避免「上一个动作」的提示拖到下一步。 */
    public void stop() {
        if (tts == null) {
            return;
        }
        try {
            tts.stop();
        } catch (Throwable ignored) {
        }
    }

    public void shutdown() {
        ready = false;
        release();
    }
}
