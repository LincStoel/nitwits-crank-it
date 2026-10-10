package com.lincstoel.nitwitscrankit.ai;

import net.minecraft.core.BlockPos;

/**
 * A play-time antic: {@code stand} is where the nitwit goes, {@code focus} what it plays with (the belt it
 * surfs, the fan it plays in, the water it "fishes" in).
 */
public record Antic(Kind kind, BlockPos stand, BlockPos focus) {
    public enum Kind {
        BELT_SURF,
        FAN_PLAY,
        STARE_FISH
    }
}
