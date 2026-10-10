package com.lincstoel.nitwitscrankit;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Advancement trigger {@code nitwits_crank_it:nitwit_mischief}: a player saw a nitwit get up to something. The
 * optional {@code kind} condition picks what (e.g. {@code "bonk"}, {@code "village_bell"}); without it, anything counts.
 */
public class NitwitMischiefTrigger extends SimpleCriterionTrigger<NitwitMischiefTrigger.TriggerInstance> {
    /** How close a player has to be to count as having seen it. */
    private static final double WITNESS_DISTANCE = 16.0;

    public static final String BONK = "bonk";
    public static final String CRANK = "crank";
    public static final String DIZZY = "dizzy";
    public static final String VILLAGE_BELL = "village_bell";
    public static final String PIG_RIDE = "pig_ride";
    public static final String HERO_FOLLOW = "hero_follow";

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Every player near {@code nitwit} saw it get up to {@code kind}. */
    public static void witness(ServerLevel level, Entity nitwit, String kind) {
        for (ServerPlayer player : level.players()) {
            if (player.closerThan(nitwit, WITNESS_DISTANCE)) {
                award(player, kind);
            }
        }
    }

    public static void award(ServerPlayer player, String kind) {
        NitwitsCrankIt.NITWIT_MISCHIEF.get().trigger(player, instance -> instance.matches(kind));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<String> kind)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                Codec.STRING.optionalFieldOf("kind").forGetter(TriggerInstance::kind)
        ).apply(instance, TriggerInstance::new));

        boolean matches(String kind) {
            return this.kind.isEmpty() || this.kind.get().equals(kind);
        }
    }
}
