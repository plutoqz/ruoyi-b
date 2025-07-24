package org.ruoyi.controller.kgstructure;

import org.ruoyi.controller.kgstructure.dto.GraphData;
import org.ruoyi.controller.kgstructure.dto.KgGenerationRequest;
import org.ruoyi.service.kgstructure.KgGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kg")
public class KgStructureController {

    @Autowired
    private KgGenerationService kgGenerationService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateGraph(@RequestBody KgGenerationRequest request) {
        try {
            GraphData graphData = kgGenerationService.generateKnowledgeGraph(request);
            return ResponseEntity.ok(graphData);
        } catch (Exception e) {
            // 务必记录详细错误日志
            e.printStackTrace();
            return ResponseEntity.status(500).body("图谱生成失败: " + e.getMessage());
        }
    }
}
