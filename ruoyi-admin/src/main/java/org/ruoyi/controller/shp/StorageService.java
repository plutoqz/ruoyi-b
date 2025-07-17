package org.ruoyi.controller.shp; // 根据你的IDE提示，修改包名

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * SHP文件解析任务状态与结果存储服务
 * <p>
 * 使用 Redis 作为后端存储，以支持异步任务状态的持久化和查询。
 * 每个任务在 Redis 中以一个 Hash 结构存储。
 *
 * @author [Your Name or Lion Li]
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PROCESSING, // 处理中
        COMPLETED,  // 已完成
        FAILED      // 已失败
    }

    // Redis Hash 中的字段名常量
    private static final String KEY_STATUS = "status";
    private static final String KEY_RESULT = "result";
    private static final String KEY_ERROR = "error";

    /**
     * 任务数据在 Redis 中的缓存过期时间，例如设置为1天
     */
    private static final Duration TASK_EXPIRATION = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper; // Spring Boot 自动配置的 Jackson ObjectMapper

    @Autowired
    public StorageService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据任务ID生成在 Redis 中对应的 Key.
     * 使用 "shp:task:" 作为前缀，方便管理和区分.
     *
     * @param taskId 任务唯一ID
     * @return Redis Key, e.g., "shp:task:123e4567-e89b-12d3-a456-426614174000"
     */
    private String getTaskKey(String taskId) {
        return "shp:task:" + taskId;
    }

    /**
     * 更新任务状态
     *
     * @param taskId 任务ID
     * @param status 新的状态
     */
    public void updateTaskStatus(String taskId, TaskStatus status) {
        String taskKey = getTaskKey(taskId);
        redisTemplate.opsForHash().put(taskKey, KEY_STATUS, status.name());
        // 每次更新都重置过期时间
        redisTemplate.expire(taskKey, TASK_EXPIRATION);
    }

    /**
     * 存储任务成功后的结果
     *
     * @param taskId 任务ID
     * @param result 解析出的属性列表
     */
    public void storeTaskResult(String taskId, List<Map<String, Object>> result) {
        try {
            // 将结果对象序列化为JSON字符串进行存储
            String jsonResult = objectMapper.writeValueAsString(result);
            String taskKey = getTaskKey(taskId);
            redisTemplate.opsForHash().put(taskKey, KEY_RESULT, jsonResult);
            redisTemplate.expire(taskKey, TASK_EXPIRATION);
        } catch (JsonProcessingException e) {
            log.error("序列化任务结果失败, taskId: {}", taskId, e);
            // 如果序列化失败，更新任务状态为FAILED
            storeTaskError(taskId, "内部错误：无法序列化任务结果。");
            updateTaskStatus(taskId, TaskStatus.FAILED);
        }
    }

    /**
     * 存储任务失败后的错误信息
     *
     * @param taskId       任务ID
     * @param errorMessage 错误信息
     */
    public void storeTaskError(String taskId, String errorMessage) {
        String taskKey = getTaskKey(taskId);
        redisTemplate.opsForHash().put(taskKey, KEY_ERROR, errorMessage);
        redisTemplate.expire(taskKey, TASK_EXPIRATION);
    }

    /**
     * 获取任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态枚举，如果任务不存在则返回null
     */
    public TaskStatus getTaskStatus(String taskId) {
        String taskKey = getTaskKey(taskId);
        Object statusObj = redisTemplate.opsForHash().get(taskKey, KEY_STATUS);
        if (statusObj == null) {
            return null;
        }
        try {
            return TaskStatus.valueOf(statusObj.toString());
        } catch (IllegalArgumentException e) {
            log.warn("在Redis中发现无效的任务状态值, taskId: {}, value: {}", taskId, statusObj);
            return null;
        }
    }

    /**
     * 获取任务结果
     *
     * @param taskId 任务ID
     * @return 解析出的属性列表，如果任务未完成或不存在则返回null
     */
    public List<Map<String, Object>> getTaskResult(String taskId) {
        String taskKey = getTaskKey(taskId);
        Object resultObj = redisTemplate.opsForHash().get(taskKey, KEY_RESULT);
        if (resultObj == null) {
            return null;
        }
        try {
            // 将从Redis取出的JSON字符串反序列化为Java对象
            return objectMapper.readValue(resultObj.toString(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            log.error("反序列化任务结果失败, taskId: {}", taskId, e);
            return null;
        }
    }

    /**
     * 获取任务错误信息
     *
     * @param taskId 任务ID
     * @return 错误信息字符串，如果没有错误则返回null
     */
    public String getTaskError(String taskId) {
        String taskKey = getTaskKey(taskId);
        Object errorObj = redisTemplate.opsForHash().get(taskKey, KEY_ERROR);
        return errorObj != null ? errorObj.toString() : null;
    }

    /**
     * 清理指定任务的所有相关数据
     *
     * @param taskId 任务ID
     */
    public void clearTask(String taskId) {
        String taskKey = getTaskKey(taskId);
        redisTemplate.delete(taskKey);
    }
}
