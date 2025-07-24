package org.ruoyi.controller.kgstructure.dto;

import lombok.Data;

// 确保这个类也是 public
@Data
public class GraphEdge { // <--- 关键修改：添加 public
    private String id;
    private String source;
    private String target;
    private String label;
}
