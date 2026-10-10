package com.lincstoel.nitwitscrankit.ai;

import java.util.Map;

import com.lincstoel.nitwitscrankit.Config;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.compat.CreateCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * ANTICS behavior: go to the antic's spot, then play: ride the belt until falling off the end, stand in the
 * fan's wind (walking back in whenever blown out), or stare at the water holding a stick like a fishing rod.
 */
public class DoAntic extends Behavior<Villager> {
    private static final String FISHING_STICK_TAG = NitwitsCrankIt.MOD_ID + ":fishing_stick";
    private static final float WALK_SPEED = 0.6F;
    private static final int APPROACH_TIMEOUT = 400;
    private static final int OFF_BELT_GRACE_TICKS = 30;
    private static final double BLOWN_AWAY_SQR = 2.5 * 2.5;
    private static final double AT_SPOT_SQR = 1.0;

    private enum Phase { APPROACH, PLAY, DONE }

    private Phase phase;
    private int phaseTicks;
    private int playTicks;
    private int offBeltTicks;

    public DoAntic() {
        super(Map.of(
                NitwitsCrankIt.ANTIC.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 2400);
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        phase = Phase.APPROACH;
        phaseTicks = 0;
        offBeltTicks = 0;
        walkTo(villager, antic(villager));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        return phase != Phase.DONE
                && brain.isActive(NitwitsCrankIt.ANTICS.get())
                && brain.hasMemoryValue(NitwitsCrankIt.ANTIC.get())
                && NitwitAi.isPlayTime(villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        Antic antic = antic(villager);
        phaseTicks++;

        if (phase == Phase.APPROACH) {
            if (phaseTicks > APPROACH_TIMEOUT || !focusStillThere(level, antic)) {
                phase = Phase.DONE;
            } else if (hasArrived(level, villager, antic)) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                phase = Phase.PLAY;
                phaseTicks = 0;
                playTicks = 300 + villager.getRandom().nextInt(400);
                if (antic.kind() == Antic.Kind.STARE_FISH) {
                    takeOutFishingStick(villager);
                }
            } else if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                walkTo(villager, antic);
            }
            return;
        }

        if (phaseTicks > playTicks) {
            phase = Phase.DONE;
            return;
        }
        switch (antic.kind()) {
            case BELT_SURF -> {
                // Let the belt do the walking; once it has dumped us off the end, we're done.
                if (CreateCompat.isBelt(level, villager.getOnPos())) {
                    offBeltTicks = 0;
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                } else if (++offBeltTicks > OFF_BELT_GRACE_TICKS) {
                    phase = Phase.DONE;
                    return;
                }
                if (villager.getRandom().nextInt(120) == 0) {
                    villager.playSound(SoundEvents.VILLAGER_CELEBRATE, 0.8F, 1.3F);
                }
            }
            case FAN_PLAY -> {
                if (!focusStillThere(level, antic)) {
                    phase = Phase.DONE;
                    return;
                }
                brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(antic.focus()));
                if (villager.distanceToSqr(Vec3.atBottomCenterOf(antic.stand())) > BLOWN_AWAY_SQR) {
                    if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                        walkTo(villager, antic);  // blown out; go right back in
                    }
                } else {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    if (villager.onGround() && villager.getRandom().nextInt(40) == 0) {
                        villager.getJumpControl().jump();  // catch some air
                    }
                }
            }
            case STARE_FISH -> {
                brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(antic.focus()));
                if (villager.distanceToSqr(Vec3.atBottomCenterOf(antic.stand())) > BLOWN_AWAY_SQR) {
                    if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                        walkTo(villager, antic);
                    }
                } else {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                }
                if (villager.getRandom().nextInt(200) == 0) {
                    villager.playAmbientSound();  // "hmm... nothing biting"
                }
            }
        }
        if (villager.getRandom().nextDouble() < Config.PARTICLE_CHANCE.get()) {
            NitwitAi.happyParticles(level, villager, 1 + villager.getRandom().nextInt(2));
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(NitwitsCrankIt.ANTIC.get());
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        putAwayFishingStick(villager);
        if (brain.isActive(NitwitsCrankIt.ANTICS.get())) {
            // Back to idling; the schedule picks the right activity from there.
            brain.useDefaultActivity();
        }
    }

    private static Antic antic(Villager villager) {
        return villager.getBrain().getMemory(NitwitsCrankIt.ANTIC.get()).orElseThrow();
    }

    private static boolean focusStillThere(ServerLevel level, Antic antic) {
        return switch (antic.kind()) {
            case BELT_SURF -> CreateCompat.isMovingFlatBelt(level, antic.focus());
            case FAN_PLAY -> CreateCompat.runningSidewaysFanFacing(level, antic.focus()) != null;
            case STARE_FISH -> NitwitAi.isWater(level, antic.focus());
        };
    }

    private static boolean hasArrived(ServerLevel level, Villager villager, Antic antic) {
        return switch (antic.kind()) {
            case BELT_SURF -> CreateCompat.isBelt(level, villager.getOnPos());
            case FAN_PLAY, STARE_FISH -> villager.distanceToSqr(Vec3.atBottomCenterOf(antic.stand())) <= AT_SPOT_SQR;
        };
    }

    private static void walkTo(Villager villager, Antic antic) {
        // A belt isn't walkable, so aim for the space above it; closeEnough 1 is satisfied once on top.
        WalkTarget walk = antic.kind() == Antic.Kind.BELT_SURF
                ? new WalkTarget(antic.stand().above(), WALK_SPEED, 1)
                : new WalkTarget(antic.stand(), WALK_SPEED, 0);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walk);
    }

    private static void takeOutFishingStick(Villager villager) {
        ItemStack stick = new ItemStack(Items.STICK);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(FISHING_STICK_TAG, true);
        stick.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        villager.setItemSlot(EquipmentSlot.MAINHAND, stick);
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    /** Removes the prop stick, if the villager is holding it. */
    static void putAwayFishingStick(Villager villager) {
        ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        CustomData data = held.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.copyTag().getBoolean(FISHING_STICK_TAG)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}
