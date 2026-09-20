package com.mineai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.util.RegistryIds;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class ListTradesTool implements AgentTool {

    @Override
    public String name() {
        return "list_trades";
    }

    @Override
    public String description() {
        return "List the trade offers of a nearby villager. Use collect_state to get the villager entity id first.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("entity_id", numberProperty("villager entity id"));
        return schema(name(), description(), properties, "entity_id");
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
        JsonArray array = new JsonArray();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("index", index);
            entry.addProperty("cost_a", describe(offer.getCostA()));
            if (!offer.getCostB().isEmpty()) {
                entry.addProperty("cost_b", describe(offer.getCostB()));
            }
            entry.addProperty("result", describe(offer.getResult()));
            entry.addProperty("uses", offer.getUses());
            entry.addProperty("max_uses", offer.getMaxUses());
            entry.addProperty("out_of_stock", offer.isOutOfStock());
            array.add(entry);
        }

        JsonObject data = new JsonObject();
        data.addProperty("profession", villager.getVillagerData().getProfession().toString());
        data.addProperty("level", villager.getVillagerData().getLevel());
        data.add("offers", array);
        return ToolResult.okData("listed " + array.size() + " offers", data);
    }

    static String describe(ItemStack stack) {
        if (stack.isEmpty()) {
            return "nothing";
        }
        return RegistryIds.item(stack.getItem()) + " x" + stack.getCount();
    }
}
