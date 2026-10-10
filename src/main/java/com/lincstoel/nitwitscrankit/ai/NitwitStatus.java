package com.lincstoel.nitwitscrankit.ai;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.lincstoel.nitwitscrankit.NitwitsCrankIt;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/** A one-line description of what a nitwit is up to, for tooltips (see the Jade integration). Server side only. */
public final class NitwitStatus {
    private static final String PREFIX = NitwitsCrankIt.MOD_ID + ".status.";

    private NitwitStatus() {
    }

    /** The translation key describing what {@code villager} is up to, or null if it isn't up to anything of ours. */
    @Nullable
    public static String translationKey(Villager villager) {
        if (!NitwitAi.isNitwit(villager)) {
            return null;
        }
        Brain<Villager> brain = villager.getBrain();
        if (GolemChase.chaserOf(villager) != null) {
            return PREFIX + "fleeing_golem";
        }
        if (brain.hasMemoryValue(NitwitsCrankIt.DIZZY.get())) {
            return PREFIX + "dizzy";
        }
        if (NitwitAi.isPigRider(villager)) {
            return PREFIX + "riding_pig";
        }
        if (brain.isActive(NitwitsCrankIt.PRANK.get())) {
            return brain.getMemory(NitwitsCrankIt.PRANK_TARGET.get())
                    .map(target -> PREFIX + "pranking." + target.kind().name().toLowerCase(Locale.ROOT))
                    .orElse(null);
        }
        if (brain.isActive(NitwitsCrankIt.ANTICS.get())) {
            return brain.getMemory(NitwitsCrankIt.ANTIC.get())
                    .map(antic -> PREFIX + "antic." + antic.kind().name().toLowerCase(Locale.ROOT))
                    .orElse(null);
        }
        if (brain.isActive(NitwitsCrankIt.FOLLOW.get())) {
            return brain.getMemory(NitwitsCrankIt.FOLLOW_TARGET.get())
                    .map(target -> PREFIX + "following." + target.kind().name().toLowerCase(Locale.ROOT))
                    .orElse(null);
        }
        if (brain.isActive(Activity.WORK)) {
            return brain.getMemory(NitwitsCrankIt.WORK_SPOT.get()).map(spot -> switch (spot.kind()) {
                case HAND_CRANK -> villager.distanceToSqr(Vec3.atCenterOf(spot.pos())) <= WorkAtSpot.REACH_SQR
                        ? PREFIX + "cranking"
                        : PREFIX + "heading_to_work";
                case TURNTABLE -> NitwitAi.isStandingOn(villager, spot.pos())
                        ? PREFIX + "riding_turntable"
                        : PREFIX + "heading_to_work";
            }).orElse(null);
        }
        return null;
    }
}
