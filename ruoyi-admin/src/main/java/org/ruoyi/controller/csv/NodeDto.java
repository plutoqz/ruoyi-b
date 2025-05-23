package org.ruoyi.controller.csv;
import java.util.List;
import java.util.Properties;
public class NodeDto {
    private String elementid;
    private List<String> labels;
    private Properties properties;
    private String id;

    public NodeDto(String id, String entityType, String name) {
        this.elementid = id;
        this.labels = List.of(entityType);
//        this.properties = new Properties(name);
        this.properties = new java.util.Properties();
        if (name != null && !name.trim().isEmpty()) { // 增加了对 name 是否为空或仅空格的检查
            this.properties.setProperty("name", name.trim()); // 存储 name 属性，并 trim()
        }
        this.id = id;
    }

    // Getters and Setters
    // 确保有所有字段的 getter 方法，以便 Jackson (或类似的 JSON库)
    // 能够序列化它们。
    public String getElementid() {
        return elementid;
    }

    public void setElementid(String elementid) {
        this.elementid = elementid;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() { // 添加 toString() 会很有助于调试
        return "NodeDto{" +
                "elementid='" + elementid + '\'' +
                ", labels=" + labels +
                ", properties=" + properties +
                ", id='" + id + '\'' +
                '}';
    }
}

