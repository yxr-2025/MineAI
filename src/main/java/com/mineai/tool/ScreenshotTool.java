package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.VisionRenderer;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class ScreenshotTool implements AgentTool {

    @Override
    public String name() {
        return "screenshot";
    }

    @Override
    public String description() {
        return "Render a top-down image of the terrain around the agent and attach it as a picture. "
                + "North is up and the red square marks the agent. Use it when a visual overview helps.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("radius", numberProperty("half-size of the view in blocks, 4 to 24 (default 12)"));
        properties.add("scale", numberProperty("pixels per block, 2 to 12 (default 6)"));
        return schema(name(), description(), properties);
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        int radius = args != null && args.has("radius") ? integer(args, "radius") : 12;
        int scale = args != null && args.has("scale") ? integer(args, "scale") : 6;
        radius = Math.max(4, Math.min(24, radius));
        scale = Math.max(2, Math.min(12, scale));

        String base64 = VisionRenderer.renderPngBase64(
                npc.level(), npc.blockPosition(), radius, scale);
        if (base64 == null) {
            return ToolResult.fail("failed to render the image");
        }
        return ToolResult.image(
                "rendered top-down map, radius " + radius + ", north is up, red square is the agent",
                base64);
    }
}
