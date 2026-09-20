package com.mineai.tool;

import com.google.gson.JsonObject;
import com.mineai.entity.AgentPlayer;
import net.minecraft.network.chat.Component;

import static com.mineai.tool.AgentTool.schema;

public final class SayTool implements AgentTool {

    @Override
    public String name() {
        return "say";
    }

    @Override
    public String description() {
        return "Send a chat message as the agent, visible to all players. Use it to report status or talk to others.";
    }

    @Override
    public JsonObject parameters() {
        JsonObject properties = new JsonObject();
        JsonObject message = new JsonObject();
        message.addProperty("type", "string");
        message.addProperty("description", "message text");
        properties.add("message", message);
        return schema(name(), description(), properties, "message");
    }

    @Override
    public ToolResult execute(AgentPlayer npc, JsonObject args) {
        String message = args.get("message").getAsString();
        npc.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("<" + npc.getName().getString() + "> " + message), false);
        return ToolResult.ok("said: " + message);
    }
}
