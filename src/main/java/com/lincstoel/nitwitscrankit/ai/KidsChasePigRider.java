package com.lincstoel.nitwitscrankit.ai;

import java.util.Comparator;
import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** CORE behavior for child villagers: run after a pig-riding nitwit, hopping and squealing. */
public class KidsChasePigRider extends Behavior<Villager> {
    private static final int CHECK_INTERVAL = 10;
    private static final double NOTICE_DISTANCE = 12.0;
    private static final double GIVE_UP_DISTANCE = 20.0;
    private static final float RUN_SPEED = 0.7F;

    private long nextCheck = -1;
    private Villager rider;

    public KidsChasePigRider() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_INTERVAL;
        if (!isOn() || !villager.isBaby() || !isPlaying(villager.getBrain())) {
            return false;
        }
        rider = nearestRider(level, villager);
        return rider != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return isOn()
                && villager.isBaby()
                && isPlaying(villager.getBrain())
                && rider != null
                && rider.isAlive()
                && NitwitAi.isPigRider(rider)
                && rider.closerThan(villager, GIVE_UP_DISTANCE);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        boolean chasing = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == rider)
                .isPresent();
        if (!chasing) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(rider, RUN_SPEED, 1));
        }
        if (villager.onGround() && villager.getRandom().nextInt(20) == 0) {
            villager.getJumpControl().jump();
        }
        if (villager.getRandom().nextInt(60) == 0) {
            villager.playAmbientSound();  // squeal (baby villagers are high-pitched)
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        rider = null;
    }

    private static boolean isOn() {
        return Config.PIG_RIDING_ENABLED.get() && Config.KIDS_JOIN_PIG_CHASE_ENABLED.get();
    }

    private static boolean isPlaying(Brain<Villager> brain) {
        return brain.isActive(Activity.PLAY) || brain.isActive(Activity.IDLE);
    }

    private static Villager nearestRider(ServerLevel level, Villager kid) {
        return level.getEntitiesOfClass(Villager.class, kid.getBoundingBox().inflate(NOTICE_DISTANCE),
                        other -> NitwitAi.isNitwit(other) && NitwitAi.isPigRider(other) && other.closerThan(kid, NOTICE_DISTANCE))
                .stream()
                .min(Comparator.comparingDouble(kid::distanceToSqr))
                .orElse(null);
    }
}
