package com.mineai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Retries transient transport failures with exponential backoff.
 *
 * <p>This is infrastructure, not a decision: a dropped connection or a relay
 * timeout should not end an agent run. Policy (what to do after repeated
 * failures) stays with the model.</p>
 */
public final class RetryingProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final int maxRetries;
    private final long backoffMs;

    public RetryingProvider(LlmProvider delegate, int maxRetries, long backoffMs) {
        this.delegate = delegate;
        this.maxRetries = Math.max(0, maxRetries);
        this.backoffMs = Math.max(0L, backoffMs);
    }

    @Override
    public String name() {
        return delegate.name() + "+retry";
    }

    @Override
    public CompletableFuture<LlmResponse> complete(List<JsonObject> messages, JsonArray tools) {
        return attempt(messages, tools, 0);
    }

    private CompletableFuture<LlmResponse> attempt(List<JsonObject> messages, JsonArray tools, int attemptIndex) {
        return delegate.complete(messages, tools).handle((response, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(response);
            }
            if (attemptIndex >= maxRetries) {
                throw new CompletionException(unwrap(error));
            }
            long delay = backoffMs * (1L << attemptIndex);
            return CompletableFuture
                    .supplyAsync(() -> {
                        sleep(delay);
                        return attemptIndex + 1;
                    })
                    .thenCompose(next -> attempt(messages, tools, next));
        }).thenCompose(future -> future);
    }

    private static void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
