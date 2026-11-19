package com.aijianshen.app;

import android.content.Context;

import java.util.Calendar;
import java.util.List;

/**
 * 练完给一句话的「下一步」。
 *
 * 这不是推荐算法，是一条本地规则：只看这次的感受、本周的出勤、有没有破纪录。
 * 规则少、口径固定，用户才能预期它为什么这么说——这也正是纯本地应用能给的确定性。
 */
public final class Coach {

    public static class Advice {
        public final String title;
        public final String body;

        Advice(String title, String body) {
            this.title = title;
            this.body = body;
        }
    }

    private Coach() {
    }

    /**
     * @param feel   0 太轻松 / 1 刚刚好 / 2 太吃力；< 0 表示还没选
     * @param motion 本次的第一个动作名，用于把建议说具体
     * @param pr     本次刷新的纪录数
     */
    public static Advice forResult(Context c, int feel, String motion, int pr) {
        if (feel < 0) {
            return null;
        }
        String name = motion == null || motion.isEmpty() ? "主项动作" : "「" + motion + "」";

        PlanProfile p = Store.profile(c);
        int today = Data.todayIndex(Calendar.getInstance());
        int done = Store.weekDone(c, today);
        int plan = Math.max(1, p.days);
        boolean onTrack = done >= plan;
        int tough = toughStreak(c);

        if (feel == 2 && tough >= 3) {
            return new Advice("连着几次偏吃力，先把量减下来",
                    "下周每次压到 10 分钟，动作不变。练得住比练得狠重要，"
                            + "这周先别加码。");
        }
        if (feel == 2) {
            return new Advice("下周三先降一档",
                    name + "的秒数减 5 秒，做稳了再加回来。"
                            + "觉得吃力不是意志问题，是这一档确实高了。");
        }
        if (feel == 0 && !onTrack) {
            int left = Math.max(0, plan - done);
            return new Advice("先把本周的出勤补齐",
                    "还差 " + left + " 次。这会儿加时长，很容易练一天歇三天，"
                            + "先把次数补齐再说。");
        }
        if (feel == 0 && onTrack) {
            return new Advice("下周三给" + name + "加 5 秒",
                    "这周的出勤已经够了，加量不会挤掉恢复。轮次不变，"
                            + "只动秒数。");
        }
        if (pr > 0) {
            return new Advice("这次刷新了 " + pr + " 个纪录",
                    name + "已经比上次强了。下周维持这个强度，"
                            + "把组间休息缩短 5 秒，比继续加时长划算。");
        }
        return new Advice("节奏刚好，下周维持",
                "强度不用动。想再挤出一点效果，把组间休息缩短 5 秒，"
                        + "同样的时间能多练一组。");
    }

    /** 最近连续几次都是「太吃力」。不看日期，只看最近三条有感受的记录。 */
    private static int toughStreak(Context c) {
        List<Store.Session> log = Store.sessions(c);
        int n = 0;
        for (int i = log.size() - 1; i >= 0 && n < 3; i--) {
            Store.Session s = log.get(i);
            if (s.feel < 0) {
                continue;
            }
            if (s.feel != 2) {
                break;
            }
            n++;
        }
        return n;
    }
}
