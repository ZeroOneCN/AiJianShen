package com.yuanlian.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WorkoutActivity extends Activity {

    public static final String EXTRA_DAY = "day_index";
    public static final String EXTRA_SINGLE = "single_exercise";

    private static final int REST_SECONDS = 20;

    private static class Step {
        final boolean rest;
        final Exercise exercise;
        final int total;
        int remaining;

        Step(boolean rest, Exercise exercise, int total) {
            this.rest = rest;
            this.exercise = exercise;
            this.total = total;
            this.remaining = total;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Step> steps = new ArrayList<>();

    private int index = 0;
    private boolean paused = false;
    private boolean finished = false;
    private int elapsedSeconds = 0;

    private PhotoView photo;
    private TextView tvTime;
    private TextView tvState;
    private TextView tvExercise;
    private TextView tvTip;
    private TextView tvSet;
    private TextView btnToggle;
    private TextView btnSkip;
    private TextView tvTitle;

    private Data.Day day;
    private PlanProfile profile;
    private String sessionTitle = "";

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (paused || finished) {
                return;
            }
            Step s = steps.get(index);
            s.remaining--;
            elapsedSeconds++;
            paintTime();
            if (s.remaining <= 0) {
                advance();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_workout);

        View media = findViewById(R.id.media_card);
        media.setBackground(Ui.round(this, Ui.SURFACE, 26f));
        media.setClipToOutline(true);
        media.setElevation(Ui.dp(this, 3));

        photo = findViewById(R.id.photo);
        photo.setRadiusDp(26f);
        photo.setAspect(1.18f);
        photo.setTrackColor(0x14000000);
        tvTime = findViewById(R.id.tv_time);
        tvState = findViewById(R.id.tv_state);
        tvExercise = findViewById(R.id.tv_exercise);
        tvTip = findViewById(R.id.tv_tip);
        tvSet = findViewById(R.id.tv_set);
        btnToggle = findViewById(R.id.btn_toggle);
        btnSkip = findViewById(R.id.btn_skip);
        tvTitle = findViewById(R.id.tv_title);

        Ui.font(tvTime, 700);
        Ui.font(tvState, 500);
        Ui.font(tvExercise, 700);
        Ui.font(tvTip, 400);
        Ui.font(tvSet, 600);
        Ui.font(tvTitle, 600);
        Ui.font(btnToggle, 700);
        Ui.font(btnSkip, 600);

        btnToggle.setBackground(Ui.ripple(this, Ui.gradientPill(this), 0x33FFFFFF));
        btnSkip.setBackground(Ui.ripple(this, Ui.round(this, Ui.SURFACE, Ui.R_PILL), 0x22000000));

        profile = Store.profile(this);
        int dayIndex = getIntent().getIntExtra(EXTRA_DAY, Data.todayIndex(Calendar.getInstance()));
        day = Data.weekPlan(profile).get(((dayIndex % 7) + 7) % 7);

        String single = getIntent().getStringExtra(EXTRA_SINGLE);
        if (single != null) {
            sessionTitle = single;
        } else {
            sessionTitle = day.title;
        }
        tvTitle.setText(sessionTitle);

        buildSteps(single);
        show(0);
        handler.postDelayed(tick, 1000);

        btnToggle.setOnClickListener(v -> onToggle());
        btnSkip.setOnClickListener(v -> onSkip());
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
    }

    private void buildSteps(String single) {
        List<Exercise> list = new ArrayList<>();
        if (single != null) {
            Exercise one = Data.get(single);
            if (one != null) {
                list.add(one);
            }
        } else {
            list.addAll(day.exercises);
        }
        if (list.isEmpty()) {
            // 兜底：从有氧/腿臀/核心里各取一个动作
            for (String part : new String[]{"有氧", "腿臀", "核心"}) {
                for (Exercise e : Data.byPart(part)) {
                    list.add(e);
                    break;
                }
            }
        }
        int rounds = single != null ? 1 : day.rounds;
        int seconds = single != null ? list.get(0).seconds : day.seconds;
        int rest = single != null ? REST_SECONDS : day.restSeconds;
        for (int r = 0; r < rounds; r++) {
            for (int i = 0; i < list.size(); i++) {
                Exercise e = list.get(i);
                steps.add(new Step(false, e, seconds));
                boolean last = r == rounds - 1 && i == list.size() - 1;
                if (!last) {
                    steps.add(new Step(true, e, rest));
                }
            }
        }
    }

    private void show(int i) {
        Step s = steps.get(i);
        paintTime();
        if (s.rest) {
            tvState.setText("休息");
            tvExercise.setText("缓一缓");
            int nextAction = i + 1 < steps.size() ? actionOrdinal(i + 1) : 0;
            boolean nextRound = nextAction > 1 && perRound() > 0
                    && (nextAction - 1) % perRound() == 0
                    && totalRounds() > 1;
            tvTip.setText(nextRound ? "下一轮马上开始，保持节奏。" : "调整呼吸，下一个动作马上开始。");
            tvSet.setText(setLabel(i));
            // 休息时保留上一个动作的画面，压暗表示暂停
            photo.bind(s.exercise);
            photo.setAlpha(0.45f);
        } else {
            tvState.setText("进行中");
            tvExercise.setText(s.exercise.name);
            tvTip.setText(s.exercise.brief());
            tvSet.setText(setLabel(i) + (s.exercise.target.isEmpty() ? "" : " · " + s.exercise.target));
            photo.bind(s.exercise);
            photo.setAlpha(1f);
        }
    }

    private int perRound() {
        int total = 0;
        for (Step s : steps) {
            if (!s.rest) {
                total++;
            }
        }
        int rounds = totalRounds();
        return rounds <= 0 ? total : total / rounds;
    }

    private int totalRounds() {
        return getIntent().getStringExtra(EXTRA_SINGLE) != null ? 1 : Math.max(1, day.rounds);
    }

    private int actionOrdinal(int stepIndex) {
        int n = 0;
        for (int k = 0; k <= stepIndex && k < steps.size(); k++) {
            if (!steps.get(k).rest) {
                n++;
            }
        }
        return n;
    }

    /** 用「第几轮 + 第几个动作」表达进度。 */
    private String setLabel(int i) {
        int per = perRound();
        if (per <= 0) {
            return "";
        }
        int action = Math.max(1, actionOrdinal(i));
        int rounds = totalRounds();
        int round = (action - 1) / per + 1;
        int pos = (action - 1) % per + 1;
        String prefix = rounds > 1 ? "第 " + round + "/" + rounds + " 轮 · " : "";
        return prefix + "第 " + pos + "/" + per + " 个动作";
    }

    private void paintTime() {
        Step s = steps.get(index);
        int sec = Math.max(0, s.remaining);
        tvTime.setText(String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60));
        photo.setProgress(s.total == 0 ? 0f : (float) sec / (float) s.total);
    }

    private void advance() {
        handler.removeCallbacks(tick);
        if (index >= steps.size() - 1) {
            complete();
            return;
        }
        index++;
        show(index);
        handler.postDelayed(tick, 1000);
    }

    private void onToggle() {
        if (finished) {
            finish();
            return;
        }
        paused = !paused;
        btnToggle.setText(paused ? "继续" : "暂停");
        tvState.setText(paused ? "已暂停" : (steps.get(index).rest ? "休息" : "进行中"));
        if (!paused) {
            handler.postDelayed(tick, 1000);
        } else {
            handler.removeCallbacks(tick);
        }
    }

    private void onSkip() {
        if (finished) {
            return;
        }
        handler.removeCallbacks(tick);
        advance();
    }

    private void complete() {
        finished = true;
        paused = true;
        photo.setProgress(-1f);
        tvTime.setText("✓");
        tvState.setText("训练完成");
        tvExercise.setText(sessionTitle);
        tvTip.setText("本次约 " + Math.max(1, elapsedSeconds / 60) + " 分钟，记得补水。");
        tvSet.setText("");
        btnToggle.setText("完成");
        btnSkip.setVisibility(TextView.GONE);
        Store.markDone(this, Calendar.getInstance(), Math.max(1, elapsedSeconds / 60));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!finished && !paused) {
            onToggle();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
