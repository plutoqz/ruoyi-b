package org.ruoyi.controller.RAG;

import cn.dev33.satoken.stp.StpUtil;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.controller.kgstructure.dto.GraphData;
import org.ruoyi.service.neo4j.Neo4jService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/RAGkg")
public class KgQueryController {

    @Autowired
    private Neo4jService neo4jService;

    @GetMapping("/full-graph")
    public R<GraphData> getFullGraph(@RequestParam(defaultValue = "100") int limit) {
        StpUtil.checkLogin();
        return R.ok(neo4jService.getFullGraph(limit));
    }
}
