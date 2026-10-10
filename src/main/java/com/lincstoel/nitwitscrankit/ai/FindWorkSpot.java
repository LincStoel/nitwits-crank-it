package com.lincstoel.nitwitscrankit.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * (up to {@link Config#MAX_NITWITS_PER_CRANK} per crank). Needs Create; without it this never finds anything.
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
        BlockPos skip = villager.getBrain().getMemory(NitwitsCrankIt.UNREACHABLE_SPOT.get()).orElse(null);
        Nearest freeCrank = new Nearest(origin);
        List<BlockPos> freeTurntables = new ArrayList<>();
        Nearest anyCrank = new Nearest(origin);

        NitwitAi.forEachBlockEntityNear(level, origin, be -> {
            BlockPos pos = be.getBlockPos();
            if (pos.equals(skip)) {
                return;
            }
            if (cranks && CreateCompat.isHandCrank(level, pos)) {
                int claimants = NitwitAi.otherClaimants(level, villager, pos).size();
                if (claimants == 0) {
                    freeCrank.offer(pos);
                }
                if (claimants < Config.MAX_NITWITS_PER_CRANK.get()) {
                    anyCrank.offer(pos);
                }
            } else if (turntables) {
                if (CreateCompat.isSpinningTurntable(level, pos)
                        && isFreeTurntable(level, villager, pos)
                        && NitwitAi.otherClaimants(level, villager, pos).isEmpty()) {
                    freeTurntables.add(pos.immutable());
                }
            }
        });

        if (freeCrank.best != null) {
            return new WorkSpot(freeCrank.best, WorkSpot.Kind.HAND_CRANK, gameTime);
        }
        // Pathfinding is the expensive check, so only try it on the few nearest candidates.
        freeTurntables.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        for (BlockPos pos : freeTurntables.subList(0, Math.min(MAX_PATH_CHECKS, freeTurntables.size()))) {
            if (NitwitAi.canReachTop(villager, pos)) {
                return new WorkSpot(pos, WorkSpot.Kind.TURNTABLE, gameTime);
            }
        }
        if (!freeOnly && anyCrank.best != null) {
            return new WorkSpot(anyCrank.best, WorkSpot.Kind.HAND_CRANK, gameTime);
        }
        return null;
    }

    /** Drops out of WORK when its requirement no longer holds, falling back to vanilla idling. */
    private static void leaveWork(Brain<Villager> brain) {
        if (brain.isActive(Activity.WORK)) {
            brain.setActiveActivityIfPossible(Activity.WORK);
        }
    }

    private static final class Nearest {
        private final BlockPos origin;
        private BlockPos best;
        private double bestDist = Double.MAX_VALUE;

        Nearest(BlockPos origin) {
            this.origin = origin;
        }

        void offer(BlockPos pos) {
            double dist = pos.distSqr(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
    }
}
