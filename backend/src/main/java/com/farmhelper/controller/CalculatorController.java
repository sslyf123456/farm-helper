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
