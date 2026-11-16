package com.aijianshen.app;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本机的同步服务：让同一 Wi-Fi 下的另一台手机来读走 / 推入数据。
 *
 * <p>只有两个接口：{@code GET /snapshot} 返回本机快照，{@code POST /merge} 接收对方快照
 * 并合并。没有鉴权，靠的是「同一个局域网」这个前提，所以默认关闭、由用户主动开启。
 */
public class LanServer {

    private static final String TAG = "LanServer";

    public interface Listener {
        void onStarted(String ip, int port);

        void onStopped();

        void onError(String message);

        /** 有设备来同步时的提示，message 已可直接展示。 */
        void onEvent(String message);
    }

    private final Context context;
    private Listener listener;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final android.os.Handler main =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private ServerSocket socket;
    private Thread worker;
    private volatile boolean running;

    private static LanServer shared;

    /**
     * 取得常驻的服务实例。
     *
     * <p>服务不能跟着页面一起走：用户开好服务后多半会切到另一台手机操作，本机这边
     * 只剩后台。所以实例挂在进程上，页面只负责挂 / 摘监听。
     */
    public static synchronized LanServer shared(Context context) {
        if (shared == null) {
            shared = new LanServer(context, null);
        }
        return shared;
    }

    public LanServer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** 页面进出时挂上 / 摘掉监听，服务本身不受影响。 */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            return;
        }
        try {
            socket = new ServerSocket(LanSync.PORT);
            socket.setReuseAddress(true);
            running = true;
        } catch (Exception e) {
            Log.w(TAG, "start", e);
            running = false;
            if (listener != null) {
                listener.onError("端口 " + LanSync.PORT + " 被占用，同步服务没起来");
            }
            return;
        }
        String ip = LanSync.localIpv4();
        if (listener != null) {
            listener.onStarted(ip, LanSync.PORT);
        }

        worker = new Thread(() -> {
            while (running) {
                try {
                    Socket client = socket.accept();
                    client.setSoTimeout(10000);
                    pool.execute(() -> handle(client));
                } catch (Exception e) {
                    if (running) {
                        Log.w(TAG, "accept", e);
                    }
                }
            }
        }, "lan-sync-server");
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
        if (listener != null) {
            listener.onStopped();
        }
    }

    /** 事件来自工作线程，转回主线程再交给页面改界面。 */
    private void event(String message) {
        if (listener == null) {
            return;
        }
        main.post(() -> {
            if (listener != null) {
                listener.onEvent(message);
            }
        });
    }

    private void handle(Socket client) {
        try {
            InputStream in = client.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int b;
            // 逐字节读到 \r\n\r\n 为止；match 记录已经匹配到第几个分隔符
            int match = 0;
            while ((b = in.read()) != -1) {
                head.write(b);
                if (b == '\r') {
                    match = match == 2 ? 3 : 1;
                } else if (b == '\n') {
                    match = match == 1 ? 2 : (match == 3 ? 4 : 0);
                } else {
                    match = 0;
                }
                if (match == 4) {
                    break;
                }
                if (head.size() > 16384) {
                    break;
                }
            }
            String header = new String(head.toByteArray(), "UTF-8");
            String[] lines = header.split("\r\n");
            if (lines.length == 0) {
                respond(client, 400, "{}");
                return;
            }

            String[] first = lines[0].split(" ");
            String method = first.length > 0 ? first[0] : "";
            String path = first.length > 1 ? first[1] : "";

            int contentLength = 0;
            for (String line : lines) {
                int c = line.indexOf(':');
                if (c > 0 && "content-length".equalsIgnoreCase(line.substring(0, c).trim())) {
                    try {
                        contentLength = Integer.parseInt(line.substring(c + 1).trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            String body = "";
            if ("POST".equals(method) && contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = in.read(buf, read, contentLength - read);
                    if (n <= 0) {
                        break;
                    }
                    read += n;
                }
                body = new String(buf, 0, read, "UTF-8");
            }

            if (path.startsWith("/snapshot")) {
                JSONObject snap = LanSync.snapshot(context);
                int n = snap.optJSONArray("body") == null ? 0 : snap.optJSONArray("body").length();
                int days = snap.optJSONArray("dates") == null ? 0 : snap.optJSONArray("dates").length();
                event(String.format(Locale.CHINA,
                        "给 %s 发出了快照（%d 个训练日 · %d 条体成分）",
                        client.getInetAddress().getHostAddress(), days, n));
                respond(client, 200, snap.toString());
                return;
            }

            if (path.startsWith("/merge") && "POST".equals(method)) {
                Store.MergeStats st;
                try {
                    st = LanSync.merge(context, new JSONObject(body));
                } catch (Exception e) {
                    respond(client, 400, "{\"ok\":false,\"msg\":\"bad json\"}");
                    return;
                }
                if (st == null) {
                    respond(client, 400, "{\"ok\":false,\"msg\":\"not an aijianshen snapshot\"}");
                    return;
                }
                event(String.format(Locale.CHINA, "收到 %s 的数据，%s",
                        client.getInetAddress().getHostAddress(), st.summary()));
                JSONObject out = new JSONObject();
                out.put("ok", true);
                out.put("added", st.body);
                out.put("days", st.days);
                out.put("weights", st.weights);
                out.put("sessions", st.sessions);
                respond(client, 200, out.toString());
                return;
            }

            if (path.startsWith("/ping")) {
                respond(client, 200, "{\"ok\":true,\"app\":\"aijianshen\"}");
                return;
            }
            respond(client, 404, "{\"ok\":false}");
        } catch (Exception e) {
            Log.w(TAG, "handle", e);
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void respond(Socket client, int code, String body) {
        try {
            byte[] bytes = body.getBytes("UTF-8");
            String head = "HTTP/1.1 " + code + (code == 200 ? " OK" : " ERROR") + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = client.getOutputStream();
            out.write(head.getBytes("UTF-8"));
            out.write(bytes);
            out.flush();
        } catch (Exception ignored) {
        }
    }
}
