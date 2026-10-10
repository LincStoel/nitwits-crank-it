package com.lincstoel.nitwitscrankit.compat;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Everything that touches Create's classes. Loading this class loads Create's, so only {@link CreateCompat} may
 * call it, and only once it knows Create is there. Method signatures stick to vanilla types for the same reason.
 */
final class CreateInternals {
    // What a turn sets HandCrankBlockEntity.inUse to.
    private static final int CRANK_TURN_TICKS = 10;

    private CreateInternals() {
    }

    static boolean isHandCrank(BlockEntity be) {
        return be instanceof HandCrankBlockEntity;
    }

    static boolean isSpinningTurntable(BlockEntity be) {
        return be instanceof TurntableBlockEntity turntable && turntable.getSpeed() != 0;
    }

    static boolean isTootableWhistle(BlockEntity be) {
        if (!(be instanceof WhistleBlockEntity whistle)) {
            return false;
        }
        FluidTankBlockEntity tank = whistle.getTank();
        // "Active" only means something is attached; the whistle also needs heat under the boiler to sound.
        return tank != null && tank.boiler.isActive() && (tank.boiler.passiveHeat || tank.boiler.activeHeat > 0);
    }

    static void setWhistleBlowing(Level level, BlockPos pos, boolean blowing) {
        BlockState state = level.getBlockState(pos);
        boolean powered = blowing || level.hasNeighborSignal(pos);
        if (state.getValue(WhistleBlock.POWERED) != powered) {
            level.setBlock(pos, state.setValue(WhistleBlock.POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    static boolean isMovingFlatBelt(BlockEntity be) {
        return be instanceof BeltBlockEntity belt
                && belt.getBlockState().getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL
                && belt.getSpeed() != 0;
    }

    static int beltSegmentsAhead(BlockEntity be) {
        if (!(be instanceof BeltBlockEntity belt)) {
            return 0;
        }
        // A belt's facing points from its start segment (index 0) toward its end.
        Direction startToEnd = belt.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean movingTowardEnd = belt.getMovementFacing() == startToEnd;
        return movingTowardEnd ? belt.beltLength - 1 - belt.index : belt.index;
    }

    static List<BlockPos> beltRideCells(BlockEntity be) {
        List<BlockPos> cells = new ArrayList<>();
        if (be instanceof BeltBlockEntity belt) {
            Direction moving = belt.getMovementFacing();
            int ahead = beltSegmentsAhead(be);
            for (int i = 0; i <= ahead + 1; i++) {
                cells.add(be.getBlockPos().relative(moving, i));
            }
        }
        return cells;
    }

    static Direction runningSidewaysFanFacing(BlockEntity be) {
        if (!(be instanceof EncasedFanBlockEntity fan) || fan.getSpeed() == 0) {
            return null;
        }
        Direction facing = fan.getBlockState().getValue(BlockStateProperties.FACING);
        return facing.getAxis().isHorizontal() ? facing : null;
    }

    static boolean isSafeFanSpot(BlockEntity be, int distance) {
        if (!(be instanceof EncasedFanBlockEntity fan)) {
            return false;
        }
        AirCurrent current = fan.getAirCurrent();
        if (current == null || current.maxDistance < distance) {
            return false;
        }
        for (float offset = 0; offset <= current.maxDistance; offset += 0.5F) {
            FanProcessingType type = current.getTypeAt(offset);
            if (type != null && type != AllFanProcessingTypes.SPLASHING) {
                return false;
            }
        }
        return true;
    }

    static void turnHandCrank(BlockEntity be, boolean backwards, boolean sync) {
        if (be instanceof HandCrankBlockEntity crank && crank.inUse < CRANK_TURN_TICKS) {
            crank.turn(crank.inUse > 0 ? crank.backwards : backwards);
            if (sync) {
                crank.sendData();
            }
        }
    }
}
