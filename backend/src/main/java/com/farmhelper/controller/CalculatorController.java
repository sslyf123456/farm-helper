package com.farmhelper.controller;

import com.farmhelper.dto.WateringRequest;
import com.farmhelper.dto.WateringResponse;
import com.farmhelper.service.WateringCalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 计算器接口控制器
 */
@RestController
@RequestMapping("/api/calculator")
public class CalculatorController {

    private final WateringCalculatorService wateringCalculatorService;

    public CalculatorController(WateringCalculatorService wateringCalculatorService) {
        this.wateringCalculatorService = wateringCalculatorService;
    }

    /**
     * 计算所有浇水策略的结果
     *
     * @param request 包含基础成熟时间、种植时间的请求对象
     * @return 所有策略的计算结果列表
     */
    @PostMapping("/watering")
    public ResponseEntity<List<WateringResponse>> calculateAllStrategies(@RequestBody WateringRequest request) {
        if (request.getBaseSeconds() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        String mode = request.getMode() != null ? request.getMode() : "forward";
        
        // 反向计算模式
        if ("reverse".equals(mode)) {
            return handleReverseCalculation(request);
        }

        // 正向计算模式（原有逻辑）
        String[] strategies = {"none", "once", "diligent", "extreme"};
        List<WateringResponse> results = new java.util.ArrayList<>();

        for (String strategy : strategies) {
            int matureSeconds = wateringCalculatorService.calcMaturitySeconds(
                    request.getBaseSeconds(), strategy);
            
            String formatted = wateringCalculatorService.formatDuration(matureSeconds);
            
            String matureAt = null;
            if (request.getPlantTime() != null) {
                matureAt = wateringCalculatorService.formatTimeFull(
                        request.getPlantTime(), matureSeconds);
            }

            List<WateringResponse.TimeNode> nodes = wateringCalculatorService.buildTimeNodes(
                    request.getBaseSeconds(), strategy, matureSeconds, request.getPlantTime());

            WateringResponse response = new WateringResponse(
                    strategy,
                    wateringCalculatorService.getStrategyLabel(strategy),
                    wateringCalculatorService.getStrategyDesc(strategy),
                    matureSeconds,
                    formatted,
                    matureAt,
                    nodes
            );

            results.add(response);
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 处理反向计算：从剩余成熟时间或成熟时间点反推浇水计划
     */
    private ResponseEntity<List<WateringResponse>> handleReverseCalculation(WateringRequest request) {
        // 确定当前时间
        java.time.LocalDateTime currentTime = request.getCurrentTime() != null 
                ? request.getCurrentTime() 
                : java.time.LocalDateTime.now();
        
        int T = request.getBaseSeconds();
        int maxMoisture = T / 3; // 水分维持度上限
        
        // 计算剩余秒数
        Integer remainingSeconds = request.getRemainingSeconds();
        if (remainingSeconds == null && request.getMatureTime() != null) {
            // 从成熟时间点计算剩余秒数
            remainingSeconds = (int) java.time.Duration.between(currentTime, request.getMatureTime()).getSeconds();
            if (remainingSeconds < 0) {
                return ResponseEntity.badRequest().build(); // 成熟时间已过
            }
        }
        
        if (remainingSeconds == null || remainingSeconds < 0) {
            return ResponseEntity.badRequest().build();
        }
        
        // 校验：剩余成熟时间不能超过作物周期
        if (remainingSeconds > T) {
            return ResponseEntity.badRequest().body(null);
        }
        
        // 水分维持时间（秒数，必需）
        Integer moistureSeconds = request.getMoistureSeconds();
        if (moistureSeconds == null || moistureSeconds < 0) {
            return ResponseEntity.badRequest().build();
        }
        
        // 校验：水分维持度不能超过周期三分之一
        if (moistureSeconds > maxMoisture) {
            return ResponseEntity.badRequest().body(null);
        }

        // 反向计算只支持三种策略：none, once, extreme
        String[] strategies = {"none", "once", "extreme"};
        List<WateringResponse> results = new java.util.ArrayList<>();

        for (String strategy : strategies) {
            // 使用反向节点构建方法（从当前状态出发的未来操作计划）
            List<WateringResponse.TimeNode> nodes = wateringCalculatorService.buildReverseTimeNodes(
                    request.getBaseSeconds(), strategy, remainingSeconds,
                    moistureSeconds, currentTime);
            
            // 计算实际成熟时间：从节点列表中获取最后一个节点的offsetSeconds
            int actualMatureSeconds = remainingSeconds; // 默认值
            if (!nodes.isEmpty()) {
                WateringResponse.TimeNode lastNode = nodes.get(nodes.size() - 1);
                actualMatureSeconds = lastNode.getOffsetSeconds();
            }
            
            // 成熟时间点 = 当前时间 + 实际成熟秒数
            String matureAt = wateringCalculatorService.formatTimeFull(currentTime, actualMatureSeconds);
            
            // 格式化实际成熟时间
            String formatted = wateringCalculatorService.formatDuration(actualMatureSeconds);

            WateringResponse response = new WateringResponse(
                    strategy,
                    wateringCalculatorService.getStrategyLabel(strategy),
                    wateringCalculatorService.getReverseStrategyDesc(strategy),
                    actualMatureSeconds,
                    formatted,
                    matureAt,
                    nodes
            );

            results.add(response);
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 计算单个浇水策略的结果
     *
     * @param request 包含基础成熟时间、策略、种植时间的请求对象
     * @return 单个策略的计算结果
     */
    @PostMapping("/watering/single")
    public ResponseEntity<WateringResponse> calculateSingleStrategy(@RequestBody WateringRequest request) {
        if (request.getBaseSeconds() <= 0 || request.getStrategy() == null) {
            return ResponseEntity.badRequest().build();
        }

        String strategy = request.getStrategy();
        int matureSeconds = wateringCalculatorService.calcMaturitySeconds(
                request.getBaseSeconds(), strategy);
        
        String formatted = wateringCalculatorService.formatDuration(matureSeconds);
        
        String matureAt = null;
        if (request.getPlantTime() != null) {
            matureAt = wateringCalculatorService.formatTimeFull(
                    request.getPlantTime(), matureSeconds);
        }

        List<WateringResponse.TimeNode> nodes = wateringCalculatorService.buildTimeNodes(
                request.getBaseSeconds(), strategy, matureSeconds, request.getPlantTime());

        WateringResponse response = new WateringResponse(
                strategy,
                wateringCalculatorService.getStrategyLabel(strategy),
                wateringCalculatorService.getStrategyDesc(strategy),
                matureSeconds,
                formatted,
                matureAt,
                nodes
        );

        return ResponseEntity.ok(response);
    }
}
