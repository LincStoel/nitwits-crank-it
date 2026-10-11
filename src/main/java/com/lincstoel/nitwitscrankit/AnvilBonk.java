package com.lincstoel.nitwitscrankit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A villager that survives an anvil landing on its head becomes a nitwit. Once a nitwit, always a nitwit.
 */
public final class AnvilBonk {
    private AnvilBonk() {
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!Config.ANVIL_BONK_ENABLED.get()
                || !(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || !villager.isAlive()
                || !event.getSource().is(DamageTypes.FALLING_ANVIL)) {
            return;
        }
        VillagerData data = villager.getVillagerData();
        if (data.getProfession() == VillagerProfession.NITWIT
                || Config.ANVIL_BONK_SPARES_EXPERIENCED.get() && villager.getVillagerXp() > 0) {
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
        NitwitMischiefTrigger.witness(level, villager, NitwitMischiefTrigger.BONK);
    }
}
