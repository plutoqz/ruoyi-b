package org.ruoyi.controller.shp.dto;

import java.util.Map;

/**
 * 封装单个地理要素的数据
 */
public class FeatureData {
    private String featureId; // 要素的唯一ID
    private Map<String, Object> attributes; // 要素的属性

    // Constructors, Getters, and Setters
    public FeatureData() {}

    public FeatureData(String featureId, Map<String, Object> attributes) {
        this.featureId = featureId;
        this.attributes = attributes;
    }

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
