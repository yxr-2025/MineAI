package com.mineai.agent;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.MiningKnowledge;
import com.mineai.util.InventoryHelper;
import com.mineai.util.RegistryIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Turns a raw failure message into a category and a targeted repair hint, so
 * the model fixes the actual cause instead of being told to "continue".
 */
public final class FailureClassifier {

    public enum Kind {
        MISSING_PREREQUISITE,
        WRONG_OR_MISSING_TOOL,
        TARGET_GONE,
        UNREACHABLE,
        ACTION_TIMEOUT,
        LOOPING,
        OBJECTIVE_UNMET,
        UNKNOWN
    }

    private FailureClassifier() {
    }

    public static JsonObject classify(AgentPlayer npc, String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        Kind kind = Kind.UNKNOWN;
        String hint = "Try a different approach.";

        if (text.contains("crafting table")) {
            kind = Kind.MISSING_PREREQUISITE;
            hint = "Place a crafting table near you first, then craft again.";
        } else if (text.contains("missing ingredients") || text.contains("no craftable recipe")) {
            kind = Kind.MISSING_PREREQUISITE;
            hint = "Call missing_for for the item, gather what is missing, then craft again.";
        } else if (text.contains("directly under your feet")) {
            kind = Kind.UNREACHABLE;
            hint = "Mine the block beside you and step down; never mine the block you stand on.";
        } else if (text.contains("too far") || text.contains("no container")) {
            kind = Kind.UNREACHABLE;
            hint = "Walk within about 5 blocks of the target first.";
        } else if (text.contains("no ") && text.contains("within")) {
            kind = Kind.TARGET_GONE;
            hint = "The target is gone; rescan and pick another one.";
        } else if (text.contains("is empty") || text.contains("is air") || text.contains("not found")) {
            kind = Kind.TARGET_GONE;
            hint = "The target no longer exists; rescan before acting.";
        } else if (text.contains("inventory full") || text.contains("does not have enough room")) {
            kind = Kind.MISSING_PREREQUISITE;
            hint = "Free inventory space (move items into a container) first.";
        } else if (text.contains("timed out") || text.contains("timeout")) {
            kind = Kind.ACTION_TIMEOUT;
            hint = "The action took too long; split it into smaller steps.";
        } else if (text.contains("repeated") || text.contains("looping")) {
            kind = Kind.LOOPING;
            hint = "Stop repeating the same call; change location or method.";
        } else if (text.contains("objective not met")) {
            kind = Kind.OBJECTIVE_UNMET;
            hint = "Check missing_for and gather the remaining materials.";
        } else if (text.contains("rejected the item") || text.contains("can place")) {
            kind = Kind.MISSING_PREREQUISITE;
            hint = "That slot does not accept the item; check the machine with list_container.";
        }

        if (kind == Kind.UNKNOWN && mentionsToolRequirement(npc, text)) {
            kind = Kind.WRONG_OR_MISSING_TOOL;
            hint = "Hold a better tool for that block before mining.";
        }

        JsonObject result = new JsonObject();
        result.addProperty("kind", kind.name());
        result.addProperty("hint", hint);
        return result;
    }

    /**
     * Detects "the current tool cannot harvest this" by checking whether a
     * nearby target block needs a tool the agent is not holding.
     */
    private static boolean mentionsToolRequirement(AgentPlayer npc, String text) {
        if (!text.contains("mine") && !text.contains("tool")) {
            return false;
        }
        BlockPos feet = npc.blockPosition();
        ItemStack held = npc.getMainHandItem();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
            BlockState state = npc.level().getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (MiningKnowledge.requiredTier(state) != MiningKnowledge.Tier.HAND
                    && !MiningKnowledge.canHarvest(held, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A targeted repair hint, including concrete missing items when the failure
     * is about an objective.
     */
    public static String hintFor(AgentPlayer npc, Objective objective, String message) {
        JsonObject classified = classify(npc, message);
        StringBuilder builder = new StringBuilder(classified.get("hint").getAsString());
        if (objective != null && objective.isSet()) {
            Item target = RegistryIds.itemById(objective.itemId());
            if (target != null) {
                int have = InventoryHelper.count(npc, target);
                if (have < objective.count()) {
                    builder.append(" Still missing ").append(objective.count() - have)
                            .append("x ").append(objective.itemId()).append(':').append(' ')
                            .append(GapAnalyzer.summarize(npc.level(), npc, target, objective.count()));
                }
            }
        }
        return builder.toString();
    }
}
