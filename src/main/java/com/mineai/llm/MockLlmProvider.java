package com.mineai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Deterministic scripted provider used to validate the decision loop without
 * an API key or network access.
 */
public final class MockLlmProvider implements LlmProvider {

    private int callCount;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public CompletableFuture<LlmResponse> complete(List<JsonObject> messages, JsonArray tools) {
        int step = callCount++;
        return CompletableFuture.supplyAsync(() -> script(step));
    }

    private LlmResponse script(int step) {
        return switch (step) {
            case 0 -> new LlmResponse(
                    "Let me inspect the area first.",
                    List.of(new ToolCall("mock_0", "collect_state", new JsonObject())));
            case 1 -> new LlmResponse(
                    "Moving two blocks east.",
                    List.of(new ToolCall("mock_1", "goto", coords(2, -60, 0))));
            case 2 -> new LlmResponse(
                    "Mining the block in front of me.",
                    List.of(new ToolCall("mock_2", "mine", coords(2, -61, 1))));
            case 3 -> new LlmResponse(
                    "Placing the block back.",
                    List.of(new ToolCall("mock_3", "place", coords(2, -61, 1))));
            default -> new LlmResponse("Goal complete.", List.of());
        };
    }

    private JsonObject coords(int x, int y, int z) {
        JsonObject object = new JsonObject();
        object.addProperty("x", x);
        object.addProperty("y", y);
        object.addProperty("z", z);
        return object;
    }
}
