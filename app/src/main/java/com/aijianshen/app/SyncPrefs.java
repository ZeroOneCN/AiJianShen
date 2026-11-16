package com.aijianshen.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 手机间同步的配置与最近一次结果。数据只走局域网，不涉及账号或云端。 */
public final class SyncPrefs {

    private static final String SP = "lan_sync";
    private static final String K_SERVER = "server_on";
    private static final String K_PEER = "peer";
    private static final String K_LAST_TS = "last_ts";
    private static final String K_LAST_MSG = "last_msg";
    private static final String K_LAST_COUNT = "last_count";

    private SyncPrefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE);
    }

    /** 是否开着「本机同步服务」，让别的手机来读。 */
    public static boolean server(Context c) {
        return sp(c).getBoolean(K_SERVER, false);
    }

    public static void setServer(Context c, boolean on) {
        sp(c).edit().putBoolean(K_SERVER, on).apply();
    }

    /** 上一次填过的对方地址。 */
    public static String peer(Context c) {
        return sp(c).getString(K_PEER, "");
    }

    public static void setPeer(Context c, String peer) {
        sp(c).edit().putString(K_PEER, peer == null ? "" : peer.trim()).apply();
    }

    public static long lastTs(Context c) {
        return sp(c).getLong(K_LAST_TS, 0);
    }

    public static int lastCount(Context c) {
        return sp(c).getInt(K_LAST_COUNT, 0);
    }

    public static String lastMessage(Context c) {
        return sp(c).getString(K_LAST_MSG, "");
    }

    public static void setLast(Context c, boolean ok, int count, String message) {
        sp(c).edit()
                .putLong(K_LAST_TS, System.currentTimeMillis())
                .putInt(K_LAST_COUNT, ok ? count : 0)
                .putString(K_LAST_MSG, message == null ? "" : message)
                .apply();
    }

    public static String lastTimeText(Context c) {
        long ts = lastTs(c);
        if (ts <= 0) {
            return "还没有同步过";
        }
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(ts));
    }
}
