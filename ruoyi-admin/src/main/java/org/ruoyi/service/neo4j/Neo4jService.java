package org.ruoyi.service.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.TransientException;
import org.ruoyi.controller.kgstructure.dto.GraphData;
import org.ruoyi.controller.kgstructure.dto.GraphEdge;
import org.ruoyi.controller.kgstructure.dto.GraphNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;

/**
 * 封装所有与 Neo4j 数据库交互的服务 (生产就绪版)。
 */
@Service
public class Neo4jService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jService.class);
    private final Driver driver;

    @Value("${neo4j.batch-size:5000}")
    private int batchSize;

    // [修复] 修改正则表达式，重新允许中文字符 (Unicode 范围 \u4e00-\u9fa5) 并且允许点号
    private static final Pattern VALID_REL_TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5\\.]+$");

    public static final class GraphConstants {
        public static final String UNIQUE_ID = "uniqueId";
        public static final String ID = "id";
        public static final String LABEL = "label";
        public static final String NODE_TYPE = "type";
        public static final String NODE_PROPERTIES = "properties";
        public static final String EDGE_SOURCE_ID = "sourceId";
        public static final String EDGE_TARGET_ID = "targetId";
        public static final String EDGE_REL_TYPE = "relType";
        public static final String EDGE_PROPS = "props";
    }

    @Autowired
    public Neo4jService(Driver driver) {
        this.driver = driver;
        log.info("Neo4jService 初始化完成，并成功注入 Neo4j Driver。");
    }

    /**
     * [修复] 恢复 getDriver() 方法以兼容 Neo4jController。
     * @return 全局的 Neo4j Driver 实例。
     */
    public Driver getDriver() {
        return driver;
    }

    /**
     * [修复] 恢复 clearDatabase() 方法以兼容 KgGenerationService。
     * 这是一个危险操作，会删除数据库中的所有节点和关系。
     */
    public void clearDatabase() {
        log.warn("正在清空整个 Neo4j 数据库...");
        try (Session session = driver.session()) {
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

    public void saveGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
        final List<GraphNode> finalNodes = nodes == null ? new ArrayList<>() : nodes;
        final List<GraphEdge> finalEdges = edges == null ? new ArrayList<>() : edges;

        validateGraphData(finalNodes, finalEdges);

        if (finalNodes.isEmpty() && finalEdges.isEmpty()) {
            log.info("节点和边列表均为空，无需存入 Neo4j。");
            return;
        }

        Set<String> nodeTypes = finalNodes.stream().map(GraphNode::getType).collect(Collectors.toSet());
        ensureConstraintsForLabels(nodeTypes);

        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (Session session = driver.session()) {
                if (!finalNodes.isEmpty()) {
                    importNodes(session, finalNodes);
                }
                if (!finalEdges.isEmpty()) {
                    importEdges(session, finalNodes, finalEdges);
                }
                log.info("成功将 {} 个节点和 {} 条边同步到 Neo4j。", finalNodes.size(), finalEdges.size());
                return;
            } catch (TransientException e) {
                log.warn("保存图谱时遇到可重试的瞬时异常 (尝试 {}/{})。错误: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("重试 {} 次后仍然失败，放弃操作。", maxRetries);
                    throw new RuntimeException("保存图谱到 Neo4j 失败，已达到最大重试次数。", e);
                }
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待时被中断。", ie);
                }
            } catch (Exception e) {
                log.error("保存图谱到 Neo4j 失败。", e);
                throw new RuntimeException("保存图谱到 Neo4j 失败。", e);
            }
        }
    }

    private void importNodes(Session session, List<GraphNode> nodes) {
        log.info("开始分批导入 {} 个节点...", nodes.size());
        for (int i = 0; i < nodes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, nodes.size());
            List<GraphNode> batch = nodes.subList(i, end);
            session.writeTransaction(tx -> {
                createOrUpdateNodesInTransaction(tx, batch);
                return null;
            });
            log.debug("已处理 {}/{} 个节点。", end, nodes.size());
        }
        log.info("所有节点导入完成。");
    }

    private void importEdges(Session session, List<GraphNode> nodes, List<GraphEdge> edges) {
        log.info("开始预处理和分组 {} 条边以优化导入性能...", edges.size());
        Map<String, String> nodeIdToTypeMap = nodes.stream()
                .collect(Collectors.toMap(GraphNode::getId, GraphNode::getType, (t1, t2) -> t1));

        Map<List<String>, List<GraphEdge>> groupedEdges = edges.stream()
                .collect(Collectors.groupingBy(edge -> {
                    String sourceType = nodeIdToTypeMap.get(edge.getSource());
                    String targetType = nodeIdToTypeMap.get(edge.getTarget());
                    if (sourceType == null) {
                        throw new IllegalArgumentException("数据不一致：边的源节点 ID '" + edge.getSource() + "' 在提供的节点列表中不存在。");
                    }
                    if (targetType == null) {
                        throw new IllegalArgumentException("数据不一致：边的目标节点 ID '" + edge.getTarget() + "' 在提供的节点列表中不存在。");
                    }
                    return List.of(sourceType, edge.getLabel(), targetType);
                }));
        log.info("边被分成了 {} 个不同的组进行处理。", groupedEdges.size());

        for (Map.Entry<List<String>, List<GraphEdge>> entry : groupedEdges.entrySet()) {
            List<String> groupKey = entry.getKey();
            List<GraphEdge> edgeGroup = entry.getValue();
            String sourceType = groupKey.get(0);
            String relType = groupKey.get(1);
            String targetType = groupKey.get(2);

            log.info("开始分批导入组 (:{})-[:{}]->(:{})，共 {} 条边...", sourceType, relType, targetType, edgeGroup.size());
            for (int i = 0; i < edgeGroup.size(); i += batchSize) {
                int end = Math.min(i + batchSize, edgeGroup.size());
                List<GraphEdge> batch = edgeGroup.subList(i, end);
                session.writeTransaction(tx -> {
                    createOrUpdateEdgesInTransaction(tx, batch, sourceType, targetType);
                    return null;
                });
                log.debug("组 (:{})-[:{}]->(:{}) 已处理 {}/{} 条边。", sourceType, relType, targetType, end, edgeGroup.size());
            }
        }
        log.info("所有边导入完成。");
    }

    private void ensureConstraintsForLabels(Set<String> labels) {
        if (CollectionUtils.isEmpty(labels)) return;
        log.info("正在为以下 Labels 确保唯一性约束: {}", labels);
        try (Session session = driver.session()) {
            for (String label : labels) {
                String constraintQuery = String.format(
                        "CREATE CONSTRAINT IF NOT EXISTS FOR (n:%s) REQUIRE n.%s IS UNIQUE", label, GraphConstants.UNIQUE_ID
                );
                try {
                    session.run(constraintQuery);
                } catch (Neo4jException e) {
                    if (e.code().contains("Schema.EquivalentSchemaRuleAlreadyExists")) {
                        log.warn("尝试创建约束时发生并发冲突，但约束已存在，忽略此错误。Label: {}", label);
                    } else {
                        throw e;
                    }
                }
            }
            log.info("所有 Label 的约束已确保存在。");
        } catch (Exception e) {
            log.error("在 Neo4j 中创建按 Label 的约束时失败。性能可能受影响。", e);
        }
    }

    private void validateGraphData(List<GraphNode> nodes, List<GraphEdge> edges) {
        nodes.forEach(node -> {
            if (!StringUtils.hasText(node.getId())) throw new IllegalArgumentException("发现节点的 ID 为空或 null，节点数据: " + node.getProperties());
            if (!StringUtils.hasText(node.getType())) throw new IllegalArgumentException("发现节点的类型(Type/Label)为空或 null，节点 ID: " + node.getId());
        });
        edges.forEach(edge -> {
            if (!StringUtils.hasText(edge.getSource()) || !StringUtils.hasText(edge.getTarget())) throw new IllegalArgumentException("发现边的源节点或目标节点 ID 为空，边 ID: " + edge.getId());
            if (!StringUtils.hasText(edge.getLabel())) throw new IllegalArgumentException("发现边的标签(Label/Type)为空，边 ID: " + edge.getId());

            if (!VALID_REL_TYPE_PATTERN.matcher(edge.getLabel()).matches()) {
                // [修复] 更新错误信息，使其更准确
                throw new IllegalArgumentException(String.format(
                        "关系类型 '%s' 包含非法字符。只允许中英文字母、数字和下划线。边 ID: %s", edge.getLabel(), edge.getId()
                ));
            }
        });
    }

    private void createOrUpdateNodesInTransaction(Transaction tx, List<GraphNode> nodes) {
        List<Map<String, Object>> nodeProperties = nodes.stream()
                .map(node -> {
                    Map<String, Object> props = new HashMap<>(node.getProperties());
                    props.remove(GraphConstants.UNIQUE_ID);
                    props.remove(GraphConstants.ID);
                    props.remove(GraphConstants.LABEL);

                    return Map.of(
                            GraphConstants.UNIQUE_ID, node.getId(),
                            GraphConstants.LABEL, node.getLabel(),
                            GraphConstants.NODE_TYPE, node.getType(),
                            GraphConstants.NODE_PROPERTIES, props
                    );
                }).collect(Collectors.toList());

        String query = "UNWIND $nodes AS node_data " +
                "MERGE (n {" + GraphConstants.UNIQUE_ID + ": node_data." + GraphConstants.UNIQUE_ID + "}) " +
                "SET n += node_data." + GraphConstants.NODE_PROPERTIES + ", " +
                "    n." + GraphConstants.LABEL + " = node_data." + GraphConstants.LABEL + " " +
                "WITH n, node_data " +
                "CALL apoc.create.addLabels(n, [node_data." + GraphConstants.NODE_TYPE + "]) YIELD node " +
                "RETURN count(node)";
        tx.run(query, Map.of("nodes", nodeProperties));
    }

    private void createOrUpdateEdgesInTransaction(Transaction tx, List<GraphEdge> edges, String sourceType, String targetType) {
        if (CollectionUtils.isEmpty(edges)) return;

        List<Map<String, Object>> edgeDataList = edges.stream()
                .map(edge -> {
                    Map<String, Object> props = new HashMap<>();
                    props.put(GraphConstants.ID, edge.getId());
                    props.put(GraphConstants.LABEL, edge.getLabel());
                    return Map.of(
                            GraphConstants.EDGE_SOURCE_ID, edge.getSource(),
                            GraphConstants.EDGE_TARGET_ID, edge.getTarget(),
                            GraphConstants.EDGE_REL_TYPE, edge.getLabel(),
                            GraphConstants.EDGE_PROPS, props
                    );
                }).toList();

        String cypher = String.format(
                "UNWIND $edges AS e\n" +
                        "MATCH (s:%s {%s: e.%s})\n" +
                        "MATCH (t:%s {%s: e.%s})\n" +
                        "CALL apoc.merge.relationship(s, e.%s, {%s: e.%s.%s}, e.%s, t, e.%s) YIELD rel\n" +
                        "RETURN count(rel) AS cnt",
                sourceType, GraphConstants.UNIQUE_ID, GraphConstants.EDGE_SOURCE_ID,
                targetType, GraphConstants.UNIQUE_ID, GraphConstants.EDGE_TARGET_ID,
                GraphConstants.EDGE_REL_TYPE, GraphConstants.ID, GraphConstants.EDGE_PROPS, GraphConstants.ID,
                GraphConstants.EDGE_PROPS, GraphConstants.EDGE_PROPS
        );

        tx.run(cypher, Map.of("edges", edgeDataList));
    }


    /**
     * 查询全图数据
     * RAG
     * @param limit 限制返回的节点和关系数量，防止前端过载
     */
    public GraphData getFullGraph(int limit) {
        String query = String.format(
                "MATCH (n) WITH n LIMIT %d " +
                        "OPTIONAL MATCH (n)-[r]-(m) " +
                        "RETURN n, r, m", limit
        );

        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                List<Record> records = tx.run(query).list();
                return convertRecordsToGraphData(records);
            });
        }
    }

    // [辅助方法] 将 Neo4j 的查询结果转换为我们的 DTO
    private GraphData convertRecordsToGraphData(List<Record> records) {
        Map<Long, GraphNode> nodes = new HashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (Record record : records) {
            // 处理节点 n
            Node n = record.get("n").asNode();
            if (!nodes.containsKey(n.id())) {
                nodes.put(n.id(), convertNodeToGraphNode(n));
            }
            // 处理节点 m
            Node m = record.get("m").asNode();
            if (m != null && !nodes.containsKey(m.id())) {
                nodes.put(m.id(), convertNodeToGraphNode(m));
            }
            // 处理关系 r
            Relationship r = record.get("r").asRelationship();
            if (r != null) {
                edges.add(convertRelationshipToGraphEdge(r));
            }
        }
        return new GraphData(new ArrayList<>(nodes.values()), edges);
    }

    private GraphNode convertNodeToGraphNode(Node node) {
        GraphNode graphNode = new GraphNode();
        // Neo4j 内部 ID 是 long，我们转为 String
        graphNode.setId(String.valueOf(node.id()));
        graphNode.setLabel(node.get("label").asString(""));
        // 第一个标签作为类型
        graphNode.setType(node.labels().iterator().next());
        graphNode.setProperties(node.asMap());
        return graphNode;
    }

    private GraphEdge convertRelationshipToGraphEdge(Relationship rel) {
        GraphEdge graphEdge = new GraphEdge();
        graphEdge.setId(String.valueOf(rel.id()));
        graphEdge.setSource(String.valueOf(rel.startNodeId()));
        graphEdge.setTarget(String.valueOf(rel.endNodeId()));
        graphEdge.setLabel(rel.type()); // 关系类型就是它的 label
        graphEdge.setProperties(rel.asMap());
        return graphEdge;
    }
}
