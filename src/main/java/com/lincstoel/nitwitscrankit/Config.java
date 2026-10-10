package com.lincstoel.nitwitscrankit;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("Shared settings for nitwits working at hand cranks and turntables.").push("workSpots");
    }

    public static final ModConfigSpec.IntValue SEARCH_RADIUS = BUILDER
            .comment("Horizontal distance (in blocks) a nitwit searches for hand cranks, turntables and desk bells.")
            .defineInRange("searchRadius", 16, 1, 64);

    public static final ModConfigSpec.IntValue VERTICAL_RANGE = BUILDER
            .comment("Vertical distance (in blocks) above and below a nitwit that is searched.")
            .defineInRange("verticalRange", 4, 0, 32);

    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS = BUILDER
            .comment("How often (in ticks) a nitwit re-evaluates where to work during work hours.")
            .defineInRange("scanIntervalTicks", 100, 20, 2400);

    public static final ModConfigSpec.DoubleValue PARTICLE_CHANCE = BUILDER
            .comment("Chance per tick that a working nitwit shows happy villager particles.")
            .defineInRange("particleChance", 0.015, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue JUMP_CHANCE = BUILDER
            .comment("Chance per tick that a working nitwit does a happy little jump.")
            .defineInRange("jumpChance", 0.004, 0.0, 1.0);

    static {
        BUILDER.pop().push("handCranks");
    }

    public static final ModConfigSpec.BooleanValue HAND_CRANKS_ENABLED = BUILDER
            .comment("Nitwits turn create:hand_crank blocks during work hours.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue MAX_NITWITS_PER_CRANK = BUILDER
            .comment("How many nitwits may cluster around the same hand crank.")
            .defineInRange("maxNitwitsPerCrank", 3, 1, 16);

    public static final ModConfigSpec.BooleanValue HEAD_SHAKE_ENABLED = BUILDER
            .comment("Cranking nitwits shake their heads the whole time.")
            .define("headShake", true);

    public static final ModConfigSpec.DoubleValue THINKING_SOUND_CHANCE = BUILDER
            .comment("Chance per tick that a cranking nitwit makes a thinking \"hmm\" sound. 0 disables it.")
            .defineInRange("thinkingSoundChance", 0.006, 0.0, 1.0);

    static {
        BUILDER.pop().push("turntables");
    }

    public static final ModConfigSpec.BooleanValue TURNTABLES_ENABLED = BUILDER
            .comment("Nitwits stand on unoccupied spinning create:turntable blocks during work hours.",
                    "A free hand crank is preferred; a turntable is preferred over sharing a hand crank.")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue TURNTABLE_DIZZY_ENABLED = BUILDER
            .comment("After a long turntable ride, nitwits stumble around dizzily for a few seconds.")
            .define("dizzy", true);

    // Pranks (except water pushing) and pig riding only start during the children's afternoon play period
    // (10000-12000), since the morning one overlaps work hours.

    static {
        BUILDER.pop().push("bellPrank");
    }

    public static final ModConfigSpec.BooleanValue BELL_PRANK_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally run to the village bell, ring it, and run off.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue BELL_PRANK_COOLDOWN_DAYS = BUILDER
            .comment("Minimum number of days between pranks on the same village bell.")
            .defineInRange("cooldownDays", 2, 1, 100);

    public static final ModConfigSpec.DoubleValue BELL_PRANK_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per nitwit once the cooldown is over, to pull the prank.")
            .defineInRange("chance", 0.05, 0.0, 1.0);

    static {
        BUILDER.pop().push("deskBellPrank");
    }

    public static final ModConfigSpec.BooleanValue DESK_BELL_PRANK_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally run to a nearby create:desk_bell, ring it several times, and run off.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue DESK_BELL_PRANK_COOLDOWN_TICKS = BUILDER
            .comment("Minimum ticks between desk bell pranks by the same nitwit.")
            .defineInRange("cooldownTicks", 600, 0, 240000);

    public static final ModConfigSpec.DoubleValue DESK_BELL_PRANK_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per nitwit once its cooldown is over, to pull the prank.")
            .defineInRange("chance", 0.1, 0.0, 1.0);

    static {
        BUILDER.pop().push("steamWhistlePrank");
    }

    public static final ModConfigSpec.BooleanValue STEAM_WHISTLE_PRANK_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally run to a working create:steam_whistle, toot it, and run off.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue STEAM_WHISTLE_PRANK_COOLDOWN_TICKS = BUILDER
            .comment("Minimum ticks between steam whistle pranks by the same nitwit.")
            .defineInRange("cooldownTicks", 1200, 0, 240000);

    public static final ModConfigSpec.DoubleValue STEAM_WHISTLE_PRANK_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per nitwit once its cooldown is over, to pull the prank.")
            .defineInRange("chance", 0.1, 0.0, 1.0);

    static {
        BUILDER.pop().push("waterPushPrank");
    }

    public static final ModConfigSpec.BooleanValue WATER_PUSH_PRANK_ENABLED = BUILDER
            .comment("Any time they're awake (even mid-work), nitwits occasionally sneak up on a villager standing next to water and shove it in.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue WATER_PUSH_PRANK_COOLDOWN_TICKS = BUILDER
            .comment("Minimum ticks between water pushes by the same nitwit.")
            .defineInRange("cooldownTicks", 1200, 0, 240000);

    public static final ModConfigSpec.DoubleValue WATER_PUSH_PRANK_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds per awake nitwit once its cooldown is over, to pull the prank.")
            .defineInRange("chance", 0.1, 0.0, 1.0);

    static {
        BUILDER.pop().push("prankReactions");
    }

    public static final ModConfigSpec.BooleanValue PRANK_UNHAPPY_PARTICLES_ENABLED = BUILDER
            .comment("Other (non-nitwit) villagers nearby show a single unhappy particle when a nitwit pulls a prank.")
            .define("unhappyParticles", true);

    public static final ModConfigSpec.BooleanValue GOLEM_CHASE_ENABLED = BUILDER
            .comment("An iron golem that sees a nitwit pull a prank chases it around for 5-15 seconds while it flees.")
            .define("golemChase", true);

    static {
        BUILDER.pop().push("pigRiding");
    }

    public static final ModConfigSpec.BooleanValue PIG_RIDING_ENABLED = BUILDER
            .comment("During play time, nitwits that come across a saddled pig ride it around, hopping off at nightfall.")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue PIG_CHASE_ENABLED = BUILDER
            .comment("Pig-riding nitwits chase other nitwits around, and the other nitwits run away from them.")
            .define("chaseNitwits", true);

    public static final ModConfigSpec.BooleanValue KIDS_JOIN_PIG_CHASE_ENABLED = BUILDER
            .comment("Child villagers at play run after a pig-riding nitwit, squealing.")
            .define("kidsJoinChase", true);

    static {
        BUILDER.pop().push("beltSurfing");
    }

    public static final ModConfigSpec.BooleanValue BELT_SURFING_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally hop onto a moving create:belt and ride it.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue BELT_SURFING_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per idle nitwit with a belt nearby, to go surfing.")
            .defineInRange("chance", 0.15, 0.0, 1.0);

    static {
        BUILDER.pop().push("fanPlay");
    }

    public static final ModConfigSpec.BooleanValue FAN_PLAY_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally stand in front of a running create:encased_fan and get blown around.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue FAN_PLAY_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per idle nitwit with a fan nearby, to go play in it.")
            .defineInRange("chance", 0.15, 0.0, 1.0);

    static {
        BUILDER.pop().push("stareFishing");
    }

    public static final ModConfigSpec.BooleanValue STARE_FISHING_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally stand at the water's edge holding a stick, \"fishing\".")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue STARE_FISHING_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per idle nitwit with water nearby, to go fishing.")
            .defineInRange("chance", 0.1, 0.0, 1.0);

    static {
        BUILDER.pop().push("junkGifts");
    }

    public static final ModConfigSpec.BooleanValue JUNK_GIFTS_ENABLED = BUILDER
            .comment("Nitwits throw junk (sticks, dead bushes, rotten flesh...) at a Hero of the Village instead of wheat seeds.")
            .define("enabled", true);

    static {
        BUILDER.pop().push("golemFollowing");
    }

    public static final ModConfigSpec.BooleanValue GOLEM_FOLLOWING_ENABLED = BUILDER
            .comment("During play time, nitwits occasionally tag along after an iron golem for a while.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue GOLEM_FOLLOWING_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds of play time per nitwit with a golem in sight, to tag along for 20-60 seconds.")
            .defineInRange("chance", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue GOLEM_CHASE_OFF_CHANCE = BUILDER
            .comment("Chance that a golem being tailed gets fed up after a few seconds and chases the nitwit off for 5-15 seconds.")
            .defineInRange("chaseOffChance", 0.3, 0.0, 1.0);

    static {
        BUILDER.pop().push("heroFollowing");
    }

    public static final ModConfigSpec.BooleanValue HERO_FOLLOWING_ENABLED = BUILDER
            .comment("Whenever they're awake, nitwits now and then notice a nearby player with Hero of the Village",
                    "and tag along after them for 20-60 seconds before getting distracted. Interrupts work and play.")
            .define("enabled", true);

    public static final ModConfigSpec.DoubleValue HERO_FOLLOWING_CHANCE = BUILDER
            .comment("Chance, checked every 10 seconds per nitwit with a hero nearby (once its cooldown is over), to start tagging along.")
            .defineInRange("chance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.IntValue HERO_FOLLOWING_COOLDOWN_TICKS = BUILDER
            .comment("Ticks after a nitwit loses interest before it can start following the hero again.")
            .defineInRange("cooldownTicks", 1200, 0, 240000);

    static {
        BUILDER.pop().push("anvilBonk");
    }

    public static final ModConfigSpec.BooleanValue ANVIL_BONK_ENABLED = BUILDER
            .comment("Any villager that survives being hit by a falling anvil becomes a nitwit, losing its job and trades.")
            .define("enabled", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
