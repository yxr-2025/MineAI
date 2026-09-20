package com.mineai.integration;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record MachineContext(Level level, BlockPos pos) {
}
