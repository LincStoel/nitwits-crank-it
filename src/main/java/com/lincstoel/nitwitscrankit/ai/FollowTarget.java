package com.lincstoel.nitwitscrankit.ai;

import net.minecraft.world.entity.LivingEntity;

/** Who a nitwit is tagging along after, and (for golems) until when. */
public record FollowTarget(LivingEntity entity, Kind kind, long until) {
    public enum Kind {
        HERO,
        IRON_GOLEM
    }
}
