package com.lincstoel.nitwitscrankit.ai;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a nitwit is pranking: a block to ring or toot at {@code pos}, or (for {@link Kind#WATER_PUSH}) the
 * {@code victim} to shove into the water.
 */
public record PrankTarget(BlockPos pos, Kind kind, @Nullable LivingEntity victim) {
    public PrankTarget(BlockPos pos, Kind kind) {
        this(pos, kind, null);
    }

    public enum Kind {
        VILLAGE_BELL,
        DESK_BELL,
        STEAM_WHISTLE,
        WATER_PUSH
    }
}
