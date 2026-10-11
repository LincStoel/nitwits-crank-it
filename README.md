# Nitwits Crank It

A highly configurable NeoForge mod for Minecraft 1.21.1 that adds behaviors to adult nitwit villagers. They will pull pranks, crank create handcranks, ring bells, and ride pigs! These whimsical idiots will liven up any village.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.0 or later

## Optional dependencies

| Mod | Version | Side | What it adds |
|---|---|---|---|
| [Create](https://modrinth.com/mod/create) | 6.0.10 up to (not including) 6.1 | Both | Hand crank and turntable work, the desk bell and steam whistle pranks, belt surfing and fan play. |
| [Jade](https://modrinth.com/mod/jade) | 15.10 or later | Both | A tooltip line showing what a nitwit is doing. |
| [Configured](https://www.curseforge.com/minecraft/mc-mods/configured) | 2.6 or later | Client | An in-game config screen with translated option names. |

Without Create, all features not listed against it above still work.

## Server and client

- Clients can join a server that has the mod without installing it.
- Players need the mod on their client only for the Jade status line, and for translated option names in Configured.

## Schedule

Nitwits use the vanilla villager schedule. The mod uses three time windows:

- **Work hours:** the vanilla villager work period.
- **Play time:** the afternoon play period of baby villagers (day time 10000–12000), until nightfall. The morning play period overlaps work hours, so it isn't used.
- **Awake:** any time the nitwit isn't sleeping or in its rest period.

## Features

### Work (work hours, needs Create)

**Hand cranks**
- The nitwit turns the crank the same way a player's right-click does. This drives anything the crank is connected to.
- Optionally, a nitwit turns the crank backwards, the way a sneaking player does (wrong-way chance, off by default). Whoever starts a crank turning sets the direction, and nitwits that join in keep it.
- While cranking, nitwits play the vanilla head-shake animation without its sound, and now and then make the villager ambient "hmm" sound.

**Turntables**
- The nitwit stands on a spinning turntable and lets it spin them. Only one villager per turntable.
- If the turntable stops, or something else gets on it, the nitwit steps off onto free ground beside it.
- After riding for 10 seconds or more, the nitwit is dizzy for 3–6 seconds when it leaves. While dizzy, it lurches randomly sideways and its head rolls around.

**Both:** working nitwits occasionally show happy villager particles and do small jumps.

### Pranks

Every 10 seconds, each nitwit rolls for each prank type in random order and performs the first one that passes its chance roll and has a target. The nitwit runs to the target and performs the prank. Then it plays the villager celebrate sound, shows happy particles, and runs away.

**Reactions to a successful prank**
- Non-nitwit villagers within 16 blocks show one angry particle. Other nitwits don't react.
- Iron golems within 16 blocks that can see the prankster and have no attack target chase it for 5–15 seconds. They don't attack. A golem drops the chase as soon as it has a real target. The nitwit keeps fleeing for as long as it's being chased.

| Prank | When | Dependency | Details |
|---|---|------------|---|
| Village bell | Play time | –          | The nitwit rings its village's bell (its meeting point) if it is within 48 blocks. The bell triggers the vanilla reaction: other villagers go into hiding, but the prankster doesn't. Cooldown is per bell (default 2 days) and is saved with the world. |
| Desk bell | Play time | Create     | Rings the nearest desk bell 3–6 times, like a player pressing it (sound, animation and redstone pulse). Cooldown is per nitwit. |
| Steam whistle | Play time | Create     | Toots the nearest steam whistle 2–3 times. The whistle must be on a boiler that is active and heated. Toots work by setting the whistle's powered state, which is returned to match its real redstone input afterwards. Whistles left blowing (e.g. the prankster died or unloaded, or the server crashed) are released automatically, including after a restart. Cooldown is per nitwit. |
| Water push | Any time awake, including during work | –          | Shoves a villager into water. The victim must be within 12 blocks, visible to the nitwit, standing on dry ground next to still water (a non-flowing source block, or a drop into one), and not sleeping, riding or leashed. Flowing water is never used, so item and mob transport streams are left alone. Plays the villager hurt sound but deals no damage. Cooldown is per nitwit. |

### Antics (play time)

Every 10 seconds, an idle nitwit rolls for each antic in random order and starts the first one that passes and has a valid spot. An antic lasts 15–35 seconds or until play time ends. Occasional happy particles.

| Antic | Dependency | Details |
|---|------------|---|
| Belt surfing | Create     | Hops on a moving, flat belt and rides it until it is dropped off the end. The ride must have at least 2 segments ahead, and the nitwit prefers the boarding point with the longest ride. A belt is skipped if: any segment lacks headroom; a block in the `nitwits_crank_it:belt_hazards` tag, or lava, is within 1 block of the path; or the drop-off point lacks solid, dry ground at most 2 blocks down. Occasional celebrate sound while riding. |
| Fan play | Create     | Stands 2 blocks in front of a running, sideways-facing encased fan and gets blown around. Walks back in when blown out, and jumps now and then. Only fans whose air current reaches the spot and has no processing along its whole length are used. Washing (water) is allowed. Blasting, smoking and haunting are not. The antic ends if a catalyst is added mid-play. |
| Stare-fishing | –          | Stands on dry ground next to open water (within 10 blocks horizontally, 4 vertically), holds a stick and looks at the water. The stick is a prop: it never drops and is removed when the antic ends, including after a save and reload. Occasional ambient "hmm" sound. |

Default `belt_hazards` tag: fire, campfires, magma blocks, cactus, sweet berry bushes, wither roses, pointed dripstone, and (if Create is present) mechanical saws, drills, crushing wheels, deployers and mechanical rollers. You can change the tag with a datapack.

### Pig riding (play time)

- A nitwit that sees a saddled adult pig within 12 blocks runs to it and rides it. Pigs that are leashed, named or already ridden are skipped.
- The rider alternates between wandering the pig around and charging it at a random nitwit within 16 blocks for 5–10 seconds.
- Other nitwits within 7 blocks of a rider run away, hopping, until they are 11 blocks away.
- Baby villagers that are playing chase a rider they see within 12 blocks, hopping and squealing. They give up past 20 blocks.
- The nitwit gets off at the end of play time, or when something takes priority over play (panic, hiding, a raid, a Hero of the Village).

### Following

**Hero of the Village** (any time awake)
- Every 10 seconds, a nitwit within 10 blocks of a player with Hero of the Village may start following them for 20–60 seconds. Invisible and spectating players are ignored.
- Following a hero interrupts work, play and golem-following. It does not interrupt pranks, panicking, hiding, raids or sleep.
- The nitwit stops when the hero is more than 16 blocks away, the effect ends, or the nitwit gets more than 48 blocks (configurable) from its village. The village is measured from its bell, or from its bed if it has no bell. After that it can't start following again until a cooldown ends (default 60 seconds).
- Nitwits following a hero still throw gifts at them.

**Iron golems** (play time)
- Every 10 seconds, a nitwit may start following a visible iron golem within 16 blocks for 20–60 seconds. It stops if the golem gets more than 24 blocks away.
- With a 30% chance (configurable), the golem gets fed up after 3–10 seconds. The nitwit says "no", and the golem chases it off for 5–15 seconds, the same as the prank chase.

### Junk gifts

Nitwits throw junk at a Hero of the Village in place of the vanilla wheat seeds. The table, with weights: stick 10, dead bush 8, rotten flesh 8, dirt 6, cobblestone 6, poisonous potato 5, string 5, feather 5, bowl 4, clay ball 4, bone 4, flint 4, wheat seeds 3, pufferfish 1.

The gift table is `nitwits_crank_it:gameplay/hero_of_the_village/nitwit_gift`, assigned through NeoForge's `raid_hero_gifts` data map. When junk gifts are disabled in the config, it gives wheat seeds.

### Anvil bonk

- A villager hit by a falling anvil that survives becomes a nitwit. It loses its job site, trades, experience and level, and plays the "no" sound. Anyone trading with it has their trade screen closed.
- By default, villagers that have already been traded with (any experience) are not affected, matching vanilla's job lock.

### Advancements

A separate advancement tab. Most are earned by being within 16 blocks when it happens.

| Advancement | Requirement |
|---|---|
| Nitwits Crank It (root) | See any of the events below. |
| Bonk! | See a villager become a nitwit from a falling anvil. |
| Crank It | See a nitwit start turning a hand crank. |
| Dizzy Spell | See a nitwit come off a turntable dizzy. |
| Ding-Dong Ditch | See a nitwit ring the village bell. |
| Hog Wild | See a nitwit get on a saddled pig. |
| Tag-Along | Have a nitwit start following you while you have Hero of the Village. |

The trigger, `nitwits_crank_it:nitwit_mischief`, takes an optional `kind` (`bonk`, `crank`, `dizzy`, `village_bell`, `pig_ride`, `hero_follow`) and can be used by datapack advancements.

### Jade status

Looking at a nitwit shows one of: Cranking, Riding a turntable, Heading to work, Dizzy, Riding a pig, Fleeing an iron golem, Up to something (village bell / desk bell / steam whistle / a shove), Belt surfing, Playing in the wind, Fishing (sort of), Tagging along after a hero, Tagging along after a golem. The line can be toggled in Jade's plugin settings ("Nitwit Activity").

### Other behavior

- If a villager becomes a nitwit (or stops being one) by any means, such as commands, its AI is rebuilt right away.
- None of the per-nitwit state (work spot, prank target, cooldowns, follow target) is saved. It is worked out again after a reload. Village bell cooldowns and steam whistle release times are saved per world.

## Configuration

Server config: `config/nitwits_crank_it-server.toml`. To override it for a single world, put a copy in `saves/<world>/serverconfig/` (on a dedicated server, `world/serverconfig/`). With Configured installed, it can be edited from the mod list.

Chances marked "per 10 s" are rolled every 10 seconds per eligible nitwit.

| Section | Option | Default | Description |
|---|---|---|---|
| `workSpots` | `searchRadius` | 16 | Horizontal search distance for hand cranks, turntables and desk bells (1–64). |
| | `verticalRange` | 4 | Vertical search distance (0–32). |
| | `scanIntervalTicks` | 100 | How often a nitwit re-evaluates its work spot (20–2400). |
| | `particleChance` | 0.015 | Per-tick chance of happy particles while working, playing or riding a pig. |
| | `jumpChance` | 0.004 | Per-tick chance of a jump while working. |
| `handCranks` | `enabled` | true | Turn hand cranks. |
| | `maxNitwitsPerCrank` | 3 | Nitwits allowed around one crank (1–16). |
| | `headShake` | true | Head-shake animation while cranking. |
| | `thinkingSoundChance` | 0.006 | Per-tick chance of the "hmm" sound. |
| | `wrongWayChance` | 0.0 | Chance a nitwit turns its crank backwards. |
| `turntables` | `enabled` | true | Ride turntables. |
| | `dizzy` | true | Dizziness after a long ride. |
| `bellPrank` | `enabled` | true | Village bell prank. |
| | `cooldownDays` | 2 | Days between pranks on the same bell (1–100). |
| | `chance` | 0.05 | Chance per 10 s. |
| `deskBellPrank` | `enabled` | true | Desk bell prank. |
| | `cooldownTicks` | 600 | Per-nitwit cooldown. |
| | `chance` | 0.1 | Chance per 10 s. |
| `steamWhistlePrank` | `enabled` | true | Steam whistle prank. |
| | `cooldownTicks` | 1200 | Per-nitwit cooldown. |
| | `chance` | 0.1 | Chance per 10 s. |
| `waterPushPrank` | `enabled` | true | Water push prank. |
| | `cooldownTicks` | 1200 | Per-nitwit cooldown. |
| | `chance` | 0.1 | Chance per 10 s. |
| `prankReactions` | `unhappyParticles` | true | Nearby villagers show an angry particle. |
| | `golemChase` | true | Iron golems chase pranksters. |
| `pigRiding` | `enabled` | true | Pig riding. |
| | `chaseNitwits` | true | Riders chase other nitwits, who flee. |
| | `kidsJoinChase` | true | Baby villagers chase riders. |
| `beltSurfing` | `enabled` | true | Belt surfing. |
| | `chance` | 0.15 | Chance per 10 s. |
| `fanPlay` | `enabled` | true | Fan play. |
| | `chance` | 0.15 | Chance per 10 s. |
| `stareFishing` | `enabled` | true | Stare-fishing. |
| | `chance` | 0.1 | Chance per 10 s. |
| `junkGifts` | `enabled` | true | Junk gifts for the Hero of the Village. |
| `golemFollowing` | `enabled` | true | Following iron golems. |
| | `chance` | 0.15 | Chance per 10 s. |
| | `chaseOffChance` | 0.3 | Chance the golem chases the nitwit off. |
| `heroFollowing` | `enabled` | true | Following a Hero of the Village. |
| | `chance` | 0.25 | Chance per 10 s. |
| | `cooldownTicks` | 1200 | Delay before following the hero again. |
| | `maxDistanceFromVillage` | 48 | Distance from the village at which following stops (16–256). |
| `anvilBonk` | `enabled` | true | Villagers become nitwits from a falling anvil. |
| | `spareExperiencedVillagers` | true | Villagers that have been traded with are unaffected. |
| | `unbonk` | true | Nitwits become unemployed villagers from a falling anvil. |

## Building

```
./gradlew build          # the mod jar ends up in build/libs/
./gradlew runClient      # dev client, with Create and Jade
./gradlew runClientNoCreate   # dev client without Create (game directory: run-nocreate/)
./gradlew runServer / runServerNoCreate   # dev servers, sharing run/ and run-nocreate/ with the clients above
./gradlew runClientJoin  # a second client (run-client/) that joins the local runServer, for testing as a player
./gradlew runClientBare  # a client without this mod (or any other) that joins the local runServerNoCreate
```
