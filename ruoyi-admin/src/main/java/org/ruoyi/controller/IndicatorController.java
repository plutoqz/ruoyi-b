package org.ruoyi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

//@CrossOrigin(origins = "*")
//@RestController
//@RequestMapping("/indicators") // 使用一个新的基础路径，与 /neo4j 区分开
//public class IndicatorController {
//
//    private final IndicatorService indicatorService;
//    private static final Logger log = LoggerFactory.getLogger(IndicatorController.class);
//
//    @Autowired
//    public IndicatorController(IndicatorService indicatorService) {
//        this.indicatorService = indicatorService;
//    }
//
//    @GetMapping("/value")
//    public ResponseEntity<IndicatorValueResponse> getIndicatorValue(
//            @RequestParam String villageName,
//            @RequestParam String indicatorName,
//            @RequestParam String timeTag) {
//
//        log.info("收到指标获取请求: villageName={}, indicatorName={}, timeTag={}", villageName, indicatorName, timeTag);
//
//        // Service层会处理空值或错误，这里直接调用
//        double value = indicatorService.getIndicatorValue(villageName, indicatorName, timeTag);
//
//        IndicatorValueResponse response = new IndicatorValueResponse(villageName, indicatorName, timeTag, value);
//
//        return ResponseEntity.ok(response);
//    }
//}
