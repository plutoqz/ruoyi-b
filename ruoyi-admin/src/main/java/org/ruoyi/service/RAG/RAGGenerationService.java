package org.ruoyi.service.RAG;

import org.ruoyi.controller.kgstructure.dto.GraphData;
import org.ruoyi.service.neo4j.Neo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
@Service
public class RAGGenerationService {
    private final RestTemplate restTemplate;
    private final Neo4jService neo4jService;

    @Value("${rag.service.url}") // 注入 Python 服务地址
    private String ragServiceUrl;

    @Autowired
    public RAGGenerationService(RestTemplate restTemplate, Neo4jService neo4jService) {
        this.restTemplate = restTemplate;
        this.neo4jService = neo4jService;
    }

    /**
     * 从 Python 服务获取图谱数据并存入 Neo4j
     * @param taskId 构建任务的 ID
     */
    public void generateAndSaveGraph(String taskId) {
        String url = ragServiceUrl + "/build/export/" + taskId;

        // 1. 调用 Python API 获取图谱数据
        GraphData graphData = restTemplate.getForObject(url, GraphData.class);

        if (graphData != null) {
            // 2. 调用 Neo4jService 保存图谱
            //    这个 saveGraph 方法会进行增量更新 (MERGE)，不会删除已有内容
            neo4jService.saveGraph(graphData.getNodes(), graphData.getEdges());
        }
    }
}
