package com.lincstoel.nitwitscrankit.ai;

import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

/** FOLLOW behavior: trail the remembered hero or iron golem around, staying a couple of blocks behind. */
public class FollowEntity extends Behavior<Villager> {
    private static final float HERO_SPEED = 0.6F;
    private static final float GOLEM_SPEED = 0.5F;
    private static final int KEEP_DISTANCE = 2;
    private static final double LOSE_HERO_DISTANCE = 16.0;
    private static final double LOSE_GOLEM_DISTANCE = 24.0;
    private static final int MIN_CHASE_OFF_DELAY = 60;
    private static final int EXTRA_CHASE_OFF_DELAY = 140;

    private long chaseOffAt = -1;

    public FollowEntity() {
        super(Map.of(
                NitwitsCrankIt.FOLLOW_TARGET.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 24000);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        chaseOffAt = -1;
        boolean followingGolem = villager.getBrain().getMemory(NitwitsCrankIt.FOLLOW_TARGET.get())
                .filter(target -> target.kind() == FollowTarget.Kind.IRON_GOLEM)
                .isPresent();
        if (followingGolem && villager.getRandom().nextDouble() < Config.GOLEM_CHASE_OFF_CHANCE.get()) {
            chaseOffAt = gameTime + MIN_CHASE_OFF_DELAY + villager.getRandom().nextInt(EXTRA_CHASE_OFF_DELAY);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        return brain.isActive(NitwitsCrankIt.FOLLOW.get())
                && GolemChase.chaserOf(villager) == null
                && brain.getMemory(NitwitsCrankIt.FOLLOW_TARGET.get())
                        .map(target -> isStillWorthFollowing(villager, target, gameTime))
                        .orElse(false);
    }

    private static boolean isStillWorthFollowing(Villager villager, FollowTarget target, long gameTime) {
        LivingEntity entity = target.entity();
        if (!entity.isAlive() || entity.isRemoved() || entity.level() != villager.level()) {
            return false;
        }
        return switch (target.kind()) {
            case HERO -> Config.HERO_FOLLOWING_ENABLED.get()
                    && gameTime < target.until()
                    && entity instanceof Player player && PlanFollow.isHero(player)
                    && NitwitAi.isAwake(villager)
                    && entity.closerThan(villager, LOSE_HERO_DISTANCE);
            case IRON_GOLEM -> Config.GOLEM_FOLLOWING_ENABLED.get()
                    && gameTime < target.until()
                    && NitwitAi.isPlayTime(villager)
                    && entity.closerThan(villager, LOSE_GOLEM_DISTANCE);
        };
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        FollowTarget target = brain.getMemory(NitwitsCrankIt.FOLLOW_TARGET.get()).orElseThrow();
        LivingEntity entity = target.entity();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(entity, true));

        if (villager.distanceToSqr(entity) > (KEEP_DISTANCE + 0.5) * (KEEP_DISTANCE + 0.5)) {
            boolean alreadyHeadingThere = brain.getMemory(MemoryModuleType.WALK_TARGET)
                    .filter(walk -> walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == entity)
                    .isPresent();
            if (!alreadyHeadingThere) {
                float speed = target.kind() == FollowTarget.Kind.HERO ? HERO_SPEED : GOLEM_SPEED;
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(entity, speed, KEEP_DISTANCE));
            }
        }
        if (chaseOffAt >= 0 && gameTime >= chaseOffAt && entity instanceof IronGolem golem) {
            // The golem has had enough of being tailed; canStillUse() ends the follow and FleeGolem takes over.
            chaseOffAt = -1;
            GolemChase.startChase(level, golem, villager);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(NitwitsCrankIt.FOLLOW_TARGET.get());
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (brain.isActive(NitwitsCrankIt.FOLLOW.get())) {
            // Back to idling; the schedule picks the right activity from there.
            brain.useDefaultActivity();
        }
    }
}
