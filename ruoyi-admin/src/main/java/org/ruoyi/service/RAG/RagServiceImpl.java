package org.ruoyi.service.RAG;

import org.ruoyi.controller.RAG.dto.RagQueryRequest;
import org.ruoyi.controller.RAG.dto.RagQueryResponse;
import org.ruoyi.controller.kgstructure.dto.GraphData;
import org.ruoyi.service.RAG.IRagService ;
import org.ruoyi.service.neo4j.Neo4jService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import org.springframework.scheduling.annotation.Async; // 异步执行
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson for JSON
import java.util.Map;

@Service
public class RagServiceImpl implements IRagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final WebClient webClient;

    // 从 application.yml 中注入 Python 服务的 URL
    @Value("${rag.service.url}")
    private String ragServiceUrl;
    @Autowired
    private Neo4jService neo4jService;
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

    // 注入 RestTemplate 用于同步 GET 请求
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Object getDocuments() {
        String url = ragServiceUrl + "/documents";
        log.info("正在代理请求知识库文档: {}", url);
        // 直接获取 body，让 Spring 去推断类型
        return restTemplate.getForObject(url, Object.class);
    }

    @Override
    public Object getGraphData() {
        String url = ragServiceUrl + "/graph";
        log.info("正在代理请求知识图谱数据: {}", url);
        return restTemplate.getForObject(url, Object.class);
    }

    @Autowired
    private BuildProgressWebSocketHandler webSocketHandler;

    @Autowired
    private ObjectMapper objectMapper; // Spring Boot 自动配置了

    @Override
    public String startBuild(MultipartFile[] files) {
        String url = ragServiceUrl + "/build/start";
        log.info("正在转发文件到 RAG 服务: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        for (MultipartFile file : files) {
            body.add("files", file.getResource());
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 返回的是 {"task_id": "...", "message": "..."}
        Map<String, String> response = restTemplate.postForObject(url, requestEntity, Map.class);

        return response.get("task_id");
    }

    @Override
    @Async // 标注为异步方法，使其在后台线程池中执行，不阻塞主线程
    public void monitorBuildProgress(String taskId) {
        String url = ragServiceUrl + "/build/status/" + taskId;
        boolean isDone = false;

        while (!isDone) {
            try {
                // 轮询 Python 服务的状态接口
                Map<String, Object> status = restTemplate.getForObject(url, Map.class);
                String statusJson = objectMapper.writeValueAsString(status);

                // 通过 WebSocket 将状态转发给前端
                webSocketHandler.sendProgressUpdate(taskId, statusJson);

                // 检查任务是否已完成或失败
                String taskStatus = (String) status.get("status");
                if ("completed".equals(taskStatus) || "failed".equals(taskStatus) || "committed".equals(taskStatus)) {
                    isDone = true;
                    log.info("任务 {} 已完成，停止轮询。", taskId);
                }

                // 等待 1 秒再进行下一次轮询
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("轮询任务 {} 状态失败: {}", taskId, e.getMessage());
                // 发生错误，也应该通知前端并停止
                try {
                    String errorJson = objectMapper.writeValueAsString(Map.of("status", "error", "message", e.getMessage()));
                    webSocketHandler.sendProgressUpdate(taskId, errorJson);
                } catch (Exception ignored) {}
                isDone = true;
            }
        }
    }

    /**
     * [FIXED] 重写了 commitBuild 方法的全部逻辑
     * 现在它负责完整的提交流程：获取数据 -> 导入数据库 -> 通知Python
     */
//    @Override
//    public Object commitBuild(String taskId) {
//        // 步骤 1: 从 Python 服务获取图谱数据。
//        // 使用正确的 /build/preview/{taskId} 接口，而不是错误的 /build/export/
//        String dataFetchUrl = ragServiceUrl + "/build/preview/" + taskId;
//        log.info("正在从 Python 服务获取任务 {} 的图谱数据...", taskId);
//        GraphData graphData = restTemplate.getForObject(dataFetchUrl, GraphData.class);
//
//        if (graphData == null) {
//            log.error("从 Python 服务获取图谱数据失败，返回为空。任务ID: {}", taskId);
//            throw new RuntimeException("获取图谱数据失败");
//        }
//
//        log.info("成功获取图谱数据，包含 {} 个节点和 {} 条边。开始导入 Neo4j...",
//                graphData.getNodes().size(), graphData.getEdges().size());
//
//        // 步骤 2: 将获取到的数据导入到 Neo4j
//        // [FIXED] 将错误的方法名 importGraphData 修正为 Neo4jService 中实际存在的 saveGraph 方法
//        neo4jService.saveGraph(graphData.getNodes(), graphData.getEdges());
//        log.info("图谱数据成功导入 Neo4j 数据库。");
//
//        // 步骤 3: (可选但推荐) 通知 Python 服务，Java 端已处理完毕
//        String commitUrl = ragServiceUrl + "/build/commit/" + taskId;
//        log.info("正在通知 Python 服务任务 {} 已提交。", taskId);
//        return restTemplate.postForObject(commitUrl, null, Object.class);
//    }

    @Override
    public Object abortBuild(String taskId) {
        String url = ragServiceUrl + "/build/abort/" + taskId;
        // RestTemplate 没有 deleteForObject，我们用 exchange
        return restTemplate.exchange(url, HttpMethod.POST, null, Object.class).getBody(); // 注意：Python 端是 POST
    }

    @Override
    public Object getBuildStatus(String taskId) {
        String url = ragServiceUrl + "/build/status/" + taskId;
        return restTemplate.getForObject(url, Object.class);
    }

    @Override
    public Object getBuildPreviewGraph(String taskId) {
        String url = ragServiceUrl + "/build/preview/" + taskId;
        log.info("正在代理请求预览知识图谱: {}", url);
        return restTemplate.getForObject(url, Object.class);
    }

    @Override
    public void notifyPythonToCommit(String taskId) {
        // 这个方法只负责调用 Python 的 commit 接口，让 Python 完成文件操作
        String commitUrl = ragServiceUrl + "/build/commit/" + taskId;
        log.info("正在通知 Python 服务任务 {} 已提交。", taskId);
        try {
            // 使用 postForObject 或 exchange，具体取决于 Python 端如何响应
            restTemplate.postForObject(commitUrl, null, String.class);
            log.info("成功通知 Python 服务提交任务 {}", taskId);
        } catch (RestClientException e) {
            log.error("通知 Python 服务提交任务 {} 失败", taskId, e);
//            throw new ServiceException("通知Python服务失败，请检查服务状态");
        }
    }
}
