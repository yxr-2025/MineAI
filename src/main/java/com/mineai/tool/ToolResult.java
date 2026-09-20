package com.mineai.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Result of a tool invocation.
 *
 * <p>{@code queued} means the tool pushed work onto the NPC action queue and
 * the real outcome is only known once the queue drains. {@code imageBase64}
 * carries an optional PNG that is appended to the conversation as an image
 * message for vision models.</p>
 */
public record ToolResult(boolean success, boolean queued, String message, JsonElement data, String imageBase64) {

    public static ToolResult ok(String message) {
        return new ToolResult(true, false, message, null, null);
    }

    public static ToolResult okData(String message, JsonElement data) {
        return new ToolResult(true, false, message, data, null);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, false, message, null, null);
    }

    public static ToolResult queued(String message) {
        return new ToolResult(true, true, message, null, null);
    }

    public static ToolResult image(String message, String base64Png) {
        return new ToolResult(true, false, message, null, base64Png);
    }

    /**
     * Serialized into the {@code tool} role message sent back to the model.
     */
    public String toToolContent() {
        JsonObject object = new JsonObject();
        object.addProperty("success", success);
        object.addProperty("message", message);
        if (data != null) {
            object.add("data", data);
        }
        return object.toString();
    }
}
