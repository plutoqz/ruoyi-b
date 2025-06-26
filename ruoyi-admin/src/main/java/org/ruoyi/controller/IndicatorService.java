package org.ruoyi.controller;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Map;

@Service
public class IndicatorService {

    private final Driver driver;
    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);

    @Autowired
    public IndicatorService(Neo4jService neo4jService) {
        // 依赖注入已有的 Neo4jService，通过它来获取 Driver
        this.driver = neo4jService.getDriver();
    }

    public double getIndicatorValue(String villageName, String indicatorName, String timeTag) {
        String cypherQuery;

        // 根据指标名称选择不同的Cypher查询
        // 注意：这里的属性名（如 ZLDWMC, Shape_Area）需要与您数据库中的完全一致
        switch (indicatorName) {
            case "耕地总面积":
                cypherQuery = "MATCH (p:地块 {ZLDWMC: $villageName}) " +
                        "WHERE p.更新时间 = $timeTag OR p.更新时间 IS NULL " +
                        "RETURN sum(toFloat(coalesce(p.Shape_Area, p.TBMJ, 0))) as value";
                break;
            case "耕地地块数量":
                cypherQuery = "MATCH (p:地块 {ZLDWMC: $villageName}) " +
                        "WHERE p.更新时间 = $timeTag OR p.更新时间 IS NULL " +
                        "RETURN count(p) as value";
                break;
            case "非粮化面积":
                cypherQuery = "MATCH (p:地块 {ZLDWMC: $villageName}) " +
                        "WHERE (p.更新时间 = $timeTag OR p.更新时间 IS NULL) " +
                        "AND NOT p.ZZSXMC IN ['种植粮食作物', '未耕种'] " +
                        "RETURN sum(toFloat(coalesce(p.Shape_Area, p.TBMJ, 0))) as value";
                break;
            case "耕种面积":
                cypherQuery = "MATCH (p:地块 {ZLDWMC: $villageName}) " +
                        "WHERE p.更新时间 = $timeTag OR p.更新时间 IS NULL " +
                        "WITH sum(toFloat(coalesce(p.Shape_Area, p.TBMJ, 0))) as totalArea, " +
                        "     sum(CASE WHEN p.ZZSXMC = '未耕种' THEN toFloat(coalesce(p.Shape_Area, p.TBMJ, 0)) ELSE 0 END) as uncultivatedArea " +
                        "RETURN totalArea - uncultivatedArea as value";
                break;
            default:
                log.warn("不支持的指标名称: {}", indicatorName);
                return 0.0;
        }

        try (Session session = driver.session()) {
            Map<String, Object> params = Map.of(
                    "villageName", villageName,
                    "timeTag", timeTag
            );

            log.info("执行指标查询: [{}], 参数: {}", cypherQuery, params);

            Result result = session.run(cypherQuery, params);
            if (result.hasNext()) {
                Value value = result.single().get("value");
                return value.isNull() ? 0.0 : value.asDouble();
            }
        } catch (Exception e) {
            log.error("执行指标查询失败 [Indicator: {}]: {}", indicatorName, e.getMessage(), e);
        }

        return 0.0;
    }
}
