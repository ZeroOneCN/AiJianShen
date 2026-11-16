package com.yuanlian.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkoutActivity extends Activity {

    public static final String EXTRA_DAY = "day_index";
    public static final String EXTRA_SINGLE = "single_exercise";
    /** 从首页「继续上次训练」进来：恢复上次没做完的进度。 */
    public static final String EXTRA_RESUME = "resume_pending";
    /** 自定义序列：「我的训练」模板与「5 分钟微训练」用它直接开练。 */
    public static final String EXTRA_IDS = "custom_ids";
    public static final String EXTRA_ROUNDS = "custom_rounds";
    public static final String EXTRA_SECONDS = "custom_seconds";
    public static final String EXTRA_REST = "custom_rest";
    public static final String EXTRA_TITLE = "custom_title";

    private static class Step {
        final boolean rest;
        Exercise exercise;
        int total;
        int remaining;
        /** 这一步实际练了多少秒：跳过时只算停留过的部分，消耗不能按计划量算。 */
        int spent;

        Step(boolean rest, Exercise exercise, int total) {
            this.rest = rest;
            this.exercise = exercise;
            this.total = total;
            this.remaining = total;
        }
    }

    /** 倒计时刷新间隔：数值由绝对时间算出，间隔只影响画面刷新，不影响计时精度。 */
    private static final long TICK_MS = 200;
    /** 休息时「再来一会儿」加多少秒。 */
    private static final int REST_EXTRA = 15;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Step> steps = new ArrayList<>();

    private int index = 0;
    private boolean paused = false;
    private boolean finished = false;

    /** 已经响过的最后几秒，避免 200ms 刷新把同一个数字响很多遍。 */
    private int lastBeep = -1;
    /** 当前步骤结束的绝对时刻（elapsedRealtime），所有剩余秒数都从它反推。 */
    private long deadline = 0;
    /** 是否仍在预备倒计时。 */
    private boolean leadIn = false;
    /** 预备倒计时的总秒数，用于画进度。 */
    private int leadTotal = 0;
    /** 已完成的有效训练毫秒数与本段计时起点。 */
    private long activeMs = 0;
    private long resumedAt = 0;
    private int finalSeconds = 0;

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
    /** 自定义序列（逗号分隔的动作 id）；为空表示按计划或单个动作练。 */
    private String customIds = "";
    private int customRounds = 1;
    private int customSeconds = 0;
    private int customRest = -1;
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
    private int resultKcal;
    /** 本次刷新了几个动作的个人纪录。 */
    private int resultPr;
    private boolean sessionMarked;
    private boolean recordsCounted;
    private boolean resultOpened;
    /** 休息中才显示的两个快捷按钮，避免和「暂停」抢位置。 */
    private LinearLayout restActions;

    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private boolean resumeMusicOnFocus;

    /**
     * 每 TICK_MS 用「结束时刻 - 现在」重算一次剩余秒数。
     * 不用逐次 -1 的做法：Handler 的延迟会被主线程的活拖后，一分半钟就会差好几秒。
     */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (paused || finished) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            int rem = (int) Math.max(0, (deadline - now + 999) / 1000);
            if (leadIn) {
                if (rem <= 0) {
                    leadIn = false;
                    paintLeadDone();
                    startStep(now);
                    Step first = steps.get(index);
                    tvState.setText(first.rest ? "休息" : "进行中");
                    tvState.setTextColor(first.rest ? Ui.GREEN : Ui.ACCENT);
                    cueStep(index);
                    show(index);
                    schedule();
                } else {
                    paintLead(rem);
                    schedule();
                }
                return;
            }
            Step s = steps.get(index);
            if (s.remaining != rem) {
                s.remaining = rem;
                paintTime();
                // 最后 3 秒滴答，提醒收尾；每档只响一次
                if (rem > 0 && rem <= 3 && rem != lastBeep) {
                    lastBeep = rem;
                    cue(WorkoutCues.TICK, 0, "");
                }
            }
            if (rem <= 0) {
                advance();
                return;
            }
            schedule();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Data.init(this);
        setContentView(R.layout.activity_workout);
        Nav.chrome(this);
        Nav.insets(findViewById(R.id.root));

        // 训练中途锁屏等于打断训练，屏幕一直亮到结束
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(R.id.media_area).setBackgroundColor(Ui.SURFACE);
        getWindow().setStatusBarColor(Ui.SURFACE);
        getWindow().setNavigationBarColor(Ui.SURFACE);

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
        restActions = findViewById(R.id.rest_actions);

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
        circle(findViewById(R.id.btn_like), glass, Ui.INK_MUTED, IconView.STAR, R.id.icon_like);
        circle(findViewById(R.id.btn_dislike), glass, Ui.INK_MUTED, IconView.REFRESH,
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
        String ids = getIntent().getStringExtra(EXTRA_IDS);
        customIds = ids == null ? "" : ids;
        customRounds = Math.max(1, getIntent().getIntExtra(EXTRA_ROUNDS, 1));
        customSeconds = Math.max(0, getIntent().getIntExtra(EXTRA_SECONDS, 0));
        customRest = getIntent().getIntExtra(EXTRA_REST, -1);
        boolean resume = getIntent().getBooleanExtra(EXTRA_RESUME, false);
        Store.Pending pending = resume ? Store.pending(this) : null;

        if (pending != null && restorePending(pending)) {
            // 存档先留着：恢复后如果进程被杀，下次还能从原处继续。
            // 练完由 complete() 清，中途退出由 savePendingIfNeeded() 覆盖。
            single = pending.single;
            sessionTitle = pending.title;
            dayIndex = pending.dayIndex;
            tvTitle.setText(sessionTitle);
            show(index);
            resumeTimers(pending.elapsedSeconds);
            cueStep(index);
            schedule();
            bindRestActions();
            bindClicks();
            return;
        }

        String customTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (customTitle != null && !customTitle.isEmpty()) {
            sessionTitle = customTitle;
        } else if (single != null) {
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
        bindRestActions();
        bindClicks();
    }

    private void bindClicks() {
        findViewById(R.id.btn_back).setOnClickListener(v -> showExit());
        findViewById(R.id.btn_help).setOnClickListener(v -> showHelp());
        findViewById(R.id.btn_music).setOnClickListener(v -> toggleMusic());
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, WorkoutSettingsActivity.class)));
        findViewById(R.id.btn_prev).setOnClickListener(v -> restartStep());
        findViewById(R.id.btn_skip).setOnClickListener(v -> onSkip());
        findViewById(R.id.btn_toggle).setOnClickListener(v -> onToggle());
        findViewById(R.id.btn_like).setOnClickListener(v -> toggleFavorite());
        findViewById(R.id.btn_dislike).setOnClickListener(v -> replaceCurrent());
    }

    private void bindRestActions() {
        TextView more = restActions.findViewById(R.id.btn_more_rest);
        TextView skip = restActions.findViewById(R.id.btn_skip_rest);
        for (TextView tv : new TextView[]{more, skip}) {
            tv.setTextSize(12.5f);
            tv.setGravity(Gravity.CENTER);
            int lr = Ui.dpi(this, 14);
            int tb = Ui.dpi(this, 8);
            tv.setPadding(lr, tb, lr, tb);
            tv.setTextColor(Ui.GREEN);
            tv.setBackground(Ui.ripple(this, Ui.round(this, Ui.GREEN_SOFT, Ui.R_PILL),
                    0x22000000));
            Ui.font(tv, 600);
        }
        more.setText("+" + REST_EXTRA + " 秒");
        skip.setText("跳过休息");
        more.setOnClickListener(v -> addRest(REST_EXTRA));
        skip.setOnClickListener(v -> onSkip());
    }

    private void paintRestActions(boolean visible) {
        if (restActions == null) {
            return;
        }
        restActions.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        boolean custom = customIds != null && !customIds.isEmpty();
        if (custom) {
            for (String id : customIds.split(",")) {
                Exercise one = Data.byId(id);
                if (one != null) {
                    list.add(one);
                }
            }
        } else if (single != null) {
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
        int rounds = custom ? customRounds : (single != null ? 1 : day.rounds);
        int seconds = custom && customSeconds > 0 ? customSeconds
                : (single != null ? list.get(0).seconds : day.seconds);
        int rest = prefs.restFor(custom && customRest >= 0 ? customRest : day.restSeconds);
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

    /** 还原上次没做完的那次训练；动作缺失（换版本、动作库变了）时放弃恢复。 */
    private boolean restorePending(Store.Pending p) {
        if (p == null || !p.valid()) {
            return false;
        }
        List<Step> restored = new ArrayList<>();
        Exercise lastExercise = null;
        for (int i = 0; i < p.ids.size(); i++) {
            String id = p.ids.get(i);
            int total = Math.max(1, p.totals.get(i));
            if (id == null || id.isEmpty()) {
                if (lastExercise == null) {
                    return false;
                }
                restored.add(new Step(true, lastExercise, total));
            } else {
                Exercise e = Data.byId(id);
                if (e == null) {
                    return false;
                }
                lastExercise = e;
                restored.add(new Step(false, e, total));
            }
            int remain = Math.max(0, Math.min(total, p.remains.get(i)));
            Step step = restored.get(restored.size() - 1);
            step.remaining = remain;
            // 已经练过的秒数也要还原，否则结算时前段训练的消耗会被当成 0
            step.spent = Math.max(0, Math.min(total, p.spents.get(i)));
        }
        steps.clear();
        steps.addAll(restored);
        restoredRounds = p.rounds;
        index = Math.max(0, Math.min(steps.size() - 1, p.index));
        if (steps.get(index).remaining <= 0) {
            steps.get(index).remaining = steps.get(index).total;
        }
        return true;
    }

    private void resumeTimers(int elapsedSeconds) {
        leadIn = false;
        paused = false;
        activeMs = Math.max(0, elapsedSeconds) * 1000L;
        resumedAt = SystemClock.elapsedRealtime();
        deadline = resumedAt + steps.get(index).remaining * 1000L;
        iconToggle.setIcon(IconView.PAUSE);
        tvToggle.setText("暂停");
        // 状态文字交给 show() 决定；这里给一句一次性提示，别让「接着上次继续」一直挂在那
        Toast.makeText(this, "已恢复上次的进度，接着练", Toast.LENGTH_SHORT).show();
    }

    private void beginLeadIn() {
        handler.removeCallbacks(tick);
        leadTotal = Math.max(0, prefs.prepSeconds);
        paused = false;
        leadIn = leadTotal > 0;
        long now = SystemClock.elapsedRealtime();
        if (!leadIn) {
            paintLeadDone();
            startStep(now);
            Step first = steps.get(index);
            tvState.setText(first.rest ? "休息" : "进行中");
            tvState.setTextColor(first.rest ? Ui.GREEN : Ui.ACCENT);
            cueStep(index);
            schedule();
            return;
        }
        deadline = now + leadTotal * 1000L;
        paintLead(leadTotal);
        cue(WorkoutCues.TICK, 30, "准备");
        schedule();
    }

    /** 把当前这一步的结束时刻定下来，并从现在开始累计有效训练时间。 */
    private void startStep(long now) {
        Step s = steps.get(index);
        if (s.remaining <= 0) {
            s.remaining = s.total;
        }
        lastBeep = -1;
        deadline = now + s.remaining * 1000L;
        if (resumedAt == 0) {
            resumedAt = now;
        }
        paintTime();
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (paused || finished) {
            return;
        }
        handler.postDelayed(tick, TICK_MS);
    }

    private int elapsedSeconds() {
        if (finished) {
            return finalSeconds;
        }
        if (leadIn || paused) {
            return (int) (activeMs / 1000);
        }
        return (int) ((activeMs + SystemClock.elapsedRealtime() - resumedAt) / 1000);
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

    /** 预备倒计时也交给圆环自己逐帧推进，这里的参数只在暂停时用来定格。 */
    private void paintLead(int seconds) {
        long total = Math.max(1, leadTotal) * 1000L;
        ring.setRingColor(Ui.ACCENT);
        if (paused) {
            ring.setRemaining(Math.max(0, seconds) * 1000L, total);
        } else {
            ring.setTime(deadline, total);
        }
        tvState.setText("预备开始");
        tvState.setTextColor(Ui.ACCENT);
        iconToggle.setIcon(IconView.PAUSE);
        tvToggle.setText("暂停");
    }

    private void paintLeadDone() {
        ring.clear();
    }

    private void show(int i) {
        Step s = steps.get(i);
        paintTime();
        paintRestActions(s.rest && !finished);
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
            tvSet.setText(restLabel(i));
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
        if (paused) {
            // 暂停中跳过或换动作时，状态文案不能又变回「进行中」
            tvState.setText("已暂停");
            tvState.setTextColor(Ui.INK_MUTED);
        }
        paintFavorite();
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

    /** 从存档恢复时用存档里的轮次数，避免跨周后计划变了导致「第 x/y 轮」错位。 */
    private int restoredRounds = 0;

    private int totalRounds() {
        if (restoredRounds > 0) {
            return restoredRounds;
        }
        return single != null ? 1 : Math.max(1, day.rounds);
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

    /** 休息步骤不能沿用上一个动作的序号，否则一直停在「第 5/5 个动作」。 */
    private String restLabel(int i) {
        int per = perRound();
        int rounds = totalRounds();
        int done = actionOrdinal(i);
        if (per <= 0) {
            return "组间休息";
        }
        int round = Math.min(rounds, Math.max(1, (done - 1) / per + 1));
        return rounds > 1
                ? "第 " + round + "/" + rounds + " 轮 · 组间休息"
                : "组间休息";
    }

    private void paintTime() {
        Step s = steps.get(index);
        long total = Math.max(1, s.total) * 1000L;
        if (paused) {
            // 暂停时不推进，只把当前余量定格
            ring.setRemaining(Math.max(0, s.remaining) * 1000L, total);
        } else {
            ring.setTime(deadline, total);
        }
    }

    private void advance() {
        handler.removeCallbacks(tick);
        closeCurrentStep();
        if (index >= steps.size() - 1) {
            complete();
            return;
        }
        index++;
        startStep(SystemClock.elapsedRealtime());
        show(index);
        cueStep(index);
        schedule();
    }

    /** 离开当前步骤时结算实际停留秒数。 */
    private void closeCurrentStep() {
        if (index < 0 || index >= steps.size()) {
            return;
        }
        Step s = steps.get(index);
        if (!paused && !leadIn) {
            // 按截止时刻现算一次，别等 tick 的 200ms 粒度，否则会少算 0–1 秒
            s.remaining = (int) Math.max(0,
                    (deadline - SystemClock.elapsedRealtime() + 999) / 1000);
        }
        s.spent = Math.max(0, Math.min(s.total, s.total - s.remaining));
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
        leadIn = false;
        if (resume) {
            paused = false;
            resumedAt = SystemClock.elapsedRealtime();
            iconToggle.setIcon(IconView.PAUSE);
            tvToggle.setText("暂停");
        }
        startStep(SystemClock.elapsedRealtime());
        show(index);
        if (!paused) {
            lastCuedStep = -1;
            cueStep(index);
            schedule();
        }
        Toast.makeText(this, "已重新开始当前动作", Toast.LENGTH_SHORT).show();
    }

    /** 休息时多给一点时间：把结束时刻往后推，剩余秒数由 tick 自动重算。 */
    private void addRest(int seconds) {
        if (finished || index >= steps.size() || !steps.get(index).rest) {
            return;
        }
        Step s = steps.get(index);
        s.total += seconds;
        s.remaining += seconds;
        deadline += seconds * 1000L;
        paintTime();
        Toast.makeText(this, "休息延长 " + seconds + " 秒", Toast.LENGTH_SHORT).show();
    }

    private void onToggle() {
        if (finished) {
            openResult();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        paused = !paused;
        if (paused) {
            // 暂停时把当前剩余量固化下来，恢复后从这里接着走
            if (leadIn) {
                leadTotal = Math.max(1, leadTotal);
                paintLead((int) Math.max(0, (deadline - now + 999) / 1000));
            } else {
                Step s = steps.get(index);
                s.remaining = (int) Math.max(0, (deadline - now + 999) / 1000);
                paintTime();
                if (resumedAt > 0) {
                    activeMs += Math.max(0, now - resumedAt);
                }
            }
            handler.removeCallbacks(tick);
        } else {
            if (leadIn) {
                // 预备倒计时不算训练时长，恢复后仍旧不计
                resumedAt = 0;
                deadline = now + Math.max(1, leadTotal) * 1000L;
            } else {
                resumedAt = now;
                Step s = steps.get(index);
                deadline = now + Math.max(1, s.remaining) * 1000L;
            }
            schedule();
        }
        iconToggle.setIcon(paused ? IconView.PLAY : IconView.PAUSE);
        tvToggle.setText(paused ? "继续" : "暂停");
        if (leadIn) {
            tvState.setText(paused ? "已暂停" : "预备开始");
            tvState.setTextColor(paused ? Ui.INK_MUTED : Ui.ACCENT);
        } else {
            boolean rest = steps.get(index).rest;
            tvState.setText(paused ? "已暂停" : (rest ? "休息" : "进行中"));
            tvState.setTextColor(paused ? Ui.INK_MUTED : (rest ? Ui.GREEN : Ui.ACCENT));
        }
    }

    private void onSkip() {
        if (finished) {
            return;
        }
        handler.removeCallbacks(tick);
        if (leadIn) {
            // 预备阶段按「跳过」是提前开始，不应该把第一个动作跳过
            leadIn = false;
            paintLeadDone();
            startStep(SystemClock.elapsedRealtime());
            show(index);
            cueStep(index);
            schedule();
            return;
        }
        closeCurrentStep();
        if (index >= steps.size() - 1) {
            complete();
            return;
        }
        index++;
        startStep(SystemClock.elapsedRealtime());
        show(index);
        cueStep(index);
        schedule();
    }

    private void complete() {
        finalSeconds = elapsedSeconds();
        finished = true;
        paused = true;
        leadIn = false;
        handler.removeCallbacks(tick);
        resultMinutes = Math.max(1, (finalSeconds + 59) / 60);
        resultCount = 0;
        resultKcal = 0;
        float kg = Store.latestWeightKg(this);
        for (Step s : steps) {
            // 只算真正练过的秒数：跳过的步骤不该计入消耗，也不该算进「完成几个动作」
            if (s.rest) {
                resultKcal += Math.round(Kcal.burn(kg, s.spent, Kcal.MET_REST));
            } else if (s.spent > 0) {
                resultCount++;
                resultKcal += Math.round(Kcal.burn(kg, s.spent, Kcal.metFor(s.exercise)));
            }
        }
        // 不设下限：全程跳过时 spent 全是 0，消耗就该是 0。
        // 早先这里用「分钟 × 6」兜底，会把「确实没练」当成「没算」，凭空多出几卡。
        // 本次真正练过的动作：写进训练记录（部位分布要用），同时刷新个人纪录
        Set<String> played = new LinkedHashSet<>();
        Map<String, Integer> bestOf = new LinkedHashMap<>();
        for (Step s : steps) {
            if (s.rest || s.exercise == null || s.spent <= 0) {
                continue;
            }
            played.add(s.exercise.id);
            Integer old = bestOf.get(s.exercise.id);
            if (old == null || s.spent > old) {
                bestOf.put(s.exercise.id, s.spent);
            }
        }
        if (!sessionMarked) {
            Store.markDone(this, Calendar.getInstance(), resultMinutes,
                    sessionTitle, resultCount, resultKcal, TextUtils.join(",", played));
            Store.clearPending(this);
            sessionMarked = true;
        }
        if (!recordsCounted) {
            for (Map.Entry<String, Integer> e : bestOf.entrySet()) {
                if (Store.recordPr(this, e.getKey(), e.getValue())) {
                    resultPr++;
                }
            }
            recordsCounted = true;
        }
        paintRestActions(false);
        photo.setAlpha(0.55f);
        ring.setRingColor(Ui.ACCENT);
        // 环从停下的位置补满，再淡入对勾，不要直接跳到 100%
        ring.finish();
        tvState.setText("训练完成");
        tvState.setTextColor(Ui.ACCENT);
        tvExercise.setText("做得不错");
        tvTip.setText("这次约 " + resultMinutes + " 分钟，消耗 " + resultKcal + " 千卡，记得补水。");
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
        it.putExtra(ResultActivity.EXTRA_KCAL, resultKcal);
        it.putExtra(ResultActivity.EXTRA_PR, resultPr);
        if (customIds != null && !customIds.isEmpty()) {
            it.putExtra(EXTRA_IDS, customIds);
            it.putExtra(EXTRA_ROUNDS, customRounds);
            it.putExtra(EXTRA_SECONDS, customSeconds);
            it.putExtra(EXTRA_REST, customRest);
        }
        startActivity(it);
        finish();
    }

    private void toggleFavorite() {
        if (index >= steps.size() || steps.get(index).rest) {
            Toast.makeText(this, "休息中，先练完这一步", Toast.LENGTH_SHORT).show();
            return;
        }
        Exercise e = steps.get(index).exercise;
        boolean on = Store.toggleFavorite(this, e.id);
        paintFavorite();
        Toast.makeText(this, on ? "已收藏：" + e.name : "已取消收藏",
                Toast.LENGTH_SHORT).show();
    }

    private void paintFavorite() {
        if (index >= steps.size()) {
            return;
        }
        Step s = steps.get(index);
        View btn = findViewById(R.id.btn_like);
        if (btn == null) {
            return;
        }
        IconView ic = btn.findViewById(R.id.icon_like);
        boolean on = s.exercise != null && Store.isFavorite(this, s.exercise.id);
        ic.setColor(on ? Ui.ORANGE : Ui.INK_MUTED);
    }

    /** 不喜欢这个动作：直接换一个，本周都按新动作。 */
    private void replaceCurrent() {
        if (finished || index >= steps.size() || steps.get(index).rest) {
            Toast.makeText(this, "休息中，不能换动作", Toast.LENGTH_SHORT).show();
            return;
        }
        showReplace(steps.get(index).exercise);
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

    // ---- 音乐与音频焦点 ----

    private void requestFocus() {
        if (audio == null) {
            audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        }
        if (audio == null) {
            return;
        }
        if (focusRequest == null) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            resumeMusicOnFocus = music != null && music.isPlaying();
                            pauseMusic();
                        } else if (change == AudioManager.AUDIOFOCUS_GAIN && resumeMusicOnFocus) {
                            resumeMusicOnFocus = false;
                            if (!musicManualPaused && !paused) {
                                startMusic(true);
                            }
                        }
                    })
                    .build();
        }
        audio.requestAudioFocus(focusRequest);
    }

    private void releaseFocus() {
        if (audio != null && focusRequest != null) {
            audio.abandonAudioFocusRequest(focusRequest);
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
            requestFocus();
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

    /** 把当前进度写成一份快照，下次从首页「继续上次训练」接着练。 */
    private void savePendingIfNeeded() {
        if (finished || steps.isEmpty()) {
            return;
        }
        int elapsed = elapsedSeconds();
        if (index == 0 && elapsed < 15) {
            // 还没真正开始，不留半截记录
            return;
        }
        closeCurrentStep();
        Store.Pending p = new Store.Pending();
        p.dayIndex = dayIndex;
        p.title = sessionTitle;
        p.single = single;
        p.index = index;
        p.elapsedSeconds = elapsed;
        p.rounds = totalRounds();
        for (Step s : steps) {
            p.ids.add(s.rest ? "" : s.exercise.id);
            p.totals.add(s.total);
            p.remains.add(Math.max(0, s.remaining));
            p.spents.add(Math.max(0, Math.min(s.total, s.spent)));
        }
        Store.savePending(this, p);
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
        sub.setText("选「稍后再做」会记住当前进度，下次接着练。");
        sub.setTextColor(Ui.INK_SOFT);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dpi(this, 6), 0, Ui.dpi(this, 18));
        Ui.font(sub, 400);
        root.addView(sub);

        TextView resume = sheetAction("继续锻炼", Ui.ACCENT, true);
        TextView restart = sheetAction("重新开始此动作", Ui.INK, false);
        TextView later = sheetAction("稍后再做，保存进度", Ui.INK_MUTED, false);
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
            savePendingIfNeeded();
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
        // 弹层贴着屏幕底，得把导航栏那一条让出来，否则最后一个按钮会被切掉
        final int pl = root.getPaddingLeft();
        final int pt = root.getPaddingTop();
        final int pr = root.getPaddingRight();
        final int pb = root.getPaddingBottom();
        root.post(() -> {
            int nav = Nav.navInset(root);
            if (nav > 0) {
                root.setPadding(pl, pt, pr, pb + nav);
            }
        });
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
        releaseFocus();
        if (voice != null) {
            voice.shutdown();
            voice = null;
        }
        super.onDestroy();
    }
}
