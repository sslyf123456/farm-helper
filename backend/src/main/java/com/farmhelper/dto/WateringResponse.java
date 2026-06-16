package com.farmhelper.dto;

import java.util.List;

/**
 * 浇水计算响应 DTO
 */
public class WateringResponse {
    /** 策略标识 */
    private String strategy;
    
    /** 策略标签 */
    private String label;
    
    /** 策略描述 */
    private String description;
    
    /** 实际成熟秒数 */
    private int matureSeconds;
    
    /** 格式化的成熟时间 */
    private String formatted;
    
    /** 成熟时刻（ISO-8601格式字符串，可选） */
    private String matureAt;
    
    /** 节点列表（浇水和收菜节点） */
    private List<TimeNode> nodes;

    public WateringResponse() {
    }

    public WateringResponse(String strategy, String label, String description, 
                           int matureSeconds, String formatted, String matureAt, 
                           List<TimeNode> nodes) {
        this.strategy = strategy;
        this.label = label;
        this.description = description;
        this.matureSeconds = matureSeconds;
        this.formatted = formatted;
        this.matureAt = matureAt;
        this.nodes = nodes;
    }

    // Getters and Setters
    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getMatureSeconds() {
        return matureSeconds;
    }

    public void setMatureSeconds(int matureSeconds) {
        this.matureSeconds = matureSeconds;
    }

    public String getFormatted() {
        return formatted;
    }

    public void setFormatted(String formatted) {
        this.formatted = formatted;
    }

    public String getMatureAt() {
        return matureAt;
    }

    public void setMatureAt(String matureAt) {
        this.matureAt = matureAt;
    }

    public List<TimeNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<TimeNode> nodes) {
        this.nodes = nodes;
    }

    /**
     * 时间节点（浇水或收菜）
     */
    public static class TimeNode {
        /** 步骤编号（从1开始） */
        private int index;
        
        /** 标题文字 */
        private String title;
        
        /** 描述：减时 + 剩余 + 湿润 */
        private String desc;
        
        /** 该时刻距种下的秒数 */
        private int offsetSeconds;
        
        /** 具体时间字符串（ISO-8601格式，可选） */
        private String timeStr;
        
        /** 是否为最终收菜节点 */
        private boolean isHarvest;

        public TimeNode() {
        }

        public TimeNode(int index, String title, String desc, int offsetSeconds, 
                       String timeStr, boolean isHarvest) {
            this.index = index;
            this.title = title;
            this.desc = desc;
            this.offsetSeconds = offsetSeconds;
            this.timeStr = timeStr;
            this.isHarvest = isHarvest;
        }

        // Getters and Setters
        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public int getOffsetSeconds() {
            return offsetSeconds;
        }

        public void setOffsetSeconds(int offsetSeconds) {
            this.offsetSeconds = offsetSeconds;
        }

        public String getTimeStr() {
            return timeStr;
        }

        public void setTimeStr(String timeStr) {
            this.timeStr = timeStr;
        }

        public boolean isHarvest() {
            return isHarvest;
        }

        public void setHarvest(boolean harvest) {
            isHarvest = harvest;
        }
    }
}
