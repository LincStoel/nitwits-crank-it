# Nitwits Crank It

A NeoForge mod for Minecraft 1.21.1 that gives nitwits something to do. Mostly it's things nobody asked them to do.

## What nitwits get up to

**At work** (villager work hours, needs [Create](https://modrinth.com/mod/create)):
- Turn nearby **hand cranks**, shaking their heads and going "hmm". Up to three nitwits will crowd around one crank.
- Stand on **spinning turntables**, and stagger off dizzy after a long ride.

**In the afternoon** (the children's play period, before bedtime):
- **Pranks:** ring the village bell (sending everyone into hiding), hammer on Create **desk bells**, toot Create **steam whistles**, and run off cackling. Villagers nearby disapprove, and iron golems that see it give chase.
- **Antics:** surf moving Create **belts**, play in the wind of running **encased fans**, and stand at the water's edge "fishing" with a stick.
- **Pig riding:** hop on a saddled pig and charge it at other nitwits, who run away while the village kids chase after it.
- **Golem following:** tag along after an iron golem until it gets fed up and chases them off.

**Any time they're awake:**
- Sneak up on a villager standing next to a pond and shove it in.
- Tag along after a player with **Hero of the Village** for a while, and throw them junk (sticks, dead bushes, rotten flesh...) instead of the usual gift.

**Anvil bonk:** a villager that survives a falling anvil becomes a nitwit. Bonk a nitwit and it comes to its senses as an unemployed villager.

Nitwits stay out of harm's way. They skip processing fans and belts that run into saws or off ledges, and they only push villagers into still water, never into item or mob transport streams. They won't steal leashed or named pigs, and they stop following a hero before they get too far from the village.

## Configuration

Every feature can be turned off or tuned. The config is a per-world server config at `saves/<world>/serverconfig/nitwits_crank_it-server.toml`. On a dedicated server it's `world/serverconfig/`. To give every new world your own defaults, put a copy in `defaultconfigs/`.

With [Configured](https://www.curseforge.com/minecraft/mc-mods/configured) installed, you can edit it in game from the mod list.

## Compatibility

- **Create** (optional, 6.0.10 and later 6.0.x builds) adds the hand crank, turntable, desk bell, steam whistle, belt and fan features. Without Create, everything else still works. If a future Create update changes something this mod relies on, the Create features switch themselves off and log an error. They don't crash the game.
- **Jade** (optional) shows what a nitwit is up to when you look at it. Install the mod on both the server and the client for this to work.
- **Configured** (optional, client) gives you an in-game config screen.

## Building

```
./gradlew build          # the mod jar ends up in build/libs/
./gradlew runClient      # dev client, with Create and Jade
./gradlew runClientNoCreate   # dev client without Create (game directory: run-nocreate/)
```
