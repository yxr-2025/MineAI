package com.mineai.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the full lifecycle of every {@link AgentPlayer}.
 *
 * <p>This is the only place that adds an NPC to or removes it from the
 * server player list, so gameplay code never touches that logic directly.</p>
 */
public final class AgentPlayerManager {

    private static final Map<String, AgentPlayer> ACTIVE = new LinkedHashMap<>();
    private static int nextId = 1;
    private static String selectedId;

    private AgentPlayerManager() {
    }

    public static AgentPlayer spawn(MinecraftServer server, ServerLevel level, Vec3 pos) {
        int id = nextId++;
        return spawn(server, level, pos, "AgentNPC_" + id, "npc_" + id);
    }

    public static AgentPlayer spawn(MinecraftServer server, ServerLevel level, Vec3 pos, String name, String npcId) {
        if (ACTIVE.containsKey(npcId)) {
            throw new IllegalArgumentException("NPC id already exists: " + npcId);
        }

        UUID uuid = UUID.nameUUIDFromBytes(("mineai:" + npcId).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);

        AgentPlayer npc = new AgentPlayer(server, level, profile, npcId);
        npc.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        npc.setGameMode(GameType.SURVIVAL);

        try {
            server.getPlayerList().placeNewPlayer(npc.npcConnection(), npc);
        } catch (RuntimeException exception) {
            npc.actionQueue().clear(npc);
            npc.mover().stop();
            if (!npc.isRemoved()) {
                npc.discard();
            }
            throw exception;
        }

        ACTIVE.put(npcId, npc);
        return npc;
    }

    public static Optional<AgentPlayer> get(String npcId) {
        return Optional.ofNullable(ACTIVE.get(npcId));
    }

    public static Optional<AgentPlayer> getFirst() {
        return ACTIVE.values().stream().findFirst();
    }

    /**
     * Selects the NPC that agent commands operate on. Falls back to the first
     * NPC when the selection is missing.
     */
    public static void select(String npcId) {
        selectedId = npcId;
    }

    public static Optional<AgentPlayer> selected() {
        if (selectedId != null) {
            AgentPlayer npc = ACTIVE.get(selectedId);
            if (npc != null) {
                return Optional.of(npc);
            }
        }
        return getFirst();
    }

    public static String selectedId() {
        return selectedId;
    }

    public static Collection<AgentPlayer> all() {
        return List.copyOf(ACTIVE.values());
    }

    public static boolean remove(MinecraftServer server, String npcId) {
        AgentPlayer npc = ACTIVE.remove(npcId);
        if (npc == null) {
            return false;
        }
        if (npcId.equals(selectedId)) {
            selectedId = null;
        }
        cleanup(server, npc);
        return true;
    }

    public static int removeAll(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        int count = 0;
        for (AgentPlayer npc : new ArrayList<>(ACTIVE.values())) {
            cleanup(server, npc);
            count++;
        }
        ACTIVE.clear();
        selectedId = null;
        return count;
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (AgentPlayer npc : List.copyOf(ACTIVE.values())) {
            if (npc.isRemoved()) {
                ACTIVE.remove(npc.npcId());
                continue;
            }
            npc.serverTick();
        }
    }

    private static void cleanup(MinecraftServer server, AgentPlayer npc) {
        if (npc.agentControllerOrNull() != null) {
            npc.agentControllerOrNull().stop();
        }
        npc.actionQueue().clear(npc);
        npc.mover().stop();
        if (!npc.isRemoved()) {
            server.getPlayerList().remove(npc);
        }
    }
}
