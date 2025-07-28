package org.ruoyi.service.neo4j;

import org.neo4j.driver.*;
import org.ruoyi.controller.kgstructure.dto.GraphEdge;
import org.ruoyi.controller.kgstructure.dto.GraphNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 封装所有与 Neo4j 数据库交互的服务.
 * 该服务同时支持原有的 Neo4jController 查询功能和新增的知识图谱构建功能。
 */
@Service
public class Neo4jService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jService.class);
    private final Driver driver;

    /**
     * 通过构造函数注入由 Neo4jConfig 创建的全局 Driver Bean。
     * @param driver Spring 容器管理的 Neo4j Driver 实例。
     */
    @Autowired
    public Neo4jService(Driver driver) {
        this.driver = driver;
        log.info("Neo4jService 初始化完成，并成功注入 Neo4j Driver。");
    }

    /**
     * [保留方法] 为 Neo4jController 提供 Driver 实例。
     * @return 全局的 Neo4j Driver 实例。
     */
    public Driver getDriver() {
        return driver;
    }

    // --- [新增] 为知识图谱构建添加的新方法 ---

    /**
     * 清空整个 Neo4j 数据库。
     * 这是一个危险操作，主要用于开发和测试阶段。
     */
    public void clearDatabase() {
        log.warn("正在清空整个 Neo4j 数据库...");
        try (Session session = driver.session()) {
            // 使用 writeTransaction 确保操作的原子性
            session.writeTransaction(tx -> {
                tx.run("MATCH (n) DETACH DELETE n");
                return 1;
            });
            log.warn("Neo4j 数据库已清空。");
        } catch (Exception e) {
            log.error("清空 Neo4j 数据库失败。", e);
            throw new RuntimeException("清空 Neo4j 数据库失败。", e);
        }
    }

    /**
     * 将生成的图谱数据（节点和边）批量存入 Neo4j。
     * @param nodes 节点列表 DTO
     * @param edges 边列表 DTO
     */
    public void saveGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
        if (nodes == null || nodes.isEmpty()) {
            log.info("节点列表为空，无需存入 Neo4j。");
            return;
        }

        try (Session session = driver.session()) {
            // 在单个事务中执行所有写入操作，保证数据一致性
            session.writeTransaction(tx -> {
                // 1. 批量创建或更新节点
                createOrUpdateNodesInTransaction(tx, nodes);

                // 2. 批量创建或更新关系
                if (edges != null && !edges.isEmpty()) {
                    createOrUpdateEdgesInTransaction(tx, edges);
                }

                return null; // writeTransaction 需要一个返回值
            });
            log.info("成功将 {} 个节点和 {} 条边同步到 Neo4j。", nodes.size(), (edges != null ? edges.size() : 0));
        } catch (Exception e) {
            log.error("保存图谱到 Neo4j 失败。", e);
            throw new RuntimeException("保存图谱到 Neo4j 失败。", e);
        }
    }

    /**
     * 在一个事务中批量创建或更新节点。
     * 使用 MERGE 可以避免重复创建具有相同 uniqueId 的节点。
     */
    private void createOrUpdateNodesInTransaction(Transaction tx, List<GraphNode> nodes) {
        List<Map<String, Object>> nodeProperties = nodes.stream()
                .map(node -> Map.of(
                        "uniqueId", node.getId(),
                        "label", node.getLabel(),
                        "type", node.getType(),
                        "properties", node.getProperties()
                ))
                .collect(Collectors.toList());

        // 使用 MERGE 保证节点的幂等性（如果节点已存在则更新，不存在则创建）
        // 使用 apoc.create.setLabels 动态设置或替换标签
        String query = "UNWIND $nodes AS node_data " +
                "MERGE (n {uniqueId: node_data.uniqueId}) " +
                "SET n.label = node_data.label " +
                "SET n += node_data.properties " +
                "WITH n, node_data " +
                "CALL apoc.create.setLabels(n, [node_data.type]) YIELD node " +
                "RETURN count(node)";

        Result result = tx.run(query, Map.of("nodes", nodeProperties));
        log.info("在 Neo4j 中创建或更新了 {} 个节点。", result.single().get(0).asLong());
    }

    /**
     * 在一个事务中批量创建或更新关系。
     * 使用 MERGE 避免重复创建相同的关系。
     */

    private void createOrUpdateEdgesInTransaction(Transaction tx, List<GraphEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            log.info("边列表为空，跳过关系创建。");
            return;
        }

        // 1. 把 DTO 转成 Cypher 参数，直接把中文 label 当作关系类型
        List<Map<String, Object>> edgeDataList = edges.stream()
                .map(edge -> {
                    Map<String, Object> props = new HashMap<>();
                    props.put("id", edge.getId());
                    props.put("label", edge.getLabel()); // 原始中文标签

                    return Map.of(
                            "sourceId", edge.getSource(),
                            "targetId", edge.getTarget(),
                            "relType", edge.getLabel(),
                            "props", props
                    );
                })
                .toList();

        // 2. 用 apoc.merge.relationship 动态创建／更新中文类型的关系
        String cypher =
                "UNWIND $edges AS e\n" +
                        "MATCH (s {uniqueId: e.sourceId}), (t {uniqueId: e.targetId})\n" +
                        "CALL apoc.merge.relationship(\n" +
                        "  s,                  // 1. 起点节点 (startNode)\n" +
                        "  e.relType,          // 2. 关系类型 (relationshipType)\n" +
                        "  {id: e.props.id},   // 3. 识别属性 (identProps)\n" +
                        "  e.props,            // 4. 创建时属性 (onCreateProps)\n" +
                        "  t,                  // 5. 终点节点 (endNode) - [修正点] t 节点移到这里\n" +
                        "  e.props             // 6. 匹配时属性 (onMatchProps) - [修正点] 更新属性移到最后\n" +
                        ") YIELD rel\n" +
                        "RETURN count(rel) AS cnt";

        Result result = tx.run(cypher, Map.of("edges", edgeDataList));
        long cnt = result.single().get("cnt").asLong();
        log.info("在 Neo4j 中创建或更新了 {} 条关系（直接使用中文类型）。", cnt);
    }

}