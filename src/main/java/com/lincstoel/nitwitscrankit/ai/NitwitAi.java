package com.lincstoel.nitwitscrankit.ai;

import java.util.List;
import java.util.function.Consumer;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared lookups for the nitwit behaviors. */
final class NitwitAi {
    private NitwitAi() {
    }

    static boolean isNitwit(Villager villager) {
        return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    static Activity scheduledActivity(Villager villager) {
        long dayTime = villager.level().getDayTime();
        return villager.getBrain().getSchedule().getActivityAt((int) (dayTime % 24000L));
    }

    static boolean isWorkTime(Villager villager) {
        return scheduledActivity(villager) == Activity.WORK;
    }

    /**
     * The child villagers' play period, minus the morning one that overlaps adult work hours
     * (in vanilla: 10000-12000, the afternoon until nightfall).
     */
    static boolean isPlayTime(Villager villager) {
        int time = (int) (villager.level().getDayTime() % 24000L);
        return Schedule.VILLAGER_BABY.getActivityAt(time) == Activity.PLAY && scheduledActivity(villager) != Activity.WORK;
    }

    static boolean isAwake(Villager villager) {
        return !villager.isSleeping() && scheduledActivity(villager) != Activity.REST;
    }

    /** Idling or at the village meeting, i.e. not busy with work, a prank, following, panicking, etc. */
    static boolean isFreeToPlay(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        return brain.isActive(Activity.IDLE) || brain.isActive(Activity.MEET);
    }

    static boolean isPigRider(Villager villager) {
        return villager.getVehicle() instanceof Pig;
    }

    /** Visits every loaded block entity within the configured search box around {@code origin}. */
    static void forEachBlockEntityNear(ServerLevel level, BlockPos origin, Consumer<BlockEntity> action) {
        int radius = Config.SEARCH_RADIUS.get();
        int vertical = Config.VERTICAL_RANGE.get();
        int minCx = SectionPos.blockToSectionCoord(origin.getX() - radius);
        int maxCx = SectionPos.blockToSectionCoord(origin.getX() + radius);
        int minCz = SectionPos.blockToSectionCoord(origin.getZ() - radius);
        int maxCz = SectionPos.blockToSectionCoord(origin.getZ() + radius);

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (Math.abs(pos.getX() - origin.getX()) <= radius
                            && Math.abs(pos.getZ() - origin.getZ()) <= radius
                            && Math.abs(pos.getY() - origin.getY()) <= vertical) {
                        action.accept(be);
                    }
                }
            }
        }
    }

    /** Other villagers that have claimed {@code pos} as their work spot. */
    static List<Villager> otherClaimants(ServerLevel level, Villager self, BlockPos pos) {
        MemoryModuleType<WorkSpot> memory = NitwitsCrankIt.WORK_SPOT.get();
        return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(Config.SEARCH_RADIUS.get() + 16),
                other -> other != self
                        && other.isAlive()
                        && other.getBrain().checkMemory(memory, MemoryStatus.VALUE_PRESENT)
                        && other.getBrain().getMemory(memory).get().pos().equals(pos));
    }

    /** How many other villagers claimed {@code spot} before {@code self}; 0 means {@code self} owns it. */
    static int claimRank(ServerLevel level, Villager self, WorkSpot spot) {
        int rank = 0;
        for (Villager other : otherClaimants(level, self, spot.pos())) {
            WorkSpot theirs = other.getBrain().getMemory(NitwitsCrankIt.WORK_SPOT.get()).orElseThrow();
            if (theirs.claimedAt() < spot.claimedAt()
                    || (theirs.claimedAt() == spot.claimedAt() && other.getId() < self.getId())) {
                rank++;
            }
        }
        return rank;
    }

    static boolean isOwner(ServerLevel level, Villager self, WorkSpot spot) {
        return claimRank(level, self, spot) == 0;
    }

    /** True if any entity other than {@code self} is on top of the block at {@code pos}. */
    static boolean isOccupiedByOthers(ServerLevel level, Villager self, BlockPos pos) {
        AABB above = new AABB(pos.getX(), pos.getY() + 0.4, pos.getZ(), pos.getX() + 1, pos.getY() + 1.5, pos.getZ() + 1);
        return !level.getEntities(self, above, e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    /** True if a villager would fit standing on top of the block (turntable, belt, ...) at {@code pos}. */
    static boolean hasHeadroom(ServerLevel level, Villager villager, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        double top = pos.getY() + (shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y));
        AABB standing = villager.getType().getDimensions().makeBoundingBox(pos.getX() + 0.5, top, pos.getZ() + 0.5);
        return level.noCollision(villager, standing.deflate(1.0E-4));
    }

    /** True if the villager can path all the way onto the top of the turntable at {@code pos}. */
    static boolean canReachTop(Villager villager, BlockPos pos) {
        if (isStandingOn(villager, pos)) {
            return true;  // pathfinding from on top of an unwalkable block fails, but we're already there
        }
        Path path = villager.getNavigation().createPath(pos.above(), 1);
        return path != null && path.canReach();
    }

    /** Solid, dry ground under {@code stand} and two blocks of clear space for a villager. */
    static boolean canStandAt(Level level, BlockPos stand) {
        BlockPos below = stand.below();
        return level.isLoaded(stand)
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                && level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()
                && !NitwitAi.isWater(level, stand);
    }

    static boolean isStandingOn(Villager villager, BlockPos pos) {
        double yOffset = villager.getY() - pos.getY();
        return Mth.floor(villager.getX()) == pos.getX()
                && Mth.floor(villager.getZ()) == pos.getZ()
                && yOffset >= 0.4 && yOffset <= 1.1;
    }

    /**
     * The side of the block space {@code feet} that is water (or drops straight into water), or null if none;
     * i.e. which way to shove someone standing there to get them wet.
     */
    static Direction waterSide(Level level, BlockPos feet) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = feet.relative(side);
            if (isWater(level, next)
                    || (level.getBlockState(next).getCollisionShape(level, next).isEmpty() && isWater(level, next.below()))) {
                return side;
            }
        }
        return null;
    }

    static boolean isWater(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** Disapproving villagers: a single unhappy particle over the head. */
    static void unhappyParticle(ServerLevel level, LivingEntity entity) {
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
    }

    static void happyParticles(ServerLevel level, Villager villager, int count) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                villager.getX(), villager.getY() + villager.getBbHeight() + 0.3, villager.getZ(),
                count, 0.4, 0.25, 0.4, 0.0);
    }
}
