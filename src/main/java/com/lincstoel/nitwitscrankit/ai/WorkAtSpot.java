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
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/** WORK behavior for nitwits: go to the remembered work spot, then turn the crank or ride the turntable. */
public class WorkAtSpot extends Behavior<Villager> {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final double REACH_SQR = 2.5 * 2.5;
    private static final float WALK_SPEED = 0.5F;
    private static final int TURN_INTERVAL = 5;   // each turn keeps the crank going for 10 ticks
    private static final int SYNC_INTERVAL = 10;  // keeps the client-side crank sound playing (multiple of TURN_INTERVAL)
    private static final int RECHECK_INTERVAL = 20;
    private static final int GIVE_UP_TICKS = 600;
    private static final int UNREACHABLE_MEMORY_TICKS = 1200;
    private static final int HEAD_SHAKE_TICKS = 40;
    private static final int HEAD_SHAKE_REFRESH = 2;
    private static final int DIZZY_AFTER_TICKS = 200;
    private static final int DIZZY_MIN_TICKS = 60;
    private static final int DIZZY_EXTRA_TICKS = 60;

    private BlockPos target;
    private int ticksFar;
    private int ticksOnTurntable;
    private boolean lost;
    private boolean backwards;
    private boolean cranking;

    public WorkAtSpot() {
        super(Map.of(
                NitwitsCrankIt.WORK_SPOT.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 24000);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return NitwitAi.isNitwit(villager)
                && villager.getBrain().getMemory(NitwitsCrankIt.WORK_SPOT.get())
                        .map(spot -> FindWorkSpot.isValid(level, villager, spot))
                        .orElse(false);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        target = null;
        ticksFar = 0;
        ticksOnTurntable = 0;
        lost = false;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.getBrain().isActive(Activity.WORK)
                && villager.getBrain().hasMemoryValue(NitwitsCrankIt.WORK_SPOT.get())
                && !lost
                && ticksFar < GIVE_UP_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        WorkSpot spot = brain.getMemory(NitwitsCrankIt.WORK_SPOT.get()).orElse(null);
        if (spot == null) {
            lost = true;
            return;
        }
        if (!spot.pos().equals(target)) {
            // New spot (first tick, or FindWorkSpot found something better).
            target = spot.pos();
            ticksFar = 0;
            cranking = false;
            backwards = villager.getRandom().nextDouble() < Config.WRONG_WAY_CHANCE.get();
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            walkTo(villager, spot);
        }

        boolean arrived = switch (spot.kind()) {
            case HAND_CRANK -> tickHandCrank(level, villager, spot, gameTime);
            case TURNTABLE -> tickTurntable(level, villager, spot, gameTime);
        };
        if (lost) {
            return;
        }
        if (!arrived) {
            ticksFar++;
            if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                walkTo(villager, spot);
            }
            return;
        }

        ticksFar = 0;
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (villager.getRandom().nextDouble() < Config.PARTICLE_CHANCE.get()) {
            NitwitAi.happyParticles(level, villager, 1 + villager.getRandom().nextInt(2));
        }
        if (villager.onGround() && villager.getRandom().nextDouble() < Config.JUMP_CHANCE.get()) {
            villager.getJumpControl().jump();
        }
    }

    /** Returns true once the nitwit is in reach and cranking. */
    private boolean tickHandCrank(ServerLevel level, Villager villager, WorkSpot spot, long gameTime) {
        if (!Config.HAND_CRANKS_ENABLED.get() || !CreateCompat.isHandCrank(level, spot.pos())) {
            lost = true;
            return false;
        }
        if (villager.distanceToSqr(Vec3.atCenterOf(spot.pos())) > REACH_SQR) {
            return false;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(spot.pos()));
        if (!cranking) {
            cranking = true;
            NitwitMischiefTrigger.witness(level, villager, NitwitMischiefTrigger.CRANK);
        }
        if (gameTime % TURN_INTERVAL == 0) {
            CreateCompat.turnHandCrank(level, spot.pos(), backwards, gameTime % SYNC_INTERVAL == 0);
        }
        if (Config.HEAD_SHAKE_ENABLED.get() && villager.getUnhappyCounter() < HEAD_SHAKE_REFRESH) {
            // Vanilla's "no" head-shake animation (synced to clients), without the "no" sound.
            villager.setUnhappyCounter(HEAD_SHAKE_TICKS);
        }
        if (villager.getRandom().nextDouble() < Config.THINKING_SOUND_CHANCE.get()) {
            // The villager "hmm": deep in thought about which way is clockwise.
            villager.playAmbientSound();
        }
        return true;
    }

    /** Returns true while the nitwit is standing on the turntable. Create's turntable does the spinning. */
    private boolean tickTurntable(ServerLevel level, Villager villager, WorkSpot spot, long gameTime) {
        if (gameTime % RECHECK_INTERVAL == 0 && !FindWorkSpot.isValid(level, villager, spot)) {
            lost = true;
            return false;
        }
        boolean on = NitwitAi.isStandingOn(villager, spot.pos());
        if (on) {
            ticksOnTurntable++;
        }
        return on;
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (ticksOnTurntable >= DIZZY_AFTER_TICKS && Config.TURNTABLE_DIZZY_ENABLED.get()) {
            brain.setMemoryWithExpiry(NitwitsCrankIt.DIZZY.get(), Unit.INSTANCE,
                    DIZZY_MIN_TICKS + villager.getRandom().nextInt(DIZZY_EXTRA_TICKS));
            LOGGER.debug("Nitwit dizzy after {} ticks on a turntable", ticksOnTurntable);
            NitwitMischiefTrigger.witness(level, villager, NitwitMischiefTrigger.DIZZY);
        }
        if (ticksFar >= GIVE_UP_TICKS) {
            brain.getMemory(NitwitsCrankIt.WORK_SPOT.get()).ifPresent(spot ->
                    FindWorkSpot.markUnreachable(brain, spot.pos(), gameTime, UNREACHABLE_MEMORY_TICKS));
            brain.eraseMemory(NitwitsCrankIt.WORK_SPOT.get());
        } else if (lost) {
            brain.getMemory(NitwitsCrankIt.WORK_SPOT.get())
                    .filter(spot -> spot.kind() == WorkSpot.Kind.TURNTABLE && NitwitAi.isStandingOn(villager, spot.pos()))
                    .ifPresent(spot -> stepOff(level, villager, spot.pos()));
            brain.eraseMemory(NitwitsCrankIt.WORK_SPOT.get());
        }
    }

    /** Hops off a turntable we can't keep (stopped, or someone/something else is on it) onto free ground beside it. */
    private static void stepOff(ServerLevel level, Villager villager, BlockPos turntable) {
        for (Direction side : Direction.Plane.HORIZONTAL.shuffledCopy(villager.getRandom())) {
            BlockPos beside = turntable.relative(side);
            if (NitwitAi.canStandAt(level, beside)) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(beside, WALK_SPEED, 0));
                return;
            }
        }
    }

    private static void walkTo(Villager villager, WorkSpot spot) {
        // For a turntable, aim for the space above it; closeEnough 1 is only satisfied on top of it.
        BlockPos pos = spot.kind() == WorkSpot.Kind.TURNTABLE ? spot.pos().above() : spot.pos();
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, WALK_SPEED, 1));
    }
}
