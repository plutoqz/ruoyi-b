package org.ruoyi.controller.kgstructure.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

// 这个主类通常已经是 public 的
@Data
@NoArgsConstructor // 生成一个无参构造函数
@AllArgsConstructor // 生成一个包含所有参数的构造函数
public class GraphData {
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
}

