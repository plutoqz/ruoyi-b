package org.ruoyi.service.kgstructure;

import org.ruoyi.service.kgstructure.model.DataSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DataSourceCacheService {

    // 使用 ConcurrentHashMap 保证线程安全
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * 将处理好的数据源添加到缓存中
     * @param dataSource 包含 features 和 spatial relationships 的数据源对象
     * @return 返回为该数据源生成的唯一ID
     */
    public String addDataSource(DataSource dataSource) {
        String id = UUID.randomUUID().toString();
        dataSource.setId(id);
        dataSourceCache.put(id, dataSource);
        return id;
    }

    /**
     * 根据ID从缓存中获取数据源
     * @param id 数据源的唯一ID
     * @return 找到的数据源，如果不存在则返回 null
     */
    public DataSource getDataSource(String id) {
        return dataSourceCache.get(id);
    }

    /**
     * 根据ID列表获取多个数据源
     * @param ids ID列表
     * @return 数据源列表
     */
    public List<DataSource> getAllDataSources(List<String> ids) {
        return ids.stream()
                .map(this::getDataSource)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 从缓存中移除一个数据源（可选，用于会话结束时清理）
     * @param id 要移除的数据源ID
     */
    public void removeDataSource(String id) {
        dataSourceCache.remove(id);
    }
}
