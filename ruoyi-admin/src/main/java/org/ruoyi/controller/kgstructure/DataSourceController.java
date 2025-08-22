package org.ruoyi.controller.kgstructure;

import org.ruoyi.controller.kgstructure.dto.DataSourceMetadataDto;
import org.ruoyi.controller.shp.StorageService;
import org.ruoyi.service.kgstructure.ShpProcessingIntegrationService;
import org.ruoyi.service.kgstructure.model.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.ruoyi.controller.shp.SseService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
public class DataSourceController {

    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

    @Autowired
    private ShpProcessingIntegrationService processingService;

    @Autowired
    private StorageService storageService; // 用于直接查询状态

    /**
     * 接收文件上传，启动异步处理
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadAndProcessFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null) {
            return ResponseEntity.badRequest().body("上传文件不能为空");
        }
        // 我们只处理zip文件
        if (!file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            return ResponseEntity.badRequest().body("目前只支持上传.zip格式的shp文件压缩包");
        }

        try {
            String taskId = processingService.startShpProcessing(file);

            // 返回一个包含 taskId 和文件名的对象，前端需要这两个信息
            Map<String, String> response = new HashMap<>();
            response.put("taskId", taskId);
            response.put("fileName", file.getOriginalFilename()); // 将文件名返回给前端

            return ResponseEntity.accepted().body(response); // 202 Accepted 表示请求已接受，正在处理

        } catch (Exception e) {
            log.error("启动文件处理任务失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("启动文件处理任务失败: " + e.getMessage());
        }
    }

    /**
     * 前端轮询此接口以检查任务状态
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId, @RequestParam String fileName) {
        StorageService.TaskStatus status = storageService.getTaskStatus(taskId);

        if (status == null) {
            // 这可能是任务刚创建，Redis还没来得及写入，或者任务已过期
            // 我们返回 PROCESSING 状态让前端继续等待
            status = StorageService.TaskStatus.PROCESSING;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", status.name());

        switch (status) {
            case COMPLETED:
                // 任务完成，调用 service 将结果从 Redis 转移到内存，并获取元数据
                DataSource dataSource = processingService.checkAndRetrieveResult(taskId);
                if (dataSource != null) {
                    // 构建返回给前端的元数据 DTO
                    DataSourceMetadataDto metadata = DataSourceMetadataDto.builder()
                            .dataSourceId(dataSource.getId()) // ID就是taskId
                            .name(fileName) // 使用前端传来的文件名
                            .attributeFields(dataSource.getAttributeFields())
                            .recordCount(dataSource.getRecordCount())
                            .build();
                    response.put("data", metadata);
                    return ResponseEntity.ok(response);
                } else {
                    // 发生异常，虽然状态是COMPLETED，但拿不到数据
                    response.put("status", StorageService.TaskStatus.FAILED.name());
                    response.put("error", "任务结果在后端处理时丢失。");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }

            case FAILED:
                response.put("error", storageService.getTaskError(taskId));
                return ResponseEntity.ok(response);

            case PROCESSING:
            default:
                return ResponseEntity.ok(response);
        }
    }
}
