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
     * @return 实际成熟秒数（向上取整）
     */
    public int calcMaturitySeconds(int baseSeconds, String strategy) {
        double T = baseSeconds;
        return switch (strategy.toLowerCase()) {
            case "none" -> baseSeconds;
            case "once" -> (int) Math.ceil((5.0 * T) / 6.0);
            case "diligent" -> (int) Math.ceil((3.0 * T) / 4.0);
            case "extreme" -> (int) Math.ceil((11.0 * T) / 15.0);
            default -> baseSeconds;
        };
    }

    /**
     * 根据策略类型，计算各浇水时刻（相对于种下时间的秒数，向上取整）
     *
     * @param baseSeconds 基础成熟时间（秒）
     * @param strategy    浇水策略
     * @return 浇水时刻数组（秒）
     */
    public List<Integer> getWaterTimes(int baseSeconds, String strategy) {
        double T = baseSeconds;
        int W = (int) Math.ceil(T / 3.0); // 满水维持时间（向上取整）
        int wait = (int) Math.ceil(T / 15.0); // 极限策略最后一次等待时间（向上取整）

        return switch (strategy.toLowerCase()) {
            case "none" -> List.of();
            case "once" -> List.of(0, (int) Math.ceil((5.0 * T) / 6.0));
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
     * @return 减时量（秒，向上取整）
     */
    public int calcWaterReduction(int T, int gap) {
        double W = T / 3.0;
        double consumed = Math.min(gap, W); // 实际消耗水分
        return (int) Math.ceil(consumed / 4.0);
    }

    /**
     * 浇水减时（固定值）：每次在干涸后浇水，减时 = W/4 = T/12（向上取整）
     *
     * @param T 基础成熟时间
     * @return 减时量（秒，向上取整）
     */
    public int calcFullReduction(int T) {
        return (int) Math.ceil(T / 12.0);
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
        int W = (int) Math.ceil(T / 3.0);
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
            String desc;

            if (i > 0) {
                title = "等" + formatDuration(gap) + "后浇水";
                timeStr = formatTimeFull(plantTime, wt);
                // 如果gap >= W，说明水分已经干涸
                if (gap >= W) {
                    desc = "水分干涸，浇水减" + formatDuration(reduction)
                            + "，剩余" + formatDuration(remaining) + "成熟"
                            + "，水分可以维持" + formatDuration(W);
                } else {
                    desc = "浇水减" + formatDuration(reduction)
                            + "，剩余" + formatDuration(remaining) + "成熟"
                            + "，水分可以维持" + formatDuration(W);
                }
            } else {
                title = "种下立即浇水";
                timeStr = formatTimeFull(plantTime, 0); // 第一个节点显示种植时间
                desc = "浇水减" + formatDuration(reduction)
                        + "，剩余" + formatDuration(remaining) + "成熟"
                        + "，水分可以维持" + formatDuration(W);
            }

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
            // 极限：最后一步是浇水即熟，合并展示（同佛系一样描述水分状态）
            int waitSec = waterTimes.get(waterTimes.size() - 1) - waterTimes.get(waterTimes.size() - 2);
            int dryWait = Math.max(0, waitSec - W); // 蒸发完后额外等待的时间
            int remainingMoisture = Math.max(0, W - waitSec); // 浇水时剩余水分
            int reduction = calcWaterReduction(T, waitSec);
            harvestTitle = "等" + formatDuration(waitSec) + "后浇水秒熟";
            if (dryWait > 60) {
                harvestDesc = "水分在" + formatDuration(W) + "后蒸发完，再等"
                        + formatDuration(dryWait) + "，浇水减" + formatDuration(reduction) + "直接成熟";
            } else if (remainingMoisture > 0) {
                harvestDesc = "浇水时还有" + formatDuration(remainingMoisture) + "水分，浇水减" + formatDuration(reduction) + "直接成熟";
            } else {
                harvestDesc = "浇水减" + formatDuration(reduction) + "，直接成熟";
            }
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

    /**
     * 反向计算策略描述（精简版）
     */
    public String getReverseStrategyDesc(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "none" -> "完全不浇水";
            case "once" -> "当前可浇水就立即浇，后面只浇最后一次卡秒熟";
            case "extreme" -> "当前可浇水就立即浇，后面每次干涸后浇水，最后一次卡秒熟";
            default -> "";
        };
    }

    /**
     * 求解浇水等待时间：满足 gap + ceil(min(gap, W) / 4) = target
     * 用于反向计算中确定最后一次浇水的时机
     *
     * @param target 目标剩余时间
     * @param W 满水维持时间
     * @return 等待时间gap
     */
    private int solveWateringGap(int target, int W) {
        // 使用迭代精确求解
        // 需要找到 gap，使得 gap + ceil(min(gap, W) / 4) = target
        
        // 如果 gap <= W，则 gap + ceil(gap/4) = target
        // 如果 gap > W，则 gap + ceil(W/4) = target
        
        int wReduction = (int) Math.ceil(W / 4.0);
        
        // 先尝试 gap > W 的情况
        if (target > W + wReduction) {
            int gap = target - wReduction;
            int r = (int) Math.ceil(Math.min(gap, W) / 4.0);
            if (gap + r == target) {
                return gap;
            }
        }
        
        // 尝试 gap <= W 的情况，从大到小搜索以找到最接近的解
        for (int gap = target; gap >= 1; gap--) {
            int r = (int) Math.ceil(Math.min(gap, W) / 4.0);
            if (gap + r == target) {
                return gap;
            }
        }
        
        // 如果找不到精确解，返回近似值（4/5乘以target，向上取整）
        return (int) Math.ceil(target * 4.0 / 5.0);
    }

    /**
     * 反向模式：构建未来操作节点列表
     * 
     * 从当前状态出发，基于剩余成熟时间和水分状态，生成接下来要做的操作步骤。
     * 
     * 策略说明：
     * - none（自然成熟）：完全不浇水
     * - once（佛系浇水）：当前可浇水立即浇，如果浇后未熟，后面只浇最后一次水，卡时间刚好浇水后秒熟
     * - extreme（极限浇水）：当前可浇水立即浇，如果浇后未熟，后面每次干涸后浇水，最后一次成熟前的浇水恰好卡浇水后秒熟
     *
     * @param baseSeconds 作物基础成熟时间（秒）
     * @param strategy 浇水策略：none, once, extreme
     * @param remainingSeconds 剩余成熟时间（秒，相对于当前时间）
     * @param moistureSeconds 当前水分还能维持的时间（秒）
     * @param currentTime 当前时间点
     * @return 节点列表（从当前开始的操作步骤）
     */
    public List<WateringResponse.TimeNode> buildReverseTimeNodes(
            int baseSeconds, String strategy, int remainingSeconds,
            int moistureSeconds, LocalDateTime currentTime) {

        int T = baseSeconds;
        int W = (int) Math.ceil(T / 3.0); // 满水维持时间
        int waterThreshold = (int) Math.ceil(T / 30.0); // 浇水阈值：C >= T/30

        List<WateringResponse.TimeNode> nodes = new ArrayList<>();
        int nodeIndex = 1;

        switch (strategy.toLowerCase()) {
            case "none" -> {
                // 自然成熟策略：完全不浇水
                nodes.add(new WateringResponse.TimeNode(
                        nodeIndex, "不浇水，自然成熟",
                        "剩余" + formatDuration(remainingSeconds) + "后成熟",
                        remainingSeconds,
                        formatTimeFull(currentTime, remainingSeconds), true));
            }
            case "once" -> {
                // 佛系浇水策略：当前可浇水就立即浇，如果浇后未熟，后面只浇最后一次水卡秒熟
                
                // 计算当前消耗的水分（W - moistureSeconds）
                int consumedWater = Math.max(0, W - moistureSeconds);
                
                // 检查是否达到浇水阈值
                if (consumedWater >= waterThreshold) {
                    // 可以立即浇水
                    int r1 = (int) Math.ceil(Math.min(consumedWater, W) / 4.0);
                    int afterFirst = remainingSeconds - r1;
                    
                    // 浇水后直接成熟
                    if (afterFirst <= 0) {
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "立即浇水秒熟",
                                "浇水减" + formatDuration(r1) + "，直接成熟",
                                0,
                                formatTimeFull(currentTime, 0), true));
                        break;
                    }
                    
                    nodes.add(new WateringResponse.TimeNode(
                            nodeIndex++, "立即浇水",
                            "浇水减" + formatDuration(r1)
                                    + "，剩余" + formatDuration(afterFirst) + "成熟"
                                    + "，水分可以维持" + formatDuration(W),
                            0,
                            formatTimeFull(currentTime, 0), false));
                    
                    // 计算最后一次浇水的时间点，使得浇水后刚好成熟
                    if (afterFirst >= waterThreshold) {
                        int gap = solveWateringGap(afterFirst, W);
                        int r2 = (int) Math.ceil(Math.min(gap, W) / 4.0);
                        
                        // 佛系浇水：详细描述水分状态和等待过程
                        String desc;
                        if (gap >= W) {
                            // 水分完全蒸发后还要等待
                            int dryWait = gap - W;
                            if (dryWait > 60) {
                                desc = "水分在" + formatDuration(W) + "后蒸发完，再等"
                                        + formatDuration(dryWait) + "，浇水减" + formatDuration(r2) + "直接成熟";
                            } else {
                                desc = "水分在" + formatDuration(W) + "后蒸发完，浇水减" + formatDuration(r2) + "直接成熟";
                            }
                        } else {
                            // 水分还没完全蒸发就浇水，说明剩余时间较短
                            int remainingMoisture = W - gap;
                            desc = "浇水时还有" + formatDuration(remainingMoisture) + "水分，浇水减" + formatDuration(r2) + "直接成熟";
                        }
                        
                        // 注意：第二次浇水的offsetSeconds应该是从当前时间开始的累计时间
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "等" + formatDuration(gap) + "后浇水秒熟",
                                desc,
                                gap,  // 这里gap就是从立即浇水(时间0)开始算的等待时间
                                formatTimeFull(currentTime, gap), true));
                    } else {
                        // 剩余时间不足以满足浇水阈值，自然成熟
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "等" + formatDuration(afterFirst) + "后自然成熟",
                                "剩余时间不足浇水阈值，自然成熟",
                                afterFirst,
                                formatTimeFull(currentTime, afterFirst), true));
                    }
                } else {
                    // 当前不可浇水，需要等待达到浇水阈值
                    int waitToThreshold = waterThreshold - consumedWater;
                    int remainingAfterWait = remainingSeconds - waitToThreshold;
                    
                    if (remainingAfterWait >= waterThreshold) {
                        // 等到可浇水时，浇一次水
                        int r1 = (int) Math.ceil(waterThreshold / 4.0);
                        int afterWater = remainingAfterWait - r1;
                        
                        // 浇水后直接成熟
                        if (afterWater <= 0) {
                            nodes.add(new WateringResponse.TimeNode(
                                    nodeIndex, "等" + formatDuration(waitToThreshold) + "后浇水秒熟",
                                    "达到浇水阈值，浇水减" + formatDuration(r1) + "，直接成熟",
                                    waitToThreshold,
                                    formatTimeFull(currentTime, waitToThreshold), true));
                            break;
                        }
                        
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex++, "等" + formatDuration(waitToThreshold) + "后浇水",
                                "达到浇水阈值，浇水减" + formatDuration(r1)
                                        + "，剩余" + formatDuration(afterWater) + "成熟",
                                waitToThreshold,
                                formatTimeFull(currentTime, waitToThreshold), false));
                        
                        // 计算最后一次浇水
                        if (afterWater >= waterThreshold) {
                            int gap = solveWateringGap(afterWater, W);
                            int r2 = (int) Math.ceil(Math.min(gap, W) / 4.0);
                            
                            // 佛系浇水：详细描述水分状态和等待过程
                            String desc;
                            if (gap >= W) {
                                // 水分完全蒸发后还要等待
                                int dryWait = gap - W;
                                if (dryWait > 60) {
                                    desc = "水分在" + formatDuration(W) + "后蒸发完，再等"
                                            + formatDuration(dryWait) + "，浇水减" + formatDuration(r2) + "直接成熟";
                                } else {
                                    desc = "水分在" + formatDuration(W) + "后蒸发完，浇水减" + formatDuration(r2) + "直接成熟";
                                }
                            } else {
                                // 水分还没完全蒸发就浇水
                                int remainingMoisture = W - gap;
                                desc = "浇水时还有" + formatDuration(remainingMoisture) + "水分，浇水减" + formatDuration(r2) + "直接成熟";
                            }
                            
                            nodes.add(new WateringResponse.TimeNode(
                                    nodeIndex, "等" + formatDuration(gap) + "后浇水秒熟",
                                    desc,
                                    waitToThreshold + gap,
                                    formatTimeFull(currentTime, waitToThreshold + gap), true));
                        } else {
                            nodes.add(new WateringResponse.TimeNode(
                                    nodeIndex, "等" + formatDuration(afterWater) + "后自然成熟",
                                    "剩余时间不足浇水阈值，自然成熟",
                                    waitToThreshold + afterWater,
                                    formatTimeFull(currentTime, waitToThreshold + afterWater), true));
                        }
                    } else {
                        // 等到可浇水时已经接近成熟，不浇水
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "不浇水，自然成熟",
                                "剩余" + formatDuration(remainingSeconds) + "后成熟",
                                remainingSeconds,
                                formatTimeFull(currentTime, remainingSeconds), true));
                    }
                }
            }
            case "extreme" -> {
                // 极限浇水策略：当前可浇水就立即浇，如果浇后未熟，后面每次干涸后浇水，最后一次卡秒熟
                
                int consumedWater = Math.max(0, W - moistureSeconds);
                int cumulativeTime = 0; // 累计时间偏移
                int remaining = remainingSeconds;
                
                // 第一次浇水（如果可以）
                if (consumedWater >= waterThreshold) {
                    int r1 = (int) Math.ceil(Math.min(consumedWater, W) / 4.0);
                    remaining -= r1;
                    
                    // 浇水后直接成熟
                    if (remaining <= 0) {
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "立即浇水秒熟",
                                "浇水减" + formatDuration(r1) + "，直接成熟",
                                cumulativeTime,
                                formatTimeFull(currentTime, cumulativeTime), true));
                        break;
                    }
                    
                    nodes.add(new WateringResponse.TimeNode(
                            nodeIndex++, "立即浇水",
                            "浇水减" + formatDuration(r1)
                                    + "，剩余" + formatDuration(remaining) + "成熟"
                                    + "，水分可以维持" + formatDuration(W),
                            cumulativeTime,
                            formatTimeFull(currentTime, cumulativeTime), false));
                } else {
                    // 需要等待达到浇水阈值
                    // 如果等待时间超过剩余成熟时间，直接自然成熟更快
                    int waitToThreshold = waterThreshold - consumedWater;
                    if (waitToThreshold >= remainingSeconds) {
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "不浇水，自然成熟",
                                "剩余" + formatDuration(remainingSeconds) + "后成熟",
                                remainingSeconds,
                                formatTimeFull(currentTime, remainingSeconds), true));
                        break;
                    }
                    
                    cumulativeTime += waitToThreshold;
                    remaining -= waitToThreshold;
                    
                    int r1 = (int) Math.ceil(waterThreshold / 4.0);
                    remaining -= r1;
                    
                    // 浇水后直接成熟
                    if (remaining <= 0) {
                        nodes.add(new WateringResponse.TimeNode(
                                nodeIndex, "等" + formatDuration(waitToThreshold) + "后浇水秒熟",
                                "达到浇水阈值，浇水减" + formatDuration(r1) + "，直接成熟",
                                cumulativeTime,
                                formatTimeFull(currentTime, cumulativeTime), true));
                        break;
                    }
                    
                    nodes.add(new WateringResponse.TimeNode(
                            nodeIndex++, "等" + formatDuration(waitToThreshold) + "后浇水",
                            "达到浇水阈值，浇水减" + formatDuration(r1)
                                    + "，剩余" + formatDuration(remaining) + "成熟"
                                    + "，水分可以维持" + formatDuration(W),
                            cumulativeTime,
                            formatTimeFull(currentTime, cumulativeTime), false));
                }
                
                // 后续浇水：每次等水干涸（等W）后浇水
                // 循环条件：remaining 扣掉本次"等干涸(W) + 浇水减时(r)"之后，
                // 剩余时间仍 >= waterThreshold，才说明还值得继续循环（后面还有卡秒熟机会）
                int fullReduction = calcFullReduction(T);
                while (remaining > W + fullReduction + waterThreshold) {
                    // 等待W秒后水分干涸
                    cumulativeTime += W;
                    remaining -= W;
                    
                    // 干涸后浇水
                    int r = fullReduction; // T/12
                    remaining -= r;
                    
                    nodes.add(new WateringResponse.TimeNode(
                            nodeIndex++, "等" + formatDuration(W) + "后浇水",
                            "水分干涸，浇水减" + formatDuration(r)
                                    + "，剩余" + formatDuration(remaining) + "成熟"
                                    + "，水分可以维持" + formatDuration(W),
                            cumulativeTime,
                            formatTimeFull(currentTime, cumulativeTime), false));
                }
                
                // 最后一次浇水：卡时间刚好浇水后秒熟（如果剩余时间 >= 浇水阈值）
                if (remaining >= waterThreshold) {
                    int gap = solveWateringGap(remaining, W);
                    int r = (int) Math.ceil(Math.min(gap, W) / 4.0);
                    cumulativeTime += gap;

                    // 描述浇水时的水分状态
                    String lastDesc;
                    int dryWait = Math.max(0, gap - W);
                    int remainingMoisture = Math.max(0, W - gap);
                    if (dryWait > 60) {
                        lastDesc = "水分在" + formatDuration(W) + "后蒸发完，再等"
                                + formatDuration(dryWait) + "，浇水减" + formatDuration(r) + "直接成熟";
                    } else if (remainingMoisture > 0) {
                        lastDesc = "浇水时还有" + formatDuration(remainingMoisture) + "水分，浇水减" + formatDuration(r) + "直接成熟";
                    } else {
                        lastDesc = "浇水减" + formatDuration(r) + "，直接成熟";
                    }

                    nodes.add(new WateringResponse.TimeNode(
                            nodeIndex, "等" + formatDuration(gap) + "后浇水秒熟",
                            lastDesc,
                            cumulativeTime,
                            formatTimeFull(currentTime, cumulativeTime), true));
                } else {
                    // 剩余时间不足浇水阈值，自然成熟
                    cumulativeTime += remaining;
                    nodes.add(new WateringResponse.TimeNode(
                            nodeIndex, "等" + formatDuration(remaining) + "后自然成熟",
                            "剩余时间不足浇水阈值，自然成熟",
                            cumulativeTime,
                            formatTimeFull(currentTime, cumulativeTime), true));
                }
            }
            default -> {
                // 未知策略，默认自然成熟
                nodes.add(new WateringResponse.TimeNode(
                        nodeIndex, "自然成熟",
                        "剩余" + formatDuration(remainingSeconds) + "后成熟",
                        remainingSeconds,
                        formatTimeFull(currentTime, remainingSeconds), true));
            }
        }

        return nodes;
    }
}
