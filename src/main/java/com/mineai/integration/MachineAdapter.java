package com.mineai.integration;

import com.mineai.entity.AgentPlayer;

public interface MachineAdapter {

    boolean supports(MachineContext context);

    MachineDescription describe(MachineContext context);

    MachineResult execute(AgentPlayer npc, MachineContext context, MachineOperation operation);
}
