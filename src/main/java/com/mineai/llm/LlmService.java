package com.mineai.llm;

import com.mineai.config.LlmConfig;

/**
 * Process-wide access to the configured provider. Set once on server start.
 */
public final class LlmService {

    private static volatile LlmProvider provider;
    private static volatile LlmProvider planner;
    private static volatile LlmConfig config;

    private LlmService() {
    }

    public static void set(LlmProvider value) {
        provider = value;
    }

    public static void setPlanner(LlmProvider value) {
        planner = value;
    }

    /**
     * Provider for planning and reflection. Falls back to the executor when no
     * separate planner model is configured.
     */
    public static LlmProvider planner() {
        return planner != null ? planner : provider;
    }

    public static void setConfig(LlmConfig value) {
        config = value;
    }

    public static LlmConfig config() {
        return config;
    }

    public static LlmProvider provider() {
        return provider;
    }

    public static boolean isReady() {
        return provider != null;
    }
}
