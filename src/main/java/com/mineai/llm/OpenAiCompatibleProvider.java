package com.mineai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineai.config.LlmConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provider for OpenAI-compatible {@code /chat/completions} endpoints, including
 * DeepSeek. Uses {@link HttpClient#sendAsync} so the server thread never blocks.
 */
public final class OpenAiCompatibleProvider implements LlmProvider {

    private final LlmConfig config;
    private final HttpClient client;

    public OpenAiCompatibleProvider(LlmConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.min(30, config.timeoutSeconds())))
                .build();
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public CompletableFuture<LlmResponse> complete(List<JsonObject> messages, JsonArray tools) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());

        JsonArray messageArray = new JsonArray();
        for (JsonObject message : messages) {
            messageArray.add(message);
        }
        body.add("messages", messageArray);

        if (tools != null && !tools.isEmpty()) {
            body.add("tools", tools);
            body.addProperty("tool_choice", "auto");
        }
        body.addProperty("temperature", config.temperature());
        body.addProperty("max_tokens", config.maxTokens());

        String endpoint = config.baseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::parse);
    }

    private LlmResponse parse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + truncate(response.body()));
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM response contains no choices");
        }

        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        String content = message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString()
                : "";

        List<ToolCall> calls = new ArrayList<>();
        if (message.has("tool_calls") && !message.get("tool_calls").isJsonNull()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                JsonObject call = element.getAsJsonObject();
                String id = call.has("id") ? call.get("id").getAsString() : "call_" + calls.size();
                JsonObject function = call.getAsJsonObject("function");
                String name = function.get("name").getAsString();
                String rawArguments = function.has("arguments") && !function.get("arguments").isJsonNull()
                        ? function.get("arguments").getAsString()
                        : "{}";
                JsonObject arguments = rawArguments.isBlank()
                        ? new JsonObject()
                        : JsonParser.parseString(rawArguments).getAsJsonObject();
                calls.add(new ToolCall(id, name, arguments));
            }
        }

        return new LlmResponse(content, calls);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }
}
