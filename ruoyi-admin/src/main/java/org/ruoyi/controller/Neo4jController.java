package org.ruoyi.controller;

import org.neo4j.driver.Value;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.ruoyi.service.neo4j.Neo4jService;
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

    @PostMapping("/indicator") // 我们创建一个新的路径 /neo4j/indicator
    public Map<String, Object> getIndicatorValue(@RequestBody Map<String, String> request) {
        String villageName = request.get("villageName");
        String indicatorName = request.get("indicatorName");
        String timeTag = request.get("timeTag");

        // --- 这是后端日志的关键，如果能看到这一行，说明请求已成功到达 ---
        log.info("收到指标获取请求: villageName={}, indicatorName={}, timeTag={}", villageName, indicatorName, timeTag);

        Map<String, Object> response = new HashMap<>();

        // 参数校验
        if (villageName == null || indicatorName == null || timeTag == null) {
            response.put("error", "Missing required parameters in request body.");
            return response;
        }

        String cypherQuery = "MATCH (i:指标) WHERE i.权属单位 = $villageName AND i.指标名称 = $indicatorName AND i.更新时间 = $timeTag RETURN i.指标值 as value";
        // 根据指标名称选择不同的Cypher查询
//        switch (indicatorName) {
//            case "耕地总面积":
//                cypherQuery = "MATCH (i:指标) " +
//                        "WHERE i.权属单位 = $villageName " +
//                        "  AND i.指标名称 = $indicatorName " +
//                        "  AND i.更新时间 = $timeTag " +
//                        "RETURN i.指标值 as value";
//                break;
//            case "耕地地块数量":
//                cypherQuery = "MATCH (i:指标) " +
//                        "WHERE i.权属单位 = $villageName " +
//                        "  AND i.指标名称 = $indicatorName " +
//                        "  AND i.更新时间 = $timeTag " +
//                        "RETURN i.指标值 as value";
//                break;
//            // 在这里添加您需要的其他 case...
//            case "非粮化面积":
//                cypherQuery = "MATCH (i:指标) " +
//                        "WHERE i.权属单位 = $villageName " +
//                        "  AND i.指标名称 = $indicatorName " +
//                        "  AND i.更新时间 = $timeTag " +
//                        "RETURN i.指标值 as value";
//                break;
//            case "耕种面积":
//                cypherQuery = "MATCH (p:地块 {ZLDWMC: $villageName}) " +
//                        "WHERE p.更新时间 = $timeTag OR p.更新时间 IS NULL " +
//                        "WITH sum(toFloat(coalesce(p.Shape_Area, p.TBMJ, 0))) as totalArea, " +
//                        "     sum(CASE WHEN p.ZZSXMC = '未耕种' THEN toFloat(coalesce(p.Shape_Area, p.TBMJ, 0)) ELSE 0 END) as uncultivatedArea " +
//                        "RETURN totalArea - uncultivatedArea as value";
//                break;
//            default:
//                log.warn("不支持的指标名称: {}", indicatorName);
//                response.put("value", 0.0);
//                response.put("message", "Unsupported indicator name");
//                return response;
//        }

        try (Session session = neo4jService.getDriver().session()) {
            Map<String, Object> params = Map.of(
                    "villageName", villageName,
                    "indicatorName", indicatorName,
                    "timeTag", timeTag
            );

            log.info("执行指标查询: [{}], 参数: {}", cypherQuery, params);

            Result result = session.run(cypherQuery, params);
            log.info("iii",result);
            double value = 0.0;
            if (result.hasNext()) {
                Value resultValue = result.single().get("value");
                if (!resultValue.isNull()) {
                    value = resultValue.asDouble();
                }
            }

            // 构造一个与前端期望的 DTO 类似的 Map 结构
            response.put("villageName", villageName);
            response.put("indicatorName", indicatorName);
            response.put("timeTag", timeTag);
            response.put("value", value);

        } catch (Exception e) {
            log.error("执行指标查询失败 [Indicator: {}]: {}", indicatorName, e.getMessage(), e);
            response.put("error", "Failed to execute query.");
            response.put("value", 0.0); // 出错时也返回一个安全的默认值
        }

        return response;
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