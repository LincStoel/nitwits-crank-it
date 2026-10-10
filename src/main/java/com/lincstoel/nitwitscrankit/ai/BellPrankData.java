package com.lincstoel.nitwitscrankit.ai;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers when each village bell was last pranked, so the cooldown survives restarts. */
public class BellPrankData extends SavedData {
    private static final String NAME = "nitwits_crank_it_bell_pranks";
    private static final Factory<BellPrankData> FACTORY = new Factory<>(BellPrankData::new, BellPrankData::load);

    private final Map<Long, Long> lastPranked = new HashMap<>();

    static BellPrankData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Records a prank on {@code bell} and returns true, unless it was pranked within the last {@code cooldown} ticks. */
    boolean tryClaim(BlockPos bell, long gameTime, long cooldown) {
        Long last = lastPranked.get(bell.asLong());
        if (last != null && gameTime >= last && gameTime - last < cooldown) {
            return false;
        }
        lastPranked.put(bell.asLong(), gameTime);
        setDirty();
        return true;
    }

    /** Undoes the claim made at {@code claimedAt} (the prank never came off), unless the bell was claimed again since. */
    void release(BlockPos bell, long claimedAt) {
        Long last = lastPranked.get(bell.asLong());
        if (last != null && last == claimedAt) {
            lastPranked.remove(bell.asLong());
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        lastPranked.forEach((pos, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos);
            entry.putLong("Time", time);
            list.add(entry);
        });
        tag.put("Bells", list);
        return tag;
    }

    private static BellPrankData load(CompoundTag tag, HolderLookup.Provider registries) {
        BellPrankData data = new BellPrankData();
        for (Tag t : tag.getList("Bells", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            data.lastPranked.put(entry.getLong("Pos"), entry.getLong("Time"));
        }
        return data;
    }
}
