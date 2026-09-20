package com.mineai.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * LLM settings loaded from {@code config/mineai/llm.json}.
 *
 * <p>The API key is never written to the repository. It is read from the
 * {@code MINEAI_LLM_API_KEY} environment variable first, then from the config
 * file. The generated example config leaves the key blank.</p>
 */
public record LlmConfig(
        boolean enabled,
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        double temperature,
        int maxTokens,
        int timeoutSeconds,
        int maxSteps,
        int maxGoals,
        int maxConcurrentRequests,
        int minRequestIntervalMs,
        int maxRetries,
        int retryBackoffMs,
        boolean reflectionEnabled,
        int reflectionMinFailures,
        int reflectionSuccessInterval,
        String plannerModel
) {
    /**
     * Model used for planning and reflection. Falls back to {@link #model()}
     * when empty, which keeps single-model setups working.
     */
    public String effectivePlannerModel() {
        return plannerModel == null || plannerModel.isBlank() ? model : plannerModel;
    }

    public boolean hasSeparatePlanner() {
        return plannerModel != null && !plannerModel.isBlank() && !plannerModel.equals(model);
    }

    public static final String ENV_API_KEY = "MINEAI_LLM_API_KEY";

    public static LlmConfig disabled() {
        return new LlmConfig(false, "deepseek", "https://api.deepseek.com/v1", "",
                "deepseek-chat", 0.2D, 1024, 60, 12, 20, 2, 1000, 2, 1000, true, 1, 3, "");
    }

    public static LlmConfig load(Path file) {
        try {
            if (!Files.exists(file)) {
                writeExample(file);
                return disabled();
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            String apiKey = System.getenv(ENV_API_KEY);
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = string(root, "api_key", "");
            }

            return new LlmConfig(
                    bool(root, "enabled", false),
                    string(root, "provider", "deepseek"),
                    string(root, "base_url", "https://api.deepseek.com/v1"),
                    apiKey,
                    string(root, "model", "deepseek-chat"),
                    number(root, "temperature", 0.2D),
                    integer(root, "max_tokens", 1024),
                    integer(root, "timeout_seconds", 60),
                    integer(root, "max_steps", 12),
                    integer(root, "max_goals", 20),
                    integer(root, "max_concurrent_requests", 2),
                    integer(root, "min_request_interval_ms", 1000),
                    integer(root, "max_retries", 2),
                    integer(root, "retry_backoff_ms", 1000),
                    bool(root, "reflection_enabled", true),
                    integer(root, "reflection_min_failures", 1),
                    integer(root, "reflection_success_interval", 3),
                    string(root, "planner_model", ""));
        } catch (IOException | RuntimeException exception) {
            return disabled();
        }
    }

    private static void writeExample(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String example = """
                {
                  "_comment": "MineAI LLM configuration. Set api_key here or via the MINEAI_LLM_API_KEY environment variable.",
                  "enabled": false,
                  "provider": "deepseek",
                  "base_url": "https://api.deepseek.com/v1",
                  "api_key": "",
                  "model": "deepseek-chat",
                  "temperature": 0.2,
                  "max_tokens": 1024,
                  "timeout_seconds": 60,
                  "max_steps": 12,
                  "max_goals": 20,
                  "max_concurrent_requests": 2,
                  "min_request_interval_ms": 1000,
                  "max_retries": 2,
                  "retry_backoff_ms": 1000,
                  "reflection_enabled": true,
                  "reflection_min_failures": 1,
                  "reflection_success_interval": 3,
                  "planner_model": ""
                }
                """;
        Files.writeString(file, example, StandardCharsets.UTF_8);
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsBoolean() : fallback;
    }

    private static String string(JsonObject root, String key, String fallback) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : fallback;
    }

    private static double number(JsonObject root, String key, double fallback) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsDouble() : fallback;
    }

    private static int integer(JsonObject root, String key, int fallback) {
        return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsInt() : fallback;
    }
}
