package org.ruoyi.controller;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class Neo4jService {
    private Driver driver;
    private final Neo4jConfig neo4jConfig;

    private static final Logger log = LoggerFactory.getLogger(Neo4jService.class);

    public Neo4jService(Neo4jConfig neo4jConfig) {
        this.neo4jConfig = neo4jConfig;
        try {
            // 打印配置值
            log.info("尝试连接 Neo4j: {}", neo4jConfig.getUri());
            log.info("使用用户名: {}", neo4jConfig.getUsername());
            log.info("使用密码: {}", neo4jConfig.getPassword().replaceAll(".", "*")); // 安全打印

            driver = GraphDatabase.driver(neo4jConfig.getUri(), AuthTokens.basic(neo4jConfig.getUsername(), neo4jConfig.getPassword()));
            log.info("Neo4j Driver 初始化成功");
        } catch (Exception e) {
            log.error("Neo4j Driver 初始化失败: {}", e.getMessage());
            // 在这里抛出异常，以便 Spring Boot 可以处理它，或者进行重试等操作
            throw new RuntimeException("Failed to initialize Neo4j driver", e);
        }
    }

    public Driver getDriver() {
        return driver;
    }

    // 在 Spring Boot 应用关闭时关闭 Neo4j 驱动
    public void close() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j Driver 关闭");
        }
    }
}


