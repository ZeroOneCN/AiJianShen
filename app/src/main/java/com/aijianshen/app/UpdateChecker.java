package com.aijianshen.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * OTA 更新检查：只管网络与文件，界面在 {@link UpdateFlow}。
 *
 * <p>出网的只有包名和 versionCode，不带设备号、账号或任何训练数据，服务端也拿不到别的。
 * 下载地址是服务端签发的临时链接（10 分钟有效），过期只能重新检查，不能缓存复用。
 */
public final class UpdateChecker {

    /** 站点根地址，接口文档：https://apps.01iot.top/docs */
    private static final String CHECK_URL = "https://apps.01iot.top/api/update/check";
    private static final int CONNECT_TIMEOUT = 8000;
    private static final int READ_TIMEOUT = 20000;
    /** 取消下载时用的错误码，和 HTTP 状态码错开。 */
    public static final int CANCELED = -1;

    private UpdateChecker() {
    }

    /** 一次检查的结果。hasUpdate=false 时其余字段基本为空，属正常。 */
    public static final class Info {
        public boolean hasUpdate;
        public boolean forceUpdate;
        public String reason;
        public String versionName = "";
        public int versionCode;
        public String changelog = "";
        public long fileSize;
        public String sha256 = "";
        public int minSdk;
        public int minSupportedVersionCode;
        public String downloadUrl = "";
        public String publishedAt = "";
        /** 发起检查时本机的 versionCode，弹窗上要写「1.9.5 → 1.9.6」。 */
        int currentVersionCode;

        /** 低于 minSupportedVersionCode 的一律按强制更新处理，与文档语义一致。 */
        public boolean forced() {
            return forceUpdate
                    || (minSupportedVersionCode > 0
                    && currentVersionCode < minSupportedVersionCode);
        }

        /** 更新日志按行拆开，去掉空行，给弹窗逐条排。 */
        public String[] lines() {
            if (changelog == null || changelog.trim().isEmpty()) {
                return new String[0];
            }
            String[] raw = changelog.replace("\r\n", "\n").split("\n");
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String s : raw) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
            return out.toArray(new String[0]);
        }
    }

    /** 检查或下载失败。httpCode 为 0 表示没走到服务端（断网、超时）。 */
    public static final class Failure extends Exception {
        public final int httpCode;

        Failure(String message, int httpCode) {
            super(message);
            this.httpCode = httpCode;
        }
    }

    public interface Progress {
        void onProgress(long done, long total);
    }

    public interface Cancelled {
        boolean isCancelled();
    }

    // ---- 检查版本 ----

    public static Info check(Context c) throws Failure {
        final int current = versionCode(c);
        HttpURLConnection conn = null;
        try {
            URL url = new URL(CHECK_URL + "?packageName=" + Uri.encode(c.getPackageName())
                    + "&versionCode=" + current);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", userAgent(c));

            int status = conn.getResponseCode();
            if (status == 404) {
                // 包名对不上，或者应用还没公开/没有正式版本。对客户端来说就是「没有更新」，
                // 不该弹一个查不到东西的错误框。
                Info none = new Info();
                none.currentVersionCode = current;
                return none;
            }
            if (status == 429) {
                throw new Failure("请求太频繁了，过几分钟再试", status);
            }
            if (status != 200) {
                throw new Failure("更新服务器返回 " + status, status);
            }
            return parse(read(conn.getInputStream()), current);
        } catch (Failure f) {
            throw f;
        } catch (Exception e) {
            throw new Failure("连不上更新服务器，检查一下网络", 0);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static Info parse(String text, int current) throws Failure {
        Info in = new Info();
        in.currentVersionCode = current;
        try {
            JSONObject o = new JSONObject(text);
            in.hasUpdate = o.optBoolean("hasUpdate", false);
            in.forceUpdate = o.optBoolean("forceUpdate", false);
            if (!o.isNull("reason")) {
                in.reason = o.optString("reason", null);
            }
            JSONObject l = o.optJSONObject("latest");
            if (l == null) {
                in.hasUpdate = false;
                return in;
            }
            if (!l.isNull("versionName")) {
                in.versionName = l.optString("versionName", "");
            }
            in.versionCode = l.optInt("versionCode", 0);
            if (!l.isNull("changelog")) {
                in.changelog = l.optString("changelog", "");
            }
            in.fileSize = l.optLong("fileSize", 0);
            if (!l.isNull("sha256")) {
                in.sha256 = l.optString("sha256", "");
            }
            in.minSdk = l.optInt("minSdk", 0);
            in.minSupportedVersionCode = l.optInt("minSupportedVersionCode", 0);
            if (!l.isNull("downloadUrl")) {
                in.downloadUrl = l.optString("downloadUrl", "");
            }
            if (!l.isNull("publishedAt")) {
                in.publishedAt = l.optString("publishedAt", "");
            }
            // 服务端说「有更新」但版本号并不更高、或者干脆没给下载地址时，
            // 按没有更新处理——否则用户会看到一个永远点不动的弹窗。
            if (in.hasUpdate && (in.versionCode <= current || in.downloadUrl.isEmpty())) {
                in.hasUpdate = false;
            }
            return in;
        } catch (Exception e) {
            throw new Failure("更新接口返回的内容看不懂", 0);
        }
    }

    // ---- 下载 ----

    /**
     * 下载到缓存目录并校验 SHA-256。校验不通过会把临时文件删掉再抛错，
     * 绝不让一个可能被篡改的包走到安装器面前。
     */
    public static File download(Context c, Info info, Progress cb, Cancelled cancel)
            throws Failure {
        File dir = updateDir(c);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Failure("没有可用的存储空间", 0);
        }
        File part = new File(dir, "update.part");
        File target = new File(dir, "update.apk");
        // 先清掉上一轮的残留，避免失败后又被当成「新包」拿去装
        if (target.exists()) {
            target.delete();
        }

        MessageDigest md = digest();
        HttpURLConnection conn = null;
        long done = 0;
        try {
            URL url = new URL(info.downloadUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", userAgent(c));
            int status = conn.getResponseCode();
            if (status == 410) {
                throw new Failure("下载链接过期了，重新检查一次", status);
            }
            if (status != 200) {
                throw new Failure("下载失败（服务器返回 " + status + "）", status);
            }

            long total = conn.getContentLengthLong();
            if (total <= 0) {
                total = info.fileSize;
            }

            try (InputStream in = conn.getInputStream();
                 OutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16384];
                int n;
                long lastTick = 0;
                while ((n = in.read(buf)) > 0) {
                    if (cancel != null && cancel.isCancelled()) {
                        throw new Failure("已取消", CANCELED);
                    }
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    // 16KB 回调一次太密，主线程每秒被叫几百次；攒到 128KB 再报，进度条也看不出来差别
                    if (cb != null && done - lastTick >= 131072) {
                        lastTick = done;
                        cb.onProgress(done, total);
                    }
                }
                out.flush();
            }
            if (cb != null) {
                cb.onProgress(done, total);
            }

            String got = hex(md.digest());
            if (info.sha256 != null && !info.sha256.trim().isEmpty()
                    && !got.equalsIgnoreCase(info.sha256.trim())) {
                throw new Failure("安装包校验没通过，可能没下完整，请重试", 0);
            }
            if (total > 0 && done != total) {
                throw new Failure("下载不完整，请重试", 0);
            }
            if (!part.renameTo(target)) {
                throw new Failure("下载完成但存不下文件", 0);
            }
            return target;
        } catch (Failure f) {
            part.delete();
            throw f;
        } catch (Exception e) {
            part.delete();
            throw new Failure("下载中断了，请重试", 0);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ---- 缓存管理 ----

    static File updateDir(Context c) {
        return new File(c.getCacheDir(), "ota");
    }

    static File cachedApk(Context c) {
        return new File(updateDir(c), "update.apk");
    }

    /** 清掉缓存里的安装包；升级完成后旧包没用了，留着只是占地方。 */
    static void clearCache(Context c) {
        File dir = updateDir(c);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    /**
     * 缓存里的包是否真的比当前装着的版本新。
     *
     * <p>这是防重复安装的最后一道闸：装完之后缓存文件还在，
     * 没有这个判断，下次回前台会把同一个包再往安装器里塞一遍。
     */
    static boolean isNewer(Context c, File apk) {
        if (apk == null || !apk.isFile()) {
            return false;
        }
        try {
            PackageInfo pi = c.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return pi != null && pi.versionCode > versionCode(c);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 小工具 ----

    static int versionCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String versionName(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static String userAgent(Context c) {
        String v = versionName(c);
        return "AiJianShen/" + (v.isEmpty() ? "unknown" : v) + " Android";
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IllegalStateException("缺少 SHA-256 支持", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
