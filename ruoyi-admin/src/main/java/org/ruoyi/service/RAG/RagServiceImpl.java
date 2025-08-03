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
    public String queryRag(String question) {
        String queryUrl = ragServiceUrl + "/query";
        log.info("正在向 RAG 服务 [{}] 发送查询: {}", queryUrl, question);

        RagQueryRequest requestPayload = new RagQueryRequest();
        requestPayload.setQuestion(question);

        try {
            // 发起 POST 请求并等待响应
            Mono<RagQueryResponse> responseMono = webClient.post()
                    .uri(queryUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestPayload)
                    .retrieve() // 获取响应体
                    .bodyToMono(RagQueryResponse.class); // 将响应体转换为我们定义的 DTO

            // 在非响应式应用中，可以使用 .block() 来同步等待结果
            // 我们设置一个超时时间，例如120秒，防止无限等待
            RagQueryResponse response = responseMono.block(java.time.Duration.ofSeconds(120));

            if (response != null && response.getAnswer() != null) {
                log.info("从 RAG 服务收到回答");
                return response.getAnswer();
            } else {
                log.error("从 RAG 服务收到空的回答");
                return "未能从RAG服务获取到有效的答案。";
            }

        } catch (Exception e) {
            log.error("调用 RAG 服务时发生异常: {}", e.getMessage());
            // 在实际项目中，可以抛出一个自定义的业务异常
            // throw new ServiceException("调用智能问答服务失败，请稍后再试");
            return "调用智能问答服务时发生错误，请检查后台日志。";
        }
    }
}
