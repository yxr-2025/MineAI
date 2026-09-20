package com.mineai.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mineai.MineAiMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-NPC persistent memory: recent conversation, learned notes and skills.
 * Stored under {@code config/mineai/memory/<npcId>.json}.
 */
public final class AgentMemory {

    private static final int MAX_HISTORY = 60;
    private static final int MAX_NOTES = 30;
    private static final int MAX_SKILLS = 40;

    private final Path file;
    private final List<JsonObject> history = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<Skill> skills = new ArrayList<>();
    private final List<String> lessons = new ArrayList<>();

    public AgentMemory(String npcId) {
        this.file = FMLPaths.CONFIGDIR.get()
                .resolve("mineai").resolve("memory").resolve(npcId + ".json");
        load();
    }

    public List<JsonObject> history() {
        return history;
    }

    public void addHistory(JsonObject message) {
        history.add(message);
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    public void clearHistory() {
        history.clear();
    }

    public List<String> notes() {
        return notes;
    }

    public void addNote(String note) {
        if (note == null || note.isBlank() || notes.contains(note)) {
            return;
        }
        notes.add(note);
        while (notes.size() > MAX_NOTES) {
            notes.remove(0);
        }
    }

    public List<String> lessons() {
        return lessons;
    }

    public void addLesson(String lesson) {
        if (lesson == null || lesson.isBlank() || lessons.contains(lesson)) {
            return;
        }
        lessons.add(lesson);
        while (lessons.size() > MAX_NOTES) {
            lessons.remove(0);
        }
    }

    public List<Skill> skills() {
        return skills;
    }

    public Skill skill(String name) {
        for (Skill skill : skills) {
            if (skill.name().equalsIgnoreCase(name)) {
                return skill;
            }
        }
        return null;
    }

    public void putSkill(Skill skill) {
        for (int i = 0; i < skills.size(); i++) {
            if (skills.get(i).name().equalsIgnoreCase(skill.name())) {
                skills.set(i, skill);
                return;
            }
        }
        skills.add(skill);
        while (skills.size() > MAX_SKILLS) {
            skills.remove(0);
        }
    }

    public void recordUse(String name) {
        Skill existing = skill(name);
        if (existing != null) {
            putSkill(new Skill(existing.name(), existing.description(), existing.steps(),
                    existing.uses() + 1, existing.originX(), existing.originY(), existing.originZ()));
        }
    }

    public void clear() {
        history.clear();
        notes.clear();
        skills.clear();
        lessons.clear();
        save();
    }

    public void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();

            JsonArray noteArray = new JsonArray();
            for (String note : notes) {
                noteArray.add(note);
            }
            root.add("notes", noteArray);

            JsonArray skillArray = new JsonArray();
            for (Skill skill : skills) {
                skillArray.add(skill.toJson());
            }
            root.add("skills", skillArray);

            JsonArray lessonArray = new JsonArray();
            for (String lesson : lessons) {
                lessonArray.add(lesson);
            }
            root.add("lessons", lessonArray);

            JsonArray historyArray = new JsonArray();
            for (JsonObject message : history) {
                historyArray.add(message);
            }
            root.add("history", historyArray);

            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            MineAiMod.LOGGER.warn("[mineai] failed to save memory {}", file, exception);
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("notes")) {
                for (JsonElement element : root.getAsJsonArray("notes")) {
                    notes.add(element.getAsString());
                }
            }
            if (root.has("skills")) {
                for (JsonElement element : root.getAsJsonArray("skills")) {
                    skills.add(Skill.fromJson(element.getAsJsonObject()));
                }
            }
            if (root.has("lessons")) {
                for (JsonElement element : root.getAsJsonArray("lessons")) {
                    lessons.add(element.getAsString());
                }
            }
            if (root.has("history")) {
                for (JsonElement element : root.getAsJsonArray("history")) {
                    history.add(element.getAsJsonObject());
                }
            }
        } catch (IOException | RuntimeException exception) {
            MineAiMod.LOGGER.warn("[mineai] failed to load memory {}", file, exception);
        }
    }
}
