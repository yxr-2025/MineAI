package com.mineai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Async chat-completion provider. Implementations must never block the server
 * thread; the returned future completes on an HTTP worker thread.
 */
public interface LlmProvider {

    String name();

    CompletableFuture<LlmResponse> complete(List<JsonObject> messages, JsonArray tools);
}
