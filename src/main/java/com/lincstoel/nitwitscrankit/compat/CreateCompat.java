package com.lincstoel.nitwitscrankit.compat;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/**
 * The mod's way into Create, which is optional. This class only uses vanilla types, so it loads fine without
 * Create; everything Create-typed lives in {@link CreateInternals}, which is only touched once Create is known
 * to be there. Without Create every check here is simply false.
 * <p>
 * Some of what's used is Create's internals, which can change in any update. Every Create call goes through
 * {@link #guard}, so if an update breaks one, the Create features switch off (with one error in the log) instead
 * of crashing the server.
 * <p>
 * Each check comes in two forms: by block entity, for callers already iterating block entities, and by position.
 */
public final class CreateCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean LOADED = ModList.get().isLoaded("create");
    private static volatile boolean broken;

    private CreateCompat() {
    }

    /** Create is installed, and compatible as far as we've found out. */
    public static boolean isLoaded() {
        return LOADED && !broken;
    }

    public static boolean isHandCrank(Level level, BlockPos pos) {
        return isHandCrank(loadedBlockEntity(level, pos));
    }

    public static boolean isHandCrank(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.HAND_CRANK) && guard(() -> CreateInternals.isHandCrank(be), false);
    }

    public static boolean isSpinningTurntable(Level level, BlockPos pos) {
        return isSpinningTurntable(loadedBlockEntity(level, pos));
    }

    public static boolean isSpinningTurntable(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.TURNTABLE) && guard(() -> CreateInternals.isSpinningTurntable(be), false);
    }

    public static boolean isDeskBell(Level level, BlockPos pos) {
        return isDeskBell(loadedBlockEntity(level, pos));
    }

    /** Desk bells are rung through the block's normal use action, so only the block matters here. */
    public static boolean isDeskBell(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.DESK_BELL);
    }

    public static boolean isTootableWhistle(Level level, BlockPos pos) {
        return isTootableWhistle(loadedBlockEntity(level, pos));
    }

    /** A steam whistle on a working boiler, i.e. one that will actually make noise when blown. */
    public static boolean isTootableWhistle(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.STEAM_WHISTLE) && guard(() -> CreateInternals.isTootableWhistle(be), false);
    }

    /**
     * Blows (or stops blowing) the steam whistle at {@code pos} as if it were powered by redstone. Stopping
     * restores whatever the real redstone input says.
     */
    public static void setWhistleBlowing(Level level, BlockPos pos, boolean blowing) {
        if (isBlock(loadedBlockEntity(level, pos), Blocks.STEAM_WHISTLE)) {
            guard(() -> {
                CreateInternals.setWhistleBlowing(level, pos, blowing);
                return null;
            }, null);
        }
    }

    public static boolean isMovingFlatBelt(Level level, BlockPos pos) {
        return isMovingFlatBelt(loadedBlockEntity(level, pos));
    }

    /** A flat, moving belt segment that will carry whoever stands on it. */
    public static boolean isMovingFlatBelt(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.BELT) && guard(() -> CreateInternals.isMovingFlatBelt(be), false);
    }

    /** How many more belt segments lie ahead of this one in the direction the belt is moving. */
    public static int beltSegmentsAhead(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.BELT) ? guard(() -> CreateInternals.beltSegmentsAhead(be), 0) : 0;
    }

    /**
     * The belt segments a rider boarding at {@code pos} passes over (starting with {@code pos}), followed by the
     * block space it gets dumped into past the end. Empty if there's no belt there.
     */
    public static List<BlockPos> beltRideCells(Level level, BlockPos pos) {
        BlockEntity be = loadedBlockEntity(level, pos);
        return isBlock(be, Blocks.BELT) ? guard(() -> CreateInternals.beltRideCells(be), List.of()) : List.of();
    }

    public static boolean isBelt(Level level, BlockPos pos) {
        return isBlock(loadedBlockEntity(level, pos), Blocks.BELT);
    }

    public static Direction runningSidewaysFanFacing(Level level, BlockPos pos) {
        return runningSidewaysFanFacing(loadedBlockEntity(level, pos));
    }

    /** The side a running, sideways-facing encased fan blows out of (or sucks into), else null. */
    public static Direction runningSidewaysFanFacing(@Nullable BlockEntity be) {
        return isBlock(be, Blocks.ENCASED_FAN) ? guard(() -> CreateInternals.runningSidewaysFanFacing(be), null) : null;
    }

    /**
     * True if a villager playing {@code distance} blocks in front of the fan at {@code pos} gets blown about
     * harmlessly: the wind reaches that far, and nowhere along it is there a blasting, smoking or haunting
     * catalyst (washing is just a splash). The whole current is checked since the nitwit gets blown around in it.
     */
    public static boolean isSafeFanSpot(Level level, BlockPos pos, int distance) {
        BlockEntity be = loadedBlockEntity(level, pos);
        return isBlock(be, Blocks.ENCASED_FAN) && guard(() -> CreateInternals.isSafeFanSpot(be, distance), false);
    }

    /**
     * Turns the hand crank at {@code pos} the way a player's right-click does ({@code backwards} the way a
     * sneaking one does); each turn keeps it going for 10 ticks. With {@code sync}, also re-sends it to clients so
     * the cranking sound keeps playing.
     * <p>
     * A crank that's already turning keeps its direction (whoever started it decides), so nitwits sharing one
     * don't fight over it. Does nothing if someone already turned it this tick, so a shared crank isn't turned
     * (and synced) once per nitwit.
     */
    public static void turnHandCrank(Level level, BlockPos pos, boolean backwards, boolean sync) {
        BlockEntity be = loadedBlockEntity(level, pos);
        if (isBlock(be, Blocks.HAND_CRANK)) {
            guard(() -> {
                CreateInternals.turnHandCrank(be, backwards, sync);
                return null;
            }, null);
        }
    }

    /**
     * Runs Create-touching code, or returns {@code fallback} if Create is missing or turned out to be incompatible
     * (a field or method we use is gone or changed), in which case every Create feature is switched off.
     */
    private static <T> T guard(Supplier<T> call, T fallback) {
        if (!isLoaded()) {
            return fallback;
        }
        try {
            return call.get();
        } catch (LinkageError e) {
            if (!broken) {
                broken = true;
                String version = ModList.get().getModContainerById("create")
                        .map(container -> container.getModInfo().getVersion().toString())
                        .orElse("?");
                LOGGER.error("Create {} isn't compatible with this version of Nitwits Crank It; "
                        + "its Create features are switched off.", version, e);
            }
            return fallback;
        }
    }

    /** Whether {@code be} is the block entity of {@code block} (matched by block, so subclasses like valve handles aren't). */
    private static boolean isBlock(@Nullable BlockEntity be, Block block) {
        return be != null && isLoaded() && be.getBlockState().is(block);
    }

    private static BlockEntity loadedBlockEntity(Level level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
    }

    /** Create's blocks, looked up when first needed (any later than registration is fine). Air if missing. */
    private static final class Blocks {
        static final Block HAND_CRANK = create("hand_crank");
        static final Block TURNTABLE = create("turntable");
        static final Block DESK_BELL = create("desk_bell");
        static final Block STEAM_WHISTLE = create("steam_whistle");
        static final Block BELT = create("belt");
        static final Block ENCASED_FAN = create("encased_fan");

        private static Block create(String path) {
            return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
        }
    }
}
