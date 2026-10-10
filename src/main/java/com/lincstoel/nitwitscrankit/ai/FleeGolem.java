package com.lincstoel.nitwitscrankit.ai;

import java.util.Map;

import com.lincstoel.nitwitscrankit.NitwitsCrankIt;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * CORE behavior for nitwits: run (hopping in a panic) from an iron golem that's chasing them, e.g. one that
 * got fed up with being tailed. A chase that starts mid-prank is handled by {@link PerformPrank} instead.
 */
public class FleeGolem extends Behavior<Villager> {
    private static final float FLEE_SPEED = 0.75F;
    private static final int REPICK_TICKS = 40;

    private Vec3 fleeTo;
    private int ticksSincePick;

    public FleeGolem() {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return canFlee(villager) && GolemChase.chaserOf(villager) != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        fleeTo = null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return canFlee(villager) && GolemChase.chaserOf(villager) != null;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        IronGolem golem = GolemChase.chaserOf(villager);
        if (golem == null) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        ticksSincePick++;
        boolean headingAway = fleeTo != null && brain.getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walk -> walk.getTarget().currentPosition().distanceToSqr(fleeTo) < 1.0)
                .isPresent();
        if (!headingAway || ticksSincePick > REPICK_TICKS) {
            fleeTo = fleePosition(villager, golem.position());
            ticksSincePick = 0;
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(fleeTo, FLEE_SPEED, 0));
        }
        if (villager.onGround() && villager.getRandom().nextInt(25) == 0) {
            villager.getJumpControl().jump();
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        fleeTo = null;
    }

    private static boolean canFlee(Villager villager) {
        return NitwitAi.isNitwit(villager)
                && !villager.isPassenger()
                && !villager.isSleeping()
                && !villager.getBrain().isActive(NitwitsCrankIt.PRANK.get());
    }

    private static Vec3 fleePosition(Villager villager, Vec3 from) {
        Vec3 pos = LandRandomPos.getPosAway(villager, 12, 6, from);
        if (pos == null) {
            pos = DefaultRandomPos.getPosAway(villager, 12, 6, from);
        }
        return pos != null ? pos : villager.position();
    }
}
