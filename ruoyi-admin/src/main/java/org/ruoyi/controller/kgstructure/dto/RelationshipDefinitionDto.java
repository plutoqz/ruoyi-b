package org.ruoyi.controller.kgstructure.dto;

import lombok.Data;

@Data
public class RelationshipDefinitionDto {
    private String method; // 'spatial' 或 'field'
    private String source; // 源节点类型 或 分组
    private String target; // 目标节点类型 或 分组
    private String type;   // 关系名称/标签 (用于字段连接)
    private String sourceForeignKey; // 源外键
    private String targetForeignKey; // 目标外键
}
