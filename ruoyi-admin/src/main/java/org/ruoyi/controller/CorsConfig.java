package org.ruoyi.controller;   // 包名保持与你现有一致

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

//    @Bean
//    public CorsFilter corsFilter() {
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowCredentials(true);
//        // 同时支持 localhost 和 127.0.0.1
//        config.addAllowedOriginPattern("http://localhost:5666");
//        config.addAllowedOriginPattern("http://127.0.0.1:5666");
//
//        config.addAllowedHeader("*");
//        config.addAllowedMethod("*");
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//        // 如果 neo4j 那一套仍需要额外限制，可单独再注册一条
//        // source.registerCorsConfiguration("/neo4j/**", neo4jConfig());
//
//        return new CorsFilter(source);
//    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    public CorsFilter corsFilter() {
        // 1. 创建 CorsConfiguration 对象
        CorsConfiguration config = new CorsConfiguration();

        // 2. 设置允许的源
        // 在开发环境中，可以暂时设置为 "*" 允许所有源
        // 在生产环境中，应该设置为你的前端域名，例如 "http://yourdomain.com"
        config.addAllowedOriginPattern("*");

        // 3. 设置是否发送 Cookie 信息
        config.setAllowCredentials(true);

        // 4. 设置允许的请求方式
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        // ... 或者直接 config.addAllowedMethod("*");

        // 5. 设置允许的头信息
        config.addAllowedHeader("*");

        // 6. 为所有接口路径应用这个配置
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // 7. 返回新的 CorsFilter
        return new CorsFilter(source);
    }
}