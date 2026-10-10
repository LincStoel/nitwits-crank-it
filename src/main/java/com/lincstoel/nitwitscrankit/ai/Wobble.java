package com.lincstoel.nitwitscrankit.ai;

import java.util.Map;

import com.lincstoel.nitwitscrankit.NitwitsCrankIt;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

/**
 * CORE behavior for nitwits: while {@link NitwitsCrankIt#DIZZY} (after a long turntable ride), stagger about with
 * random sideways lurches and a lolling head, on top of whatever else it's doing.
 */
public class Wobble extends Behavior<Villager> {
    private static final int LURCH_INTERVAL = 5;
    private static final double LURCH_STRENGTH = 0.12;

    public Wobble() {
        super(Map.of(NitwitsCrankIt.DIZZY.get(), MemoryStatus.VALUE_PRESENT), 400);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.getBrain().hasMemoryValue(NitwitsCrankIt.DIZZY.get()) && !villager.isSleeping();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        RandomSource random = villager.getRandom();
        villager.setYHeadRot(villager.getYHeadRot() + (random.nextFloat() - 0.5F) * 40.0F);
        // Not onGround(): a villager standing on a turntable doesn't count as on the ground.
        if (gameTime % LURCH_INTERVAL == 0 && !villager.isInWater() && !villager.isPassenger()) {
            villager.push((random.nextDouble() - 0.5) * 2 * LURCH_STRENGTH, 0.0,
                    (random.nextDouble() - 0.5) * 2 * LURCH_STRENGTH);
            villager.hurtMarked = true;
        }
    }
}
