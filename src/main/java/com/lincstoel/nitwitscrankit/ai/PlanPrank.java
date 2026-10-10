package com.lincstoel.nitwitscrankit.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.compat.CreateCompat;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * CORE behavior for nitwits: occasionally decides to pull a prank, then switches the nitwit into the
 * {@link NitwitsCrankIt#PRANK} activity. Bells and whistles are for the afternoon play period; shoving a
 * villager into water can happen any time the nitwit is awake.
 */
public class PlanPrank extends Behavior<Villager> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL = 200;
    private static final double MAX_BELL_DISTANCE = 48.0;
    private static final double MAX_VICTIM_DISTANCE = 12.0;

    private long nextCheck = -1;

    public PlanPrank() {
        super(Map.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (nextCheck < 0) {
            nextCheck = now + villager.getRandom().nextInt(CHECK_INTERVAL);
        }
        return now >= nextCheck;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        nextCheck = gameTime + CHECK_INTERVAL;
        Brain<Villager> brain = villager.getBrain();
        if (!NitwitAi.isNitwit(villager)
                || !NitwitAi.isAwake(villager)
                || villager.isPassenger()
                || brain.hasMemoryValue(NitwitsCrankIt.PRANK_TARGET.get())) {
            return;
        }
        boolean playing = NitwitAi.isPlayTime(villager) && NitwitAi.isFreeToPlay(villager);
        // Shoving someone in the water is fair game whenever awake, even in the middle of work.
        boolean couldPush = NitwitAi.isFreeToPlay(villager) || brain.isActive(Activity.WORK);
        if (!playing && !couldPush) {
            return;
        }

        // Consider each kind of prank in a random order and pull the first one that rolls and has a target.
        List<PrankTarget.Kind> kinds = new ArrayList<>(List.of(PrankTarget.Kind.values()));
        Util.shuffle(kinds, villager.getRandom());
        for (PrankTarget.Kind kind : kinds) {
            if (kind == PrankTarget.Kind.WATER_PUSH ? !couldPush : !playing) {
                continue;
            }
            PrankTarget target = tryPlan(level, villager, kind, gameTime);
            if (target != null) {
                startPrank(brain, target);
                return;
            }
        }
    }

    private static PrankTarget tryPlan(ServerLevel level, Villager villager, PrankTarget.Kind kind, long gameTime) {
        if (!isEnabled(kind) || isCoolingDown(villager, kind, gameTime)
                || villager.getRandom().nextDouble() >= chance(kind)) {
            return null;
        }
        PrankTarget target = switch (kind) {
            case VILLAGE_BELL -> {
                BlockPos bell = villageBell(level, villager);
                long cooldown = Config.BELL_PRANK_COOLDOWN_DAYS.get() * 24000L;
                yield bell != null && BellPrankData.get(level).tryClaim(bell, gameTime, cooldown)
                        ? new PrankTarget(bell, kind, gameTime)
                        : null;
            }
            case DESK_BELL -> nearestBlock(level, villager, kind, gameTime, CreateCompat::isDeskBell);
            case STEAM_WHISTLE -> nearestBlock(level, villager, kind, gameTime, CreateCompat::isTootableWhistle);
            case WATER_PUSH -> {
                Villager victim = findVictim(level, villager);
                yield victim != null ? new PrankTarget(victim.blockPosition(), kind, victim, gameTime) : null;
            }
        };
        if (target != null && kind != PrankTarget.Kind.VILLAGE_BELL) {
            startCooldown(villager, kind, gameTime + cooldownTicks(kind));
        }
        return target;
    }

    private static boolean isEnabled(PrankTarget.Kind kind) {
        return switch (kind) {
            case VILLAGE_BELL -> Config.BELL_PRANK_ENABLED.get();
            case DESK_BELL -> CreateCompat.isLoaded() && Config.DESK_BELL_PRANK_ENABLED.get();
            case STEAM_WHISTLE -> CreateCompat.isLoaded() && Config.STEAM_WHISTLE_PRANK_ENABLED.get();
            case WATER_PUSH -> Config.WATER_PUSH_PRANK_ENABLED.get();
        };
    }

    private static double chance(PrankTarget.Kind kind) {
        return switch (kind) {
            case VILLAGE_BELL -> Config.BELL_PRANK_CHANCE.get();
            case DESK_BELL -> Config.DESK_BELL_PRANK_CHANCE.get();
            case STEAM_WHISTLE -> Config.STEAM_WHISTLE_PRANK_CHANCE.get();
            case WATER_PUSH -> Config.WATER_PUSH_PRANK_CHANCE.get();
        };
    }

    /** Per-nitwit cooldowns; the village bell's cooldown is per bell instead (see {@link BellPrankData}). */
    private static int cooldownTicks(PrankTarget.Kind kind) {
        return switch (kind) {
            case VILLAGE_BELL -> 0;
            case DESK_BELL -> Config.DESK_BELL_PRANK_COOLDOWN_TICKS.get();
            case STEAM_WHISTLE -> Config.STEAM_WHISTLE_PRANK_COOLDOWN_TICKS.get();
            case WATER_PUSH -> Config.WATER_PUSH_PRANK_COOLDOWN_TICKS.get();
        };
    }

    private static boolean isCoolingDown(Villager villager, PrankTarget.Kind kind, long gameTime) {
        return villager.getBrain().getMemory(NitwitsCrankIt.PRANK_COOLDOWNS.get())
                .map(cooldowns -> cooldowns.getOrDefault(kind, 0L) > gameTime)
                .orElse(false);
    }

    private static void startCooldown(Villager villager, PrankTarget.Kind kind, long until) {
        Brain<Villager> brain = villager.getBrain();
        Map<PrankTarget.Kind, Long> cooldowns = new EnumMap<>(PrankTarget.Kind.class);
        brain.getMemory(NitwitsCrankIt.PRANK_COOLDOWNS.get()).ifPresent(cooldowns::putAll);
        cooldowns.put(kind, until);
        brain.setMemory(NitwitsCrankIt.PRANK_COOLDOWNS.get(), cooldowns);
    }

    /** The village's bell, i.e. the villager's meeting point, if it is close by and still a bell. */
    private static BlockPos villageBell(ServerLevel level, Villager villager) {
        Optional<GlobalPos> meetingPoint = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (meetingPoint.isEmpty() || meetingPoint.get().dimension() != level.dimension()) {
            return null;
        }
        BlockPos pos = meetingPoint.get().pos();
        if (!pos.closerToCenterThan(villager.position(), MAX_BELL_DISTANCE)
                || !level.isLoaded(pos)
                || !(level.getBlockState(pos).getBlock() instanceof BellBlock)) {
            return null;
        }
        return pos;
    }

    private static PrankTarget nearestBlock(ServerLevel level, Villager villager, PrankTarget.Kind kind, long gameTime,
                                            Predicate<BlockEntity> matches) {
        BlockPos origin = villager.blockPosition();
        BlockPos[] best = {null};
        NitwitAi.forEachBlockEntityNear(level, origin, be -> {
            BlockPos pos = be.getBlockPos();
            if ((best[0] == null || pos.distSqr(origin) < best[0].distSqr(origin)) && matches.test(be)) {
                best[0] = pos.immutable();
            }
        });
        return best[0] != null ? new PrankTarget(best[0], kind, gameTime) : null;
    }

    /** The nearest other villager in sight standing on dry land right next to still water. */
    private static Villager findVictim(ServerLevel level, Villager nitwit) {
        return NitwitAi.nearestVisible(nitwit, Villager.class, other -> other != nitwit
                && other.isAlive()
                && other.closerThan(nitwit, MAX_VICTIM_DISTANCE)
                && other.onGround()
                && !other.isInWater()
                && !other.isSleeping()
                && !other.isPassenger()
                && !other.isLeashed()
                && NitwitAi.waterSide(level, other.blockPosition()) != null);
    }

    private static void startPrank(Brain<Villager> brain, PrankTarget target) {
        LOGGER.debug("Nitwit pranking {} at {}", target.kind(), target.pos());
        brain.setMemory(NitwitsCrankIt.PRANK_TARGET.get(), target);
        brain.setActiveActivityIfPossible(NitwitsCrankIt.PRANK.get());
    }
}
