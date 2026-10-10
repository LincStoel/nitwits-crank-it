package com.lincstoel.nitwitscrankit;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/** Loot condition: passes while {@link Config#JUNK_GIFTS_ENABLED} is on. */
public final class JunkGiftsEnabled implements LootItemCondition {
    public static final JunkGiftsEnabled INSTANCE = new JunkGiftsEnabled();
    public static final MapCodec<JunkGiftsEnabled> CODEC = MapCodec.unit(INSTANCE);

    private JunkGiftsEnabled() {
    }

    @Override
    public LootItemConditionType getType() {
        return NitwitsCrankIt.JUNK_GIFTS_ENABLED.get();
    }

    @Override
    public boolean test(LootContext context) {
        return Config.JUNK_GIFTS_ENABLED.get();
    }
}
