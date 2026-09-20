package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.MiningKnowledge;
import com.mineai.util.InventoryHelper;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enumerates the relevant things the agent could do right now, grounded in the
 * objective's gap, the inventory and nearby sources.
 *
 * <p>This shrinks the model's search space: instead of inventing an action from
 * nothing, it picks from a short, valid list. It is also the prerequisite for
 * routing the choice to a fast decision model later.</p>
 */
public final class CandidateActions {

    private static final int MAX_CANDIDATES = 12;
    private static final int SOURCE_RADIUS = 24;

    private CandidateActions() {
    }

    public static JsonObject build(AgentPlayer npc, Objective objective) {
        Map<String, JsonObject> candidates = new LinkedHashMap<>();

        if (objective != null && objective.isSet()) {
            Item target = RegistryIds.itemById(objective.itemId());
            if (target != null) {
                addObjectiveCandidates(npc, objective, target, candidates);
            }
        }
        addSafetyCandidates(npc, candidates);

        JsonArray array = new JsonArray();
        for (JsonObject candidate : candidates.values()) {
            if (array.size() >= MAX_CANDIDATES) {
                break;
            }
            array.add(candidate);
        }
        JsonObject result = new JsonObject();
        result.add("candidates", array);
        return result;
    }

    private static void addObjectiveCandidates(AgentPlayer npc, Objective objective, Item target,
                                               Map<String, JsonObject> candidates) {
        String targetId = objective.itemId();
        int have = InventoryHelper.count(npc, target);
        int missing = objective.count() - have;
        if (missing <= 0) {
            return;
        }

        // already craftable
        var crafting = RecipeLookup.findCrafting(npc.level(), target);
        if (crafting != null) {
            boolean haveAll = true;
            for (var ingredient : crafting.getIngredients()) {
                if (ingredient.getItems().length == 0) {
                    continue;
                }
                boolean satisfied = false;
                for (ItemStack stack : ingredient.getItems()) {
                    if (InventoryHelper.count(npc, stack.getItem()) > 0) {
                        satisfied = true;
                        break;
                    }
                }
                if (!satisfied) {
                    haveAll = false;
                    break;
                }
            }
            add(candidates, "craft:" + targetId, "craft", "craft " + missing + "x " + targetId,
                    craftArgs(targetId, missing), haveAll);
        }

        var smelting = RecipeLookup.findSmelting(npc.level(), target);
        if (smelting != null && !smelting.getIngredients().isEmpty()) {
            Item input = RecipeLookup.representative(smelting.getIngredients().get(0), npc);
            if (input != null) {
                String inputId = RegistryIds.item(input);
                add(candidates, "smelt:" + targetId, "smelt",
                        "smelt " + missing + "x " + inputId + " into " + targetId,
                        processArgs("smelt", targetId, missing), InventoryHelper.count(npc, input) >= missing);
            }
        }

        // gather the target directly if it occurs as a block
        List<BlockPos> sources = MiningKnowledge.findSources(npc.level(), npc.blockPosition(),
                SOURCE_RADIUS, 12, npc, npc.getMainHandItem(), targetId, 1);
        if (!sources.isEmpty()) {
            BlockPos source = sources.get(0);
            boolean harvestable = MiningKnowledge.canHarvest(npc.getMainHandItem(),
                    npc.level().getBlockState(source));
            add(candidates, "gather:" + targetId, "gather",
                    "gather " + missing + "x " + targetId + " from " + source.getX() + "," + source.getY() + "," + source.getZ(),
                    processArgs("gather", targetId, missing), harvestable);
            if (!harvestable) {
                add(candidates, "ensure_tool:" + targetId, "ensure_tool",
                        "get a tool that can harvest " + targetId,
                        ensureArgs(npc.level().getBlockState(source)), false);
            }
        }

        // intermediate materials
        JsonObject gap = GapAnalyzer.analyze(npc.level(), npc, target, objective.count());
        if (gap.has("missing")) {
            for (var element : gap.getAsJsonArray("missing")) {
                JsonObject row = element.getAsJsonObject();
                String itemId = row.get("item").getAsString();
                int need = row.get("missing").getAsInt();
                if (itemId.equals(targetId)) {
                    continue;
                }
                String source = row.has("source") ? row.get("source").getAsString() : "gather";
                String id = source + ":" + itemId;
                if ("craft".equals(source)) {
                    add(candidates, id, "craft", "craft " + need + "x " + itemId,
                            craftArgs(itemId, need), false);
                } else if ("smelt".equals(source)) {
                    add(candidates, id, "smelt", "smelt " + need + "x " + itemId,
                            processArgs("smelt", itemId, need), false);
                } else {
                    add(candidates, id, "gather", "gather " + need + "x " + itemId,
                            processArgs("gather", itemId, need), false);
                }
            }
        }
    }

    private static void addSafetyCandidates(AgentPlayer npc, Map<String, JsonObject> candidates) {
        if (npc.getFoodData().getFoodLevel() < 12) {
            add(candidates, "eat", "use", "eat food to restore hunger", new JsonObject(), false);
        }
    }

    private static void add(Map<String, JsonObject> candidates, String id, String process,
                            String description, JsonObject args, boolean ready) {
        if (candidates.containsKey(id)) {
            return;
        }
        JsonObject candidate = new JsonObject();
        candidate.addProperty("id", id);
        candidate.addProperty("process", process);
        candidate.addProperty("description", description);
        candidate.addProperty("ready_now", ready);
        candidate.add("args", args);
        candidates.put(id, candidate);
    }

    private static JsonObject craftArgs(String item, int count) {
        JsonObject args = new JsonObject();
        args.addProperty("process", "craft");
        args.addProperty("item", item);
        args.addProperty("count", count);
        return args;
    }

    private static JsonObject processArgs(String process, String item, int count) {
        JsonObject args = new JsonObject();
        args.addProperty("process", process);
        args.addProperty("item", item);
        args.addProperty("count", count);
        return args;
    }

    private static JsonObject ensureArgs(net.minecraft.world.level.block.state.BlockState state) {
        JsonObject args = new JsonObject();
        args.addProperty("process", "ensure_tool");
        args.addProperty("kind", MiningKnowledge.toolKind(state).name().toLowerCase(java.util.Locale.ROOT));
        args.addProperty("tier", MiningKnowledge.requiredTier(state).name().toLowerCase(java.util.Locale.ROOT));
        return args;
    }
}
