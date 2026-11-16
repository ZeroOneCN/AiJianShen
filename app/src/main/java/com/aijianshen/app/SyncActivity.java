package com.aijianshen.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * 手机间同步：把本机的训练记录、体重与身体成分，通过同一 Wi-Fi 搬到另一台手机。
 *
 * <p>两种用法：本机开「同步服务」，另一台手机填地址来拉；或者本机填对方的地址，
 * 主动拉 / 推。数据只走局域网，不经过服务器。
 */
public class SyncActivity extends Activity implements LanServer.Listener {

    private EditText peerInput;
    private TextView stateText;
    private TextView resultText;
    private TextView serverAddr;
    private TextView serverHint;
    private TextView pullBtn;
    private TextView pushBtn;

    private LanServer server;
    private android.widget.Switch serverToggle;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Nav.chrome(this);

        // 必须在搭界面之前拿到：serverCard() 里把开关拨到「开」时会触发回调调用 start()
        server = LanServer.shared(this);
        server.setListener(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "手机间同步", v -> finish()));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 8), p, Ui.dpi(this, 24));
        sc.addView(body);

        body.addView(intro());
        body.addView(serverCard(), cardLp());
        body.addView(peerCard(), cardLp());
        body.addView(stateCard(), cardLp());
        UiList.hint(this, body,
                "同步只在本机与对方之间点对点传输：不开服务、不填地址时，应用不会发起任何网络请求。");

        // 开关已在上面的 serverCard() 里按记忆状态拨好并起过服务，这里兜底一次（start 幂等）
        if (SyncPrefs.server(this)) {
            server.start();
        } else {
            renderServer();
        }
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dpi(this, 12);
        return lp;
    }

    private LinearLayout intro() {
        LinearLayout card = UiList.card(this);
        int pad = Ui.dpi(this, 18);
        card.setPadding(pad, pad, pad, Ui.dpi(this, 16));

        TextView title = new TextView(this);
        title.setText("换手机不用重新开始");
        title.setTextColor(Ui.INK);
        title.setTextSize(17);
        Ui.font(title, 700);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("两台手机连同一个 Wi-Fi：一台开着「同步服务」，另一台填它的地址点「拉取」，"
                + "训练日、每天分钟数、训练明细、体重与 18 项身体成分都会合并过来。"
                + "重复同步不会产生重复记录。");
        desc.setTextColor(Ui.INK_SOFT);
        desc.setTextSize(13);
        desc.setLineSpacing(Ui.dp(this, 3), 1f);
        desc.setPadding(0, Ui.dpi(this, 8), 0, 0);
        Ui.font(desc, 400);
        card.addView(desc);
        return card;
    }

    // ---- 本机服务 ----

    private LinearLayout serverCard() {
        LinearLayout card = UiList.card(this);
        UiList.Row row = UiList.row(this, card, IconView.WIFI, Ui.BLUE_TILE,
                "本机同步服务", "开着才能被另一台手机读取", true, null,
                (btn, checked) -> {
                    SyncPrefs.setServer(this, checked);
                    if (checked) {
                        server.start();
                    } else {
                        server.stop();
                    }
                });
        if (row.toggle != null) {
            serverToggle = row.toggle;
            row.toggle.setChecked(SyncPrefs.server(this));
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int bp = Ui.dpi(this, 16);
        box.setPadding(bp, Ui.dpi(this, 12), bp, bp);
        card.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        serverAddr = new TextView(this);
        serverAddr.setTextColor(Ui.INK);
        serverAddr.setTextSize(17);
        serverAddr.setTypeface(Typeface.MONOSPACE);
        Ui.font(serverAddr, 700);
        box.addView(serverAddr);

        serverHint = new TextView(this);
        serverHint.setTextColor(Ui.INK_SOFT);
        serverHint.setTextSize(12);
        serverHint.setLineSpacing(Ui.dp(this, 2), 1f);
        serverHint.setPadding(0, Ui.dpi(this, 6), 0, 0);
        Ui.font(serverHint, 400);
        box.addView(serverHint);

        renderServer();
        return card;
    }

    private void renderServer() {
        if (serverAddr == null) {
            return;
        }
        boolean on = SyncPrefs.server(this);
        String ip = LanSync.localIpv4();
        if (on) {
            serverAddr.setText("http://" + (ip.isEmpty() ? "当前没有 Wi-Fi" : ip) + ":"
                    + LanSync.PORT);
            serverHint.setText(ip.isEmpty()
                    ? "没有检测到局域网地址，请先连上 Wi-Fi。"
                    : "在另一台手机的「对方地址」里填这段，然后点拉取。");
        } else {
            serverAddr.setText("服务已关闭");
            serverHint.setText("打开上面的开关后，这里会显示可以填到另一台手机的地址。");
        }
    }

    // ---- 对方地址 ----

    private LinearLayout peerCard() {
        LinearLayout card = UiList.card(this);
        int pad = Ui.dpi(this, 18);
        card.setPadding(pad, Ui.dpi(this, 16), pad, pad);

        TextView label = new TextView(this);
        label.setText("对方地址");
        label.setTextColor(Ui.INK);
        label.setTextSize(15);
        Ui.font(label, 600);
        card.addView(label);

        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(Ui.round(this, Ui.CARD_SOFT, 14f));
        int fp = Ui.dpi(this, 14);
        field.setPadding(fp, 0, fp, 0);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 52));
        flp.topMargin = Ui.dpi(this, 10);
        card.addView(field, flp);

        peerInput = new EditText(this);
        peerInput.setText(SyncPrefs.peer(this));
        peerInput.setHint("192.168.1.12:8766");
        peerInput.setHintTextColor(0xFFB4B9C2);
        peerInput.setTextColor(Ui.INK);
        peerInput.setTextSize(15);
        peerInput.setSingleLine(true);
        peerInput.setBackground(null);
        peerInput.setPadding(0, 0, 0, 0);
        peerInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        peerInput.setTypeface(Typeface.MONOSPACE);
        field.addView(peerInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        alp.topMargin = Ui.dpi(this, 14);
        card.addView(actions, alp);

        TextView push = UiList.secondary(this, "推送本机");
        actions.addView(push, new LinearLayout.LayoutParams(0, Ui.dpi(this, 48), 1f));
        push.setOnClickListener(v -> transfer(false));

        pullBtn = UiList.primary(this, "拉取对方");
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, Ui.dpi(this, 48), 1.15f);
        plp.leftMargin = Ui.dpi(this, 10);
        actions.addView(pullBtn, plp);
        pullBtn.setOnClickListener(v -> transfer(true));
        pushBtn = push;

        TextView tip = new TextView(this);
        tip.setText("「拉取对方」把对方的数据合并到本机；「推送本机」把本机数据发给对方。");
        tip.setTextColor(Ui.INK_SOFT);
        tip.setTextSize(12);
        tip.setLineSpacing(Ui.dp(this, 2), 1f);
        tip.setPadding(0, Ui.dpi(this, 10), 0, 0);
        Ui.font(tip, 400);
        card.addView(tip);
        return card;
    }

    // ---- 状态 ----

    private LinearLayout stateCard() {
        LinearLayout card = UiList.card(this);
        int pad = Ui.dpi(this, 18);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("上次同步");
        title.setTextColor(Ui.INK);
        title.setTextSize(15);
        Ui.font(title, 600);
        card.addView(title);

        stateText = new TextView(this);
        stateText.setTextColor(Ui.INK_SOFT);
        stateText.setTextSize(13);
        stateText.setPadding(0, Ui.dpi(this, 6), 0, 0);
        Ui.font(stateText, 400);
        card.addView(stateText);

        resultText = new TextView(this);
        resultText.setTextColor(Ui.INK_MUTED);
        resultText.setTextSize(13);
        resultText.setLineSpacing(Ui.dp(this, 3), 1f);
        resultText.setPadding(0, Ui.dpi(this, 8), 0, 0);
        Ui.font(resultText, 400);
        card.addView(resultText);

        renderState();
        return card;
    }

    private void renderState() {
        // 具体新增了什么写在 resultText 里，这里只留时间，避免「时间 · N 条新记录」重复一遍
        stateText.setText(SyncPrefs.lastTimeText(this));
        String msg = SyncPrefs.lastMessage(this);
        resultText.setText(msg);
        resultText.setTextColor(msg.startsWith("连不上") || msg.startsWith("对方")
                ? Ui.RED : Ui.INK_MUTED);
    }

    // ---- 传输 ----

    private void transfer(boolean pull) {
        if (busy) {
            return;
        }
        String raw = peerInput.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "先填对方手机上显示的地址", Toast.LENGTH_SHORT).show();
            return;
        }
        final String url = LanSync.normalize(raw);
        if (url == null) {
            Toast.makeText(this, "地址格式不对，例：192.168.1.12:8766", Toast.LENGTH_SHORT).show();
            return;
        }
        SyncPrefs.setPeer(this, raw);
        busy = true;
        setBusy(true);

        new Thread(() -> {
            String message;
            boolean ok = false;
            int added = 0;
            try {
                if (pull) {
                    String body = LanSync.get(url);
                    Store.MergeStats st = LanSync.merge(getApplicationContext(),
                            new JSONObject(body));
                    if (st == null) {
                        message = "对方返回的不是本应用的数据";
                    } else {
                        ok = true;
                        added = st.body;
                        message = st.nothing()
                                ? "对方的数据已经在你这台手机上了，没有新增"
                                : "已从对方导入 " + st.summary();
                    }
                } else {
                    String body = LanSync.post(url,
                            LanSync.snapshot(getApplicationContext()).toString());
                    JSONObject o = new JSONObject(body);
                    ok = o.optBoolean("ok", false);
                    added = o.optInt("added", 0);
                    if (ok) {
                        int days = o.optInt("days", 0);
                        int weights = o.optInt("weights", 0);
                        int sessions = o.optInt("sessions", 0);
                        if (days + weights + added + sessions == 0) {
                            message = "已推送给对方，对方没有新数据需要合并";
                        } else {
                            StringBuilder sb = new StringBuilder("已推送给对方，对方新增");
                            boolean first = true;
                            if (days > 0) {
                                sb.append(' ').append(days).append(" 个训练日");
                                first = false;
                            }
                            if (weights > 0) {
                                sb.append(first ? " " : "、").append(weights).append(" 条体重");
                                first = false;
                            }
                            if (added > 0) {
                                sb.append(first ? " " : "、").append(added).append(" 条体成分");
                                first = false;
                            }
                            if (sessions > 0) {
                                sb.append(first ? " " : "、").append(sessions).append(" 次训练明细");
                            }
                            message = sb.toString();
                        }
                    } else {
                        message = "对方没有接受（可能不是本应用）";
                    }
                }
            } catch (Exception e) {
                message = "连不上 " + url.replace("http://", "")
                        + "，确认两台手机在同一个 Wi-Fi、对方已开启同步服务";
            }
            final String msg = message;
            final boolean fok = ok;
            final int fadded = added;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                busy = false;
                setBusy(false);
                SyncPrefs.setLast(this, fok, fadded, msg);
                renderState();
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void setBusy(boolean on) {
        pullBtn.setEnabled(!on);
        pushBtn.setEnabled(!on);
        pullBtn.setAlpha(on ? 0.6f : 1f);
        pushBtn.setAlpha(on ? 0.5f : 1f);
        pullBtn.setText(on ? "传输中…" : "拉取对方");
    }

    // ---- LanServer 回调 ----

    @Override
    public void onStarted(String ip, int port) {
        renderServer();
    }

    @Override
    public void onStopped() {
        renderServer();
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        SyncPrefs.setServer(this, false);
        if (serverToggle != null) {
            serverToggle.setChecked(false);
        }
        renderServer();
    }

    @Override
    public void onEvent(String message) {
        if (resultText == null) {
            return;
        }
        // 对方来取数据也算一次同步活动，把「上次同步」的时间一起更新掉
        SyncPrefs.setLast(this, true, 0, message);
        renderState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (peerInput != null) {
            SyncPrefs.setPeer(this, peerInput.getText().toString().trim());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到页面时重新挂上监听：服务在页面离开期间是继续跑的
        if (server != null) {
            server.setListener(this);
            if (SyncPrefs.server(this) && !server.isRunning()) {
                server.start();
            }
            renderServer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 只摘监听，不关服务：用户开好服务后通常要切到另一台手机去拉
        if (server != null) {
            server.setListener(null);
        }
    }
}
