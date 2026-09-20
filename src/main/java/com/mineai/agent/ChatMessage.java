package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.llm.LlmResponse;
import com.mineai.llm.ToolCall;

/**
 * Builders for OpenAI-format chat messages.
 */
public final class ChatMessage {

    private ChatMessage() {
    }

    public static JsonObject system(String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "system");
        message.addProperty("content", content);
        return message;
    }

    public static JsonObject user(String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", content);
        return message;
    }

    public static JsonObject assistant(LlmResponse response) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.addProperty("content", response.content() == null ? "" : response.content());

        if (response.hasToolCalls()) {
            JsonArray toolCalls = new JsonArray();
            for (ToolCall call : response.toolCalls()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", call.id());
                entry.addProperty("type", "function");

                JsonObject function = new JsonObject();
                function.addProperty("name", call.name());
                function.addProperty("arguments", call.arguments().toString());
                entry.add("function", function);

                toolCalls.add(entry);
            }
            message.add("tool_calls", toolCalls);
        }
        return message;
    }

    /**
     * User message carrying a PNG for vision models (OpenAI image_url format).
     */
    public static JsonObject image(String base64Png, String text) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");

        JsonArray content = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "text");
        textPart.addProperty("text", text == null ? "" : text);
        content.add(textPart);

        JsonObject imagePart = new JsonObject();
        imagePart.addProperty("type", "image_url");
        JsonObject url = new JsonObject();
        url.addProperty("url", "data:image/png;base64," + base64Png);
        imagePart.add("image_url", url);
        content.add(imagePart);

        message.add("content", content);
        return message;
    }

    public static JsonObject tool(String toolCallId, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "tool");
        message.addProperty("tool_call_id", toolCallId);
        message.addProperty("content", content);
        return message;
    }
}
