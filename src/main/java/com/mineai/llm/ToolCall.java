package com.mineai.llm;

import com.google.gson.JsonObject;

public record ToolCall(String id, String name, JsonObject arguments) {
}
