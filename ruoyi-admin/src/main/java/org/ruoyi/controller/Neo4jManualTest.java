package org.ruoyi.controller;

import org.neo4j.driver.AuthTokens;  // 认证令牌生成器
import org.neo4j.driver.Driver;      // 驱动接口
import org.neo4j.driver.GraphDatabase; // 驱动实例化入口
import org.neo4j.driver.Session;     // 会话管理
import org.neo4j.driver.Result;      // 查询结果处理
import org.neo4j.driver.exceptions.ClientException; // 异常处理

public class Neo4jManualTest {
    public static void main(String[] args) {
        // Neo4j 连接信息（根据实际情况修改）
        String uri = "bolt://localhost:7687";
        String username = "neo4j";
        String password = "neo4j";

        // 使用 try-with-resources 确保资源释放
        try (Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
             Session session = driver.session()) { // 创建会话

            // 执行简单查询
            Result result = session.run("MATCH (n)-[r]->(m) RETURN n,r,m LIMIT 20");

            // 处理结果
            if (result.hasNext()) {
                System.out.println("✅ 连接成功，找到节点：");
                while (result.hasNext()) {
                    org.neo4j.driver.Record record = result.next();
                    org.neo4j.driver.Value nodeValue = record.get("n");
                    if (nodeValue.isNull()) {
                        System.out.println("节点为空");
                        continue;
                    }
                    org.neo4j.driver.types.Node node = nodeValue.asNode();

                    // 输出节点的标签
                    System.out.println("节点标签: " + String.join(", ", node.labels()));

                    // 输出节点的属性
                    System.out.println("节点属性:");
                    node.keys().forEach(key -> {
                        System.out.println("  " + key + ": " + node.get(key).asString());
                    });

                    System.out.println("------------------------");
                }
            } else {
                System.out.println("⚠️ 连接成功，但未找到任何节点");
            }

        } catch (ClientException e) {
            System.err.println("🔒 认证失败: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ 连接异常: " + e.getMessage());
        }
    }
}
