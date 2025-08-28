package org.ruoyi.config;

import org.ruoyi.service.RAG.BuildProgressWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private BuildProgressWebSocketHandler buildProgressWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册我们的处理器，并指定路径
        // withSockJS() 提供了对不支持 WebSocket 的浏览器的降级兼容
        registry.addHandler(buildProgressWebSocketHandler, "/ws/build-progress").withSockJS();
    }
}
