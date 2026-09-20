package com.mineai.action;

import com.mineai.entity.AgentPlayer;
import com.mineai.util.LookHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Attacks a target repeatedly until it dies.
 *
 * <p>Fake players do not accumulate vanilla attack charge reliably, so a single
 * swing does almost no damage. Swinging every tick until the target dies is the
 * robust behaviour for an NPC.</p>
 */
public final class AttackAction implements Action {

    private static final double MAX_REACH_SQR = 36.0D;
    private static final int MAX_TICKS = 200;

    private final int entityId;
    private int ticks;
    private boolean executed;
    private ActionResult result = ActionResult.FAILED;

    public AttackAction(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void onStart(AgentPlayer npc) {
        executed = true;
        if (!isAlive(npc)) {
            result = ActionResult.FAILED;
        }
    }

    @Override
    public ActionResult tick(AgentPlayer npc) {
        if (!executed) {
            return ActionResult.FAILED;
        }
        if (!isAlive(npc)) {
            return ActionResult.SUCCESS;
        }
        if (npc.distanceToSqr(target(npc)) > MAX_REACH_SQR) {
            return ActionResult.FAILED;
        }
        if (++ticks > MAX_TICKS) {
            return ActionResult.FAILED;
        }

        Entity target = target(npc);
        LookHelper.lookAt(npc, target.position().add(0.0D, target.getBbHeight() / 2.0D, 0.0D));
        npc.attack(target);
        return ActionResult.RUNNING;
    }

    private boolean isAlive(AgentPlayer npc) {
        Entity target = target(npc);
        return target instanceof LivingEntity living && living.isAlive();
    }

    private Entity target(AgentPlayer npc) {
        return npc.level().getEntity(entityId);
    }

    @Override
    public int timeoutTicks() {
        return MAX_TICKS + 40;
    }

    @Override
    public String describe() {
        return "AttackAction(entity=" + entityId + ")";
    }
}
