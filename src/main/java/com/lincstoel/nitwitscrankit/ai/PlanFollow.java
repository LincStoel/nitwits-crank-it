package com.lincstoel.nitwitscrankit.ai;

import java.util.Map;
import java.util.Optional;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

/**
 * CORE behavior for nitwits: occasionally notices a nearby Hero of the Village (any awake time, dropping work
 * or play) or, during the afternoon play period, an iron golem, and switches into the
 * {@link NitwitsCrankIt#FOLLOW} activity to tag along after them for a while.
 */
public class PlanFollow extends Behavior<Villager> {
    private static final int CHECK_INTERVAL = 200;
    private static final double NOTICE_HERO_DISTANCE = 10.0;
    private static final double NOTICE_GOLEM_DISTANCE = 16.0;
    // Nitwits have a short attention span: they tag along for a while, then wander off to do something else.
    private static final int MIN_FOLLOW_TICKS = 400;
    private static final int EXTRA_FOLLOW_TICKS = 800;

    private long nextCheck = -1;
    private long nextHeroFollowAllowed;

    public PlanFollow() {
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
        if (!NitwitAi.isNitwit(villager) || !NitwitAi.isAwake(villager)) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        Optional<FollowTarget> current = brain.getMemory(NitwitsCrankIt.FOLLOW_TARGET.get());
        if (current.isPresent() && current.get().kind() == FollowTarget.Kind.HERO) {
            return;
        }

        // Noticing a hero can interrupt work, play and golems, but not pranks, panicking, hiding, raids or bedtime.
        boolean interruptible = NitwitAi.isFreeToPlay(villager)
                || brain.isActive(Activity.WORK)
                || brain.isActive(NitwitsCrankIt.FOLLOW.get());
        if (!interruptible) {
            return;
        }
        if (Config.HERO_FOLLOWING_ENABLED.get()
                && gameTime >= nextHeroFollowAllowed
                && NitwitAi.isNearVillage(villager, Config.HERO_FOLLOWING_MAX_DISTANCE.get())
                && villager.getRandom().nextDouble() < Config.HERO_FOLLOWING_CHANCE.get()) {
            Player hero = findHero(level, villager);
            if (hero != null) {
                long until = gameTime + MIN_FOLLOW_TICKS + villager.getRandom().nextInt(EXTRA_FOLLOW_TICKS);
                nextHeroFollowAllowed = until + Config.HERO_FOLLOWING_COOLDOWN_TICKS.get();
                startFollowing(brain, new FollowTarget(hero, FollowTarget.Kind.HERO, until));
                return;
            }
        }

        if (current.isPresent()) {
            return;
        }
        if (Config.GOLEM_FOLLOWING_ENABLED.get()
                && NitwitAi.isPlayTime(villager)
                && NitwitAi.isFreeToPlay(villager)
                && !villager.isPassenger()
                && villager.getRandom().nextDouble() < Config.GOLEM_FOLLOWING_CHANCE.get()) {
            IronGolem golem = findGolem(villager);
            if (golem != null) {
                long until = gameTime + MIN_FOLLOW_TICKS + villager.getRandom().nextInt(EXTRA_FOLLOW_TICKS);
                startFollowing(brain, new FollowTarget(golem, FollowTarget.Kind.IRON_GOLEM, until));
            }
        }
    }

    static boolean isHero(Player player) {
        // Not invisible ones: a nitwit trailing after thin air would give them away.
        return player.isAlive() && !player.isSpectator() && !player.isInvisible()
                && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    private static Player findHero(ServerLevel level, Villager villager) {
        return level.getNearestPlayer(villager.getX(), villager.getY(), villager.getZ(), NOTICE_HERO_DISTANCE,
                entity -> entity instanceof Player player && isHero(player));
    }

    private static IronGolem findGolem(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> visible.findClosest(entity -> entity instanceof IronGolem
                        && entity.closerThan(villager, NOTICE_GOLEM_DISTANCE)))
                .map(IronGolem.class::cast)
                .orElse(null);
    }

    private static void startFollowing(Brain<Villager> brain, FollowTarget target) {
        brain.setMemory(NitwitsCrankIt.FOLLOW_TARGET.get(), target);
        brain.setActiveActivityIfPossible(NitwitsCrankIt.FOLLOW.get());
    }
}
