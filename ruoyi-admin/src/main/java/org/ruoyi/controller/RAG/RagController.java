package org.ruoyi.controller.RAG;

import org.ruoyi.common.web.core.BaseController;
import org.ruoyi.common.core.domain.R ;
import org.ruoyi.controller.RAG.dto.RagQueryRequest;
import org.ruoyi.service.RAG.IRagService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 智能问答控制器
 * (本版本不依赖 ruoyi-framework)
 */
@RestController
@RequestMapping("/system/rag")
// @CrossOrigin // 开发时如果前端跨域可以临时打开此注解，上线前建议关闭
public class RagController extends BaseController {

    @Autowired
    private IRagService ragService;

    /**
     * 执行 RAG 查询
     * 注意：此版本移除了 @PreAuthorize 权限控制，任何登录用户都可以访问。
     * 如果需要控制，可以在 Service 层或通过其他方式实现简单的角色判断。
     */
    @PostMapping("/query")
    public R<String> query(@RequestBody RagQueryRequest request) {
        // 参数校验
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            // 5. 使用 R.fail() 替代 AjaxResult.error()
            return R.fail("问题内容不能为空");
        }

        // 调用 Service 层执行查询
        String answer = ragService.queryRag(request.getQuestion());

        // 6. 使用 R.ok() 替代 AjaxResult.success()
        return R.ok("查询成功", answer);
    }
}