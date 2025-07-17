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
import java.util.HashMap;
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

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> handleFileUpload(@RequestParam("file") MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        try {
            // Create a temporary file
            Path tempDir = Files.createTempDirectory("shp-upload-");
            File tempFile = tempDir.resolve(multipartFile.getOriginalFilename()).toFile();
            multipartFile.transferTo(tempFile);

            // Generate a unique task ID
            String taskId = UUID.randomUUID().toString();

            // Start the async parsing process
            fileParsingService.parseShapefile(taskId, tempFile);

            // Immediately return the task ID to the client
            return ResponseEntity.ok(Map.of("taskId", taskId));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        StorageService.TaskStatus status = storageService.getTaskStatus(taskId);
        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Task is not completed or has failed."));
        }

        Object result = storageService.getTaskResult(taskId);

        // Clean up the stored data after it's retrieved
        storageService.clearTask(taskId);

        return ResponseEntity.ok(result);
    }
}
