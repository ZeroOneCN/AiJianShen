package com.yuanlian.app;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** 训练提示音与振动，全部由系统本地生成。 */
public final class WorkoutCues {

    public static final int TICK = 0;
    public static final int START = 1;
    public static final int REST = 2;
    public static final int DONE = 3;

    private ToneGenerator tone;

    public void play(int kind) {
        try {
            if (tone == null) {
                tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
            }
            switch (kind) {
                case TICK:
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60);
                    break;
                case START:
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 160);
                    break;
                case REST:
                    tone.startTone(ToneGenerator.TONE_PROP_PROMPT, 120);
                    break;
                default:
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 260);
                    break;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void vibrate(Context c, int ms) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    public void release() {
        if (tone != null) {
            try {
                tone.release();
            } catch (Throwable ignored) {
            }
            tone = null;
        }
    }
}
