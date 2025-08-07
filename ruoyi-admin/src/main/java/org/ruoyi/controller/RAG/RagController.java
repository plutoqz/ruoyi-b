package org.ruoyi.controller.RAG;

import cn.dev33.satoken.stp.StpUtil;
import org.ruoyi.service.RAG.IRagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
}