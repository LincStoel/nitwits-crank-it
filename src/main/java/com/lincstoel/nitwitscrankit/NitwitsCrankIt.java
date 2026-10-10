package com.lincstoel.nitwitscrankit;

import java.util.Map;
import java.util.Optional;

import com.lincstoel.nitwitscrankit.ai.Antic;
import com.lincstoel.nitwitscrankit.ai.FollowTarget;
import com.lincstoel.nitwitscrankit.ai.GolemChase;
import com.lincstoel.nitwitscrankit.ai.PrankTarget;
import com.lincstoel.nitwitscrankit.ai.WhistleTracker;
import com.lincstoel.nitwitscrankit.ai.WorkSpot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NitwitsCrankIt.MOD_ID)
public class NitwitsCrankIt {
    public static final String MOD_ID = "nitwits_crank_it";

    private static final DeferredRegister<MemoryModuleType<?>> MEMORY_TYPES =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, MOD_ID);
    private static final DeferredRegister<Activity> ACTIVITIES =
            DeferredRegister.create(Registries.ACTIVITY, MOD_ID);
    private static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, MOD_ID);

    // None of these memories are saved; they are re-derived after a reload.

    /** The hand crank or turntable a nitwit has chosen to work at. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<WorkSpot>> WORK_SPOT =
            MEMORY_TYPES.register("work_spot", () -> new MemoryModuleType<>(Optional.empty()));

    /** Work spots the nitwit gave up trying to reach, each with the game time until which the search skips it. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Map<BlockPos, Long>>> UNREACHABLE_SPOTS =
            MEMORY_TYPES.register("unreachable_spots", () -> new MemoryModuleType<>(Optional.empty()));

    /** What a nitwit is currently pranking. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<PrankTarget>> PRANK_TARGET =
            MEMORY_TYPES.register("prank_target", () -> new MemoryModuleType<>(Optional.empty()));

    /** Game time at which this nitwit may next pull each kind of (per-nitwit cooldown) prank. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Map<PrankTarget.Kind, Long>>> PRANK_COOLDOWNS =
            MEMORY_TYPES.register("prank_cooldowns", () -> new MemoryModuleType<>(Optional.empty()));

    /** The play-time antic (belt surfing, fan play, stare-fishing) a nitwit is up to. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Antic>> ANTIC =
            MEMORY_TYPES.register("antic", () -> new MemoryModuleType<>(Optional.empty()));

    /** Present (with an expiry) while a nitwit is dizzy from a long turntable ride. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Unit>> DIZZY =
            MEMORY_TYPES.register("dizzy", () -> new MemoryModuleType<>(Optional.empty()));

    /** Present (with an expiry) while an iron golem is chasing this nitwit around. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<IronGolem>> CHASED_BY =
            MEMORY_TYPES.register("chased_by", () -> new MemoryModuleType<>(Optional.empty()));

    /** The hero or iron golem a nitwit is tagging along after. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<FollowTarget>> FOLLOW_TARGET =
            MEMORY_TYPES.register("follow_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final DeferredHolder<Activity, Activity> PRANK =
            ACTIVITIES.register("prank", () -> new Activity(MOD_ID + ":prank"));

    public static final DeferredHolder<Activity, Activity> FOLLOW =
            ACTIVITIES.register("follow", () -> new Activity(MOD_ID + ":follow"));

    public static final DeferredHolder<Activity, Activity> ANTICS =
            ACTIVITIES.register("antics", () -> new Activity(MOD_ID + ":antics"));

    /** Loot condition used by the nitwit gift table so the junk gifts can be switched off in the config. */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> JUNK_GIFTS_ENABLED =
            LOOT_CONDITIONS.register("junk_gifts_enabled", () -> new LootItemConditionType(JunkGiftsEnabled.CODEC));

    public NitwitsCrankIt(IEventBus modBus, ModContainer container) {
        MEMORY_TYPES.register(modBus);
        ACTIVITIES.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(AnvilBonk.class);
        NeoForge.EVENT_BUS.register(GolemChase.class);
        NeoForge.EVENT_BUS.register(WhistleTracker.class);
    }
}
