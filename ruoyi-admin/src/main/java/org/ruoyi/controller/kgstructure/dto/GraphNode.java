package org.ruoyi.controller.kgstructure.dto;

import lombok.Data;

import java.util.Map;

// 确保这个类是 public
@Data
public class GraphNode { // <--- 关键修改：添加 public
    private String id;
    private String label;
    private String type;
    private String group;
    private Map<String, Object> properties;
}
