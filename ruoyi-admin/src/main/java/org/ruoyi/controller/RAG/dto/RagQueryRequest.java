package org.ruoyi.controller.RAG.dto;



public class RagQueryRequest {
    private String question;
    // 如果需要支持对话历史，可以添加
    // private java.util.List<java.util.Map<String, String>> conversationHistory;

    // --- 以下是手写的 getter 和 setter ---
    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
