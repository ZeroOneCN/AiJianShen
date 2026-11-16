package com.aijianshen.app;

import android.content.Context;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 挑战：固定几周、每周练够几次就算通关。
 *
 * <p>进度不单独记录，直接从已有的训练日期里算——用户不需要为挑战再"打卡"一次，
 * 平时练的记录会自动算进来。
 */
public final class Challenges {

    public static class Def {
        public final String id;
        public final String name;
        public final String tagline;
        public final int weeks;
        public final int perWeek;
        public final int color;
        public final int icon;

        Def(String id, String name, String tagline, int weeks, int perWeek, int color, int icon) {
            this.id = id;
            this.name = name;
            this.tagline = tagline;
            this.weeks = weeks;
            this.perWeek = perWeek;
            this.color = color;
            this.icon = icon;
        }

        public int totalDays() {
            return weeks * 7;
        }

        public String spec() {
            return "共 " + weeks + " 周 · 每周 " + perWeek + " 次";
        }
    }

    public static class Progress {
        public int weeksDone;
        /** 今天落在第几周，0 起。 */
        public int currentWeek;
        public int thisWeek;
        public int daysLeft;
        public boolean done;
    }

    private Challenges() {
    }

    private static final List<Def> ALL = new ArrayList<>();

    static {
        ALL.add(new Def("warm", "7 天热身", "先养成一周三次的节奏",
                1, 3, Ui.ORANGE, IconView.BOLT));
        ALL.add(new Def("habit", "21 天习惯", "三周之后，训练开始变成习惯",
                3, 3, Ui.BLUE_TILE, IconView.FLAME));
        ALL.add(new Def("build", "四周进阶", "一周四练，把训练量稳稳堆上去",
                4, 4, Ui.PURPLE, IconView.TROPHY));
        ALL.add(new Def("core", "14 天核心", "两周核心，每天十分钟就够",
                2, 5, Ui.GREEN, IconView.TARGET));
    }

    public static List<Def> all() {
        return ALL;
    }

    public static Def byId(String id) {
        for (Def d : ALL) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return null;
    }

    /** 按开始日期和已有训练记录算进度。 */
    public static Progress progress(Context c, Def def, long start) {
        Progress p = new Progress();
        if (def == null || start <= 0) {
            return p;
        }
        LocalDate startDay = LocalDate.ofEpochDay(start / 86400000L);
        LocalDate today = LocalDate.now();
        long elapsed = ChronoUnit.DAYS.between(startDay, today);
        int total = def.totalDays();
        p.daysLeft = (int) Math.max(0, total - elapsed);
        int currentWeek = (int) Math.max(0, Math.min(def.weeks - 1, elapsed / 7));
        p.currentWeek = currentWeek;

        int[] count = new int[def.weeks];
        for (String date : Store.dates(c)) {
            LocalDate d;
            try {
                d = LocalDate.parse(date);
            } catch (Exception e) {
                continue;
            }
            long offset = ChronoUnit.DAYS.between(startDay, d);
            if (offset < 0 || offset >= total) {
                continue;
            }
            count[(int) (offset / 7)]++;
        }
        for (int i = 0; i < def.weeks; i++) {
            if (count[i] >= def.perWeek) {
                p.weeksDone++;
            }
        }
        p.thisWeek = count[currentWeek];
        p.done = p.weeksDone >= def.weeks;
        return p;
    }
}
