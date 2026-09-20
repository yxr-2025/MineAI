package com.mineai.tool;

import com.google.gson.JsonArray;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whitelist of capabilities exposed to the language model.
 */
public final class ToolRegistry {

    private static final Map<String, AgentTool> TOOLS = new LinkedHashMap<>();

    static {
        register(new CollectStateTool());
        register(new ScanAreaTool());
        register(new QueryBlockTool());
        register(new GotoTool());
        register(new MineTool());
        register(new PlaceTool());
        register(new UseTool());
        register(new ListContainerTool());
        register(new InsertItemTool());
        register(new ExtractItemTool());
        register(new CraftTool());
        register(new EquipTool());
        register(new AttackTool());
        register(new ReadSignTool());
        register(new SayTool());
        register(new ListTradesTool());
        register(new TradeTool());
        register(new ShareTool());
        register(new ReadBoardTool());
        register(new DeclareObjectiveTool());
        register(new RecipeTool());
        register(new MissingForTool());
        register(new ProcessTool());
        register(new WaitTool());
        register(new CandidatesTool());
        register(new SaveSkillTool());
        register(new RecallSkillTool());
        register(new RunSkillTool());
        register(new RememberTool());
        register(new ScreenshotTool());
    }

    private ToolRegistry() {
    }

    public static void register(AgentTool tool) {
        TOOLS.put(tool.name(), tool);
    }

    public static AgentTool get(String name) {
        return TOOLS.get(name);
    }

    public static List<AgentTool> all() {
        return List.copyOf(TOOLS.values());
    }

    public static JsonArray schema() {
        JsonArray array = new JsonArray();
        for (AgentTool tool : TOOLS.values()) {
            array.add(tool.parameters());
        }
        return array;
    }
}
