package com.mineai.llm;

import java.util.List;

public record LlmResponse(String content, List<ToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
