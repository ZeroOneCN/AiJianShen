package com.aijianshen.app;

import android.app.Activity;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * 3 分钟接上。
 *
 * <p>断签的人不缺计划，缺的是一个「现在就能做完」的动作。这里只取 4 个动作、
 * 每个 40 秒、休息 10 秒，一共三分钟出头。定位不是训练，是别断——
 * 所以宁可短，也不要让他站在「今天要不要练 15 分钟」这个问题前面。
 */
public final class Rescue {

    public static final String TITLE = "3 分钟接上";
    private static final int COUNT = 4;
    private static final int SECONDS = 40;
    private static final int REST = 10;

    private Rescue() {
    }

    /** 优先用今天课表里的动作，让「接上」和计划对得上；不够再补徒手动作。 */
    public static void start(Activity a) {
        Data.init(a);
        List<Exercise> pool = new ArrayList<>();
        try {
            List<Data.Day> plan = Data.weekPlan(Store.profile(a));
            Data.Day today = plan.get(Data.todayIndex(Calendar.getInstance()));
            if (today != null) {
                pool.addAll(today.exercises);
            }
        } catch (Exception ignored) {
            // 计划读不出来也要能接上，后面补徒手动作兜底
        }
        if (pool.size() < COUNT) {
            List<Exercise> spare = new ArrayList<>();
            for (Exercise e : Data.all()) {
                if (!e.needsGear() && !pool.contains(e)) {
                    spare.add(e);
                }
            }
            Collections.shuffle(spare);
            for (Exercise e : spare) {
                if (pool.size() >= COUNT) {
                    break;
                }
                pool.add(e);
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(COUNT, pool.size()); i++) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(pool.get(i).id);
        }
        Intent it = new Intent(a, WorkoutActivity.class);
        it.putExtra(WorkoutActivity.EXTRA_IDS, sb.toString());
        it.putExtra(WorkoutActivity.EXTRA_ROUNDS, 1);
        it.putExtra(WorkoutActivity.EXTRA_SECONDS, SECONDS);
        it.putExtra(WorkoutActivity.EXTRA_REST, REST);
        it.putExtra(WorkoutActivity.EXTRA_TITLE, TITLE);
        a.startActivity(it);
    }
}
