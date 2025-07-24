package org.ruoyi.controller.kgstructure.dto;

import lombok.Data;
import java.util.List;

@Data // 使用Lombok简化get/set/toString等
public class NodeDefinitionDto {
    private String name;          // 节点类型名称
    private String field;         // 唯一标识字段
    private String labelField;    // 显示标签字段
    private String filterField;   // 过滤字段
    private String filterValue;   // 过滤值
    private String group;         // 分组名
    private List<String> properties; // 需要附加的属性字段列表
}
