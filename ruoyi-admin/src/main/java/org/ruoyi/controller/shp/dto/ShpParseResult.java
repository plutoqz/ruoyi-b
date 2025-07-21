package org.ruoyi.controller.shp.dto;

import java.util.List;

/**
 * 封装 SHP 文件解析的完整结果，包括要素和空间关系
 */
public class ShpParseResult {
    private List<FeatureData> features;
    private List<SpatialRelationship> spatialRelationships;

    // Constructors, Getters, and Setters
    public ShpParseResult() {}

    public ShpParseResult(List<FeatureData> features, List<SpatialRelationship> spatialRelationships) {
        this.features = features;
        this.spatialRelationships = spatialRelationships;
    }

    public List<FeatureData> getFeatures() {
        return features;
    }

    public void setFeatures(List<FeatureData> features) {
        this.features = features;
    }

    public List<SpatialRelationship> getSpatialRelationships() {
        return spatialRelationships;
    }

    public void setSpatialRelationships(List<SpatialRelationship> spatialRelationships) {
        this.spatialRelationships = spatialRelationships;
    }
}
