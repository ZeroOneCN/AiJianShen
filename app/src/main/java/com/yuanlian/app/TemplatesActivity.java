package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** 我的训练：自己挑动作、定轮次存成模板，一键开练。 */
public class TemplatesActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private LinearLayout body;
    /** 非空表示这次选动作是在改已有模板，而不是新建。 */
    private String editingId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        Nav.chrome(this);

        LinearLayout root = UiList.page(this);
        setContentView(root);
        Nav.insets(root);
        root.addView(UiList.topBar(this, "我的训练", v -> finish()));

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dpi(this, 20);
        body.setPadding(p, Ui.dpi(this, 4), p, Ui.dpi(this, 20));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(p, Ui.dpi(this, 8), p, Ui.dpi(this, 12));
        root.addView(bottom);

        TextView add = UiList.primary(this, "新建训练");
        bottom.addView(add, UiList.lp(this, 52, 0));
        add.setOnClickListener(v -> pick(null, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        // 去选动作时本页可能被系统回收，回来时还要知道这次是在改哪个模板，
        // 否则会退化成「新建」，凭空多出一个训练。
        out.putString("editing_id", editingId);
    }

    @Override
    protected void onRestoreInstanceState(Bundle in) {
        super.onRestoreInstanceState(in);
        editingId = in.getString("editing_id");
    }

    private void render() {
        body.removeAllViews();
        List<Store.Template> list = Store.templates(this);

        if (list.isEmpty()) {
            LinearLayout card = UiList.card(this);
            int p = Ui.dpi(this, 20);
            card.setPadding(p, p, p, p);
            TextView t = new TextView(this);
            t.setText("还没有自己编排的训练");
            t.setTextColor(Ui.INK);
            t.setTextSize(17);
            Ui.font(t, 700);
            card.addView(t);
            TextView d = new TextView(this);
            d.setText("从 " + Data.all().size() + " 个动作里挑几个存下来，定好轮次和动作时长，"
                    + "以后在首页点一下就能开练，不用每次重新想练什么。");
            d.setTextColor(Ui.INK_SOFT);
            d.setTextSize(13);
            d.setLineSpacing(Ui.dp(this, 3), 1f);
            d.setPadding(0, Ui.dpi(this, 8), 0, 0);
            Ui.font(d, 400);
            card.addView(d);
            body.addView(card);
            return;
        }

        for (Store.Template t : list) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dpi(this, 12);
            body.addView(card(t), lp);
        }
        UiList.hint(this, body, "点卡片上的「开始」直接练；长按卡片可以改轮次、改动作时长、"
                + "重选动作或删除。");
    }

    private View card(Store.Template t) {
        LinearLayout card = UiList.card(this);
        int p = Ui.dpi(this, 16);
        card.setPadding(p, p, p, p);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(head);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        head.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextColor(Ui.INK);
        name.setTextSize(17);
        Ui.font(name, 700);
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText(t.summary());
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(12.5f);
        sub.setPadding(0, Ui.dpi(this, 3), 0, 0);
        Ui.font(sub, 400);
        texts.addView(sub);

        TextView start = new TextView(this);
        start.setText("开始");
        start.setTextColor(Color.WHITE);
        start.setTextSize(14);
        start.setGravity(Gravity.CENTER);
        start.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        Ui.font(start, 700);
        head.addView(start, new LinearLayout.LayoutParams(
                Ui.dpi(this, 72), Ui.dpi(this, 38)));
        start.setOnClickListener(v -> start(t));

        card.setOnClickListener(v -> start(t));
        card.setOnLongClickListener(v -> {
            ops(t);
            return true;
        });

        TextView preview = new TextView(this);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.ids.size() && i < 6; i++) {
            Exercise e = Data.byId(t.ids.get(i));
            if (e == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.name);
        }
        if (t.ids.size() > 6) {
            sb.append(" 等 ").append(t.ids.size()).append(" 个");
        }
        preview.setText(sb.toString());
        preview.setTextColor(Ui.INK_MUTED);
        preview.setTextSize(12);
        preview.setLineSpacing(Ui.dp(this, 2), 1f);
        preview.setPadding(0, Ui.dpi(this, 10), 0, 0);
        Ui.font(preview, 400);
        card.addView(preview);
        return card;
    }

    private void ops(Store.Template t) {
        String[] ops = {"开始训练", "改轮次", "改动作时长", "重选动作", "删除这个训练"};
        ChoiceDialog.show(this, t.name, ops, -1, i -> {
            switch (i) {
                case 0:
                    start(t);
                    break;
                case 1:
                    rounds(t);
                    break;
                case 2:
                    seconds(t);
                    break;
                case 3:
                    pick(t.ids, t.id);
                    break;
                case 4:
                    Store.deleteTemplate(this, t.id);
                    Toast.makeText(this, "已删除「" + t.name + "」", Toast.LENGTH_SHORT).show();
                    render();
                    break;
                default:
                    break;
            }
        });
    }

    private void rounds(Store.Template t) {
        String[] labels = {"1 轮", "2 轮", "3 轮", "4 轮", "5 轮"};
        ChoiceDialog.show(this, "练几轮", labels, Math.min(4, t.rounds - 1), i -> {
            t.rounds = i + 1;
            Store.saveTemplate(this, t);
            render();
        });
    }

    private void seconds(Store.Template t) {
        String[] labels = {"30 秒", "40 秒", "50 秒", "60 秒", "90 秒"};
        int[] values = {30, 40, 50, 60, 90};
        int sel = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == t.seconds) {
                sel = i;
            }
        }
        ChoiceDialog.show(this, "每个动作多长时间", labels, sel, i -> {
            t.seconds = values[i];
            Store.saveTemplate(this, t);
            render();
        });
    }

    private void pick(java.util.List<String> pre, String templateId) {
        editingId = templateId;
        Intent it = new Intent(this, PickActivity.class);
        if (pre != null && !pre.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String id : pre) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(id);
            }
            it.putExtra(PickActivity.EXTRA_SELECTED, sb.toString());
        }
        startActivityForResult(it, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            editingId = null;
            return;
        }
        String ids = data.getStringExtra(PickActivity.EXTRA_SELECTED);
        if (ids == null || ids.isEmpty()) {
            editingId = null;
            return;
        }
        if (editingId != null) {
            Store.Template t = Store.template(this, editingId);
            editingId = null;
            if (t != null) {
                t.ids = new java.util.ArrayList<>();
                for (String id : ids.split(",")) {
                    if (!id.isEmpty()) {
                        t.ids.add(id);
                    }
                }
                Store.saveTemplate(this, t);
                render();
            }
            return;
        }
        nameDialog(ids);
    }

    /** 新建时先起个名字，默认「我的训练 N」。 */
    private void nameDialog(String ids) {
        int n = Store.templates(this).size() + 1;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 24f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("给这次训练起个名字");
        title.setTextColor(Ui.INK);
        title.setTextSize(20);
        Ui.font(title, 800);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("之后在「我的训练」和首页都能一键开练。");
        tip.setTextColor(Ui.INK_SOFT);
        tip.setTextSize(12.5f);
        tip.setPadding(0, Ui.dpi(this, 6), 0, Ui.dpi(this, 12));
        Ui.font(tip, 400);
        root.addView(tip);

        EditText input = new EditText(this);
        input.setText("我的训练 " + n);
        input.setSelectAllOnFocus(true);
        input.setTextColor(Ui.INK);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackground(Ui.round(this, Ui.CARD_SOFT, 12f));
        input.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 10),
                Ui.dpi(this, 12), Ui.dpi(this, 10));
        Ui.font(input, 500);
        root.addView(input);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
        rlp.topMargin = Ui.dpi(this, 18);
        root.addView(row, rlp);

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(Ui.INK_MUTED);
        cancel.setTextSize(15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 24f), 0x14000000));
        Ui.font(cancel, 600);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        clp.rightMargin = Ui.dpi(this, 10);
        row.addView(cancel, clp);
        cancel.setOnClickListener(v -> dialog.dismiss());

        TextView ok = new TextView(this);
        ok.setText("保存");
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(15);
        ok.setGravity(Gravity.CENTER);
        ok.setBackground(Ui.ripple(this, Ui.round(this, Ui.ACCENT, 24f), 0x33FFFFFF));
        Ui.font(ok, 700);
        row.addView(ok, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ok.setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "我的训练 " + n;
            }
            Store.Template t = new Store.Template();
            t.name = name;
            for (String id : ids.split(",")) {
                if (!id.isEmpty()) {
                    t.ids.add(id);
                }
            }
            Store.saveTemplate(this, t);
            dialog.dismiss();
            Toast.makeText(this, "已保存「" + name + "」", Toast.LENGTH_SHORT).show();
            render();
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        // setLayout 必须在 show 之后：窗口还没 attach 时设的尺寸会被 content 的测量覆盖
        if (w != null) {
            w.setLayout(Ui.dpi(this, 300), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void start(Store.Template t) {
        if (t.ids.isEmpty()) {
            Toast.makeText(this, "这个训练还没有动作", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent it = new Intent(this, WorkoutActivity.class);
        StringBuilder sb = new StringBuilder();
        for (String id : t.ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        it.putExtra(WorkoutActivity.EXTRA_IDS, sb.toString());
        it.putExtra(WorkoutActivity.EXTRA_ROUNDS, t.rounds);
        it.putExtra(WorkoutActivity.EXTRA_SECONDS, t.seconds);
        it.putExtra(WorkoutActivity.EXTRA_REST, t.rest);
        it.putExtra(WorkoutActivity.EXTRA_TITLE, t.name);
        startActivity(it);
    }
}
