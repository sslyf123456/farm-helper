package com.farmhelper.service;

import com.farmhelper.dto.WateringResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 浇水计算器服务
 * 根据作物基础成熟时间和浇水策略，计算实际成熟时间及各节点时刻
 */
@Service
public class WateringCalculatorService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 计算浇水策略的成熟时间
     *
     * @param baseSeconds 基础成熟时间（秒）
     * @param strategy    浇水策略：none, once, diligent, extreme
     * @return 实际成熟秒数
     */
    public int calcMaturitySeconds(int baseSeconds, String strategy) {
        int T = baseSeconds;
        return switch (strategy.toLowerCase()) {
            case "none" -> T;
            case "once" -> (5 * T) / 6;
            case "diligent" -> (3 * T) / 4;
            case "extreme" -> (11 * T) / 15;
            default -> T;
        };
    }

    /**
     * 根据策略类型，计算各浇水时刻（相对于种下时间的秒数）
     *
     * @param baseSeconds 基础成熟时间（秒）
     * @param strategy    浇水策略
     * @return 浇水时刻数组（秒）
     */
    public List<Integer> getWaterTimes(int baseSeconds, String strategy) {
        int T = baseSeconds;
        int W = T / 3; // 满水维持时间
        int wait = T / 15; // 极限策略最后一次等待时间

        return switch (strategy.toLowerCase()) {
            case "none" -> List.of();
            case "once" -> List.of(0, (5 * T) / 6);
            case "diligent" -> List.of(0, W, 2 * W);
            case "extreme" -> List.of(0, W, 2 * W, 2 * W + wait);
            default -> List.of();
        };
    }

    /**
     * 计算某次浇水的减时量
     * 水分从满值 W 线性蒸发，蒸发速率 = 1（每秒消耗 1 秒水分值），无湿润期。
     * 浇水减时 = 消耗水分 / 4 = min(gap, W) / 4
     *
     * @param T   基础成熟时间
     * @param gap 距上次浇水的间隔时间
     * @return 减时量（秒）
     */
    public int calcWaterReduction(int T, int gap) {
        int W = T / 3;
        int consumed = Math.min(gap, W); // 实际消耗水分
        return Math.round((float) consumed / 4);
    }

    /**
     * 浇水减时（固定值）：每次在干涸后浇水，减时 = W/4 = T/12
     *
     * @param T 基础成熟时间
     * @return 减时量（秒）
     */
    public int calcFullReduction(int T) {
        return Math.round((float) T / 12);
    }

    /**
     * 格式化秒数为可读中文
     *
     * @param seconds 秒数
     * @return 格式化字符串，如 "1小时30分钟"
     */
    public String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "秒";
        }

        List<String> parts = new ArrayList<>();
        int remain = seconds;

        int hours = remain / 3600;
        if (hours > 0) {
            parts.add(hours + "小时");
            remain %= 3600;
        }

        int minutes = remain / 60;
        if (minutes > 0) {
            parts.add(minutes + "分钟");
            remain %= 60;
        }

        if (remain > 0 && hours == 0) {
            parts.add(remain + "秒");
        }

        return parts.isEmpty() ? "0秒" : String.join("", parts);
    }

    /**
     * 格式化时间为 "yyyy-MM-dd HH:mm"
     *
     * @param base       基准时间
     * @param offsetSec  偏移秒数
     * @return 格式化的时间字符串，如果 base 为 null 则返回 null
     */
    public String formatTimeFull(LocalDateTime base, int offsetSec) {
        if (base == null) return null;
        LocalDateTime targetTime = base.plusSeconds(offsetSec);
        return targetTime.format(TIME_FORMATTER);
    }

    /**
     * 根据策略生成浇水/收菜节点列表
     *
     * @param baseSeconds    基础成熟时间（秒）
     * @param strategy       浇水策略
     * @param matureSeconds  实际成熟秒数
     * @param plantTime      种植时间（可选）
     * @return 节点列表
     */
    public List<WateringResponse.TimeNode> buildTimeNodes(
            int baseSeconds, String strategy, int matureSeconds, LocalDateTime plantTime) {

        int T = baseSeconds;
        int W = T / 3;
        List<Integer> waterTimes = getWaterTimes(baseSeconds, strategy);
        List<WateringResponse.TimeNode> nodes = new ArrayList<>();

        // extreme 和 once 策略：最后一个浇水点与成熟同时发生，合并展示，循环不含最后一点
        int loopEnd = ("extreme".equals(strategy) || "once".equals(strategy))
                ? waterTimes.size() - 1
                : waterTimes.size();

        int remaining = T; // 剩余成熟时间，逐次减去等待时间与浇水减时

        for (int i = 0; i < loopEnd; i++) {
            int wt = waterTimes.get(i);
            int prevTime = i > 0 ? waterTimes.get(i - 1) : 0;
            int gap = wt - prevTime;

            // 种下立即浇水：土地干涸(w=0)，消耗=W，减时=T/12
            // 之后浇水：上次浇水后满水(w=W)，经过gap时间线性蒸发，消耗=min(gap,W)，减时=消耗/4
            int reduction = i == 0 ? calcFullReduction(T) : calcWaterReduction(T, gap);
            remaining = remaining - gap - reduction;

            String title;
            String timeStr;

            if (i > 0) {
                title = "等" + formatDuration(gap) + "后浇水";
                timeStr = formatTimeFull(plantTime, wt);
            } else {
                title = "种下立即浇水";
                timeStr = formatTimeFull(plantTime, 0); // 第一个节点显示种植时间
            }

            String desc = "浇水减" + formatDuration(reduction)
                    + "，剩余" + formatDuration(remaining)
                    + "，水分可以维持" + formatDuration(W);

            nodes.add(new WateringResponse.TimeNode(
                    nodes.size() + 1, title, desc, wt, timeStr, false));
        }

        // 最终收菜节点
        int lastWaterTime = waterTimes.isEmpty() ? 0 : waterTimes.get(waterTimes.size() - 1);
        int finalGap = matureSeconds - lastWaterTime;

        String harvestTitle;
        String harvestDesc;

        if ("none".equals(strategy)) {
            harvestTitle = "自然成熟";
            harvestDesc = "不浇水，等待自然成熟";
        } else if ("extreme".equals(strategy)) {
            // 极限：最后一步是浇水即熟，合并展示
            int waitSec = waterTimes.get(waterTimes.size() - 1) - waterTimes.get(waterTimes.size() - 2);
            int reduction = calcWaterReduction(T, waitSec);
            harvestTitle = "等" + formatDuration(waitSec) + "后浇水秒熟";
            harvestDesc = "浇水减" + formatDuration(reduction) + "，直接成熟";
        } else if ("once".equals(strategy)) {
            // 佛系：水分先蒸发完，再等一段时间后浇水直接成熟
            int waitSec = waterTimes.get(waterTimes.size() - 1) - waterTimes.get(waterTimes.size() - 2);
            int dryWait = Math.max(0, waitSec - W); // 蒸发完后额外等待的时间
            int reduction = calcWaterReduction(T, waitSec);
            harvestTitle = "等" + formatDuration(waitSec) + "后浇水秒熟";

            if (dryWait > 60) {
                harvestDesc = "水分在" + formatDuration(W) + "后蒸发完，再等"
                        + formatDuration(dryWait) + "，浇水减" + formatDuration(reduction) + "直接成熟";
            } else {
                harvestDesc = "浇水减" + formatDuration(reduction) + "，直接成熟";
            }
        } else if ("diligent".equals(strategy)) {
            // 勤奋：最后等剩余时间自然成熟
            harvestTitle = "再等" + formatDuration(remaining) + "后自然成熟";
            harvestDesc = "不需要浇水，直接自然成熟";
        } else if (finalGap > 60) {
            int reduction = calcWaterReduction(T, finalGap);
            if (reduction > 0) {
                harvestTitle = "再等" + formatDuration(finalGap) + "后浇水秒熟";
                harvestDesc = "浇水减" + formatDuration(reduction) + "，直接成熟";
            } else {
                harvestTitle = "再等" + formatDuration(finalGap) + "后自然成熟";
                harvestDesc = "不需要浇水，直接自然成熟";
            }
        } else {
            harvestTitle = "直接成熟";
            harvestDesc = "减时" + formatDuration(calcWaterReduction(T, finalGap)) + "，直接成熟";
        }

        nodes.add(new WateringResponse.TimeNode(
                nodes.size() + 1, harvestTitle, harvestDesc,
                matureSeconds, formatTimeFull(plantTime, matureSeconds), true));

        return nodes;
    }

    /**
     * 获取策略标签
     */
    public String getStrategyLabel(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "none" -> "自然成熟";
            case "once" -> "佛系浇水";
            case "diligent" -> "勤奋浇水";
            case "extreme" -> "极限浇水";
            default -> "未知策略";
        };
    }

    /**
     * 获取策略描述
     */
    public String getStrategyDesc(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "none" -> "不浇水，耗时最长";
            case "once" -> "共浇水2次，缩短约16.7%的成熟时间";
            case "diligent" -> "共浇水3次，缩短25%的成熟时间";
            case "extreme" -> "共浇水4次，缩短约26.7%的成熟时间";
            default -> "";
        };
    }
}
