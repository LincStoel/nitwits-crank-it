# Testing

## Still to test in game

Everything else from the 2026-10-10 review pass has been tested in game (see the commit history).

- [ ] **Desk bell prank.** A nitwit runs to a `create:desk_bell`, presses it several times, and flees. Check the sound, the redstone pulse, and the per-nitwit cooldown.
- [ ] **Stare-fishing.** A nitwit stands at still water holding a stick. Check the stick is taken away afterward (also after a save/reload mid-fishing) and never drops.
- [ ] **Kids chase a pig rider.** Baby villagers run after a pig-riding nitwit. This now uses the vanilla visible-entities sensor, so check they still notice it.
- [ ] **Golem chase-off.** A nitwit tags along after a golem, the golem gets fed up (`golemFollowing.chaseOffChance`) and chases it, and the nitwit flees.
- [ ] **Nitwits flee a pig rider.** Also switched to the sensor memory.
- [ ] **Wrong-way cranking with a crowd.** Nitwits joining a crank should turn it the same way as whoever started it.
- [ ] **Belt and fan edge cases.** Sloped or encased belts, reversed (pulling) fans, and a washing (water) catalyst in the wind, which should still be allowed.
- [ ] **Older NeoForge 21.1.x.** `mods.toml` accepts `[21.1.0,)`, but only 21.1.248 has been tested.
- [ ] **MCA Reborn** compatibility. Lithium and Guard Villagers have already passed.

## How to test

The dev runs share game directories:

| Run | Directory | Mods |
|---|---|---|
| `runServer` | `run/` | this mod, Create, Jade |
| `runClientJoin` | `run-client/` | this mod, Create, Jade; joins `runServer` on localhost |
| `runServerNoCreate` / `runClientNoCreate` | `run-nocreate/` | this mod only |
| `runClientBare` | `run-bare/` | nothing; joins `runServerNoCreate` |

Test scenes are easiest to drive over RCON (`enable-rcon=true` in the dev `server.properties`):
- Build rigs with `setblock`. Create blocks can take block-entity NBT; for example, a belt segment needs `Controller`, `Index` and `Length`, and a `create:creative_motor` drives things at 16 RPM.
- Summon nitwits with `{VillagerData:{profession:"minecraft:nitwit",level:1,type:"minecraft:plains"}}`.
- Set the time (`2500` is work hours, `10500` is afternoon play time) and run `gamerule doDaylightCycle false`.
- Raise a feature's `chance` to `1.0` (and its `cooldownTicks` to `0`) in `config/nitwits_crank_it-server.toml`. It reloads live. Turn off other features so they don't keep the nitwit busy, and restore the defaults afterward.
- The `run` configs log at DEBUG, so lines like `Nitwit pranking ...`, `Nitwit starting ...`, `Iron golem chasing ...` and `Nitwit dizzy after ...` show what a nitwit decided.

Gotchas:
- A villager with `NoAI` has no physics, so it can't be pushed. To keep a victim in place, give it `slowness infinite 255` instead.
- Walls between a golem and the prankster block the golem's line of sight, so it won't chase.
- Wait for the previous server to release port 25565 before starting another.
