package org.ruoyi.controller.shp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    // 使用 ConcurrentHashMap 保证线程安全
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建并存储一个新的 SSE 连接
     * @param taskId 任务ID
     * @return 创建的 SseEmitter
     */
    public SseEmitter createEmitter(String taskId) {
        // 设置超时时间，例如30秒，以避免连接无限期占用资源
        SseEmitter emitter = new SseEmitter(30_000L);

        emitters.put(taskId, emitter);
        log.info("为任务 {} 创建了新的 SSE 连接。", taskId);

        // 连接完成时的回调（无论是成功、超时还是错误）
        emitter.onCompletion(() -> {
            emitters.remove(taskId);
            log.info("任务 {} 的 SSE 连接已完成并移除。", taskId);
        });

        // 连接超时时的回调
        emitter.onTimeout(() -> {
            emitters.remove(taskId);
            log.warn("任务 {} 的 SSE 连接超时。", taskId);
        });

        // 连接发生错误时的回调
        emitter.onError(e -> {
            emitters.remove(taskId);
            log.error("任务 {} 的 SSE 连接发生错误。", taskId, e);
        });

        return emitter;
    }

    /**
     * 向指定的 SSE 连接发送消息
     * @param taskId 任务ID
     * @param data 要发送的数据对象
     */
    public void send(String taskId, Object data) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                // 发送事件，可以指定事件名称，方便前端区分
                emitter.send(SseEmitter.event().name("message").data(data));
            } catch (IOException e) {
                log.error("向任务 {} 发送 SSE 消息失败。", taskId, e);
                // 移除无效的 emitter
                emitters.remove(taskId);
            }
        } else {
            log.warn("尝试向一个不存在或已关闭的 SSE 连接发送消息，任务ID: {}", taskId);
        }
    }

    /**
     * 主动完成并关闭一个 SSE 连接
     * @param taskId 任务ID
     */
    public void complete(String taskId) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            emitter.complete();
            // onCompletion 回调会自动移除
        }
    }
}