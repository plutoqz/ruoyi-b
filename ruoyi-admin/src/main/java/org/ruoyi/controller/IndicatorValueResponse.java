package org.ruoyi.controller;

public class IndicatorValueResponse {
    private String villageName;
    private String indicatorName;
    private String timeTag;
    private double value;

    public IndicatorValueResponse(String villageName, String indicatorName, String timeTag, double value) {
        this.villageName = villageName;
        this.indicatorName = indicatorName;
        this.timeTag = timeTag;
        this.value = value;
    }

    // --- Getters and Setters ---
    public String getVillageName() { return villageName; }
    public void setVillageName(String villageName) { this.villageName = villageName; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getTimeTag() { return timeTag; }
    public void setTimeTag(String timeTag) { this.timeTag = timeTag; }
    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}
