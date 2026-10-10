package com.lincstoel.nitwitscrankit.ai;

import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitMischiefTrigger;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.compat.CreateCompat;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * PRANK behavior: run to the target, do the deed (ring a bell, toot a whistle, shove a villager into water),
 * cheer, then run off. Nearby villagers disapprove, and any iron golem that saw it gives chase.
 * <p>
 * Like pulling the fire alarm: a village bell sends everyone else into hiding, but not the prankster.
 */
public class PerformPrank extends Behavior<Villager> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double BELL_REACH_SQR = 2.5 * 2.5;
    private static final double WHISTLE_REACH_HORIZONTAL_SQR = 2.5 * 2.5;
    private static final double WHISTLE_REACH_VERTICAL = 4.0;
    private static final double SHOVE_REACH_SQR = 1.8 * 1.8;
    private static final double SHOVE_STRENGTH = 0.6;
    private static final double DISAPPROVAL_DISTANCE = 16.0;
    private static final float RUN_SPEED = 0.75F;
    private static final int APPROACH_TIMEOUT = 600;
    private static final int FLEE_TICKS = 160;
    private static final int GOLEM_REPATH_TICKS = 40;
    private static final int DESK_BELL_PRESS_INTERVAL = 4;
    private static final int WHISTLE_TOOT_TICKS = 12;
    private static final int WHISTLE_PAUSE_TICKS = 6;
    // How long past its toot a whistle may keep blowing if the prankster never lets go (died, unloaded...).
    private static final int WHISTLE_RELEASE_MARGIN = 20;

    private enum Phase { APPROACH, ACT, FLEE, DONE }

    private Phase phase;
    private int phaseTicks;
    private int actionsLeft;
    private boolean acted;
    private long nextAction;
    private boolean whistleBlowing;
    private Vec3 fleeTo;
    private int ticksSinceFleePick;

    public PerformPrank() {
        super(Map.of(
                NitwitsCrankIt.PRANK_TARGET.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.HEARD_BELL_TIME, MemoryStatus.REGISTERED
        ), 2400);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        setPhase(Phase.APPROACH);
        acted = false;
        whistleBlowing = false;
        approach(villager, target(villager));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        if (phase == Phase.DONE || !brain.hasMemoryValue(NitwitsCrankIt.PRANK_TARGET.get())) {
            return false;
        }
        // The village bell makes the prankster want to hide too; tick() undoes that while fleeing.
        return brain.isActive(NitwitsCrankIt.PRANK.get()) || (phase == Phase.FLEE && brain.isActive(Activity.HIDE));
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        PrankTarget target = target(villager);
        phaseTicks++;

        switch (phase) {
            case APPROACH -> {
                if (!targetStillValid(level, target) || phaseTicks > APPROACH_TIMEOUT) {
                    setPhase(Phase.DONE);
                    return;
                }
                lookAt(villager, target);
                if (inReach(villager, target)) {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    actionsLeft = switch (target.kind()) {
                        case DESK_BELL -> 3 + villager.getRandom().nextInt(4);
                        case STEAM_WHISTLE -> 2 + villager.getRandom().nextInt(2);
                        case VILLAGE_BELL, WATER_PUSH -> 1;
                    };
                    nextAction = gameTime;
                    setPhase(Phase.ACT);
                } else if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                    approach(villager, target);
                }
            }
            case ACT -> {
                lookAt(villager, target);
                if (gameTime < nextAction) {
                    return;
                }
                if (!targetStillValid(level, target)
                        || (target.victim() != null && !villager.hasLineOfSight(target.victim()))) {
                    setPhase(Phase.DONE);
                    return;
                }
                act(level, villager, target, gameTime);
                acted = true;
                if (actionsLeft <= 0 && !whistleBlowing) {
                    pulledItOff(level, villager, target);
                }
            }
            case FLEE -> tickFlee(level, villager);
            case DONE -> {
            }
        }
    }

    /** Does one step of the prank and schedules the next. */
    private void act(ServerLevel level, Villager villager, PrankTarget target, long gameTime) {
        BlockPos pos = target.pos();
        switch (target.kind()) {
            case VILLAGE_BELL -> {
                BlockState state = level.getBlockState(pos);
                ((BellBlock) state.getBlock()).attemptToRing(villager, level, pos, null);
                NitwitMischiefTrigger.witness(level, villager, NitwitMischiefTrigger.VILLAGE_BELL);
                actionsLeft--;
            }
            case DESK_BELL -> {
                // Press it exactly like a player would, so sound, animation and redstone pulse all happen.
                level.getBlockState(pos).useWithoutItem(level, FakePlayerFactory.getMinecraft(level),
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                actionsLeft--;
                nextAction = gameTime + DESK_BELL_PRESS_INTERVAL;
            }
            case STEAM_WHISTLE -> {
                // Alternate toot / pause until out of toots.
                whistleBlowing = !whistleBlowing;
                if (whistleBlowing) {
                    WhistleTracker.blow(level, pos, gameTime + WHISTLE_TOOT_TICKS + WHISTLE_RELEASE_MARGIN);
                    actionsLeft--;
                    nextAction = gameTime + WHISTLE_TOOT_TICKS;
                } else {
                    WhistleTracker.release(level, pos);
                    nextAction = gameTime + WHISTLE_PAUSE_TICKS;
                }
            }
            case WATER_PUSH -> {
                LivingEntity victim = target.victim();
                Direction side = NitwitAi.waterSide(level, victim.blockPosition());
                victim.push(side.getStepX() * SHOVE_STRENGTH, 0.3, side.getStepZ() * SHOVE_STRENGTH);
                victim.hurtMarked = true;
                victim.playSound(SoundEvents.VILLAGER_HURT, 1.0F, 1.0F);
                actionsLeft--;
            }
        }
        LOGGER.debug("Nitwit pranked {} at {}", target.kind(), pos);
    }

    /** Cheer, collect everyone's disapproval, and leg it. */
    private void pulledItOff(ServerLevel level, Villager villager, PrankTarget target) {
        villager.playSound(SoundEvents.VILLAGER_CELEBRATE, 1.0F, 1.1F + villager.getRandom().nextFloat() * 0.3F);
        NitwitAi.happyParticles(level, villager, 4 + villager.getRandom().nextInt(3));
        if (Config.PRANK_UNHAPPY_PARTICLES_ENABLED.get()) {
            for (Villager other : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(DISAPPROVAL_DISTANCE),
                    // Fellow nitwits think it's hilarious.
                    other -> other != villager && other.isAlive() && !NitwitAi.isNitwit(other))) {
                NitwitAi.unhappyParticle(level, other);
            }
        }
        GolemChase.witnessPrank(level, villager);

        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        Vec3 from = target.victim() != null ? target.victim().position() : Vec3.atCenterOf(target.pos());
        fleeFrom(villager, from);
        setPhase(Phase.FLEE);
    }

    private void tickFlee(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
        if (!brain.isActive(NitwitsCrankIt.PRANK.get())) {
            brain.setActiveActivityIfPossible(NitwitsCrankIt.PRANK.get());
        }

        IronGolem chaser = GolemChase.chaserOf(villager);
        boolean arrived = villager.position().distanceToSqr(fleeTo) < 4.0;
        if (chaser == null && (phaseTicks > FLEE_TICKS || arrived)) {
            setPhase(Phase.DONE);
            return;
        }
        ticksSinceFleePick++;
        if (chaser != null && (arrived || ticksSinceFleePick > GOLEM_REPATH_TICKS)) {
            // Keep running away from the golem for as long as it's after us.
            fleeFrom(villager, chaser.position());
        }

        boolean headingAway = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget().currentPosition().distanceToSqr(fleeTo) < 1.0)
                .isPresent();
        if (!headingAway) {
            // Arrived early, or the hide activity redirected us home for a tick.
            runTo(villager, fleeTo);
        }
        if (villager.onGround() && villager.getRandom().nextInt(25) == 0) {
            villager.getJumpControl().jump();
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        brain.getMemory(NitwitsCrankIt.PRANK_TARGET.get()).ifPresent(target -> {
            if (target.kind() == PrankTarget.Kind.STEAM_WHISTLE && whistleBlowing) {
                WhistleTracker.release(level, target.pos());
            } else if (target.kind() == PrankTarget.Kind.VILLAGE_BELL && !acted) {
                // Never got to ring it, so the bell is fair game again.
                BellPrankData.get(level).release(target.pos(), target.plannedAt());
            }
        });
        whistleBlowing = false;
        brain.eraseMemory(NitwitsCrankIt.PRANK_TARGET.get());
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (phase == Phase.FLEE || phase == Phase.DONE) {
            brain.eraseMemory(MemoryModuleType.HEARD_BELL_TIME);
        }
        if (brain.isActive(NitwitsCrankIt.PRANK.get())) {
            // Back to idling; the schedule picks the right activity from there.
            brain.useDefaultActivity();
        }
    }

    private void setPhase(Phase phase) {
        this.phase = phase;
        this.phaseTicks = 0;
    }

    private static PrankTarget target(Villager villager) {
        return villager.getBrain().getMemory(NitwitsCrankIt.PRANK_TARGET.get()).orElseThrow();
    }

    private static boolean targetStillValid(ServerLevel level, PrankTarget target) {
        BlockPos pos = target.pos();
        return switch (target.kind()) {
            case VILLAGE_BELL -> level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof BellBlock;
            case DESK_BELL -> CreateCompat.isDeskBell(level, pos);
            case STEAM_WHISTLE -> CreateCompat.isTootableWhistle(level, pos);
            case WATER_PUSH -> {
                LivingEntity victim = target.victim();
                yield victim.isAlive()
                        && victim.level() == level
                        && !victim.isInWater()
                        && NitwitAi.waterSide(level, victim.blockPosition()) != null;
            }
        };
    }

    private static boolean inReach(Villager villager, PrankTarget target) {
        return switch (target.kind()) {
            case VILLAGE_BELL, DESK_BELL -> villager.distanceToSqr(Vec3.atCenterOf(target.pos())) <= BELL_REACH_SQR;
            case STEAM_WHISTLE -> {
                // Whistles often sit on top of a boiler; "pulling the cord" from below is fine.
                Vec3 center = Vec3.atCenterOf(target.pos());
                double dx = villager.getX() - center.x;
                double dz = villager.getZ() - center.z;
                yield dx * dx + dz * dz <= WHISTLE_REACH_HORIZONTAL_SQR
                        && Math.abs(villager.getEyeY() - center.y) <= WHISTLE_REACH_VERTICAL;
            }
            case WATER_PUSH -> villager.distanceToSqr(target.victim()) <= SHOVE_REACH_SQR;
        };
    }

    private static void lookAt(Villager villager, PrankTarget target) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, target.victim() != null
                ? new EntityTracker(target.victim(), true)
                : new BlockPosTracker(target.pos()));
    }

    private static void approach(Villager villager, PrankTarget target) {
        WalkTarget walk = switch (target.kind()) {
            // Bells aren't walkable, so stopping one block short is close enough to reach them.
            case VILLAGE_BELL, DESK_BELL -> new WalkTarget(target.pos(), RUN_SPEED, 1);
            case STEAM_WHISTLE -> new WalkTarget(target.pos(), RUN_SPEED, 2);
            case WATER_PUSH -> new WalkTarget(target.victim(), RUN_SPEED, 1);
        };
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walk);
    }

    private void fleeFrom(Villager villager, Vec3 from) {
        Vec3 pos = LandRandomPos.getPosAway(villager, 16, 7, from);
        if (pos == null) {
            pos = DefaultRandomPos.getPosAway(villager, 16, 7, from);
        }
        fleeTo = pos != null ? pos : villager.position();
        ticksSinceFleePick = 0;
        runTo(villager, fleeTo);
    }

    private static void runTo(Villager villager, Vec3 pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, RUN_SPEED, 0));
    }
}
