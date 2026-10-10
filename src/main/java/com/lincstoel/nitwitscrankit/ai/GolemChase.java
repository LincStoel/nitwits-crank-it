package com.lincstoel.nitwitscrankit.ai;

import java.util.EnumSet;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Iron golems that catch a nitwit pranking (or get fed up with one tailing them) lumber after it for a while,
 * never attacking, and dropping it the moment there's something real to fight. The chase lives on the golem's
 * goal and in the nitwit's {@link NitwitsCrankIt#CHASED_BY} memory, so it's gone with either of them; it's short
 * and harmless to lose on a restart.
 */
public final class GolemChase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double WITNESS_DISTANCE = 16.0;
    private static final int MIN_CHASE_TICKS = 100;
    private static final int EXTRA_CHASE_TICKS = 200;
    private static final double CHASE_SPEED = 0.9;
    // Below the golem's melee attack (1), so that always wins.
    private static final int GOAL_PRIORITY = 2;

    private GolemChase() {
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof IronGolem golem && goalOf(golem) == null) {
            golem.goalSelector.addGoal(GOAL_PRIORITY, new ChasePranksterGoal(golem));
        }
    }

    /** Every iron golem that can see {@code prankster} starts chasing it for 5-15 seconds. */
    static void witnessPrank(ServerLevel level, Villager prankster) {
        if (!Config.GOLEM_CHASE_ENABLED.get()) {
            return;
        }
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, prankster.getBoundingBox().inflate(WITNESS_DISTANCE),
                golem -> golem.isAlive() && golem.getTarget() == null && golem.hasLineOfSight(prankster))) {
            startChase(level, golem, prankster);
        }
    }

    /** {@code golem} chases {@code nitwit} around for 5-15 seconds. */
    static void startChase(ServerLevel level, IronGolem golem, Villager nitwit) {
        ChasePranksterGoal goal = goalOf(golem);
        if (goal == null) {
            return;
        }
        int ticks = MIN_CHASE_TICKS + golem.getRandom().nextInt(EXTRA_CHASE_TICKS + 1);
        goal.chase(nitwit, level.getGameTime() + ticks);
        nitwit.getBrain().setMemoryWithExpiry(NitwitsCrankIt.CHASED_BY.get(), golem, ticks);
        LOGGER.debug("Iron golem chasing nitwit for {} ticks", ticks);
    }

    /** The golem currently chasing {@code villager}, if any. */
    static IronGolem chaserOf(Villager villager) {
        return villager.getBrain().getMemory(NitwitsCrankIt.CHASED_BY.get())
                .filter(golem -> {
                    ChasePranksterGoal goal = goalOf(golem);
                    return golem.isAlive() && goal != null && goal.isChasing(villager);
                })
                .orElse(null);
    }

    private static ChasePranksterGoal goalOf(IronGolem golem) {
        for (WrappedGoal wrapped : golem.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof ChasePranksterGoal goal) {
                return goal;
            }
        }
        return null;
    }

    private static final class ChasePranksterGoal extends Goal {
        private final IronGolem golem;
        private Villager prankster;
        private long until;

        ChasePranksterGoal(IronGolem golem) {
            this.golem = golem;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        void chase(Villager prankster, long until) {
            this.prankster = prankster;
            this.until = until;
        }

        boolean isChasing(Villager villager) {
            return prankster == villager && isOn();
        }

        /** Whether the chase is still on; calls it off for good once it's over or the golem has a real target. */
        private boolean isOn() {
            if (prankster == null) {
                return false;
            }
            if (until <= golem.level().getGameTime()
                    || !prankster.isAlive()
                    || prankster.level() != golem.level()
                    || golem.getTarget() != null) {
                prankster = null;
                return false;
            }
            return true;
        }

        @Override
        public boolean canUse() {
            return isOn();
        }

        @Override
        public boolean canContinueToUse() {
            return isOn();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            golem.getLookControl().setLookAt(prankster, 30.0F, 30.0F);
            if (golem.tickCount % 10 == 0) {
                golem.getNavigation().moveTo(prankster, CHASE_SPEED);
            }
        }

        @Override
        public void stop() {
            golem.getNavigation().stop();
        }
    }
}
