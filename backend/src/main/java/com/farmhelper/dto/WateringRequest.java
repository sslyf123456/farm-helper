package com.farmhelper.dto;

import java.time.LocalDateTime;

/**
 * 浇水计算请求 DTO
 */
public class WateringRequest {
    /** 作物基础成熟时间（秒） */
    private int baseSeconds;
    
    /** 浇水策略：none, once, diligent, extreme */
    private String strategy;
    
    /** 种植时间（可选，用于计算具体时刻） */
    private LocalDateTime plantTime;

    public WateringRequest() {
    }

    public WateringRequest(int baseSeconds, String strategy, LocalDateTime plantTime) {
        this.baseSeconds = baseSeconds;
        this.strategy = strategy;
        this.plantTime = plantTime;
    }

    public int getBaseSeconds() {
        return baseSeconds;
    }

    public void setBaseSeconds(int baseSeconds) {
        this.baseSeconds = baseSeconds;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public LocalDateTime getPlantTime() {
        return plantTime;
    }

    public void setPlantTime(LocalDateTime plantTime) {
        this.plantTime = plantTime;
    }
}
