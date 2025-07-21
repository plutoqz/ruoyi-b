package org.ruoyi.controller.shp.dto;

/**
 * 封装两个要素之间的空间关系
 */
public class SpatialRelationship {
    private String sourceFeatureId; // 源要素ID
    private String targetFeatureId; // 目标要素ID
    private String type; // 关系类型, e.g., "TOUCHES", "INTERSECTS"

    // Constructors, Getters, and Setters
    public SpatialRelationship() {}

    public SpatialRelationship(String sourceFeatureId, String targetFeatureId, String type) {
        this.sourceFeatureId = sourceFeatureId;
        this.targetFeatureId = targetFeatureId;
        this.type = type;
    }

    public String getSourceFeatureId() {
        return sourceFeatureId;
    }

    public void setSourceFeatureId(String sourceFeatureId) {
        this.sourceFeatureId = sourceFeatureId;
    }

    public String getTargetFeatureId() {
        return targetFeatureId;
    }

    public void setTargetFeatureId(String targetFeatureId) {
        this.targetFeatureId = targetFeatureId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
