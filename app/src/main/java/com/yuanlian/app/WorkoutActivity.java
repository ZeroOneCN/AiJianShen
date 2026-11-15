package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WorkoutActivity extends Activity {

    public static final String EXTRA_DAY = "day_index";
    public static final String EXTRA_SINGLE = "single_exercise";

    private static class Step {
        final boolean rest;
        Exercise exercise;
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
    private int leadIn = 0;
    private boolean paused = false;
    private boolean finished = false;
    private int elapsedSeconds = 0;

    private PhotoView photo;
    private TimerRingView ring;
    private TextView tvState;
    private TextView tvExercise;
    private TextView tvTip;
    private TextView tvSet;
    private TextView tvToggle;
    private TextView tvTitle;
    private IconView iconToggle;

    private Data.Day day;
    private PlanProfile profile;
    private String sessionTitle = "";
    private Store.WorkoutPrefs prefs;
    private Voice voice;
    private MediaPlayer music;
    private String musicSrc;
    private boolean musicManualPaused;
    private boolean musicErrorShown;
    private final WorkoutCues cues = new WorkoutCues();
    private int dayIndex;
    private String single;
    private int lastCuedStep = -1;
    private int resultMinutes;
    private int resultCount;
    private boolean sessionMarked;
    private boolean resultOpened;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (paused || finished) {
                return;
            }
            if (leadIn > 0) {
                leadIn--;
                if (leadIn <= 0) {
                    paintLeadDone();
                    Step first = steps.get(index);
                    tvState.setText(first.rest ? "休息" : "进行中");
                    tvState.setTextColor(first.rest ? Ui.GREEN : Ui.ACCENT);
                    cueStep(index);
                    handler.postDelayed(this, 700);
                } else {
                    paintLead();
                    handler.postDelayed(this, 1000);
                }
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
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        findViewById(R.id.media_area).setBackgroundColor(0xFFEEF1F6);

        photo = findViewById(R.id.photo);
        photo.setRadiusDp(24f);
        photo.setZoom(1f);
        photo.setAnimated(true);
        photo.setTrackColor(0x14000000);

        tvState = findViewById(R.id.tv_state);
        tvExercise = findViewById(R.id.tv_exercise);
        tvTip = findViewById(R.id.tv_tip);
        tvSet = findViewById(R.id.tv_set);
        tvToggle = findViewById(R.id.tv_toggle);
        tvTitle = findViewById(R.id.tv_title);
        ring = findViewById(R.id.ring);
        iconToggle = findViewById(R.id.icon_toggle);

        Ui.font(tvState, 800);
        Ui.font(tvExercise, 700);
        Ui.font(tvTip, 400);
        Ui.font(tvSet, 600);
        Ui.font(tvToggle, 700);
        Ui.font(tvTitle, 600);

        findViewById(R.id.sheet).setBackgroundResource(R.drawable.bg_sheet);

        int glass = 0xF2FFFFFF;
        circle(findViewById(R.id.btn_back), glass, Ui.INK, IconView.BACK, R.id.icon_back);
        circle(findViewById(R.id.btn_help), glass, Ui.INK, IconView.INFO, R.id.icon_help);
        circle(findViewById(R.id.btn_music), glass, Ui.INK, IconView.MUSIC, R.id.icon_music);
        circle(findViewById(R.id.btn_settings), glass, Ui.INK, IconView.SETTINGS,
                R.id.icon_settings);
        circle(findViewById(R.id.btn_like), glass, Ui.INK_MUTED, IconView.THUMB_UP, R.id.icon_like);
        circle(findViewById(R.id.btn_dislike), glass, Ui.INK_MUTED, IconView.THUMB_DOWN,
                R.id.icon_dislike);
        circle(findViewById(R.id.btn_prev), Ui.CARD_SOFT, Ui.INK, IconView.SKIP_PREV,
                R.id.icon_prev);
        circle(findViewById(R.id.btn_skip), Ui.CARD_SOFT, Ui.INK, IconView.SKIP_NEXT,
                R.id.icon_next);

        LinearLayout toggle = findViewById(R.id.btn_toggle);
        toggle.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        iconToggle.setIcon(IconView.PAUSE);
        iconToggle.setColor(Color.WHITE);
        iconToggle.setStrokeDp(2.2f);

        profile = Store.profile(this);
        prefs = Store.WorkoutPrefs.load(this);
        dayIndex = getIntent().getIntExtra(EXTRA_DAY, Data.todayIndex(Calendar.getInstance()));
        day = Data.withOverrides(Data.weekPlan(profile).get(((dayIndex % 7) + 7) % 7),
                Store.overrides(this, dayIndex));

        single = getIntent().getStringExtra(EXTRA_SINGLE);
        if (single != null) {
            sessionTitle = single;
        } else {
            sessionTitle = day.title;
        }
        tvTitle.setText(sessionTitle);

        buildSteps(single);
        show(0);
        tvState.setText(prefs.prepSeconds > 0 ? "预备开始" : "开始训练");
        tvTip.setText("站好位置，跟着动作示范，保持呼吸。");
        beginLeadIn();

        findViewById(R.id.btn_back).setOnClickListener(v -> showExit());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelp());
        findViewById(R.id.btn_music).setOnClickListener(v -> toggleMusic());
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, WorkoutSettingsActivity.class)));
        findViewById(R.id.btn_prev).setOnClickListener(v -> restartStep());
        findViewById(R.id.btn_skip).setOnClickListener(v -> onSkip());
        findViewById(R.id.btn_toggle).setOnClickListener(v -> onToggle());
        findViewById(R.id.btn_like).setOnClickListener(v -> feedback(true));
        findViewById(R.id.btn_dislike).setOnClickListener(v -> feedback(false));
    }

    private void circle(View view, int bg, int color, int icon, int iconId) {
        view.setBackground(Ui.ripple(this,
                Ui.roundStroke(this, bg, 21f, 0x14000000), 0x22000000));
        IconView ic = view.findViewById(iconId);
        ic.setIcon(icon);
        ic.setColor(color);
        ic.setStrokeDp(2f);
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
            for (String part : new String[]{"有氧", "腿臀", "核心"}) {
                for (Exercise e : Data.byPart(part)) {
                    list.add(e);
                    break;
                }
            }
        }
        int rounds = single != null ? 1 : day.rounds;
        int seconds = single != null ? list.get(0).seconds : day.seconds;
        int rest = prefs.restFor(day.restSeconds);
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

    private void beginLeadIn() {
        handler.removeCallbacks(tick);
        leadIn = Math.max(0, prefs.prepSeconds);
        paused = false;
        if (leadIn <= 0) {
            paintLeadDone();
            Step first = steps.get(index);
            tvState.setText(first.rest ? "休息" : "进行中");
            tvState.setTextColor(first.rest ? Ui.GREEN : Ui.ACCENT);
            cueStep(index);
            handler.postDelayed(tick, 1000);
            return;
        }
        paintLead();
        cue(WorkoutCues.TICK, 30, "准备");
        handler.postDelayed(tick, 1000);
    }

    private void cue(int kind, int vibrateMs, String say) {
        if (prefs.sound) {
            cues.play(kind);
        }
        if (prefs.vibrate && vibrateMs > 0) {
            WorkoutCues.vibrate(this, vibrateMs);
        }
        if (prefs.voice && voice != null) {
            voice.speak(say);
        }
    }

    private void cueStep(int i) {
        if (i < 0 || i >= steps.size() || i == lastCuedStep) {
            return;
        }
        lastCuedStep = i;
        Step s = steps.get(i);
        if (s.rest) {
            cue(WorkoutCues.REST, 30, "休息");
        } else {
            cue(WorkoutCues.START, 60, s.exercise.name);
        }
    }

    private void paintLead() {
        ring.setRingColor(Ui.ACCENT);
        ring.setDone(false);
        ring.setSeconds(leadIn);
        ring.setProgress(leadIn / (float) Math.max(1, prefs.prepSeconds));
        tvState.setText("预备开始");
        tvState.setTextColor(Ui.ACCENT);
        iconToggle.setIcon(IconView.PAUSE);
        tvToggle.setText("暂停");
    }

    private void paintLeadDone() {
        ring.setSeconds(0);
        ring.setProgress(0f);
    }

    private void show(int i) {
        Step s = steps.get(i);
        paintTime();
        if (s.rest) {
            tvState.setText("休息");
            tvState.setTextColor(Ui.GREEN);
            ring.setRingColor(Ui.GREEN);
            tvExercise.setText("缓一缓");
            int nextAction = i + 1 < steps.size() ? actionOrdinal(i + 1) : 0;
            boolean nextRound = nextAction > 1 && perRound() > 0
                    && (nextAction - 1) % perRound() == 0
                    && totalRounds() > 1;
            tvTip.setText(nextRound ? "下一轮马上开始，保持节奏。" : "调整呼吸，下一个动作马上开始。");
            tvSet.setText(setLabel(i));
            photo.bind(s.exercise);
            photo.setAlpha(0.35f);
        } else {
            tvState.setText("进行中");
            tvState.setTextColor(Ui.ACCENT);
            ring.setRingColor(Ui.ACCENT);
            tvExercise.setText(s.exercise.name);
            tvTip.setText(s.exercise.brief());
            tvSet.setText(setLabel(i));
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
        ring.setDone(false);
        ring.setSeconds(sec);
        ring.setProgress(s.total == 0 ? 0f : (float) sec / (float) s.total);
    }

    private void advance() {
        handler.removeCallbacks(tick);
        if (index >= steps.size() - 1) {
            complete();
            return;
        }
        index++;
        show(index);
        cueStep(index);
        if (!paused) {
            handler.postDelayed(tick, 1000);
        }
    }

    private void restartStep() {
        restartStep(false);
    }

    private void restartStep(boolean resume) {
        if (finished) {
            return;
        }
        handler.removeCallbacks(tick);
        Step s = steps.get(index);
        s.remaining = s.total;
        leadIn = 0;
        if (resume) {
            paused = false;
            iconToggle.setIcon(IconView.PAUSE);
            tvToggle.setText("暂停");
        }
        show(index);
        if (!paused) {
            lastCuedStep = -1;
            cueStep(index);
            handler.postDelayed(tick, 1000);
        }
        Toast.makeText(this, "已重新开始当前动作", Toast.LENGTH_SHORT).show();
    }

    private void onToggle() {
        if (finished) {
            openResult();
            return;
        }
        paused = !paused;
        iconToggle.setIcon(paused ? IconView.PLAY : IconView.PAUSE);
        tvToggle.setText(paused ? "继续" : "暂停");
        if (leadIn > 0) {
            tvState.setText(paused ? "已暂停" : "预备开始");
            tvState.setTextColor(paused ? Ui.INK_MUTED : Ui.ACCENT);
        } else {
            boolean rest = steps.get(index).rest;
            tvState.setText(paused ? "已暂停" : (rest ? "休息" : "进行中"));
            tvState.setTextColor(paused ? Ui.INK_MUTED : (rest ? Ui.GREEN : Ui.ACCENT));
        }
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
        leadIn = 0;
        resultMinutes = Math.max(1, (elapsedSeconds + 59) / 60);
        resultCount = 0;
        for (Step s : steps) {
            if (!s.rest) {
                resultCount++;
            }
        }
        if (!sessionMarked) {
            Store.markDone(this, Calendar.getInstance(), resultMinutes,
                    sessionTitle, resultCount);
            sessionMarked = true;
        }
        photo.setAlpha(0.55f);
        ring.setProgress(1f);
        ring.setSeconds(0);
        ring.setRingColor(Ui.ACCENT);
        ring.setDone(true);
        tvState.setText("训练完成");
        tvState.setTextColor(Ui.ACCENT);
        tvExercise.setText("做得不错");
        tvTip.setText("这次约 " + resultMinutes + " 分钟，记得补水。");
        tvSet.setText("已保存到本机 · 不联网");
        iconToggle.setIcon(IconView.CHECK);
        tvToggle.setText("完成");
        findViewById(R.id.btn_skip).setVisibility(View.GONE);
        findViewById(R.id.btn_prev).setVisibility(View.GONE);
        cue(WorkoutCues.DONE, 120, "训练完成，做得不错");
        handler.postDelayed(this::openResult, 1200);
    }

    private void openResult() {
        if (resultOpened || isFinishing()) {
            return;
        }
        resultOpened = true;
        Intent it = new Intent(this, ResultActivity.class);
        it.putExtra(ResultActivity.EXTRA_DAY, dayIndex);
        if (single != null) {
            it.putExtra(ResultActivity.EXTRA_SINGLE, single);
        }
        it.putExtra(ResultActivity.EXTRA_TITLE, sessionTitle);
        it.putExtra(ResultActivity.EXTRA_MINUTES, resultMinutes);
        it.putExtra(ResultActivity.EXTRA_COUNT, resultCount);
        startActivity(it);
        finish();
    }

    private void feedback(boolean up) {
        Toast.makeText(this, up ? "已记为喜欢，仅保存在本机" : "已记录，下次会换动作",
                Toast.LENGTH_SHORT).show();
    }

    private void ensureVoice() {
        if (prefs.voice && voice == null) {
            voice = new Voice(this, (ok, status) -> {
            });
        } else if (!prefs.voice && voice != null) {
            voice.shutdown();
            voice = null;
        }
    }

    private void toggleMusic() {
        if (prefs.musicUri.isEmpty()) {
            Toast.makeText(this, "还没有选择训练音乐", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, WorkoutSettingsActivity.class));
            return;
        }
        if (music != null && music.isPlaying()) {
            pauseMusic();
            musicManualPaused = true;
            Toast.makeText(this, "音乐已暂停", Toast.LENGTH_SHORT).show();
        } else {
            musicManualPaused = false;
            startMusic(true);
        }
    }

    private void startMusic(boolean manual) {
        if (!manual && (!prefs.musicAuto || prefs.musicUri.isEmpty())) {
            return;
        }
        if (prefs.musicUri.isEmpty()) {
            return;
        }
        if (music != null && !prefs.musicUri.equals(musicSrc)) {
            releaseMusic();
        }
        if (music != null) {
            try {
                if (!music.isPlaying()) {
                    music.start();
                }
            } catch (Throwable t) {
                releaseMusic();
                notifyMusicError();
            }
            return;
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            String src = prefs.musicUri;
            if (src.startsWith("content://")) {
                mp.setDataSource(this, Uri.parse(src));
            } else if (src.startsWith("file://")) {
                mp.setDataSource(Uri.parse(src).getPath());
            } else {
                mp.setDataSource(src);
            }
            mp.setLooping(true);
            mp.setVolume(0.55f, 0.55f);
            mp.setOnPreparedListener(p -> {
                musicErrorShown = false;
                if (!paused && !finished) {
                    try {
                        p.start();
                    } catch (Throwable ignored) {
                    }
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                releaseMusic();
                notifyMusicError();
                return true;
            });
            mp.prepareAsync();
            music = mp;
            musicSrc = src;
        } catch (Throwable t) {
            releaseMusic();
            notifyMusicError();
        }
    }

    private void notifyMusicError() {
        if (musicErrorShown) {
            return;
        }
        musicErrorShown = true;
        Toast.makeText(this, "这首音乐无法播放，请在训练设置里重新选择",
                Toast.LENGTH_LONG).show();
    }

    private void pauseMusic() {
        if (music == null) {
            return;
        }
        try {
            if (music.isPlaying()) {
                music.pause();
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseMusic() {
        if (music == null) {
            return;
        }
        try {
            music.stop();
        } catch (Throwable ignored) {
        }
        try {
            music.release();
        } catch (Throwable ignored) {
        }
        music = null;
        musicSrc = null;
    }

    private void showReplace(Exercise current) {
        ReplaceSheet.show(this, current, pick -> {
            Store.setOverride(this, dayIndex, current.id, pick.id);
            for (Step st : steps) {
                if (st.exercise != null && st.exercise.id.equals(current.id)) {
                    st.exercise = pick;
                }
            }
            day = Data.withOverrides(Data.weekPlan(profile)
                    .get(((dayIndex % 7) + 7) % 7), Store.overrides(this, dayIndex));
            lastCuedStep = -1;
            show(index);
            Toast.makeText(this, "已换成 " + pick.name + "，本周都按这个动作",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showHelp() {
        Step s = steps.get(index);
        Exercise e = s.exercise;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 26f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, p, p, Ui.dpi(this, 16));
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sc.addView(box);

        TextView title = new TextView(this);
        title.setText("怎么做 · " + e.name);
        title.setTextColor(Ui.INK);
        title.setTextSize(20);
        Ui.font(title, 700);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText(e.target.isEmpty() ? e.part : e.part + " · " + e.target);
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(12);
        sub.setPadding(0, Ui.dpi(this, 4), 0, Ui.dpi(this, 8));
        Ui.font(sub, 400);
        box.addView(sub);

        for (int i = 0; i < e.steps.size(); i++) {
            TextView line = new TextView(this);
            line.setText((i + 1) + "  " + e.steps.get(i));
            line.setTextColor(Ui.INK);
            line.setTextSize(14);
            line.setLineSpacing(Ui.dp(this, 5), 1f);
            line.setPadding(0, Ui.dpi(this, 7), 0, 0);
            Ui.font(line, 400);
            box.addView(line);
        }

        TextView breathTitle = new TextView(this);
        breathTitle.setText("呼吸");
        breathTitle.setTextColor(Ui.INK);
        breathTitle.setTextSize(15);
        breathTitle.setPadding(0, Ui.dpi(this, 16), 0, Ui.dpi(this, 3));
        Ui.font(breathTitle, 700);
        box.addView(breathTitle);

        TextView breath = new TextView(this);
        breath.setText(e.breathingTip());
        breath.setTextColor(Ui.INK_SOFT);
        breath.setTextSize(13);
        breath.setLineSpacing(Ui.dp(this, 4), 1f);
        Ui.font(breath, 400);
        box.addView(breath);

        if (!e.mistakes.isEmpty()) {
            TextView mistakeTitle = new TextView(this);
            mistakeTitle.setText("常见错误");
            mistakeTitle.setTextColor(Ui.INK);
            mistakeTitle.setTextSize(15);
            mistakeTitle.setPadding(0, Ui.dpi(this, 16), 0, Ui.dpi(this, 3));
            Ui.font(mistakeTitle, 700);
            box.addView(mistakeTitle);

            for (String m : e.mistakes) {
                TextView line = new TextView(this);
                line.setText("·  " + m);
                line.setTextColor(Ui.INK_SOFT);
                line.setTextSize(13);
                line.setLineSpacing(Ui.dp(this, 4), 1f);
                line.setPadding(0, Ui.dpi(this, 3), 0, 0);
                Ui.font(line, 400);
                box.addView(line);
            }
        }

        TextView close = new TextView(this);
        close.setText("知道了");
        close.setTextColor(Color.WHITE);
        close.setTextSize(15);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF));
        Ui.font(close, 700);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 50));
        clp.topMargin = Ui.dpi(this, 16);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (single == null) {
            TextView change = new TextView(this);
            change.setText("换一个动作");
            change.setTextColor(Ui.INK);
            change.setTextSize(15);
            change.setGravity(Gravity.CENTER);
            change.setBackground(Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, 25f),
                    0x22000000));
            Ui.font(change, 600);
            LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 48));
            chlp.topMargin = Ui.dpi(this, 14);
            root.addView(change, chlp);
            change.setOnClickListener(v -> {
                dialog.dismiss();
                showReplace(e);
            });
        }
        root.addView(close, clp);
        close.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        dialog.show();
        if (w != null) {
            int width = getResources().getDisplayMetrics().widthPixels - Ui.dpi(this, 48);
            int maxH = Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            final Window fw = w;
            root.post(() -> {
                if (root.getHeight() <= maxH) {
                    return;
                }
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sc.getLayoutParams();
                lp.height = maxH - (root.getPaddingTop() + root.getPaddingBottom()
                        + clp.height + clp.topMargin
                        + (single == null ? Ui.dpi(this, 62) : 0));
                sc.setLayoutParams(lp);
                fw.setLayout(width, maxH);
            });
        }
    }

    private void showExit() {
        if (finished) {
            finish();
            return;
        }
        if (!paused) {
            onToggle();
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.round(this, Ui.SURFACE, 28f));
        int p = Ui.dpi(this, 22);
        root.setPadding(p, Ui.dpi(this, 10), p, Ui.dpi(this, 24));

        View handle = new View(this);
        handle.setBackground(Ui.round(this, 0xFFE2E5EA, 3f));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 42), Ui.dpi(this, 5));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dpi(this, 18);
        root.addView(handle, hlp);

        TextView title = new TextView(this);
        title.setText("要结束这次训练吗？");
        title.setTextColor(Ui.INK);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        Ui.font(title, 800);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("已完成的动作会保留在本机记录里。");
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dpi(this, 6), 0, Ui.dpi(this, 18));
        Ui.font(sub, 400);
        root.addView(sub);

        TextView resume = sheetAction("继续锻炼", Ui.ACCENT, true);
        TextView restart = sheetAction("重新开始此动作", Ui.INK, false);
        TextView later = sheetAction("稍后再做", Ui.INK_MUTED, false);
        root.addView(resume);
        root.addView(restart);
        root.addView(later);

        resume.setOnClickListener(v -> {
            dialog.dismiss();
            onToggle();
        });
        restart.setOnClickListener(v -> {
            dialog.dismiss();
            restartStep(true);
        });
        later.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
        }
        dialog.show();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private TextView sheetAction(String text, int color, boolean primary) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(primary ? Color.WHITE : color);
        tv.setBackground(primary
                ? Ui.ripple(this, Ui.gradientBluePill(this), 0x33FFFFFF)
                : Ui.ripple(this, Ui.round(this, Ui.CARD_SOFT, Ui.R_PILL), 0x22000000));
        Ui.font(tv, primary ? 700 : 600);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(this, 52));
        lp.topMargin = Ui.dpi(this, 9);
        tv.setLayoutParams(lp);
        return tv;
    }

    @Override
    public void onBackPressed() {
        showExit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs == null) {
            return;
        }
        prefs = Store.WorkoutPrefs.load(this);
        photo.setAnimated(prefs.animate);
        ensureVoice();
        if (prefs.musicAuto && !musicManualPaused) {
            startMusic(false);
        } else {
            pauseMusic();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseMusic();
        if (!finished && !paused) {
            onToggle();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cues.release();
        releaseMusic();
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        super.onDestroy();
    }
}
