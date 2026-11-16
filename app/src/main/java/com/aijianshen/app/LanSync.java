package com.aijianshen.app;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 手机与手机之间的局域网同步。
 *
 * <p>一台手机开「同步服务」，另一台在同一 Wi-Fi 下输入它的地址，就能把训练记录、
 * 体重与身体成分搬过去。数据只走局域网、不经过任何服务器；传输格式是这个类自己
 * 定义的紧凑 JSON（{@code /snapshot} 读取，{@code /merge} 推送）。
 */
public final class LanSync {

    public static final int PORT = 8766;
    private static final int CONNECT_MS = 3000;
    private static final int READ_MS = 8000;

    private LanSync() {
    }

    // ---- 快照 ----

    /** 把本机数据打包成一份可传输的快照。 */
    public static JSONObject snapshot(Context c) {
        JSONObject o = new JSONObject();
        try {
            o.put("app", "aijianshen");
            o.put("v", 1);
            o.put("at", System.currentTimeMillis());
            o.put("device", Build.MODEL == null ? "" : Build.MODEL);

            JSONArray dates = new JSONArray();
            List<String> sorted = new ArrayList<>(Store.dates(c));
            java.util.Collections.sort(sorted);
            for (String d : sorted) {
                dates.put(d);
            }
            o.put("dates", dates);

            JSONObject day = new JSONObject();
            for (Map.Entry<String, Integer> e : Store.dayMinutes(c).entrySet()) {
                day.put(e.getKey(), e.getValue());
            }
            o.put("day", day);
            o.put("total", Store.totalMinutes(c));

            JSONArray sessions = new JSONArray();
            for (Store.Session s : Store.sessions(c)) {
                JSONObject j = new JSONObject();
                j.put("d", s.date);
                j.put("t", s.ts);
                j.put("n", s.title);
                j.put("m", s.minutes);
                j.put("c", s.count);
                j.put("f", s.feel);
                if (s.kcal > 0) {
                    j.put("k", s.kcal);
                }
                if (s.ids != null && !s.ids.isEmpty()) {
                    j.put("x", s.ids);
                }
                sessions.put(j);
            }
            o.put("sessions", sessions);

            JSONArray weights = new JSONArray();
            for (Store.WeightEntry e : Store.weights(c)) {
                JSONObject j = new JSONObject();
                j.put("d", e.date);
                j.put("w", Math.round(e.kg * 100) / 100.0);
                weights.put(j);
            }
            o.put("weights", weights);

            JSONArray body = new JSONArray();
            for (Store.BodyRecord r : Store.bodyRecords(c)) {
                JSONObject j = new JSONObject();
                j.put("ts", r.ts);
                j.put("d", r.date);
                j.put("kg", Math.round(r.kg * 100) / 100.0);
                j.put("imp", Math.round(r.impedance * 10) / 10.0);
                if (r.body != null && !r.body.isEmpty()) {
                    j.put("body", new JSONObject(r.body));
                }
                if (r.userName != null && !r.userName.isEmpty()) {
                    j.put("u", r.userName);
                }
                j.put("sm", r.synced);
                body.put(j);
            }
            o.put("body", body);

            JSONObject profile = new JSONObject();
            profile.put("h", Store.heightCm(c));
            profile.put("a", Store.age(c));
            profile.put("m", Store.isMale(c));
            o.put("profile", profile);
        } catch (Exception ignored) {
        }
        return o;
    }

    /** 合并一份快照，返回各类新增条数；不是本应用的快照时返回 null。 */
    public static Store.MergeStats merge(Context c, JSONObject snap) {
        // 改名前的快照标的是 yuanlian，过渡期两种都收，免得两台手机一新一旧对不上
        String tag = snap == null ? "" : snap.optString("app", "");
        if (!"aijianshen".equals(tag) && !"yuanlian".equals(tag)) {
            return null;
        }
        Set<String> dates = new HashSet<>();
        JSONArray dArr = snap.optJSONArray("dates");
        if (dArr != null) {
            for (int i = 0; i < dArr.length(); i++) {
                String d = dArr.optString(i, "");
                if (!d.isEmpty()) {
                    dates.add(d);
                }
            }
        }

        Map<String, Integer> day = new LinkedHashMap<>();
        JSONObject dayObj = snap.optJSONObject("day");
        if (dayObj != null) {
            java.util.Iterator<String> keys = dayObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                day.put(k, dayObj.optInt(k, 0));
            }
        }

        List<Store.Session> sessions = new ArrayList<>();
        JSONArray sArr = snap.optJSONArray("sessions");
        if (sArr != null) {
            for (int i = 0; i < sArr.length(); i++) {
                JSONObject j = sArr.optJSONObject(i);
                if (j == null) {
                    continue;
                }
                Store.Session s = new Store.Session();
                s.date = j.optString("d", "");
                s.ts = j.optLong("t", 0);
                s.title = j.optString("n", "训练");
                s.minutes = j.optInt("m", 0);
                s.count = j.optInt("c", 0);
                s.feel = j.optInt("f", -1);
                s.kcal = j.optInt("k", -1);
                s.ids = j.optString("x", "");
                if (s.ts > 0) {
                    sessions.add(s);
                }
            }
        }

        List<Store.WeightEntry> weights = new ArrayList<>();
        JSONArray wArr = snap.optJSONArray("weights");
        if (wArr != null) {
            for (int i = 0; i < wArr.length(); i++) {
                JSONObject j = wArr.optJSONObject(i);
                if (j == null) {
                    continue;
                }
                String d = j.optString("d", "");
                float w = (float) j.optDouble("w", 0);
                if (!d.isEmpty() && w > 0) {
                    weights.add(newWeight(d, w));
                }
            }
        }

        List<Store.BodyRecord> body = new ArrayList<>();
        JSONArray bArr = snap.optJSONArray("body");
        if (bArr != null) {
            for (int i = 0; i < bArr.length(); i++) {
                JSONObject j = bArr.optJSONObject(i);
                if (j == null) {
                    continue;
                }
                long ts = j.optLong("ts", 0);
                float kg = (float) j.optDouble("kg", 0);
                if (ts <= 0 || kg <= 0) {
                    continue;
                }
                JSONObject bodyJson = j.optJSONObject("body");
                body.add(newBodyRecord(ts, kg,
                        (float) j.optDouble("imp", 0),
                        bodyJson == null ? null : bodyJson.toString(),
                        j.optString("u", ""),
                        j.optBoolean("sm", false)));
            }
        }

        float h = 0;
        int a = 0;
        Boolean male = null;
        JSONObject p = snap.optJSONObject("profile");
        if (p != null) {
            h = (float) p.optDouble("h", 0);
            a = p.optInt("a", 0);
            if (p.has("m")) {
                male = p.optBoolean("m", true);
            }
        }

        return Store.mergeSnapshot(c, dates, day, sessions, weights, body, h, a, male);
    }

    /** Store 的内部构造器是包内可见，这里包一层便于在本类里组装。 */
    private static Store.WeightEntry newWeight(String date, float kg) {
        return Store.weightOf(date, kg);
    }

    private static Store.BodyRecord newBodyRecord(long ts, float kg, float imp,
                                                  String body, String user, boolean synced) {
        return Store.bodyRecordOf(ts, kg, imp, body, user, synced);
    }

    // ---- HTTP ----

    public static String get(String baseUrl) throws Exception {
        return request(baseUrl + "/snapshot", "GET", null);
    }

    public static String post(String baseUrl, String json) throws Exception {
        return request(baseUrl + "/merge", "POST", json);
    }

    private static String request(String url, String method, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_MS);
            conn.setReadTimeout(READ_MS);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("HTTP " + code);
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 本机在局域网里的 IPv4 地址，用于告诉对方填什么。 */
    public static String localIpv4() {
        String fallback = "";
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (nif.isLoopback() || !nif.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = a.getHostAddress();
                    if (ip == null) {
                        continue;
                    }
                    if (ip.startsWith("192.168.") || ip.startsWith("10.")
                            || ip.startsWith("172.")) {
                        return ip;
                    }
                    if (fallback.isEmpty()) {
                        fallback = ip;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** 规范化用户输入的地址：允许只填 IP，端口默认 {@link #PORT}。 */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.startsWith("http://")) {
            s = s.substring(7);
        } else if (s.startsWith("https://")) {
            s = s.substring(8);
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        s = s.trim();
        if (s.isEmpty()) {
            return null;
        }
        String host = s;
        int port = PORT;
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(':') == colon) {
            try {
                port = Integer.parseInt(s.substring(colon + 1).trim());
                host = s.substring(0, colon).trim();
            } catch (Exception ignored) {
            }
        }
        if (host.isEmpty()) {
            return null;
        }
        return String.format(Locale.US, "http://%s:%d", host, port);
    }
}
