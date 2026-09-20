package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineai.MineAiMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Facts shared between all NPCs. One agent can publish a discovery and another
 * can act on it, which is the basis for collaboration without a central
 * controller.
 */
public final class SharedBlackboard {

    private static final int MAX_ENTRIES = 50;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean loaded;

    public record Entry(String key, String value, String author, long timestamp) {
    }

    private SharedBlackboard() {
    }

    public static synchronized void put(String key, String value, String author) {
        ensureLoaded();
        ENTRIES.removeIf(entry -> entry.key().equalsIgnoreCase(key));
        ENTRIES.add(new Entry(key, value, author, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
        save();
    }

    public static synchronized List<Entry> all() {
        ensureLoaded();
        return List.copyOf(ENTRIES);
    }

    public static synchronized void clear() {
        ensureLoaded();
        ENTRIES.clear();
        save();
    }

    public static synchronized JsonArray toJson() {
        ensureLoaded();
        JsonArray array = new JsonArray();
        for (Entry entry : ENTRIES) {
            JsonObject object = new JsonObject();
            object.addProperty("key", entry.key());
            object.addProperty("value", entry.value());
            object.addProperty("author", entry.author());
            array.add(object);
        }
        return array;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            load();
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("mineai").resolve("blackboard.json");
    }

    private static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("entries")) {
                for (JsonElement element : root.getAsJsonArray("entries")) {
                    JsonObject object = element.getAsJsonObject();
                    ENTRIES.add(new Entry(
                            object.get("key").getAsString(),
                            object.get("value").getAsString(),
                            object.has("author") ? object.get("author").getAsString() : "unknown",
                            object.has("timestamp") ? object.get("timestamp").getAsLong() : 0L));
                }
            }
        } catch (Exception exception) {
            MineAiMod.LOGGER.warn("[mineai] failed to load blackboard", exception);
        }
    }

    private static void save() {
        try {
            Path path = file();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            root.add("entries", toJson());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            MineAiMod.LOGGER.warn("[mineai] failed to save blackboard", exception);
        }
    }
}
