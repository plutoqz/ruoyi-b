package org.ruoyi.service.RAG;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.ResponseEntity;

public interface IRagService {
    /**
     * 调用 RAG 服务进行查询
     * @param question 用户的问题
     * @return RAG 服务返回的答案
     */
//    String queryRag(String question);
    SseEmitter streamQueryRag(String question);

//    ResponseEntity<String> getDocuments();
//
//    ResponseEntity<String> getGraphData();
    Object getDocuments();
    Object getGraphData();
}
