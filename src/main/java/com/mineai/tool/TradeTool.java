package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.InventoryHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class TradeTool implements AgentTool {

    @Override
    public String name() {
        return "trade";
    }

    @Override
    public String description() {
        return "Execute a villager trade by offer index. Use list_trades first to see the offers and costs.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("entity_id", numberProperty("villager entity id"));
        properties.add("offer_index", numberProperty("offer index from list_trades"));
        properties.add("count", numberProperty("how many times to trade (default 1)"));
        return schema(name(), description(), properties, "entity_id", "offer_index");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        int entityId = integer(args, "entity_id");
        Entity entity = npc.level().getEntity(entityId);
        if (!(entity instanceof Villager villager)) {
            return ToolResult.fail("no villager with entity id " + entityId);
        }
        if (npc.distanceToSqr(villager) > 36.0D) {
            return ToolResult.fail("villager is too far away");
        }

        MerchantOffers offers = villager.getOffers();
        int index = integer(args, "offer_index");
        if (index < 0 || index >= offers.size()) {
            return ToolResult.fail("offer index out of range: " + index);
        }
        MerchantOffer offer = offers.get(index);
        int requested = args.has("count") ? Math.max(1, integer(args, "count")) : 1;

        int done = 0;
        for (int i = 0; i < requested; i++) {
            if (offer.isOutOfStock()) {
                break;
            }
            if (!InventoryHelper.has(npc, offer.getCostA())
                    || !InventoryHelper.has(npc, offer.getCostB())) {
                break;
            }
            InventoryHelper.consume(npc, offer.getCostA());
            InventoryHelper.consume(npc, offer.getCostB());

            ItemStack result = offer.getResult().copy();
            if (!npc.getInventory().add(result)) {
                npc.drop(result, false);
            }
            offer.increaseUses();
            villager.notifyTrade(offer);
            done++;
        }

        if (done == 0) {
            return ToolResult.fail("could not trade: missing items, out of stock, or index invalid");
        }
        return ToolResult.ok("traded " + done + " time(s), received "
                + ListTradesTool.describe(offer.getResult()));
    }
}
