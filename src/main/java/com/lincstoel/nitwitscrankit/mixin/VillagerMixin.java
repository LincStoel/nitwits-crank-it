package com.lincstoel.nitwitscrankit.mixin;

import java.util.Collection;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.lincstoel.nitwitscrankit.NitwitsCrankIt;
import com.lincstoel.nitwitscrankit.ai.NitwitPackages;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Shadow
    public abstract VillagerData getVillagerData();

    @Shadow
    public abstract void refreshBrain(ServerLevel level);

    /** A brain only stores memory types it was built with, so add ours to every villager's brain. */
    @ModifyArg(
            method = "brainProvider",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;provider(Ljava/util/Collection;Ljava/util/Collection;)Lnet/minecraft/world/entity/ai/Brain$Provider;"),
            index = 0
    )
    private Collection<? extends MemoryModuleType<?>> nitwitsCrankIt$addMemoryTypes(Collection<? extends MemoryModuleType<?>> memoryTypes) {
        return ImmutableList.<MemoryModuleType<?>>builder()
                .addAll(memoryTypes)
                .add(NitwitsCrankIt.WORK_SPOT.get(), NitwitsCrankIt.UNREACHABLE_SPOTS.get(),
                        NitwitsCrankIt.PRANK_TARGET.get(), NitwitsCrankIt.PRANK_COOLDOWNS.get(),
                        NitwitsCrankIt.FOLLOW_TARGET.get(), NitwitsCrankIt.ANTIC.get(), NitwitsCrankIt.DIZZY.get(),
                        NitwitsCrankIt.CHASED_BY.get())
                .build();
    }

    /** Nitwits get a WORK activity that requires a remembered work spot instead of a job site. */
    @WrapOperation(
            method = "registerBrainGoals",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;addActivityWithConditions(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;Ljava/util/Set;)V")
    )
    private void nitwitsCrankIt$replaceNitwitWork(
            Brain<Villager> brain,
            Activity activity,
            ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super Villager>>> behaviors,
            Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions,
            Operation<Void> original
    ) {
        if (activity == Activity.WORK && nitwitsCrankIt$isNitwit()) {
            original.call(brain, activity, NitwitPackages.work(),
                    ImmutableSet.of(Pair.of(NitwitsCrankIt.WORK_SPOT.get(), MemoryStatus.VALUE_PRESENT)));
        } else {
            original.call(brain, activity, behaviors, conditions);
        }
    }

    @Inject(method = "registerBrainGoals", at = @At("TAIL"))
    private void nitwitsCrankIt$addNitwitBehaviors(Brain<Villager> brain, CallbackInfo ci) {
        if (nitwitsCrankIt$isNitwit() && !((Villager) (Object) this).isBaby()) {
            brain.addActivity(Activity.CORE, NitwitPackages.core());
            brain.addActivityWithConditions(NitwitsCrankIt.PRANK.get(), NitwitPackages.prank(),
                    ImmutableSet.of(Pair.of(NitwitsCrankIt.PRANK_TARGET.get(), MemoryStatus.VALUE_PRESENT)));
            brain.addActivityWithConditions(NitwitsCrankIt.FOLLOW.get(), NitwitPackages.follow(),
                    ImmutableSet.of(Pair.of(NitwitsCrankIt.FOLLOW_TARGET.get(), MemoryStatus.VALUE_PRESENT)));
            brain.addActivityWithConditions(NitwitsCrankIt.ANTICS.get(), NitwitPackages.antics(),
                    ImmutableSet.of(Pair.of(NitwitsCrankIt.ANTIC.get(), MemoryStatus.VALUE_PRESENT)));
        } else if (((Villager) (Object) this).isBaby()) {
            brain.addActivity(Activity.CORE, NitwitPackages.kids());
        }
    }

    /**
     * Vanilla only rebuilds the brain for a profession change on save load or job changes, which never involve
     * nitwits; rebuild it when a villager becomes (or stops being) a nitwit some other way, e.g. /data.
     */
    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void nitwitsCrankIt$rememberWasNitwit(VillagerData data, CallbackInfo ci) {
        nitwitsCrankIt$wasNitwit = nitwitsCrankIt$isNitwit();
    }

    // At TAIL, so the brain is rebuilt from the newly stored data.
    @Inject(method = "setVillagerData", at = @At("TAIL"))
    private void nitwitsCrankIt$refreshOnNitwitChange(VillagerData data, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (nitwitsCrankIt$wasNitwit != nitwitsCrankIt$isNitwit() && self.level() instanceof ServerLevel level) {
            refreshBrain(level);
        }
    }

    @Unique
    private boolean nitwitsCrankIt$wasNitwit;

    @Unique
    private boolean nitwitsCrankIt$isNitwit() {
        return getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }
}
