package org.ruoyi.controller.kgstructure.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

// 这个主类通常已经是 public 的
@Data
public class GraphData {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
}

