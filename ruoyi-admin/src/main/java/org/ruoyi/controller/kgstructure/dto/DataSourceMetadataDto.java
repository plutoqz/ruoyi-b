package org.ruoyi.controller.kgstructure.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder // 使用Builder模式方便创建对象
public class DataSourceMetadataDto {
    private String dataSourceId; // 后端生成的唯一ID
    private String name;
    private List<String> attributeFields;
    private long recordCount;
}
