package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.agent.process.ProgramBuilder;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.MiningKnowledge;
import com.mineai.util.RegistryIds;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Locale;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.schema;

/**
 * Runs a common procedure deterministically: gather, craft, smelt, ensure_tool.
 * The model decides what to obtain; the mechanical steps run without further
 * model calls.
 */
public final class ProcessTool implements AgentTool {

    @Override
    public String name() {
        return "process";
    }

    @Override
    public String description() {
        return "Run a standard procedure as a program, without further decisions: "
                + "gather (mine the item), craft (make it, placing a crafting table if needed), "
                + "smelt (place a furnace, load ore and fuel, wait, take the result), "
                + "ensure_tool (obtain and hold a tool of a kind and tier, resolving prerequisites). "
                + "kind is pickaxe|axe|shovel|hoe, tier is wood|stone|iron|diamond.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject process = new JsonObject();
        process.addProperty("type", "string");
        process.addProperty("description", "gather | craft | smelt | ensure_tool");
        properties.add("process", process);
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        item.addProperty("description", "item id (for gather/craft/smelt)");
        properties.add("item", item);
        properties.add("count", AgentTool.numberProperty("how many (default 1)"));
        JsonObject kind = new JsonObject();
        kind.addProperty("type", "string");
        kind.addProperty("description", "tool kind for ensure_tool");
        properties.add("kind", kind);
        JsonObject tier = new JsonObject();
        tier.addProperty("type", "string");
        tier.addProperty("description", "tool tier for ensure_tool");
        properties.add("tier", tier);
        return schema(name(), description(), properties, "process");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String process = args.get("process").getAsString().toLowerCase(Locale.ROOT);
        int count = args.has("count") ? Math.max(1, integer(args, "count")) : 1;

        List<JsonObject> steps = switch (process) {
            case "gather" -> gather(npc, args, count);
            case "craft" -> craft(npc, args, count);
            case "smelt" -> smelt(npc, args, count);
            case "ensure_tool" -> ensureTool(npc, args);
            default -> null;
        };
        if (steps == null) {
            return ToolResult.fail("unknown process: " + process);
        }
        if (steps.isEmpty()) {
            return ToolResult.fail("process '" + process + "' produced no steps "
                    + "(nothing to do, missing recipe, or no source found)");
        }
        return npc.agentController().startProgram(process + ":" + describe(args), steps,
                npc.blockPosition(), npc.blockPosition());
    }

    private static List<JsonObject> gather(AgentPlayer npc, JsonObject args, int count) {
        Item item = item(args);
        return item == null ? List.of() : ProgramBuilder.gather(npc, item, count);
    }

    private static List<JsonObject> craft(AgentPlayer npc, JsonObject args, int count) {
        Item item = item(args);
        return item == null ? List.of() : ProgramBuilder.craft(npc, item, count);
    }

    private static List<JsonObject> smelt(AgentPlayer npc, JsonObject args, int count) {
        Item item = item(args);
        return item == null ? List.of() : ProgramBuilder.smelt(npc, item, count);
    }

    private static List<JsonObject> ensureTool(AgentPlayer npc, JsonObject args) {
        if (!args.has("kind") || !args.has("tier")) {
            return List.of();
        }
        MiningKnowledge.ToolKind kind;
        MiningKnowledge.Tier tier;
        try {
            kind = MiningKnowledge.ToolKind.valueOf(args.get("kind").getAsString().toUpperCase(Locale.ROOT));
            tier = MiningKnowledge.Tier.valueOf(args.get("tier").getAsString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return List.of();
        }
        return ProgramBuilder.ensureTool(npc, kind, tier);
    }

    private static Item item(JsonObject args) {
        if (!args.has("item")) {
            return null;
        }
        return RegistryIds.itemById(args.get("item").getAsString());
    }

    private static String describe(JsonObject args) {
        StringBuilder builder = new StringBuilder();
        for (String key : new String[]{"item", "kind", "tier"}) {
            if (args.has(key)) {
                builder.append(key).append('=').append(args.get(key).getAsString()).append(' ');
            }
        }
        return builder.toString().trim();
    }
}
