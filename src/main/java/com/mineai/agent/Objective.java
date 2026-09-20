package com.mineai.agent;

import com.google.gson.JsonObject;

/**
 * A machine-checkable success criterion. The model may declare one, but the
 * controller owns the check: a goal is only accepted when this predicate is
 * satisfied by the world.
 */
public record Objective(Type type, String itemId, int count) {

    public enum Type {
        NONE,
        HAVE_ITEM
    }

    public static final Objective NONE = new Objective(Type.NONE, "", 0);

    public static Objective haveItem(String itemId, int count) {
        return new Objective(Type.HAVE_ITEM, itemId, Math.max(1, count));
    }

    public boolean isSet() {
        return type != Type.NONE;
    }

    public String describe() {
        return switch (type) {
            case HAVE_ITEM -> count + "x " + itemId + " in inventory";
            case NONE -> "none";
        };
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", type.name());
        if (type == Type.HAVE_ITEM) {
            object.addProperty("item", itemId);
            object.addProperty("count", count);
        }
        return object;
    }

    public static Objective fromJson(JsonObject object) {
        if (object == null || !object.has("type")) {
            return NONE;
        }
        String type = object.get("type").getAsString();
        if (!"HAVE_ITEM".equalsIgnoreCase(type)) {
            return NONE;
        }
        String item = object.has("item") ? object.get("item").getAsString() : "";
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        if (item.isBlank()) {
            return NONE;
        }
        return haveItem(item, count);
    }
}
