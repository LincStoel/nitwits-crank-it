package com.lincstoel.nitwitscrankit.ai;

import java.util.List;
import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitMischiefTrigger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * CORE behavior for nitwits: during the afternoon play period, hop on a saddled pig they come across and
 * steer it around (chasing other nitwits) until nightfall.
 */
public class RideSaddledPig extends Behavior<Villager> {
    private static final int CHECK_INTERVAL = 40;
    private static final double SPOT_DISTANCE = 12.0;
    private static final double MOUNT_DISTANCE_SQR = 2.0 * 2.0;
    private static final float RUN_SPEED = 0.7F;
    private static final int APPROACH_TIMEOUT = 400;
    private static final double PIG_SPEED = 1.3;
    private static final double PIG_CHASE_SPEED = 1.6;
    private static final double CHASE_DISTANCE = 16.0;
    private static final int MIN_CHASE_TICKS = 100;
    private static final int EXTRA_CHASE_TICKS = 100;
    private static final int WANDER_TICKS = 100;
    private static final int CHASE_REPATH_INTERVAL = 10;

    private long nextCheck = -1;
    private Pig pig;
    private int approachTicks;
    private Villager chaseTarget;
    private long modeUntil;

    public RideSaddledPig() {
        super(Map.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED
        ), 24000);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (villager.getVehicle() instanceof Pig) {
            // Still mounted after a reload; resume, and stop() dismounts if it's past playtime.
            return true;
        }
        long now = level.getGameTime();
        if (nextCheck < 0) {
            nextCheck = now + villager.getRandom().nextInt(CHECK_INTERVAL);
        }
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_INTERVAL;
        return Config.PIG_RIDING_ENABLED.get()
                && NitwitAi.isNitwit(villager)
                && !villager.isPassenger()
                && NitwitAi.isPlayTime(villager)
                && NitwitAi.isFreeToPlay(villager)
                && findPig(villager) != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        pig = villager.getVehicle() instanceof Pig mounted ? mounted : findPig(villager);
        approachTicks = 0;
        chaseTarget = null;
        modeUntil = 0;
        if (pig != null && !villager.isPassenger()) {
            runTo(villager, pig);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        if (pig == null || !pig.isAlive() || !Config.PIG_RIDING_ENABLED.get() || !NitwitAi.isPlayTime(villager)) {
            return false;
        }
        // Anything more pressing than play (a hero, panic, hiding, a raid) ends the ride.
        if (!NitwitAi.isFreeToPlay(villager)) {
            return false;
        }
        if (villager.getVehicle() == pig) {
            return true;
        }
        return !villager.isPassenger() && isRideable(pig) && approachTicks < APPROACH_TIMEOUT;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        if (villager.getVehicle() == pig) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            steerPig(level, villager, gameTime);
            if (villager.getRandom().nextDouble() < Config.PARTICLE_CHANCE.get()) {
                NitwitAi.happyParticles(level, villager, 1 + villager.getRandom().nextInt(2));
            }
            return;
        }

        approachTicks++;
        if (villager.distanceToSqr(pig) <= MOUNT_DISTANCE_SQR) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            if (villager.startRiding(pig)) {
                villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.2F);
                NitwitMischiefTrigger.witness(level, villager, NitwitMischiefTrigger.PIG_RIDE);
                NitwitAi.happyParticles(level, villager, 3);
            }
        } else if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            runTo(villager, pig);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        if (villager.getVehicle() instanceof Pig) {
            villager.stopRiding();
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pig = null;
        chaseTarget = null;
    }

    /**
     * Alternates between charging the pig at a nearby nitwit and wandering it somewhere random.
     * (Other nitwits run away from riders; see {@link FleePigRider}.)
     */
    private void steerPig(ServerLevel level, Villager villager, long gameTime) {
        if (gameTime >= modeUntil || (chaseTarget != null && !isChaseable(villager, chaseTarget))) {
            chaseTarget = Config.PIG_CHASE_ENABLED.get() && villager.getRandom().nextBoolean()
                    ? findNitwitToChase(level, villager)
                    : null;
            modeUntil = gameTime + (chaseTarget != null
                    ? MIN_CHASE_TICKS + villager.getRandom().nextInt(EXTRA_CHASE_TICKS)
                    : WANDER_TICKS);
            pig.getNavigation().stop();
        }

        if (chaseTarget != null) {
            if (gameTime % CHASE_REPATH_INTERVAL == 0) {
                pig.getNavigation().moveTo(chaseTarget, PIG_CHASE_SPEED);
            }
        } else if (pig.getNavigation().isDone() && villager.getRandom().nextInt(20) == 0) {
            Vec3 dest = LandRandomPos.getPos(pig, 12, 5);
            if (dest != null) {
                pig.getNavigation().moveTo(dest.x, dest.y, dest.z, PIG_SPEED);
            }
        }
    }

    private static Villager findNitwitToChase(ServerLevel level, Villager rider) {
        List<Villager> candidates = level.getEntitiesOfClass(Villager.class, rider.getBoundingBox().inflate(CHASE_DISTANCE),
                other -> isChaseable(rider, other));
        return candidates.isEmpty() ? null : candidates.get(rider.getRandom().nextInt(candidates.size()));
    }

    private static boolean isChaseable(Villager rider, Villager other) {
        return other != rider
                && other.isAlive()
                && NitwitAi.isNitwit(other)
                && !other.isPassenger()
                && other.closerThan(rider, CHASE_DISTANCE);
    }

    /** Leashed or named pigs are someone's pets, not a nitwit's ride. */
    private static boolean isRideable(Pig pig) {
        return pig.isAlive() && pig.isSaddled() && !pig.isVehicle() && !pig.isBaby()
                && !pig.isLeashed() && !pig.hasCustomName();
    }

    private static Pig findPig(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> visible.findClosest(entity -> entity instanceof Pig pig
                        && isRideable(pig)
                        && entity.closerThan(villager, SPOT_DISTANCE)))
                .map(Pig.class::cast)
                .orElse(null);
    }

    private static void runTo(Villager villager, Pig pig) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pig, RUN_SPEED, 1));
    }
}
