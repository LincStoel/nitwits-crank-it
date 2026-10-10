package com.lincstoel.nitwitscrankit.ai;

import java.util.Comparator;
import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** CORE behavior for nitwits at play: run away, hopping in a panic, from a nitwit charging around on a pig. */
public class FleePigRider extends Behavior<Villager> {
    private static final int CHECK_INTERVAL = 5;
    private static final double NOTICE_DISTANCE = 7.0;
    private static final double SAFE_DISTANCE = 11.0;
    private static final float FLEE_SPEED = 0.7F;

    private long nextCheck = -1;
    private Vec3 fleeTo;

    public FleePigRider() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_INTERVAL;
        return isChaseOn()
                && NitwitAi.isNitwit(villager)
                && !villager.isPassenger()
                && NitwitAi.isFreeToPlay(villager)
                && nearestRider(level, villager, NOTICE_DISTANCE) != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        fleeTo = null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return isChaseOn()
                && !villager.isPassenger()
                && NitwitAi.isFreeToPlay(villager)
                && nearestRider(level, villager, SAFE_DISTANCE) != null;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        boolean fleeing = fleeTo != null && brain.getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget().currentPosition().distanceToSqr(fleeTo) < 1.0)
                .isPresent();
        if (!fleeing) {
            Villager rider = nearestRider(level, villager, SAFE_DISTANCE);
            if (rider != null) {
                fleeTo = fleePosition(villager, rider.position());
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(fleeTo, FLEE_SPEED, 0));
            }
        }
        if (villager.onGround() && villager.getRandom().nextInt(30) == 0) {
            villager.getJumpControl().jump();
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        fleeTo = null;
    }

    private static boolean isChaseOn() {
        return Config.PIG_RIDING_ENABLED.get() && Config.PIG_CHASE_ENABLED.get();
    }

    private static Villager nearestRider(ServerLevel level, Villager villager, double distance) {
        return level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(distance),
                        other -> other != villager
                                && NitwitAi.isNitwit(other)
                                && NitwitAi.isPigRider(other)
                                && other.closerThan(villager, distance))
                .stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
    }

    private static Vec3 fleePosition(Villager villager, Vec3 from) {
        Vec3 pos = LandRandomPos.getPosAway(villager, 10, 5, from);
        if (pos == null) {
            pos = DefaultRandomPos.getPosAway(villager, 10, 5, from);
        }
        return pos != null ? pos : villager.position();
    }
}
