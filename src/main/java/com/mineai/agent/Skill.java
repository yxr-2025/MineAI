package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A named, executable procedure.
 *
 * <p>Only executable primitives are kept (movement, world edits, equip, craft,
 * container moves). Read-only and memory tools are dropped, so replaying a
 * skill does not spend LLM calls on reconnaissance.</p>
 *
 * <p>Coordinates are stored relative to the origin (the agent position when the
 * skill was recorded), so the same skill can run at a new anchor.</p>
 */
public record Skill(String name, String description, List<JsonObject> steps, int uses,
                    int originX, int originY, int originZ) {

    /** Tools that can actually be replayed without a model. */
    private static final Set<String> EXECUTABLE = Set.of(
            "goto", "mine", "place", "use", "equip", "craft", "insert_item", "extract_item");

    public static boolean isExecutable(String tool) {
        return EXECUTABLE.contains(tool);
    }

    /** Keeps only executable steps, preserving order. */
    public static List<JsonObject> executableSteps(List<JsonObject> trace) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject step : trace) {
            if (step.has("tool") && isExecutable(step.get("tool").getAsString())) {
                result.add(step);
            }
        }
        return result;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.addProperty("description", description);
        object.addProperty("uses", uses);
        object.addProperty("origin_x", originX);
        object.addProperty("origin_y", originY);
        object.addProperty("origin_z", originZ);
        JsonArray stepArray = new JsonArray();
        for (JsonObject step : steps) {
            stepArray.add(step);
        }
        object.add("steps", stepArray);
        return object;
    }

    public static Skill fromJson(JsonObject object) {
        String name = object.has("name") ? object.get("name").getAsString() : "skill";
        String description = object.has("description") ? object.get("description").getAsString() : "";
        int uses = object.has("uses") ? object.get("uses").getAsInt() : 0;
        int originX = object.has("origin_x") ? object.get("origin_x").getAsInt() : 0;
        int originY = object.has("origin_y") ? object.get("origin_y").getAsInt() : 0;
        int originZ = object.has("origin_z") ? object.get("origin_z").getAsInt() : 0;
        List<JsonObject> steps = new ArrayList<>();
        if (object.has("steps")) {
            for (JsonElement element : object.getAsJsonArray("steps")) {
                steps.add(element.getAsJsonObject());
            }
        }
        return new Skill(name, description, steps, uses, originX, originY, originZ);
    }

    public String summary() {
        return name + " - " + description + " (" + steps.size() + " steps)";
    }
}
