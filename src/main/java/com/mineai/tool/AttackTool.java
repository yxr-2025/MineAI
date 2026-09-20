package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.action.AttackAction;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.EntityFinder;
import com.mineai.util.RegistryIds;
import net.minecraft.world.entity.LivingEntity;

import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class AttackTool implements AgentTool {

    @Override
    public String name() {
        return "attack";
    }

    @Override
    public String description() {
        return "Attack the nearest living entity, optionally filtered by entity type. Other players are never targeted.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject target = new JsonObject();
        target.addProperty("type", "string");
        target.addProperty("description", "entity type id such as minecraft:zombie, or any");
        properties.add("target", target);
        properties.add("range", numberProperty("search range in blocks (default 6)"));
        return schema(name(), description(), properties);
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String type = args.has("target") ? args.get("target").getAsString() : "any";
        double range = args.has("range") ? AgentTool.number(args, "range") : 6.0D;

        LivingEntity entity = EntityFinder.nearest(npc, type, range);
        if (entity == null) {
            return ToolResult.fail("no " + type + " within " + (int) range + " blocks");
        }
        npc.actionQueue().enqueue(new AttackAction(entity.getId()));
        return ToolResult.queued("attacking " + RegistryIds.entity(entity.getType()));
    }
}
