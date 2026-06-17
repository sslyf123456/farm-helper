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
    
    /** 计算模式：forward（正向，从种植时间计算） 或 reverse（反向，从剩余成熟时间/成熟时间反推） */
    private String mode;
    
    /** 剩余成熟时间（秒，仅用于 reverse 模式） */
    private Integer remainingSeconds;
    
    /** 成熟时间点（可选，用于 reverse 模式，与 remainingSeconds 二选一） */
    private LocalDateTime matureTime;
    
    /** 当前时间（可选，用于 reverse 模式计算，如果不提供则使用服务器当前时间） */
    private LocalDateTime currentTime;
    
    /** 水分维持时间（秒，表示当前水分还能维持多久，仅用于 reverse 模式） */
    private Integer moistureSeconds;

    public WateringRequest() {
    }

    public WateringRequest(int baseSeconds, String strategy, LocalDateTime plantTime) {
        this.baseSeconds = baseSeconds;
        this.strategy = strategy;
        this.plantTime = plantTime;
        this.mode = "forward";
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(Integer remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public LocalDateTime getMatureTime() {
        return matureTime;
    }

    public void setMatureTime(LocalDateTime matureTime) {
        this.matureTime = matureTime;
    }

    public LocalDateTime getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(LocalDateTime currentTime) {
        this.currentTime = currentTime;
    }

    public Integer getMoistureSeconds() {
        return moistureSeconds;
    }

    public void setMoistureSeconds(Integer moistureSeconds) {
        this.moistureSeconds = moistureSeconds;
    }
}
