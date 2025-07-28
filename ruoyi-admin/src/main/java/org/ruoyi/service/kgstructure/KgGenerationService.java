package org.ruoyi.service.kgstructure;

import org.ruoyi.controller.kgstructure.dto.*;
import org.ruoyi.service.kgstructure.model.DataSource;
import org.ruoyi.service.neo4j.Neo4jService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class KgGenerationService {

    // [新增] 添加日志记录器
    private static final Logger log = LoggerFactory.getLogger(KgGenerationService.class);

    @Autowired
    private DataSourceCacheService dataSourceCacheService;

    @Autowired
    private Neo4jService neo4jService;

    public GraphData generateKnowledgeGraph(KgGenerationRequest request) {

        log.info("开始生成知识图谱...");

        // 1. 获取所有需要的数据源
        List<DataSource> dataSources = dataSourceCacheService.getAllDataSources(request.getDataSourceIds());
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("未找到有效的数据源");
        }
        log.info("已加载 {} 个数据源。", dataSources.size());

        // 2. 聚合所有数据源的 features 和 spatial relationships
        List<DataSource.Feature> allFeatures = new ArrayList<>();
        List<DataSource.SpatialRelationship> allSpatialRelationships = new ArrayList<>();

        for (DataSource ds : dataSources) {
            String prefix = ds.getName() + "_";
            ds.getFeatures().forEach(feature -> {
                DataSource.Feature newFeature = new DataSource.Feature();
                newFeature.setFeatureId(prefix + feature.getFeatureId());
                newFeature.setAttributes(feature.getAttributes());
                allFeatures.add(newFeature);
            });
            if (ds.getSpatialRelationships() != null) {
                ds.getSpatialRelationships().forEach(rel -> {
                    DataSource.SpatialRelationship newRel = new DataSource.SpatialRelationship();
                    newRel.setSourceFeatureId(prefix + rel.getSourceFeatureId());
                    newRel.setTargetFeatureId(prefix + rel.getTargetFeatureId());
                    newRel.setType(rel.getType());
                    allSpatialRelationships.add(newRel);
                });
            }
        }
        log.info("聚合后总共有 {} 个要素和 {} 条空间关系。", allFeatures.size(), allSpatialRelationships.size());

        // --- 节点生成 ---
        Map<String, GraphNode> nodeMapById = new HashMap<>();
        allFeatures.forEach(feature -> {
            Map<String, Object> item = feature.getAttributes();
            request.getNodeDefinitions().forEach(def -> {
                if (!StringUtils.hasText(def.getName()) || !StringUtils.hasText(def.getField()) || !item.containsKey(def.getField())) {
                    return;
                }
                if (StringUtils.hasText(def.getFilterField()) && StringUtils.hasText(def.getFilterValue())) {
                    if (!String.valueOf(item.get(def.getFilterField())).trim().equals(def.getFilterValue().trim())) {
                        return;
                    }
                }
                Object nodeValueObj = item.get(def.getField());
                if (nodeValueObj == null || String.valueOf(nodeValueObj).isEmpty()) {
                    return;
                }
                String nodeValue = String.valueOf(nodeValueObj);
                String nodeId = def.getName() + "_" + nodeValue;

                GraphNode node = nodeMapById.computeIfAbsent(nodeId, id -> {
                    GraphNode newNode = new GraphNode();
                    newNode.setId(id);
                    newNode.setType(def.getName());
                    newNode.setGroup(StringUtils.hasText(def.getGroup()) ? def.getGroup() : null);
                    String displayLabel = StringUtils.hasText(def.getLabelField()) && item.containsKey(def.getLabelField())
                            ? String.valueOf(item.get(def.getLabelField()))
                            : nodeValue;
                    newNode.setLabel(displayLabel);
                    Map<String, Object> properties = new HashMap<>();
                    properties.put("featureIds", new ArrayList<>(Collections.singletonList(feature.getFeatureId())));
                    newNode.setProperties(properties);
                    return newNode;
                });

                @SuppressWarnings("unchecked")
                List<String> featureIds = (List<String>) node.getProperties().get("featureIds");
                if (!featureIds.contains(feature.getFeatureId())) {
                    featureIds.add(feature.getFeatureId());
                }
                if (def.getProperties() != null) {
                    def.getProperties().forEach(prop -> {
                        if (!node.getProperties().containsKey(prop) && item.containsKey(prop)) {
                            node.getProperties().put(prop, item.get(prop));
                        }
                    });
                }
                node.getProperties().putIfAbsent(def.getField(), item.get(def.getField()));
            });
        });

        List<GraphNode> allNodes = new ArrayList<>(nodeMapById.values());
        log.info("生成了 {} 个节点。", allNodes.size());

        // --- 关系生成 ---
        List<GraphEdge> allEdges = new ArrayList<>();
        Set<String> uniqueEdgeKeys = new HashSet<>();

        // ... (空间关系和字段连接关系生成逻辑保持不变) ...
        // [此处省略了关系生成的详细代码，因为它们不变]
        // --- 步骤 2a: 处理空间关系 ---
        Map<String, List<GraphNode>> nodesByFeatureId = new HashMap<>();
        allNodes.forEach(node -> {
            @SuppressWarnings("unchecked")
            List<String> fids = (List<String>) node.getProperties().get("featureIds");
            if (fids != null) {
                fids.forEach(fid -> nodesByFeatureId.computeIfAbsent(fid, k -> new ArrayList<>()).add(node));
            }
        });

        Map<String, String> spatialRelationMap = Map.of(
                "TOUCHES", "邻接", "OVERLAPS", "重叠", "CONTAINS", "包含",
                "WITHIN", "在...之内", "INTERSECTS", "相交", "EQUALS", "空间相等",
                "DISJOINT", "相离", "CROSSES", "穿越"
        );

        List<RelationshipDefinitionDto> spatialRules = request.getRelationshipDefinitions().stream()
                .filter(r -> "spatial".equals(r.getMethod()))
                .toList();

        if(!spatialRules.isEmpty()){
            allSpatialRelationships.forEach(rel -> {
                List<GraphNode> sourceNodes = nodesByFeatureId.getOrDefault(rel.getSourceFeatureId(), Collections.emptyList());
                List<GraphNode> targetNodes = nodesByFeatureId.getOrDefault(rel.getTargetFeatureId(), Collections.emptyList());

                if (sourceNodes.isEmpty() || targetNodes.isEmpty()) return;

                spatialRules.forEach(rule -> {
                    findAndAddSpatialEdge(sourceNodes, targetNodes, rule.getSource(), rule.getTarget(), rel, spatialRelationMap, allEdges, uniqueEdgeKeys);
                    findAndAddSpatialEdge(targetNodes, sourceNodes, rule.getSource(), rule.getTarget(), rel, spatialRelationMap, allEdges, uniqueEdgeKeys);
                });
            });
        }

        // --- 步骤 2b: 处理字段连接关系 ---
        List<RelationshipDefinitionDto> fieldRules = request.getRelationshipDefinitions().stream()
                .filter(r -> "field".equals(r.getMethod()))
                .toList();

        fieldRules.forEach(rule -> {
            List<GraphNode> sourceNodes = allNodes.stream()
                    .filter(n -> rule.getSource().equals(n.getType()) || rule.getSource().equals(n.getGroup()))
                    .toList();
            List<GraphNode> targetNodes = allNodes.stream()
                    .filter(n -> rule.getTarget().equals(n.getType()) || rule.getTarget().equals(n.getGroup()))
                    .toList();

            if(sourceNodes.isEmpty() || targetNodes.isEmpty()) return;

            Map<Object, List<GraphNode>> sourceNodeMapByFK = new HashMap<>();
            sourceNodes.forEach(sNode -> {
                Object key = sNode.getProperties().get(rule.getSourceForeignKey());
                if(key != null) {
                    sourceNodeMapByFK.computeIfAbsent(key, k -> new ArrayList<>()).add(sNode);
                }
            });

            if(sourceNodeMapByFK.isEmpty()) return;

            targetNodes.forEach(tNode -> {
                Object key = tNode.getProperties().get(rule.getTargetForeignKey());
                if(sourceNodeMapByFK.containsKey(key)){
                    sourceNodeMapByFK.get(key).forEach(sNode -> {
                        if(!sNode.getId().equals(tNode.getId())) {
                            String edgeLabel = StringUtils.hasText(rule.getType()) ? rule.getType() : "相关";
                            GraphEdge edge = new GraphEdge();
                            edge.setId("e_field_" + sNode.getId() + "_" + tNode.getId() + "_" + edgeLabel);
                            edge.setSource(sNode.getId());
                            edge.setTarget(tNode.getId());
                            edge.setLabel(edgeLabel);

                            String edgeKey = Stream.of(sNode.getId(), tNode.getId()).sorted().collect(Collectors.joining("-")) + "-" + edgeLabel;
                            if (uniqueEdgeKeys.add(edgeKey)) {
                                allEdges.add(edge);
                            }
                        }
                    });
                }
            });
        });

        log.info("生成了 {} 条边。", allEdges.size());


        // ==========================================================
        // === [新增] 调用 Neo4jService 将图谱持久化 ===
        // ==========================================================
        try {
            // === 清空数据库（可注释掉以取消该功能） ===
            neo4jService.clearDatabase();

            // 调用服务保存图谱数据
            neo4jService.saveGraph(allNodes, allEdges);

        } catch (Exception e) {
            log.error("知识图谱持久化到 Neo4j 失败。", e);
            // 向上抛出运行时异常，触发全局异常处理，向前端返回500错误
            throw new RuntimeException("图谱计算完成，但存入数据库失败: " + e.getMessage(), e);
        }

        // --- 步骤 3: 返回结果给前端 ---
        GraphData finalGraph = new GraphData();
        finalGraph.setNodes(allNodes);
        finalGraph.setEdges(allEdges);

        log.info("知识图谱生成和持久化全部完成。");
        return finalGraph;
    }

    private void findAndAddSpatialEdge(List<GraphNode> nodesA, List<GraphNode> nodesB, String ruleSource, String ruleTarget, DataSource.SpatialRelationship rel, Map<String, String> labelMap, List<GraphEdge> allEdges, Set<String> uniqueEdgeKeys) {
        Optional<GraphNode> sourceNodeOpt = nodesA.stream().filter(n -> ruleSource.equals(n.getType()) || ruleSource.equals(n.getGroup())).findFirst();
        Optional<GraphNode> targetNodeOpt = nodesB.stream().filter(n -> ruleTarget.equals(n.getType()) || ruleTarget.equals(n.getGroup())).findFirst();

        if (sourceNodeOpt.isPresent() && targetNodeOpt.isPresent()) {
            GraphNode sourceNode = sourceNodeOpt.get();
            GraphNode targetNode = targetNodeOpt.get();
            if (sourceNode.getId().equals(targetNode.getId())) return;

            String edgeLabel = labelMap.getOrDefault(rel.getType().toUpperCase(), rel.getType());
            String edgeKey = Stream.of(sourceNode.getId(), targetNode.getId()).sorted().collect(Collectors.joining("-")) + "-" + edgeLabel;

            if (uniqueEdgeKeys.add(edgeKey)) {
                GraphEdge edge = new GraphEdge();
                edge.setId("e_spatial_" + sourceNode.getId() + "_" + targetNode.getId() + "_" + rel.getType());
                edge.setSource(sourceNode.getId());
                edge.setTarget(targetNode.getId());
                edge.setLabel(edgeLabel);
                allEdges.add(edge);
            }
        }
    }
}