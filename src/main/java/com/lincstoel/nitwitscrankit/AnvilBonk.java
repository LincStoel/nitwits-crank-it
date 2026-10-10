package com.lincstoel.nitwitscrankit;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A villager that survives an anvil landing on its head becomes a nitwit; a nitwit that does comes to its senses
 * and becomes unemployed.
 */
public final class AnvilBonk {
    private AnvilBonk() {
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!Config.ANVIL_BONK_ENABLED.get()
                || !(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide()
                || !villager.isAlive()
                || !event.getSource().is(DamageTypes.FALLING_ANVIL)) {
            return;
        }
        VillagerData data = villager.getVillagerData();
        if (data.getProfession() == VillagerProfession.NITWIT) {
            if (Config.ANVIL_UNBONK_ENABLED.get()) {
                villager.setVillagerData(data.setProfession(VillagerProfession.NONE).setLevel(1));
                villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
            }
            return;
        }
        if (Config.ANVIL_BONK_SPARES_EXPERIENCED.get() && villager.getVillagerXp() > 0) {
            return;
        }

        // Close the trading screen of anyone mid-trade; its trades are about to go.
        villager.setTradingPlayer(null);
        // Give up the job: free the workstation for someone else and forget the trades and experience.
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.setVillagerXp(0);
        villager.setVillagerData(data.setProfession(VillagerProfession.NITWIT).setLevel(1));
        villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, 0.8F);
    }
}
