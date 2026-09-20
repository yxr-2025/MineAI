package com.mineai.action;

import com.mineai.entity.AgentPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Uses the item currently held in the main hand.
 *
 * <p>Handles the use duration (mechanism). It does not choose what to use: the
 * model equips an item first. Food is consumed directly after a grace period
 * because server-side players do not reliably tick item use.</p>
 */
public final class UseAction implements Action {

    private static final int FORCE_CONSUME_TICK = 45;
    private static final int MAX_TICKS = 120;

    private boolean started;
    private int ticks;
    private ItemStack before = ItemStack.EMPTY;
    private boolean edible;

    @Override
    public void onStart(AgentPlayer npc) {
        started = true;
        before = npc.getMainHandItem().copy();
        edible = !before.isEmpty() && before.isEdible();
        npc.gameMode.useItem(npc, npc.level(), npc.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        if (!started) {
            return ActionResult.FAILED;
        }

        ItemStack now = npc.getMainHandItem();
        if (changed(before, now)) {
            return ActionResult.SUCCESS;
        }

        if (!edible) {
            return npc.isUsingItem() ? ActionResult.RUNNING : ActionResult.SUCCESS;
        }

        if (++ticks >= FORCE_CONSUME_TICK) {
            FoodProperties food = now.getFoodProperties(npc);
            if (food == null) {
                return ActionResult.FAILED;
            }
            npc.getFoodData().eat(food.getNutrition(), food.getSaturationModifier());
            now.shrink(1);
            return ActionResult.SUCCESS;
        }
        return ticks > MAX_TICKS ? ActionResult.FAILED : ActionResult.RUNNING;
    }

    @Override
    public int timeoutTicks() {
        return MAX_TICKS + 40;
    }

    @Override
    public String describe() {
        return "UseAction";
    }

    private static boolean changed(ItemStack before, ItemStack now) {
        if (before.isEmpty() != now.isEmpty()) {
            return true;
        }
        if (before.isEmpty()) {
            return false;
        }
        if (before.getItem() != now.getItem()) {
            return true;
        }
        return before.getCount() != now.getCount();
    }
}
