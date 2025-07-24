package org.ruoyi.service.kgstructure.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DataSource {
    private String id;
    private String name;
    private List<String> attributeFields;
    private long recordCount;
    private List<Feature> features;
    private List<SpatialRelationship> spatialRelationships;

    public void setId(String id) {
        this.id = id;
    }

    // 内部类，用于表示一条记录/一个要素
    @Data
    public static class Feature {
        private String featureId; // 局部唯一ID (例如，在SHP文件中的FID)
        private Map<String, Object> attributes;
    }

    // 内部类，用于表示一条空间关系
    @Data
    public static class SpatialRelationship {
        private String sourceFeatureId;
        private String targetFeatureId;
        private String type; // e.g., TOUCHES, CONTAINS
    }
}
