package org.ruoyi.controller.shp;

import org.ruoyi.controller.shp.FileParsingService;
import org.ruoyi.controller.shp.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileParsingService fileParsingService;
    private final StorageService storageService;

    public FileController(FileParsingService fileParsingService, StorageService storageService) {
        this.fileParsingService = fileParsingService;
        this.storageService = storageService;
    }

    /**
     * 处理一个或多个文件的上传。
     * @param files 客户端上传的文件列表。
     * @return 包含任务列表的响应实体，每个任务都有一个文件名和 taskId。
     */
    @PostMapping("/upload")
    public ResponseEntity<?> handleFileUpload(@RequestParam("files") List<MultipartFile> files) {
        if (files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件列表为空"));
        }

        List<Map<String, String>> tasks = new ArrayList<>();
        Path tempDir;

        try {
            // 为这批上传创建一个临时的总目录
            tempDir = Files.createTempDirectory("shp-upload-batch-");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "无法创建临时目录: " + e.getMessage()));
        }

        for (MultipartFile multipartFile : files) {
            if (multipartFile.isEmpty()) {
                continue; // 跳过空文件
            }
            try {
                // 将文件保存到临时位置
                File tempFile = tempDir.resolve(multipartFile.getOriginalFilename()).toFile();
                multipartFile.transferTo(tempFile);

                // 为每个文件生成唯一的任务ID
                String taskId = UUID.randomUUID().toString();

                // 为每个文件启动一个异步解析任务
                fileParsingService.parseShapefile(taskId, tempFile);

                // 将任务信息添加到返回列表中
                Map<String, String> taskInfo = new HashMap<>();
                taskInfo.put("fileName", multipartFile.getOriginalFilename());
                taskInfo.put("taskId", taskId);
                tasks.add(taskInfo);

            } catch (Exception e) {
                e.printStackTrace();
                // 在实际应用中，您可能希望收集所有错误并一次性报告
                // 为简单起见，这里在遇到第一个错误时就返回
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "文件上传失败 " + multipartFile.getOriginalFilename() + ": " + e.getMessage()));
            }
        }

        // 立即将任务ID列表返回给客户端
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        StorageService.TaskStatus status = storageService.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "未找到任务"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", status.toString());

        if (status == StorageService.TaskStatus.FAILED) {
            response.put("error", storageService.getTaskError(taskId));
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/data/{taskId}")
    public ResponseEntity<?> getTaskData(@PathVariable String taskId) {
        StorageService.TaskStatus status = storageService.getTaskStatus(taskId);
        if (status != StorageService.TaskStatus.COMPLETED) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "任务未完成或已失败。"));
        }

        Object result = storageService.getTaskResult(taskId);
        storageService.clearTask(taskId);
        return ResponseEntity.ok(result);
    }
}