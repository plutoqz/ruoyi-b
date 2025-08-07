//package org.ruoyi.controller;   // 包名保持与你现有一致
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
//import org.springframework.web.filter.CorsFilter;
//
//@Configuration
//public class CorsConfig {
//
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
//}