package org.ruoyi.service.RAG;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class BuildProgressWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BuildProgressWebSocketHandler.class);

    // 使用线程安全的 Map 来存储 session，键是 task_id
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket 连接已建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 客户端发送的第一条消息应该是 task_id
        String taskId = message.getPayload();
        log.info("WebSocket 会话 {} 注册了任务 ID: {}", session.getId(), taskId);
        // 将 session 与 task_id 关联起来
        sessions.put(taskId, session);
    }

    /**
     * 向指定任务的客户端发送进度信息
     * @param taskId 任务ID
     * @param progressJson 进度信息的JSON字符串
     */
    public void sendProgressUpdate(String taskId, String progressJson) {
        WebSocketSession session = sessions.get(taskId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(progressJson));
            } catch (IOException e) {
                log.error("发送 WebSocket 消息失败 for task {}: {}", taskId, e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket 连接已关闭: {}, 状态: {}", session.getId(), status);
        // 移除关闭的 session
        sessions.values().remove(session);
    }
}
