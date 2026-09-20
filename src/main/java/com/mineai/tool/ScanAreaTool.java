package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import com.mineai.state.StructureScanner;
import com.mineai.state.TerrainScanner;

import static com.mineai.tool.AgentTool.integer;
import static com.mineai.tool.AgentTool.numberProperty;
import static com.mineai.tool.AgentTool.schema;

public final class ScanAreaTool implements AgentTool {

    @Override
    public String name() {
        return "scan_area";
    }

    @Override
    public String description() {
        return "Return a compact top-down map of the terrain plus a list of notable structures around the agent "
                + "(containers, workstations, doors, beds, farmland, water, hazards). "
                + "Use this to understand hills, holes, obstacles and what is built nearby.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        properties.add("radius", numberProperty("scan radius in blocks, 1 to 12 (default 6)"));
        return schema(name(), description(), properties);
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        int radius = args != null && args.has("radius") ? integer(args, "radius") : 6;
        radius = Math.max(1, Math.min(12, radius));

        com.google.gson.JsonObject data = TerrainScanner.scan(npc.level(), npc.blockPosition(), radius);
        data.add("features", StructureScanner.features(npc.level(), npc.blockPosition(), radius));
        return ToolResult.okData("scanned terrain and structures", data);
    }
}
