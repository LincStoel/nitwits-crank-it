package com.lincstoel.nitwitscrankit.ai;

import net.minecraft.core.BlockPos;

/**
 * Where a nitwit works. When several nitwits claim the same spot, the earliest claim owns it
 * (ties broken by entity id); only a hand crank may be shared with non-owners.
 */
public record WorkSpot(BlockPos pos, Kind kind, long claimedAt) {
    public enum Kind {
        HAND_CRANK,
        TURNTABLE
    }
}
