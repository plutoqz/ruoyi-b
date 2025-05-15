package org.ruoyi.controller;

import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/neo4j")
public class Neo4jController {

    private final Neo4jService neo4jService;
    private static final Logger log = LoggerFactory.getLogger(Neo4jController.class);

    public Neo4jController(Neo4jService neo4jService) {
        this.neo4jService = neo4jService;
    }

    // 执行 Cypher 查询的接口
    @PostMapping("/query")
    public Map<String, Object> executeQuery(@RequestBody Map<String, String> request) {
        // 如果前端未传入 cypher，则使用默认查询语句
        String cypher = request.get("cypher");
        log.info("执行 Cypher 查询: {}", cypher);

        Map<String, Object> result = new HashMap<>();
        try (Session session = neo4jService.getDriver().session()) {
            List<Map<String, Object>> data = session.readTransaction(tx -> {
                Result queryResult = tx.run(cypher);
                List<Map<String, Object>> resultList = new ArrayList<>();
                while (queryResult.hasNext()) {
                    org.neo4j.driver.Record record = queryResult.next();
                    Map<String, Object> recordMap = new HashMap<>();
                    for (String key : record.keys()) {
                        // 处理节点： n 或 m
                        if ("NODE".equals(record.get(key).type().name())) {
                            org.neo4j.driver.types.Node node = record.get(key).asNode();
                            // 使用自定义 id（如果存在）否则用内部 id 转为字符串
                            String nodeId = node.asMap().containsKey("id") ?
                                    node.get("id").asString() :
                                    String.valueOf(node.id());
                            Map<String, Object> nodeMap = new HashMap<>();
                            nodeMap.put("id", nodeId);
                            nodeMap.put("properties", node.asMap());
                            nodeMap.put("labels", node.labels());
                            nodeMap.put("elementid",node.elementId());
                            recordMap.put(key, nodeMap);
                        }
                        // 处理关系，假设 key 为 "r"
                        else if ("RELATIONSHIP".equals(record.get(key).type().name())) {
                            org.neo4j.driver.types.Relationship rel = record.get(key).asRelationship();
                            Map<String, Object> relMap = new HashMap<>();
                            // 确保返回的 start 与 end 与节点 id 保持一致（这里使用内部 id）
                            relMap.put("start", String.valueOf(rel.startNodeElementId()));
                            relMap.put("end", String.valueOf(rel.endNodeElementId()));
                            relMap.put("type", rel.type());
                            relMap.put("properties", rel.asMap());
                            recordMap.put(key, relMap);
                        } else {
                            recordMap.put(key, record.get(key).asObject());
                        }
                    }
                    resultList.add(recordMap);
                }
                return resultList;
            });
            //result.put("code", 200);
            //result.put("message", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            log.error("查询执行失败: {}", e.getMessage());
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }


    // 一个简单的示例接口，用于测试 Neo4j 连接
    @GetMapping("/testConnection")
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();
        try (Session session = neo4jService.getDriver().session()) {
            Result queryResult = session.run("MATCH (n) RETURN n LIMIT 1"); // 简单查询，验证连接
            if (queryResult.hasNext()) {
                result.put("code", 200);
                result.put("message", "Neo4j 连接成功");
            } else {
                result.put("code", 500);
                result.put("message", "Neo4j 连接失败：无法执行查询");
            }
        } catch (Exception e) {
            log.error("Neo4j 连接测试失败: {}", e.getMessage());
            result.put("code", 500);
            result.put("message", "Neo4j 连接失败: " + e.getMessage());
        }
        return result;
    }
}