package com.mineai.agent;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Static checks applied before a recorded trace is saved as a skill.
 *
 * <p>Replaying a broken procedure wastes a whole run, so obviously bad programs
 * are rejected at record time. The rules mirror the runtime guards.</p>
 */
public final class SkillValidator {

    public record Result(boolean valid, List<String> problems) {
        public String describe() {
            return String.join("; ", problems);
        }
    }

    private SkillValidator() {
    }

    public static Result validate(List<JsonObject> steps) {
        List<String> problems = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            JsonObject step = steps.get(i);
            String tool = step.has("tool") ? step.get("tool").getAsString() : "";
            JsonObject args = step.has("args") ? step.getAsJsonObject("args") : new JsonObject();

            if (!Skill.isExecutable(tool)) {
                continue;
            }

            // mining the block you are standing on traps the agent
            if ("mine".equals(tool) && i > 0) {
                JsonObject previous = steps.get(i - 1);
                if ("goto".equals(previous.has("tool") ? previous.get("tool").getAsString() : "")
                        && previous.has("args")) {
                    JsonObject gotoArgs = previous.getAsJsonObject("args");
                    if (samePosition(gotoArgs, args, 0, -1, 0) || samePosition(gotoArgs, args, 0, 0, 0)) {
                        problems.add("step " + (i + 1) + " mines the block under the agent's feet");
                    }
                }
            }

            // placing at a position the agent occupies
            if ("place".equals(tool) && i > 0) {
                JsonObject previous = steps.get(i - 1);
                if ("goto".equals(previous.has("tool") ? previous.get("tool").getAsString() : "")
                        && previous.has("args") && samePosition(previous.getAsJsonObject("args"), args, 0, 0, 0)) {
                    problems.add("step " + (i + 1) + " places a block into the agent's own position");
                }
            }

            // smelting without loading a furnace is meaningless
            if ("smelt".equals(tool) && !hasContainerStepBefore(steps, i, "insert_item")) {
                problems.add("step " + (i + 1) + " smelts without loading a furnace");
            }
        }

        return new Result(problems.isEmpty(), problems);
    }

    private static boolean samePosition(JsonObject a, JsonObject b, int dx, int dy, int dz) {
        return a.has("x") && a.has("y") && a.has("z") && b.has("x") && b.has("y") && b.has("z")
                && a.get("x").getAsInt() + dx == b.get("x").getAsInt()
                && a.get("y").getAsInt() + dy == b.get("y").getAsInt()
                && a.get("z").getAsInt() + dz == b.get("z").getAsInt();
    }

    private static boolean hasContainerStepBefore(List<JsonObject> steps, int index, String tool) {
        for (int i = 0; i < index; i++) {
            if (steps.get(i).has("tool") && tool.equals(steps.get(i).get("tool").getAsString())) {
                return true;
            }
        }
        return false;
    }
}
