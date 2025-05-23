package org.ruoyi.controller.csv;
import java.util.Properties;
public class EdgeDto {
    private String start;
    private String end;
    private String type;
    private Properties properties;
    private String id;

    public EdgeDto(String id, String entityType, String beginId, String endId) {
        this.id = id;
        this.type = entityType;
        this.start = beginId;
        this.end = endId;
//        this.properties = new Properties();
        this.properties = new java.util.Properties();
    }

    // Getters and Setters

    public String getId() {
        return id;
    }
    public String gettype() {
        return type;
    }
    public String getStartNodeId() {
        return start;
    }

    public String getEndNodeId() {
        return end;
    }

    public Properties getProperties() {
        return properties;
    }

    // --- Setters (通常 DTO 也需要 Setter，除非设计为不可变) ---
    public void setId(String id) {
        this.id = id;
    }
    public void settype(String entityType) {
        this.type = entityType;
    }
    public void setStartNodeId(String startNodeId) {
        this.start = startNodeId;
    }

    public void setEndNodeId(String endNodeId) {
        this.end = endNodeId;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

}
