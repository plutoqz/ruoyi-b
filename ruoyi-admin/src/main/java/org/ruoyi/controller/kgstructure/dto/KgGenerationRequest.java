package org.ruoyi.controller.kgstructure.dto;

import lombok.Data;
import java.util.List;

@Data
public class KgGenerationRequest {
    private List<String> dataSourceIds; // 前端上传文件后，后端返回的唯一ID列表
    private List<NodeDefinitionDto> nodeDefinitions;
    private List<RelationshipDefinitionDto> relationshipDefinitions;
}
