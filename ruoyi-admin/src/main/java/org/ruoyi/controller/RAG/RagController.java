package org.ruoyi.controller.RAG;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.RAG.IRagService;
import org.ruoyi.service.RAG.RAGGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/system/rag")
public class RagController {

    @Autowired
    private IRagService ragService;

    /**
     * 执行 RAG 流式查询
     * @param question 问题内容
     * @return SseEmitter 流式响应
     */
    @GetMapping(value = "/stream-query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuery(@RequestParam String question) {

//        try {
//            // 1. 直接检查当前会话是否已登录
//            //    这个方法会自动从请求的 Header 或 Cookie 中寻找 Token 并验证。
//            //    如果未登录，它会直接抛出 NotLoginException 异常。
//            StpUtil.checkLogin();
//        } catch (Exception e) {
//            // 如果 StpUtil.checkLogin() 抛出异常，说明未登录或 token 无效
//            SseEmitter emitter = new SseEmitter();
//            emitter.completeWithError(new SecurityException("无效的身份认证信息，请重新登录。"));
//            return emitter;
//        }

        // 2. 检查问题是否为空
        if (question == null || question.trim().isEmpty()) {
            SseEmitter emitter = new SseEmitter();
            emitter.completeWithError(new IllegalArgumentException("问题内容不能为空"));
            return emitter;
        }

        // 3. 调用 Service 层，开始真正的流式处理
        return ragService.streamQueryRag(question);
    }

    // 返回类型改为 R<?>，data 字段可以是任何类型
    @GetMapping("/documents")
    public R<?> getDocuments() {
        StpUtil.checkLogin();
        // 直接将 Service 返回的 Object 放入 R.ok 的 data 中
        return R.ok(ragService.getDocuments());
    }

    @GetMapping("/graph")
    public R<?> getGraphData() {
        StpUtil.checkLogin();
        return R.ok(ragService.getGraphData());
    }

    @PostMapping("/build/start")
    public R<?> startBuild(@RequestParam("files") MultipartFile[] files) {
        StpUtil.checkLogin();
        if (files == null || files.length == 0) {
            return R.fail("上传的文件不能为空");
        }
        String taskId = ragService.startBuild(files);
        // 立即开始在后台监控这个任务的进度
        ragService.monitorBuildProgress(taskId);
        return R.ok("构建任务已创建", taskId);
    }

//    @PostMapping("/build/commit/{taskId}") // 1. 将 taskId 作为路径的一部分
//    public R<?> commitBuild(@PathVariable String taskId) { // 2. 使用 @PathVariable
//        StpUtil.checkLogin();
//        return R.ok(ragService.commitBuild(taskId));
//    }

    @PostMapping("/build/abort/{taskId}") // 1. 将 taskId 作为路径的一部分
    public R<?> abortBuild(@PathVariable String taskId) { // 2. 使用 @PathVariable
        StpUtil.checkLogin();
        return R.ok(ragService.abortBuild(taskId));
    }

    @GetMapping("/build/status/{taskId}")
    public R<?> getBuildStatus(@PathVariable String taskId) {
        StpUtil.checkLogin();
        return R.ok(ragService.getBuildStatus(taskId));
    }

    @GetMapping("/build/preview/{taskId}")
    public R<?> getBuildPreviewGraph(@PathVariable String taskId) {
        StpUtil.checkLogin();
        return R.ok(ragService.getBuildPreviewGraph(taskId));
    }

    @Autowired
    private RAGGenerationService RAGGenerationService;

    @PostMapping("/build/commit/{taskId}")
    public R<?> commitBuild(@PathVariable String taskId) {
        StpUtil.checkLogin();

        CompletableFuture.runAsync(() -> {
            // 1. 异步地触发导入 Neo4j 的任务
            log.info("开始导入");
            RAGGenerationService.generateAndSaveGraph(taskId);
            log.info("结束导入");
            log.info("重命名文件夹");
            // 2. 再触发 Python 端的 commit (重命名文件夹)
            ragService.notifyPythonToCommit(taskId);
        });
//        ragService.notifyPythonToCommit(taskId);
        return R.ok("知识库已应用，并已开始同步到图数据库。");
    }
}