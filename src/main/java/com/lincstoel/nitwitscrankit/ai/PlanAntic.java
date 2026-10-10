package com.lincstoel.nitwitscrankit.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.compat.CreateCompat;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.pathfinder.Path;

/**
 * CORE behavior for nitwits: during the afternoon play period, occasionally finds something silly to do
 * (surf a belt, play in a fan's wind, stare-fish at the water) and switches into the
 * {@link NitwitsCrankIt#ANTICS} activity to do it.
 */
public class PlanAntic extends Behavior<Villager> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_INTERVAL = 200;
    private static final int MAX_PATH_CHECKS = 3;
    static final int FAN_STAND_DISTANCE = 2;
    private static final int MIN_BELT_RIDE = 2;
    private static final int WATER_SEARCH_RADIUS = 10;
    private static final int WATER_SEARCH_HEIGHT = 4;
    // Belts are checked for hazards (and drop-offs) before the costlier path check, but only so many of them.
    private static final int MAX_BELT_SAFETY_CHECKS = 8;

    /** Blocks that hurt a nitwit riding past them on a belt (saws, crushing wheels, fire...). */
    static final TagKey<Block> BELT_HAZARDS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(NitwitsCrankIt.MOD_ID, "belt_hazards"));

    private long nextCheck = -1;

    public PlanAntic() {
        super(Map.of());
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
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(NitwitsCrankIt.ANTICS.get())) {
            // E.g. saved and reloaded mid-fishing: don't keep the prop forever.
            DoAntic.putAwayFishingStick(villager);
        }
        if (!NitwitAi.isNitwit(villager)
                || villager.isPassenger()
                || brain.hasMemoryValue(NitwitsCrankIt.ANTIC.get())
                || !NitwitAi.isPlayTime(villager)
                || !NitwitAi.isFreeToPlay(villager)) {
            return;
        }

        List<Antic.Kind> kinds = new ArrayList<>(List.of(Antic.Kind.values()));
        Util.shuffle(kinds, villager.getRandom());
        for (Antic.Kind kind : kinds) {
            if (!isEnabled(kind) || villager.getRandom().nextDouble() >= chance(kind)) {
                continue;
            }
            Antic antic = switch (kind) {
                case BELT_SURF -> findBelt(level, villager);
                case FAN_PLAY -> findFan(level, villager);
                case STARE_FISH -> findFishingSpot(level, villager);
            };
            if (antic != null) {
                LOGGER.debug("Nitwit starting {} at {}", kind, antic.stand());
                brain.setMemory(NitwitsCrankIt.ANTIC.get(), antic);
                brain.setActiveActivityIfPossible(NitwitsCrankIt.ANTICS.get());
                return;
            }
        }
    }

    private static boolean isEnabled(Antic.Kind kind) {
        return switch (kind) {
            case BELT_SURF -> CreateCompat.isLoaded() && Config.BELT_SURFING_ENABLED.get();
            case FAN_PLAY -> CreateCompat.isLoaded() && Config.FAN_PLAY_ENABLED.get();
            case STARE_FISH -> Config.STARE_FISHING_ENABLED.get();
        };
    }

    private static double chance(Antic.Kind kind) {
        return switch (kind) {
            case BELT_SURF -> Config.BELT_SURFING_CHANCE.get();
            case FAN_PLAY -> Config.FAN_PLAY_CHANCE.get();
            case STARE_FISH -> Config.STARE_FISHING_CHANCE.get();
        };
    }

    /**
     * A reachable moving flat belt segment with a safe ride ahead of it, preferring the one with the longest ride
     * (so the nitwit hops on upstream instead of being dumped straight off the end), then the nearest.
     */
    private static Antic findBelt(ServerLevel level, Villager villager) {
        BlockPos origin = villager.blockPosition();
        List<BlockPos> belts = new ArrayList<>();
        NitwitAi.forEachBlockEntityNear(level, origin, be -> {
            BlockPos pos = be.getBlockPos();
            if (CreateCompat.isMovingFlatBelt(level, pos)
                    && CreateCompat.beltSegmentsAhead(level, pos) >= MIN_BELT_RIDE
                    && NitwitAi.hasHeadroom(level, villager, pos)) {
                belts.add(pos.immutable());
            }
        });
        belts.sort(Comparator.<BlockPos>comparingInt(pos -> -CreateCompat.beltSegmentsAhead(level, pos))
                .thenComparingDouble(pos -> pos.distSqr(origin)));
        int pathChecks = 0;
        for (BlockPos belt : belts.subList(0, Math.min(MAX_BELT_SAFETY_CHECKS, belts.size()))) {
            if (!isSafeRide(level, villager, belt)) {
                continue;
            }
            if (NitwitAi.canReachTop(villager, belt)) {
                return new Antic(Antic.Kind.BELT_SURF, belt, belt);
            }
            if (++pathChecks >= MAX_PATH_CHECKS) {
                break;
            }
        }
        return null;
    }

    /**
     * Room to ride every segment from {@code start} to the end, nothing hazardous next to (or above) the way, and
     * a safe landing where the belt dumps the nitwit off. Belts often feed saws, crushers, chutes and drops.
     */
    private static boolean isSafeRide(ServerLevel level, Villager villager, BlockPos start) {
        List<BlockPos> cells = CreateCompat.beltRideCells(level, start);
        if (cells.size() < 2) {
            return false;
        }
        BlockPos dropOff = cells.get(cells.size() - 1);
        for (BlockPos cell : cells) {
            boolean isSegment = cell != dropOff;
            if ((isSegment && !NitwitAi.hasHeadroom(level, villager, cell)) || hasHazardAround(level, cell)) {
                return false;
            }
        }
        return NitwitAi.isSafeLanding(level, dropOff);
    }

    /** Any belt hazard or lava within a block of a rider at {@code cell} (rider-height and above included). */
    private static boolean hasHazardAround(ServerLevel level, BlockPos cell) {
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-1, -1, -1), cell.offset(1, 2, 1))) {
            if (!level.isLoaded(pos)
                    || level.getBlockState(pos).is(BELT_HAZARDS)
                    || level.getFluidState(pos).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    /** A reachable spot two blocks in front of the nearest running sideways-facing fan whose wind is harmless. */
    private static Antic findFan(ServerLevel level, Villager villager) {
        BlockPos origin = villager.blockPosition();
        List<Antic> spots = new ArrayList<>();
        NitwitAi.forEachBlockEntityNear(level, origin, be -> {
            BlockPos fan = be.getBlockPos();
            Direction facing = CreateCompat.runningSidewaysFanFacing(level, fan);
            if (facing != null) {
                BlockPos stand = fan.relative(facing, FAN_STAND_DISTANCE);
                if (NitwitAi.canStandAt(level, stand) && CreateCompat.isSafeFanSpot(level, fan, FAN_STAND_DISTANCE)) {
                    spots.add(new Antic(Antic.Kind.FAN_PLAY, stand, fan.immutable()));
                }
            }
        });
        spots.sort(Comparator.comparingDouble(antic -> antic.stand().distSqr(origin)));
        for (Antic spot : spots.subList(0, Math.min(MAX_PATH_CHECKS, spots.size()))) {
            if (canReach(villager, spot.stand())) {
                return spot;
            }
        }
        return null;
    }

    /**
     * A reachable patch of dry shore next to open water. Scans the water surface in each nearby column (a few
     * thousand cheap block reads, only when the chance roll has already passed), then path-checks a few
     * random candidates.
     */
    private static Antic findFishingSpot(ServerLevel level, Villager villager) {
        BlockPos origin = villager.blockPosition();
        List<Antic> spots = new ArrayList<>();
        BlockPos.MutableBlockPos water = new BlockPos.MutableBlockPos();
        for (int dx = -WATER_SEARCH_RADIUS; dx <= WATER_SEARCH_RADIUS; dx++) {
            for (int dz = -WATER_SEARCH_RADIUS; dz <= WATER_SEARCH_RADIUS; dz++) {
                for (int dy = WATER_SEARCH_HEIGHT; dy >= -WATER_SEARCH_HEIGHT; dy--) {
                    water.setWithOffset(origin, dx, dy, dz);
                    if (!level.isLoaded(water) || !NitwitAi.isWater(level, water)) {
                        continue;
                    }
                    if (isOpenAbove(level, water)) {
                        for (Direction side : Direction.Plane.HORIZONTAL) {
                            BlockPos stand = water.relative(side).above();
                            if (NitwitAi.canStandAt(level, stand)) {
                                spots.add(new Antic(Antic.Kind.STARE_FISH, stand, water.immutable()));
                                break;
                            }
                        }
                    }
                    break;  // only the topmost water block in a column is the surface
                }
            }
        }
        Util.shuffle(spots, villager.getRandom());
        for (Antic spot : spots.subList(0, Math.min(MAX_PATH_CHECKS, spots.size()))) {
            if (canReach(villager, spot.stand())) {
                return spot;
            }
        }
        return null;
    }

    private static boolean isOpenAbove(ServerLevel level, BlockPos water) {
        BlockPos above = water.above();
        return level.getBlockState(above).getCollisionShape(level, above).isEmpty() && !NitwitAi.isWater(level, above);
    }

    private static boolean canReach(Villager villager, BlockPos stand) {
        Path path = villager.getNavigation().createPath(stand, 0);
        return path != null && path.canReach();
    }
}
