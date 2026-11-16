package com.aijianshen.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 成就徽章。全部按本机已有的记录算，不需要额外埋点：
 * 连续天数、训练日、累计分钟与消耗来自 Store，练过多少动作来自个人纪录表，
 * 收藏与体重来自各自的集合。
 */
public final class Achievements {

    public static class Badge {
        public final String group;
        public final String title;
        public final String desc;
        public final int icon;
        public final int color;
        public final int target;
        public final int value;
        public final String unit;

        Badge(String group, String title, String desc, int icon, int color,
              int target, int value, String unit) {
            this.group = group;
            this.title = title;
            this.desc = desc;
            this.icon = icon;
            this.color = color;
            this.target = Math.max(1, target);
            this.value = Math.max(0, value);
            this.unit = unit;
        }

        public boolean earned() {
            return value >= target;
        }

        /** 0–1，用于进度条。 */
        public float progress() {
            return Math.min(1f, value / (float) target);
        }

        public String progressText() {
            if (earned()) {
                return "已达成";
            }
            return Math.min(value, target) + " / " + target + " " + unit;
        }
    }

    private Achievements() {
    }

    public static List<Badge> all(Context c) {
        List<Badge> out = new ArrayList<>();

        // 这里只读一次记录和个人纪录表：之前 goal/合计/早晚各查一遍，
        // 首页每次 onResume 都要把同一份 JSON 解析六七次。
        List<Store.Session> log = Store.sessions(c);
        Map<String, Store.Pr> prs = Store.prs(c);

        int days = Store.daysTrained(c);
        int best = Store.bestStreak(c);
        int minutes = Store.totalMinutes(c);
        int kcal = Store.totalKcal(log);
        int favs = Store.favoriteIds(c).size();
        int moves = prs.size();
        int weights = Store.weights(c).size();
        int parts = trainedParts(prs);
        int week = Store.weekDone(c, Data.todayIndex(Calendar.getInstance()));
        int plan = Math.max(2, Store.profile(c).days);
        boolean dawn = trainedAtHour(log, 5, 9);
        boolean night = trainedAtHour(log, 21, 24);

        // 起步：第一次走到终点
        out.add(new Badge("起步", "动起来", "完成第一次训练", IconView.PLAY,
                Ui.GREEN, 1, days, "次"));
        out.add(new Badge("起步", "三日不断", "连续训练 3 天", IconView.FLAME,
                Ui.ORANGE, 3, best, "天"));
        out.add(new Badge("起步", "一周不断", "连续训练 7 天", IconView.FLAME,
                Ui.RED, 7, best, "天"));
        out.add(new Badge("起步", "一月不断", "连续训练 30 天", IconView.TROPHY,
                Ui.PURPLE, 30, best, "天"));

        // 坚持：训练日与满勤
        out.add(new Badge("坚持", "十个训练日", "累计 10 天完成训练", IconView.CALENDAR,
                Ui.BLUE_TILE, 10, days, "天"));
        out.add(new Badge("坚持", "五十个训练日", "累计 50 天完成训练", IconView.CALENDAR,
                Ui.SLATE, 50, days, "天"));
        out.add(new Badge("坚持", "满勤一周", "本周完成次数达到计划的 " + plan + " 次",
                IconView.CHECK, Ui.GREEN, plan, week, "次"));
        out.add(new Badge("坚持", "早起的人", "在早上 5–9 点完成一次训练", IconView.CLOCK,
                Ui.ORANGE, 1, dawn ? 1 : 0, "次"));
        out.add(new Badge("坚持", "夜练者", "在晚上 21 点后完成一次训练", IconView.CLOCK,
                Ui.PURPLE, 1, night ? 1 : 0, "次"));

        // 累计：时间与消耗
        out.add(new Badge("累计", "一百分钟", "累计训练 100 分钟", IconView.BOLT,
                Ui.GREEN, 100, minutes, "分钟"));
        out.add(new Badge("累计", "十小时", "累计训练 600 分钟", IconView.BOLT,
                Ui.BLUE_TILE, 600, minutes, "分钟"));
        out.add(new Badge("累计", "两千千卡", "累计消耗 2000 千卡", IconView.FLAME,
                Ui.RED, 2000, kcal, "千卡"));
        out.add(new Badge("累计", "五千千卡", "累计消耗 5000 千卡", IconView.FLAME,
                Ui.ORANGE, 5000, kcal, "千卡"));

        // 探索：动作广度与记录习惯
        out.add(new Badge("探索", "练过三十个动作", "不同动作各练一组", IconView.COMPASS,
                Ui.TEAL, 30, moves, "个"));
        out.add(new Badge("探索", "全身都练", "六个部位都练到过", IconView.TARGET,
                Ui.PURPLE, 6, parts, "个部位"));
        out.add(new Badge("探索", "收藏家", "收藏 10 个动作", IconView.STAR,
                Ui.ORANGE, 10, favs, "个"));
        out.add(new Badge("探索", "记录体重", "记录 10 次体重", IconView.SCALE,
                Ui.BLUE_TILE, 10, weights, "次"));
        return out;
    }

    public static int earned(Context c) {
        int n = 0;
        for (Badge b : all(c)) {
            if (b.earned()) {
                n++;
            }
        }
        return n;
    }

    /** 最近一次达成的徽章标题，没有则空串（首页用来做一句话提示）。 */
    public static String nextHint(Context c) {
        return nextHint(all(c));
    }

    /** 已经算好徽章列表时用这个重载，省一次全量统计。 */
    public static String nextHint(List<Badge> list) {
        Badge nearest = null;
        for (Badge b : list) {
            if (b.earned()) {
                continue;
            }
            if (nearest == null || b.progress() > nearest.progress()) {
                nearest = b;
            }
        }
        return nearest == null ? "" : "离「" + nearest.title + "」还差 "
                + (nearest.target - nearest.value) + " " + nearest.unit;
    }

    /** 个人纪录表里有记录的动作，覆盖了几个部位。 */
    private static int trainedParts(Map<String, Store.Pr> prs) {
        Set<String> parts = new HashSet<>();
        for (Map.Entry<String, Store.Pr> e : prs.entrySet()) {
            Exercise ex = Data.byId(e.getKey());
            if (ex != null && ex.part != null && !ex.part.isEmpty()) {
                parts.add(ex.part);
            }
        }
        return parts.size();
    }

    /** [from, to) 点之间有没有完成过训练。 */
    private static boolean trainedAtHour(List<Store.Session> log, int from, int to) {
        Calendar cal = Calendar.getInstance();
        for (Store.Session s : log) {
            if (s.ts <= 0) {
                continue;
            }
            cal.setTimeInMillis(s.ts);
            int h = cal.get(Calendar.HOUR_OF_DAY);
            if (h >= from && h < to) {
                return true;
            }
        }
        return false;
    }
}
