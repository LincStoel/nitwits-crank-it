package com.lincstoel.nitwitscrankit.ai;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import com.lincstoel.nitwitscrankit.Config;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Iron golems that catch a nitwit pranking (or get fed up with one tailing them) lumber after it for a while,
 * never attacking. Chases live only in memory; they're short and harmless to lose on a restart.
 */
public final class GolemChase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double WITNESS_DISTANCE = 16.0;
    private static final int MIN_CHASE_TICKS = 100;
    private static final int EXTRA_CHASE_TICKS = 200;
    private static final double CHASE_SPEED = 0.9;

    // Server thread only.
    private static final Map<IronGolem, Chase> CHASES = new WeakHashMap<>();

    private record Chase(Villager prankster, long until) {
    }

    private GolemChase() {
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof IronGolem golem) {
            golem.goalSelector.addGoal(1, new ChasePranksterGoal(golem));
        }
    }

    /** Every iron golem that can see {@code prankster} starts chasing it for 5-15 seconds. */
    static void witnessPrank(ServerLevel level, Villager prankster) {
        if (!Config.GOLEM_CHASE_ENABLED.get()) {
            return;
        }
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, prankster.getBoundingBox().inflate(WITNESS_DISTANCE),
                golem -> golem.isAlive() && golem.hasLineOfSight(prankster))) {
            startChase(level, golem, prankster);
        }
    }

    /** {@code golem} chases {@code nitwit} around for 5-15 seconds. */
    static void startChase(ServerLevel level, IronGolem golem, Villager nitwit) {
        long until = level.getGameTime() + MIN_CHASE_TICKS + golem.getRandom().nextInt(EXTRA_CHASE_TICKS + 1);
        CHASES.put(golem, new Chase(nitwit, until));
        LOGGER.debug("Iron golem chasing nitwit for {} ticks", until - level.getGameTime());
    }

    /** The golem currently chasing {@code villager}, if any. */
    static IronGolem chaserOf(Villager villager) {
        long now = villager.level().getGameTime();
        for (Map.Entry<IronGolem, Chase> entry : CHASES.entrySet()) {
            if (entry.getValue().prankster() == villager && entry.getValue().until() > now && entry.getKey().isAlive()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static final class ChasePranksterGoal extends Goal {
        private final IronGolem golem;
        private Villager prankster;

        ChasePranksterGoal(IronGolem golem) {
            this.golem = golem;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            Chase chase = CHASES.get(golem);
            if (chase == null) {
                return false;
            }
            Villager target = chase.prankster();
            if (chase.until() <= golem.level().getGameTime() || !target.isAlive() || target.level() != golem.level()) {
                CHASES.remove(golem);
                return false;
            }
            prankster = target;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
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
            prankster = null;
        }
    }
}
