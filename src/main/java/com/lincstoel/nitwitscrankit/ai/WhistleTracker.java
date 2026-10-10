package com.lincstoel.nitwitscrankit.ai;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.lincstoel.nitwitscrankit.compat.CreateCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Steam whistles a prank is blowing, and when to let go of them. The whistle stays powered until something lets
 * go, and a prankster that dies or unloads mid-toot never does, so this lets go for it. It's saved, so a whistle
 * left blowing by a crash is silenced on the next load too.
 */
public class WhistleTracker extends SavedData {
    private static final String NAME = "nitwits_crank_it_whistles";
    private static final Factory<WhistleTracker> FACTORY = new Factory<>(WhistleTracker::new, WhistleTracker::load);

    private final Map<Long, Long> releaseAt = new HashMap<>();

    /** Starts blowing the whistle at {@code pos}; it's let go of at {@code until} at the latest. */
    static void blow(ServerLevel level, BlockPos pos, long until) {
        CreateCompat.setWhistleBlowing(level, pos, true);
        WhistleTracker tracker = level.getDataStorage().computeIfAbsent(FACTORY, NAME);
        tracker.releaseAt.put(pos.asLong(), until);
        tracker.setDirty();
    }

    /** Stops blowing the whistle at {@code pos}, restoring whatever its real redstone input says. */
    static void release(ServerLevel level, BlockPos pos) {
        CreateCompat.setWhistleBlowing(level, pos, false);
        WhistleTracker tracker = level.getDataStorage().get(FACTORY, NAME);
        if (tracker != null && tracker.releaseAt.remove(pos.asLong()) != null) {
            tracker.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            releaseLoaded(level, level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            releaseLoaded(level, Long.MAX_VALUE);
        }
    }

    /** Lets go of every whistle due by {@code now}, unless its chunk isn't loaded (it's retried once it is). */
    private static void releaseLoaded(ServerLevel level, long now) {
        WhistleTracker tracker = level.getDataStorage().get(FACTORY, NAME);
        if (tracker == null || tracker.releaseAt.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Long, Long>> it = tracker.releaseAt.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Long> entry = it.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (now >= entry.getValue() && level.isLoaded(pos)) {
                CreateCompat.setWhistleBlowing(level, pos, false);
                it.remove();
                tracker.setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        releaseAt.forEach((pos, time) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos);
            entry.putLong("Until", time);
            list.add(entry);
        });
        tag.put("Whistles", list);
        return tag;
    }

    private static WhistleTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        WhistleTracker data = new WhistleTracker();
        for (Tag t : tag.getList("Whistles", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            data.releaseAt.put(entry.getLong("Pos"), entry.getLong("Until"));
        }
        return data;
    }
}
