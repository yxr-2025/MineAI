package com.mineai;

import com.mineai.command.NPCCommand;
import com.mineai.config.LlmConfig;
import com.mineai.entity.AgentPlayer;
import com.mineai.entity.AgentPlayerManager;
import com.mineai.llm.LlmProvider;
import com.mineai.llm.LlmService;
import com.mineai.llm.MockLlmProvider;
import com.mineai.llm.OpenAiCompatibleProvider;
import com.mineai.llm.RateLimitedProvider;
import com.mineai.llm.RetryingProvider;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Locale;

@Mod(MineAiMod.MOD_ID)
public final class MineAiMod {

    public static final String MOD_ID = "mineai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineAiMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        NPCCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("mineai").resolve("llm.json");
        LlmConfig config = LlmConfig.load(configFile);
        LlmService.setConfig(config);

        if (!config.enabled()) {
            LlmService.set(null);
            LOGGER.info("[mineai] LLM disabled; edit {}", configFile);
            return;
        }

        LlmProvider provider = buildProvider(config);
        LlmService.set(provider);

        if (config.hasSeparatePlanner()) {
            LlmConfig plannerConfig = withModel(config, config.effectivePlannerModel());
            LlmService.setPlanner(buildProvider(plannerConfig));
            LOGGER.info("[mineai] tiered models: executor={} planner={}",
                    config.model(), config.effectivePlannerModel());
        } else {
            LlmService.setPlanner(provider);
        }

        LOGGER.info("[mineai] LLM enabled: provider={} model={} baseUrl={} maxConcurrent={} minIntervalMs={}",
                provider.name(), config.model(), config.baseUrl(),
                config.maxConcurrentRequests(), config.minRequestIntervalMs());
    }

    private static LlmProvider buildProvider(LlmConfig config) {
        return switch (config.provider().toLowerCase(Locale.ROOT)) {
            case "mock" -> new MockLlmProvider();
            default -> new RetryingProvider(
                    new RateLimitedProvider(
                            new OpenAiCompatibleProvider(config),
                            config.maxConcurrentRequests(),
                            config.minRequestIntervalMs()),
                    config.maxRetries(),
                    config.retryBackoffMs());
        };
    }

    private static LlmConfig withModel(LlmConfig config, String model) {
        return new LlmConfig(config.enabled(), config.provider(), config.baseUrl(), config.apiKey(),
                model, config.temperature(), config.maxTokens(), config.timeoutSeconds(),
                config.maxSteps(), config.maxGoals(), config.maxConcurrentRequests(),
                config.minRequestIntervalMs(), config.maxRetries(), config.retryBackoffMs(),
                config.reflectionEnabled(), config.reflectionMinFailures(),
                config.reflectionSuccessInterval(), config.plannerModel());
    }

    /**
     * An AgentPlayer has no client, so the vanilla death/respawn flow would
     * leave it dead forever (PlayerList.respawn also builds a plain
     * ServerPlayer, losing the agent state). Instead the death is cancelled and
     * the agent recovers in place.
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof AgentPlayer npc) {
            event.setCanceled(true);
            npc.setHealth(npc.getMaxHealth());
            npc.getFoodData().eat(20, 1.0F);
            npc.removeAllEffects();
            npc.clearFire();
            npc.setRemainingFireTicks(0);
            LOGGER.info("[mineai] agent {} was rescued from death", npc.npcId());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AgentPlayerManager.tick();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        AgentPlayerManager.removeAll(event.getServer());
        LlmService.set(null);
        LlmService.setPlanner(null);
    }
}
