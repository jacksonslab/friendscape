# Sailing implementation

Branch `sailing-stage-1` (revision 241). Built on the world-entity boat engine from OpenRune PR 182
(https://github.com/OpenRune/OpenRune-Server/pull/182). Not yet tested in a live client.

## Working

- **Boarding**: any port's gangplank (32 ports, from `friendscape_sailing_ports`). It checks the
  dock level (`sailing_dock`) and the boat level (`sailing_boat`). The cache retrieval fee is
  charged when the boat was last moored elsewhere. Board-previous chooses a boat; Board-friend
  boards another player's moored boat.
- **Navigation**: the helm steers. The hull collides against `SeaMap`. Rev 241 gives open sea no
  collision flags, so water comes from the terrain overlay textures. To disembark, stop within 10
  tiles of a dock; the boat snaps to the mooring.
- **Port tasks (courier)**: notice boards, ledger tables and the cargo hold, on Jagex's
  `port_task_slot_*` varbits. 438 tasks with wiki XP (`enum.friendscape_courier_task_xp`).
  Slots unlock at 1/7/28/56/84. Port coin bags pay out the wiki ranges. Relocating a boat
  confiscates crates and hands them back to the task.
- **Salvaging**: 185 shipwreck spawns cycle through active, depleted and respawned. The raft swaps
  its cargo hold for a hook (Modify); skiff and sloop carry both. Deploy the hook next to a wreck.
  Sort salvage at port salvaging stations using the wiki loot tables.
- **Charting**: the 92 generic "Chart" landmarks give 35 XP each, tracked in server varps.
- **Boat purchases**: shipwright Buy-boat or notice board > Commission a boat. Skiffs cost 15,000 coins at level 15; sloops cost 200,000 coins at level 50. Payment and ownership are rechecked atomically.
- **Boat parts**: Make-X at shipwright workbenches makes all seven hull tiers and large hull parts. Anvils make bronze through rune keel parts; use five ordinary parts on an anvil for a large part.

## Not done / deviations

- No bounty tasks, boat combat, trawling, crew, sea charting beyond generic landmarks, sail
  trimming, Barracuda Trials, facility upgrades, or the Pandemonium quest; everyone owns a raft.
- Task choice uses chat menus rather than Jagex's port_task_board interface.
- Salvage success uses a flat 64/192 curve. Wreck duration is the wiki's average.
- Dock quest requirements are ignored.

## Part recipe references

- https://oldschool.runescape.wiki/w/Construction_training (Hull parts)
- https://oldschool.runescape.wiki/w/Wooden_hull_parts
- https://oldschool.runescape.wiki/w/Rune_keel_parts
- https://oldschool.runescape.wiki/w/Skiff
- https://oldschool.runescape.wiki/w/Sloop

The pre-Pandemonium anvil menu exposes keel crafting because Friendscape permits Sailing before the quest; it does not mark the quest complete. Dragon keel recipes already exist at the Ancient Forge and have not been changed or verified in this milestone.
