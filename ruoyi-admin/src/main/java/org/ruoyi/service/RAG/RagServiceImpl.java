package org.ruoyi.service.RAG;

import org.ruoyi.controller.RAG.dto.RagQueryRequest;
import org.ruoyi.controller.RAG.dto.RagQueryResponse;
import org.ruoyi.service.RAG.IRagService ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Service
public class RagServiceImpl implements IRagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final WebClient webClient;

    // 从 application.yml 中注入 Python 服务的 URL
    @Value("${rag.service.url}")
    private String ragServiceUrl;

    // 通过构造函数注入 WebClient.Builder
    public RagServiceImpl(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public SseEmitter streamQueryRag(String question) {
        String queryUrl = ragServiceUrl + "/query";
        log.info("正在向 RAG 服务 [{}] 发送流式查询: {}", queryUrl, question);

        RagQueryRequest requestPayload = new RagQueryRequest();
        requestPayload.setQuestion(question);

        // 创建一个 SseEmitter，超时时间设长一点，例如 5 分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        // 使用 WebClient 接收 SSE 流
        Flux<String> streamFlux = webClient.post()
                .uri(queryUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToFlux(String.class); // 将响应体作为字符串流 (Flux)

        // 订阅流，并将每个数据块转发给前端
        streamFlux.subscribe(
                data -> { // onNext: 收到数据时
                    try {
                        // Python 返回的是 "data: ...\n\n"，我们需要把整个字符串转发
                        // SSE 要求每个 event 都有 id, event, data 等字段，这里做个简化
                        emitter.send(SseEmitter.event().data(data));
                    } catch (Exception e) {
                        log.error("转发 SSE 数据失败: {}", e.getMessage());
                        emitter.completeWithError(e); // 发生错误，终止连接
                    }
                },
                error -> { // onError: 发生错误时
                    log.error("调用 RAG 流式服务时发生异常: {}", error.getMessage());
                    emitter.completeWithError(error);
                },
                () -> { // onComplete: 流结束时
                    log.info("RAG 流式服务连接已关闭");
                    emitter.complete(); // 正常结束连接
                }
        );

        // 返回 emitter 给 Controller，Spring MVC 会负责后续的流处理
        return emitter;
    }
}
