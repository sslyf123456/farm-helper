package com.farmhelper.controller;

import com.farmhelper.dto.WateringRequest;
import com.farmhelper.dto.WateringResponse;
import com.farmhelper.service.WateringCalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * 反向请求中解析并校验后的参数，供各反向接口共用
     */
    private record ReverseParams(
            LocalDateTime currentTime,
            int remainingSeconds,
            int moistureSeconds
    ) {}

    /**
     * 解析并校验反向计算所需参数。
     * 任何参数非法时返回 null，调用方应直接返回 400。
     */
    private ReverseParams parseReverseParams(WateringRequest request) {
        LocalDateTime currentTime = request.getCurrentTime() != null
                ? request.getCurrentTime()
                : LocalDateTime.now();

        int T = request.getBaseSeconds();
        int maxMoisture = (int) Math.ceil(T / 3.0);

        // 解析剩余秒数（可由 remainingSeconds 直接给出，或由 matureTime 推算）
        Integer remainingSeconds = request.getRemainingSeconds();
        if (remainingSeconds == null && request.getMatureTime() != null) {
            long diff = java.time.Duration.between(currentTime, request.getMatureTime()).getSeconds();
            if (diff < 0) return null;
            remainingSeconds = (int) diff;
        }
        if (remainingSeconds == null || remainingSeconds < 0 || remainingSeconds > T) return null;

        // 校验水分
        Integer moistureSeconds = request.getMoistureSeconds();
        if (moistureSeconds == null || moistureSeconds < 0 || moistureSeconds > maxMoisture) return null;

        return new ReverseParams(currentTime, remainingSeconds, moistureSeconds);
    }

    /**
     * 计算所有浇水策略的结果
     * - forward（正向）：已知种植时间，计算各策略成熟时间
     * - reverse（反向）：已知剩余时间和当前水分，推算各策略浇水计划
     */
    @PostMapping("/watering")
    public ResponseEntity<List<WateringResponse>> calculateAllStrategies(@RequestBody WateringRequest request) {
        if (request.getBaseSeconds() <= 0) {
            return ResponseEntity.badRequest().build();
        }

        String mode = request.getMode() != null ? request.getMode() : "forward";

        return switch (mode) {
            case "reverse" -> handleReverseCalculation(request);
            default -> handleForwardCalculation(request);
        };
    }

    /**
     * 计算单个浇水策略的结果（正向）
     */
    @PostMapping("/watering/single")
    public ResponseEntity<WateringResponse> calculateSingleStrategy(@RequestBody WateringRequest request) {
        if (request.getBaseSeconds() <= 0 || request.getStrategy() == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(buildForwardResponse(request, request.getStrategy()));
    }

    /**
     * 计算单个浇水策略的结果（反向）
     */
    @PostMapping("/watering/single/reverse")
    public ResponseEntity<WateringResponse> calculateSingleStrategyReverse(@RequestBody WateringRequest request) {
        if (request.getBaseSeconds() <= 0 || request.getStrategy() == null) {
            return ResponseEntity.badRequest().build();
        }

        ReverseParams params = parseReverseParams(request);
        if (params == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(buildReverseResponse(
                request, request.getStrategy(),
                params.remainingSeconds(), params.moistureSeconds(), params.currentTime()));
    }

    /**
     * 正向计算：遍历所有策略，返回全量结果
     */
    private ResponseEntity<List<WateringResponse>> handleForwardCalculation(WateringRequest request) {
        String[] strategies = {"none", "once", "diligent", "extreme"};
        List<WateringResponse> results = new ArrayList<>();

        for (String strategy : strategies) {
            results.add(buildForwardResponse(request, strategy));
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 反向计算：校验参数后遍历所有策略，返回全量结果
     */
    private ResponseEntity<List<WateringResponse>> handleReverseCalculation(WateringRequest request) {
        ReverseParams params = parseReverseParams(request);
        if (params == null) {
            return ResponseEntity.badRequest().build();
        }

        String[] strategies = {"none", "once", "extreme"};
        List<WateringResponse> results = new ArrayList<>();

        for (String strategy : strategies) {
            results.add(buildReverseResponse(
                    request, strategy,
                    params.remainingSeconds(), params.moistureSeconds(), params.currentTime()));
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 构建单个策略的正向计算响应
     */
    private WateringResponse buildForwardResponse(WateringRequest request, String strategy) {
        int matureSeconds = wateringCalculatorService.calcMaturitySeconds(request.getBaseSeconds(), strategy);
        String formatted = wateringCalculatorService.formatDuration(matureSeconds);
        String matureAt = request.getPlantTime() != null
                ? wateringCalculatorService.formatTimeFull(request.getPlantTime(), matureSeconds)
                : null;
        List<WateringResponse.TimeNode> nodes = wateringCalculatorService.buildTimeNodes(
                request.getBaseSeconds(), strategy, matureSeconds, request.getPlantTime());

        return new WateringResponse(
                strategy,
                wateringCalculatorService.getStrategyLabel(strategy),
                wateringCalculatorService.getStrategyDesc(strategy),
                matureSeconds, formatted, matureAt, nodes
        );
    }

    /**
     * 构建单个策略的反向计算响应
     */
    private WateringResponse buildReverseResponse(WateringRequest request, String strategy,
                                                  int remainingSeconds, int moistureSeconds,
                                                  LocalDateTime currentTime) {
        List<WateringResponse.TimeNode> nodes = wateringCalculatorService.buildReverseTimeNodes(
                request.getBaseSeconds(), strategy, remainingSeconds, moistureSeconds, currentTime);

        int actualMatureSeconds = nodes.isEmpty()
                ? remainingSeconds
                : nodes.get(nodes.size() - 1).getOffsetSeconds();

        String matureAt = wateringCalculatorService.formatTimeFull(currentTime, actualMatureSeconds);
        String formatted = wateringCalculatorService.formatDuration(actualMatureSeconds);

        return new WateringResponse(
                strategy,
                wateringCalculatorService.getStrategyLabel(strategy),
                wateringCalculatorService.getReverseStrategyDesc(strategy),
                actualMatureSeconds, formatted, matureAt, nodes
        );
    }
}
