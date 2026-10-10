package com.lincstoel.nitwitscrankit.compat;

import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;

/**
 * The only place that touches Create's classes. Create is optional, so callers must check {@link #isLoaded()}
 * first; when it is false this class's Create-typed code is never run (or even linked).
 */
public final class CreateCompat {
    private static final boolean LOADED = ModList.get().isLoaded("create");

    // Matched by id so subclasses (e.g. valve handles extend HandCrankBlockEntity) are excluded.
    private static final ResourceLocation HAND_CRANK_ID = ResourceLocation.fromNamespaceAndPath("create", "hand_crank");
    private static final ResourceLocation TURNTABLE_ID = ResourceLocation.fromNamespaceAndPath("create", "turntable");
    private static final ResourceLocation DESK_BELL_ID = ResourceLocation.fromNamespaceAndPath("create", "desk_bell");
    private static final ResourceLocation STEAM_WHISTLE_ID = ResourceLocation.fromNamespaceAndPath("create", "steam_whistle");
    private static final ResourceLocation BELT_ID = ResourceLocation.fromNamespaceAndPath("create", "belt");
    private static final ResourceLocation ENCASED_FAN_ID = ResourceLocation.fromNamespaceAndPath("create", "encased_fan");

    private CreateCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static boolean isHandCrank(Level level, BlockPos pos) {
        return LOADED && blockEntity(level, pos, HAND_CRANK_ID) instanceof HandCrankBlockEntity;
    }

    public static boolean isTurntable(Level level, BlockPos pos) {
        return LOADED && blockEntity(level, pos, TURNTABLE_ID) instanceof TurntableBlockEntity;
    }

    public static boolean isSpinningTurntable(Level level, BlockPos pos) {
        return LOADED && blockEntity(level, pos, TURNTABLE_ID) instanceof TurntableBlockEntity turntable
                && turntable.getSpeed() != 0;
    }

    /** Desk bells are rung through the block's normal use action, so only the id matters here. */
    public static boolean isDeskBell(Level level, BlockPos pos) {
        return LOADED && blockEntity(level, pos, DESK_BELL_ID) != null;
    }

    /** A steam whistle on a working boiler, i.e. one that will actually make noise when blown. */
    public static boolean isTootableWhistle(Level level, BlockPos pos) {
        if (!LOADED || !(blockEntity(level, pos, STEAM_WHISTLE_ID) instanceof WhistleBlockEntity whistle)) {
            return false;
        }
        FluidTankBlockEntity tank = whistle.getTank();
        // "Active" only means something is attached; the whistle also needs heat under the boiler to sound.
        return tank != null && tank.boiler.isActive() && (tank.boiler.passiveHeat || tank.boiler.activeHeat > 0);
    }

    /**
     * Blows (or stops blowing) the steam whistle at {@code pos} as if it were powered by redstone. Stopping
     * restores whatever the real redstone input says.
     */
    public static void setWhistleBlowing(Level level, BlockPos pos, boolean blowing) {
        if (!LOADED || blockEntity(level, pos, STEAM_WHISTLE_ID) == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        boolean powered = blowing || level.hasNeighborSignal(pos);
        if (state.getValue(WhistleBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(WhistleBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    /** A flat, moving belt segment that will carry whoever stands on it. */
    public static boolean isMovingFlatBelt(Level level, BlockPos pos) {
        return LOADED && blockEntity(level, pos, BELT_ID) instanceof BeltBlockEntity belt
                && belt.getBlockState().getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL
                && belt.getSpeed() != 0;
    }

    /** How many more belt segments lie ahead of {@code pos} in the direction the belt is moving. */
    public static int beltSegmentsAhead(Level level, BlockPos pos) {
        if (!LOADED || !(blockEntity(level, pos, BELT_ID) instanceof BeltBlockEntity belt)) {
            return 0;
        }
        // A belt's facing points from its start segment (index 0) toward its end.
        Direction startToEnd = belt.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean movingTowardEnd = belt.getMovementFacing() == startToEnd;
        return movingTowardEnd ? belt.beltLength - 1 - belt.index : belt.index;
    }

    public static boolean isBelt(Level level, BlockPos pos) {
        return LOADED && blockEntity(level, pos, BELT_ID) != null;
    }

    /** The side a running, sideways-facing encased fan blows out of (or sucks into), else null. */
    public static Direction runningSidewaysFanFacing(Level level, BlockPos pos) {
        if (!LOADED || !(blockEntity(level, pos, ENCASED_FAN_ID) instanceof EncasedFanBlockEntity fan) || fan.getSpeed() == 0) {
            return null;
        }
        Direction facing = fan.getBlockState().getValue(BlockStateProperties.FACING);
        return facing.getAxis().isHorizontal() ? facing : null;
    }

    /**
     * Turns the hand crank at {@code pos} the way a player's right-click does; each turn keeps it going for
     * 10 ticks. With {@code sync}, also re-sends it to clients so the cranking sound keeps playing.
     */
    public static void turnHandCrank(Level level, BlockPos pos, boolean sync) {
        if (LOADED && blockEntity(level, pos, HAND_CRANK_ID) instanceof HandCrankBlockEntity crank) {
            crank.turn(false);
            if (sync) {
                crank.sendData();
            }
        }
    }

    private static BlockEntity blockEntity(Level level, BlockPos pos, ResourceLocation blockId) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null && BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).equals(blockId)) {
            return be;
        }
        return null;
    }
}
