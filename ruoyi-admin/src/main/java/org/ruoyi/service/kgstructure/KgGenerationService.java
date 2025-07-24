package org.ruoyi.service.kgstructure;

import org.ruoyi.controller.kgstructure.dto.*;
import org.ruoyi.service.kgstructure.model.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KgGenerationService {

    @Autowired
    private DataSourceCacheService dataSourceCacheService;

    public GraphData generateKnowledgeGraph(KgGenerationRequest request) {

        // 1. 获取所有需要的数据源
        List<DataSource> dataSources = dataSourceCacheService.getAllDataSources(request.getDataSourceIds());
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("未找到有效的数据源");
        }

        // 2. 聚合所有数据源的 features 和 spatial relationships
        // *** START: MODIFICATION - 全局唯一ID处理 ***
        // 我们在聚合数据时，为每个 feature 和 relationship 的 ID 加上其数据源名称作为前缀。
        List<DataSource.Feature> allFeatures = new ArrayList<>();
        List<DataSource.SpatialRelationship> allSpatialRelationships = new ArrayList<>();

        for (DataSource ds : dataSources) {
            String prefix = ds.getName() + "_"; // e.g., "buildings.zip_"

            // 处理 Features
            ds.getFeatures().forEach(feature -> {
                DataSource.Feature newFeature = new DataSource.Feature();
                newFeature.setFeatureId(prefix + feature.getFeatureId()); // 添加前缀
                newFeature.setAttributes(feature.getAttributes());
                allFeatures.add(newFeature);
            });

            // 处理 SpatialRelationships
            if (ds.getSpatialRelationships() != null) {
                ds.getSpatialRelationships().forEach(rel -> {
                    DataSource.SpatialRelationship newRel = new DataSource.SpatialRelationship();
                    newRel.setSourceFeatureId(prefix + rel.getSourceFeatureId()); // 添加前缀
                    newRel.setTargetFeatureId(prefix + rel.getTargetFeatureId()); // 添加前缀
                    newRel.setType(rel.getType());
                    allSpatialRelationships.add(newRel);
                });
            }
        }
        // *** END: MODIFICATION ***


        // ==========================================================
        //  VUE generateKG 逻辑的 Java 实现 (后续代码无需修改)
        // ==========================================================

        Map<String, GraphNode> nodeMapById = new HashMap<>();

        // --- 步骤 1: 节点生成 ---
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
        Set<String> uniqueEdgeKeys = new HashSet<>();
        List<GraphEdge> allEdges = new ArrayList<>();

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
                .collect(Collectors.toList());

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
                .collect(Collectors.toList());

        fieldRules.forEach(rule -> {
            List<GraphNode> sourceNodes = allNodes.stream()
                    .filter(n -> rule.getSource().equals(n.getType()) || rule.getSource().equals(n.getGroup()))
                    .collect(Collectors.toList());
            List<GraphNode> targetNodes = allNodes.stream()
                    .filter(n -> rule.getTarget().equals(n.getType()) || rule.getTarget().equals(n.getGroup()))
                    .collect(Collectors.toList());

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

                            String edgeKey = java.util.stream.Stream.of(sNode.getId(), tNode.getId()).sorted().collect(Collectors.joining("-")) + "-" + edgeLabel;
                            if (uniqueEdgeKeys.add(edgeKey)) {
                                allEdges.add(edge);
                            }
                        }
                    });
                }
            });
        });

        // --- 步骤 3: 返回结果 ---
        GraphData finalGraph = new GraphData();
        finalGraph.setNodes(allNodes);
        finalGraph.setEdges(allEdges);
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
            String edgeKey = java.util.stream.Stream.of(sourceNode.getId(), targetNode.getId()).sorted().collect(Collectors.joining("-")) + "-" + edgeLabel;

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