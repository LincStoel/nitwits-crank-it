package com.lincstoel.nitwitscrankit.ai;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;

import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GiveGiftToHero;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.npc.Villager;

/** Behavior packages added to adult nitwit brains. */
public final class NitwitPackages {
    private NitwitPackages() {
    }

    /** Added to the CORE activity: decides what the nitwit gets up to. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> core() {
        return ImmutableList.of(
                Pair.of(0, new FindWorkSpot()),
                Pair.of(0, new PlanPrank()),
                Pair.of(0, new RideSaddledPig()),
                Pair.of(0, new FleePigRider()),
                Pair.of(0, new FleeGolem()),
                Pair.of(0, new PlanFollow()),
                Pair.of(0, new PlanAntic()),
                Pair.of(0, new Wobble())
        );
    }

    /** Added to the CORE activity of child villagers. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> kids() {
        return ImmutableList.of(Pair.of(0, new KidsChasePigRider()));
    }

    /** Replaces the vanilla WORK package. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work() {
        return ImmutableList.of(
                Pair.of(0, new WorkAtSpot()),
                Pair.of(99, UpdateActivityFromSchedule.create())
        );
    }

    /** The PRANK activity; deliberately not schedule-driven, the prank ends the activity itself. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> prank() {
        return ImmutableList.of(Pair.of(0, new PerformPrank()));
    }

    /** The FOLLOW activity; like PRANK, it ends itself when the hero or golem is no longer worth following. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> follow() {
        return ImmutableList.of(
                Pair.of(0, new FollowEntity()),
                // So nitwits tagging along after a hero still throw their (junk) gifts.
                Pair.of(3, new GiveGiftToHero(100))
        );
    }

    /** The ANTICS activity; ends itself when the antic is over. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> antics() {
        return ImmutableList.of(Pair.of(0, new DoAntic()));
    }
}
