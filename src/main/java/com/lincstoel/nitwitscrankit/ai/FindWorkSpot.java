package com.lincstoel.nitwitscrankit.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.compat.CreateCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * CORE behavior for nitwits: during work hours, periodically picks a work spot and stores it in
 * {@link NitwitsCrankIt#WORK_SPOT}, which is what lets the schedule move the nitwit into its WORK activity.
 * <p>
 * Preference: a free hand crank, then a free spinning turntable, then sharing a hand crank another nitwit owns
 * (up to {@link Config#MAX_NITWITS_PER_CRANK} per crank), each the nearest one it can actually get to. Needs
 * Create; without it this never finds anything.
 */
public class FindWorkSpot extends Behavior<Villager> {
    private static final int MAX_PATH_CHECKS = 3;

    private long nextScan = -1;

    public FindWorkSpot() {
        super(Map.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (nextScan < 0) {
            // Spread scans from different nitwits across ticks.
            nextScan = now + villager.getRandom().nextInt(Config.SCAN_INTERVAL_TICKS.get());
        }
        return now >= nextScan;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        nextScan = gameTime + Config.SCAN_INTERVAL_TICKS.get();
        Brain<Villager> brain = villager.getBrain();
        MemoryModuleType<WorkSpot> memory = NitwitsCrankIt.WORK_SPOT.get();

        boolean anyEnabled = CreateCompat.isLoaded()
                && (Config.HAND_CRANKS_ENABLED.get() || Config.TURNTABLES_ENABLED.get());
        if (!NitwitAi.isNitwit(villager) || !NitwitAi.isWorkTime(villager) || !anyEnabled) {
            brain.eraseMemory(memory);
            leaveWork(brain);
            return;
        }

        Optional<WorkSpot> current = brain.getMemory(memory);
        if (current.isPresent() && isValid(level, villager, current.get())) {
            WorkSpot spot = current.get();
            if (spot.kind() == WorkSpot.Kind.HAND_CRANK && !NitwitAi.isOwner(level, villager, spot)) {
                // Sharing someone else's crank; move on if something free turned up.
                WorkSpot better = findBest(level, villager, gameTime, true);
                if (better != null) {
                    brain.setMemory(memory, better);
                }
            }
            return;
        }

        WorkSpot best = findBest(level, villager, gameTime, false);
        if (best != null) {
            brain.setMemory(memory, best);
        } else {
            brain.eraseMemory(memory);
            leaveWork(brain);
        }
    }

    /** Whether the nitwit should keep the spot it already has. */
    static boolean isValid(ServerLevel level, Villager villager, WorkSpot spot) {
        return switch (spot.kind()) {
            case HAND_CRANK -> Config.HAND_CRANKS_ENABLED.get()
                    && CreateCompat.isHandCrank(level, spot.pos())
                    // Only the first few claimants may cluster around a crank.
                    && NitwitAi.claimRank(level, villager, spot) < Config.MAX_NITWITS_PER_CRANK.get();
            case TURNTABLE -> Config.TURNTABLES_ENABLED.get()
                    && CreateCompat.isSpinningTurntable(level, spot.pos())
                    && isFreeTurntable(level, villager, spot.pos())
                    && NitwitAi.isOwner(level, villager, spot);
        };
    }

    /** Nothing else is on top of the turntable, and a villager would fit up there. */
    private static boolean isFreeTurntable(ServerLevel level, Villager villager, BlockPos pos) {
        return !NitwitAi.isOccupiedByOthers(level, villager, pos) && NitwitAi.hasHeadroom(level, villager, pos);
    }

    private static WorkSpot findBest(ServerLevel level, Villager villager, long gameTime, boolean freeOnly) {
        boolean cranks = Config.HAND_CRANKS_ENABLED.get();
        boolean turntables = Config.TURNTABLES_ENABLED.get();
        BlockPos origin = villager.blockPosition();
        Set<BlockPos> skip = unreachable(villager.getBrain(), gameTime);
        List<BlockPos> freeCranks = new ArrayList<>();
        List<BlockPos> freeTurntables = new ArrayList<>();
        List<BlockPos> sharedCranks = new ArrayList<>();

        NitwitAi.forEachBlockEntityNear(level, origin, be -> {
            BlockPos pos = be.getBlockPos();
            if (skip.contains(pos)) {
                return;
            }
            if (cranks && CreateCompat.isHandCrank(level, pos)) {
                int claimants = NitwitAi.otherClaimants(level, villager, pos).size();
                if (claimants == 0) {
                    freeCranks.add(pos.immutable());
                } else if (claimants < Config.MAX_NITWITS_PER_CRANK.get()) {
                    sharedCranks.add(pos.immutable());
                }
            } else if (turntables) {
                if (CreateCompat.isSpinningTurntable(level, pos)
                        && isFreeTurntable(level, villager, pos)
                        && NitwitAi.otherClaimants(level, villager, pos).isEmpty()) {
                    freeTurntables.add(pos.immutable());
                }
            }
        });

        Predicate<BlockPos> crankReachable = pos -> NitwitAi.canGetWithin(villager, pos, WorkAtSpot.REACH_SQR);
        BlockPos crank = nearestReachable(origin, freeCranks, crankReachable);
        if (crank != null) {
            return new WorkSpot(crank, WorkSpot.Kind.HAND_CRANK, gameTime);
        }
        BlockPos turntable = nearestReachable(origin, freeTurntables, pos -> NitwitAi.canReachTop(villager, pos));
        if (turntable != null) {
            return new WorkSpot(turntable, WorkSpot.Kind.TURNTABLE, gameTime);
        }
        if (!freeOnly) {
            BlockPos shared = nearestReachable(origin, sharedCranks, crankReachable);
            if (shared != null) {
                return new WorkSpot(shared, WorkSpot.Kind.HAND_CRANK, gameTime);
            }
        }
        return null;
    }

    /** Pathfinding is the expensive check, so it's only tried on the few nearest candidates. */
    private static BlockPos nearestReachable(BlockPos origin, List<BlockPos> candidates, Predicate<BlockPos> reachable) {
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos pos : candidates.subList(0, Math.min(MAX_PATH_CHECKS, candidates.size()))) {
            if (reachable.test(pos)) {
                return pos;
            }
        }
        return null;
    }

    /** The work spots this nitwit still remembers as unreachable at {@code gameTime}. */
    private static Set<BlockPos> unreachable(Brain<Villager> brain, long gameTime) {
        return brain.getMemory(NitwitsCrankIt.UNREACHABLE_SPOTS.get())
                .map(spots -> spots.entrySet().stream()
                        .filter(entry -> entry.getValue() > gameTime)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** Remembers {@code pos} as unreachable for {@code ticks}, forgetting any spots that have expired. */
    static void markUnreachable(Brain<Villager> brain, BlockPos pos, long gameTime, int ticks) {
        Map<BlockPos, Long> spots = new HashMap<>();
        brain.getMemory(NitwitsCrankIt.UNREACHABLE_SPOTS.get()).ifPresent(old -> old.forEach((spot, until) -> {
            if (until > gameTime) {
                spots.put(spot, until);
            }
        }));
        spots.put(pos.immutable(), gameTime + ticks);
        brain.setMemory(NitwitsCrankIt.UNREACHABLE_SPOTS.get(), spots);
    }

    /** Drops out of WORK when its requirement no longer holds, falling back to vanilla idling. */
    private static void leaveWork(Brain<Villager> brain) {
        if (brain.isActive(Activity.WORK)) {
            brain.setActiveActivityIfPossible(Activity.WORK);
        }
    }
}
