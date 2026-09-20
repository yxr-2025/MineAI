package com.mineai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;

/**
 * A bounded capability exposed to the language model. The model may only act
 * through registered tools; it never touches Minecraft APIs directly.
 */
public interface AgentTool {

    String name();

    String description();

    /**
     * JSON Schema describing the arguments object.
     */
    JsonObject parameters();

    ToolResult execute(AgentPlayer npc, JsonObject args);

    static JsonObject schema(String name, String description, JsonObject properties, String... required) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        JsonArray requiredArray = new JsonArray();
        for (String key : required) {
            requiredArray.add(key);
        }
        parameters.add("required", requiredArray);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    static JsonObject numberProperty(String description) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "number");
        property.addProperty("description", description);
        return property;
    }

    static double number(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing numeric argument: " + key);
        }
        return args.get(key).getAsDouble();
    }

    static int integer(JsonObject args, String key) {
        return (int) Math.floor(number(args, key));
    }
}
