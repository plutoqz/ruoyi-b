package org.ruoyi.controller.shp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruoyi.controller.shp.dto.ShpParseResult; // 导入新的DTO
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    public enum TaskStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    private static final String KEY_STATUS = "status";
    private static final String KEY_RESULT = "result";
    private static final String KEY_ERROR = "error";
    private static final Duration TASK_EXPIRATION = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public StorageService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String getTaskKey(String taskId) {
        return "shp:task:" + taskId;
    }

    public void updateTaskStatus(String taskId, TaskStatus status) {
        String taskKey = getTaskKey(taskId);
        redisTemplate.opsForHash().put(taskKey, KEY_STATUS, status.name());
        redisTemplate.expire(taskKey, TASK_EXPIRATION);
    }

    /**
     * MODIFIED: 存储任务成功后的 ShpParseResult 对象
     *
     * @param taskId 任务ID
     * @param result 解析出的包含要素和关系的结果对象
     */
    public void storeTaskResult(String taskId, ShpParseResult result) {
        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            String taskKey = getTaskKey(taskId);
            redisTemplate.opsForHash().put(taskKey, KEY_RESULT, jsonResult);
            redisTemplate.expire(taskKey, TASK_EXPIRATION);
        } catch (JsonProcessingException e) {
            log.error("序列化任务结果失败, taskId: {}", taskId, e);
            storeTaskError(taskId, "内部错误：无法序列化任务结果。");
            updateTaskStatus(taskId, TaskStatus.FAILED);
        }
    }

    public void storeTaskError(String taskId, String errorMessage) {
        String taskKey = getTaskKey(taskId);
        redisTemplate.opsForHash().put(taskKey, KEY_ERROR, errorMessage);
        redisTemplate.expire(taskKey, TASK_EXPIRATION);
    }

    public TaskStatus getTaskStatus(String taskId) {
        String taskKey = getTaskKey(taskId);
        Object statusObj = redisTemplate.opsForHash().get(taskKey, KEY_STATUS);
        if (statusObj == null) return null;
        try {
            return TaskStatus.valueOf(statusObj.toString());
        } catch (IllegalArgumentException e) {
            log.warn("在Redis中发现无效的任务状态值, taskId: {}, value: {}", taskId, statusObj);
            return null;
        }
    }

    /**
     * MODIFIED: 获取任务结果 ShpParseResult 对象
     *
     * @param taskId 任务ID
     * @return 解析出的结果对象，如果任务未完成或不存在则返回null
     */
    public ShpParseResult getTaskResult(String taskId) {
        String taskKey = getTaskKey(taskId);
        Object resultObj = redisTemplate.opsForHash().get(taskKey, KEY_RESULT);
        if (resultObj == null) {
            return null;
        }
        try {
            // 将JSON字符串反序列化为 ShpParseResult 对象
            return objectMapper.readValue(resultObj.toString(), ShpParseResult.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化任务结果失败, taskId: {}", taskId, e);
            return null;
        }
    }

    public String getTaskError(String taskId) {
        String taskKey = getTaskKey(taskId);
        Object errorObj = redisTemplate.opsForHash().get(taskKey, KEY_ERROR);
        return errorObj != null ? errorObj.toString() : null;
    }

    public void clearTask(String taskId) {
        String taskKey = getTaskKey(taskId);
        redisTemplate.delete(taskKey);
    }
}