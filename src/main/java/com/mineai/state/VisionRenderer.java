package com.mineai.state;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Renders a top-down PNG of the terrain around the agent for vision models.
 *
 * <p>Colours come from {@link BlockState#getMapColor} so the image is meaningful
 * for modded blocks too. The agent is marked with a red square; north is up.</p>
 */
public final class VisionRenderer {

    private static final int EMPTY_COLOR = 0x101010;
    private static final int AGENT_COLOR = 0xFF0000;

    private VisionRenderer() {
    }

    public static String renderPngBase64(Level level, BlockPos center, int radius, int scale) {
        int size = radius * 2 + 1;
        BufferedImage image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        int top = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 16);
        int bottom = Math.max(level.getMinBuildHeight(), center.getY() - 32);

        for (int dz = 0; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int x = center.getX() - radius + dx;
                int z = center.getZ() - radius + dz;

                BlockState surface = null;
                int surfaceY = bottom;
                for (int y = top; y >= bottom; y--) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir()) {
                        surface = state;
                        surfaceY = y;
                        break;
                    }
                }

                int rgb = EMPTY_COLOR;
                if (surface != null) {
                    MapColor color = surface.getMapColor(level, new BlockPos(x, surfaceY, z));
                    if (color != null && color != MapColor.NONE) {
                        rgb = color.col & 0xFFFFFF;
                    }
                }
                graphics.setColor(new Color(rgb));
                graphics.fillRect(dx * scale, dz * scale, scale, scale);
            }
        }

        graphics.setColor(new Color(AGENT_COLOR));
        graphics.fillRect(radius * scale, radius * scale, scale, scale);
        graphics.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception exception) {
            return null;
        }
    }
}
