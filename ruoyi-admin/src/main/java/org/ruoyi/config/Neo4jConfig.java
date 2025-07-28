package org.ruoyi.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
//@Component
public class Neo4jConfig {

    @Value("${spring.neo4j.uri}")
    private String uri;

    @Value("${spring.neo4j.authentication.username}")
    private String username;

    @Value("${spring.neo4j.authentication.password}")
    private String password;

    @Bean(destroyMethod = "close") // Spring 会在应用关闭时自动调用 driver.close()
    public Driver neo4jDriver() {
        // Driver 实例作为 Spring Bean 被管理
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}

