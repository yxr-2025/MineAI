package com.mineai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throttles outbound LLM calls for multi-NPC operation.
 *
 * <p>Limits how many requests may be in flight at once and enforces a minimum
 * delay between request starts, so several agents do not hammer the same API
 * endpoint simultaneously.</p>
 */
public final class RateLimitedProvider implements LlmProvider {

    private final LlmProvider delegate;
    private final Semaphore permits;
    private final long minIntervalMs;
    private final AtomicLong lastStart = new AtomicLong(0L);
    private final ExecutorService executor;

    public RateLimitedProvider(LlmProvider delegate, int maxConcurrent, long minIntervalMs) {
        this.delegate = delegate;
        this.permits = new Semaphore(Math.max(1, maxConcurrent));
        this.minIntervalMs = Math.max(0L, minIntervalMs);
        this.executor = Executors.newFixedThreadPool(Math.max(2, maxConcurrent + 1), runnable -> {
            Thread thread = new Thread(runnable, "mineai-llm");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public String name() {
        return delegate.name() + "+throttled";
    }

    @Override
    public CompletableFuture<LlmResponse> complete(List<JsonObject> messages, JsonArray tools) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                permits.acquire();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            }
            try {
                throttle();
                return delegate.complete(messages, tools).join();
            } finally {
                permits.release();
            }
        }, executor);
    }

    private synchronized void throttle() {
        if (minIntervalMs <= 0L) {
            lastStart.set(System.currentTimeMillis());
            return;
        }
        long now = System.currentTimeMillis();
        long wait = lastStart.get() + minIntervalMs - now;
        if (wait > 0L) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        lastStart.set(System.currentTimeMillis());
    }
}
