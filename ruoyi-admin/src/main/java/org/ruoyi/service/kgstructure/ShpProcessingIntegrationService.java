package org.ruoyi.service.kgstructure;

import org.ruoyi.controller.shp.FileParsingService;
import org.ruoyi.controller.shp.SseService;
import org.ruoyi.controller.shp.StorageService;
import org.ruoyi.controller.shp.dto.FeatureData;
import org.ruoyi.controller.shp.dto.ShpParseResult;
import org.ruoyi.service.kgstructure.model.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ShpProcessingIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ShpProcessingIntegrationService.class);

    @Autowired
    private FileParsingService fileParsingService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private DataSourceCacheService dataSourceCacheService;
    private final SseService sseService;

    // 【新增】用于暂存文件的 Map
    private final Map<String, File> stagedFiles = new ConcurrentHashMap<>();

    // 【修改】更新构造函数以注入 SseService
    @Autowired
    public ShpProcessingIntegrationService(FileParsingService fileParsingService, StorageService storageService,
                                           DataSourceCacheService dataSourceCacheService, SseService sseService) {
        this.fileParsingService = fileParsingService;
        this.storageService = storageService;
        this.dataSourceCacheService = dataSourceCacheService;
        this.sseService = sseService;
    }

    /**
     * 【新增】第 1 步：暂存文件。由 /upload 接口调用。
     * 接收上传的文件，保存到临时位置，并返回一个任务ID。
     *
     * @param file 前端上传的文件
     * @return 任务ID
     * @throws IOException 文件处理异常
     */
    public String stageFileForProcessing(MultipartFile file) throws IOException {
        String taskId = UUID.randomUUID().toString();
        File tempFile = saveMultipartFileToTemp(file, taskId);

        // 将文件暂存起来，等待 SSE 连接建立后再处理
        stagedFiles.put(taskId, tempFile);
        log.info("文件 {} 已被暂存，路径: {}，任务ID: {}", file.getOriginalFilename(), tempFile.getAbsolutePath(), taskId);

        // 可以在这里设置一个定时清理任务，防止文件无限期暂存

        return taskId;
    }

    /**
     * 【新增】第 2 步：启动解析。由 /subscribe 接口调用。
     * 查找暂存的文件，并启动异步解析任务。
     *
     * @param taskId 任务ID
     */
    public void startParsingForStagedFile(String taskId) {
        File fileToParse = stagedFiles.remove(taskId); // 取出并移除
        if (fileToParse != null) {
            log.info("SSE 连接已建立，为任务 {} 启动异步解析。", taskId);
            // 启动异步解析，这个方法会立即返回
            fileParsingService.parseShapefile(taskId, fileToParse);
        } else {
            log.warn("无法为任务 {} 找到暂存的文件来启动解析，可能已被处理或已过期。", taskId);
            // 通过 SSE 发送一个错误通知，告知前端任务失败
            Map<String, Object> sseData = Map.of("status", "FAILED", "error", "Task file not found or expired.");
            sseService.send(taskId, sseData);
            sseService.complete(taskId);
        }
    }
    /**
     * 主入口方法：接收上传的文件，启动异步解析，并返回一个任务ID。
     * @param file 前端上传的文件
     * @return 任务ID
     * @throws IOException 文件处理异常
     */
    public String startShpProcessing(MultipartFile file) throws IOException {
        String taskId = UUID.randomUUID().toString();
        File tempFile = saveMultipartFileToTemp(file, taskId);

        log.info("为文件 {} 创建了临时文件 {}，并启动异步解析，任务ID: {}", file.getOriginalFilename(), tempFile.getAbsolutePath(), taskId);
        // 启动异步解析，这个方法会立即返回
        fileParsingService.parseShapefile(taskId, tempFile);

        return taskId;
    }

    /**
     * 检查任务状态，如果已完成，则将结果从Redis转移到内存缓存(DataSourceCacheService)
     * @param taskId 任务ID
     * @return 如果任务完成，返回填充好的 DataSource；否则返回 null
     */
    public DataSource checkAndRetrieveResult(String taskId) {
        StorageService.TaskStatus status = storageService.getTaskStatus(taskId);

        if (status != StorageService.TaskStatus.COMPLETED) {
            log.debug("任务 {} 尚未完成，当前状态: {}", taskId, status);
            return null; // 任务未完成或失败
        }

        // 尝试从 DataSourceCacheService 获取，如果已经处理过，直接返回
        DataSource cachedDs = dataSourceCacheService.getDataSource(taskId);
        if (cachedDs != null) {
            return cachedDs;
        }

        // 任务已完成，从Redis获取结果
        ShpParseResult result = storageService.getTaskResult(taskId);
        if (result == null) {
            log.error("任务 {} 状态为COMPLETED，但无法从Redis获取结果！", taskId);
            // 可以在这里处理异常情况，例如将任务标记为失败
            return null;
        }

        // --- 将Redis中的结果转换为内部DataSource模型 ---
        DataSource dataSource = new DataSource();

        // 转换 Features
        List<DataSource.Feature> features = result.getFeatures().stream().map(rawFeature -> {
            DataSource.Feature feature = new DataSource.Feature();
            // 在我们的模型中，featureId 是字符串，这与你的 DTO 一致
            feature.setFeatureId(rawFeature.getFeatureId());
            feature.setAttributes(rawFeature.getAttributes());
            return feature;
        }).collect(Collectors.toList());
        dataSource.setFeatures(features);

        // 转换 SpatialRelationships
        if (result.getSpatialRelationships() != null) {
            List<DataSource.SpatialRelationship> relationships = result.getSpatialRelationships().stream().map(rawRel -> {
                DataSource.SpatialRelationship rel = new DataSource.SpatialRelationship();
                rel.setSourceFeatureId(rawRel.getSourceFeatureId());
                rel.setTargetFeatureId(rawRel.getTargetFeatureId());
                rel.setType(rawRel.getType());
                return rel;
            }).collect(Collectors.toList());
            dataSource.setSpatialRelationships(relationships);
        }

        // 填充元数据
        dataSource.setRecordCount(features.size());
        if (!features.isEmpty()) {
            dataSource.setAttributeFields(features.get(0).getAttributes().keySet().stream().collect(Collectors.toList()));
        } else {
            dataSource.setAttributeFields(Collections.emptyList());
        }

        // 将转换后的 DataSource 存入我们新的内存缓存中，使用 taskId 作为 key
        // 注意：我们直接复用 taskId 作为 dataSourceId
        dataSource.setId(taskId);
        dataSourceCacheService.addDataSource(dataSource);

        log.info("任务 {} 的结果已成功从Redis转移到DataSourceCacheService", taskId);

        // 清理Redis中的旧数据，因为我们已经把它转移到新缓存中了（可选）
        storageService.clearTask(taskId);

        return dataSource;
    }

    /**
     * 将 MultipartFile 保存到临时文件，因为 @Async 方法需要一个稳定的 File 对象
     */
    private File saveMultipartFileToTemp(MultipartFile multipartFile, String taskId) throws IOException {
        File tempFile = File.createTempFile("shp-upload-" + taskId + "-", ".zip");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(multipartFile.getBytes());
        }
        return tempFile;
    }
}
