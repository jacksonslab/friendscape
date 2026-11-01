# Sailing implementation

Charting expansion: branch `sailing-charting-isolated`, based on `friendscape-release` (revision 241).
Built on the world-entity boat engine from OpenRune PR 182
(https://github.com/OpenRune/OpenRune-Server/pull/182). Not yet tested in a live client.

## Working

- **Boarding**: any port's gangplank (32 ports, from `friendscape_sailing_ports`). It checks the
  dock level (`sailing_dock`) and the boat level (`sailing_boat`). The cache retrieval fee is
  charged when the boat was last moored elsewhere. Board-previous chooses a boat; Board-friend
  boards another player's moored boat.
- **Navigation**: the helm steers. The hull collides against `SeaMap`. Rev 241 gives open sea no
  collision flags, so water comes from the terrain overlay textures. To disembark, stop within 10
  tiles of a dock; the boat snaps to the mooring.
- **Boat controls** (`sailing-controls` branch): the native `sailing_sidepanel` opens in the combat
  tab when boarding. Its Set/Un-set, Lower speed and Raise speed buttons control the actual boat,
  including half speed and reverse. Take the physical helm first; heading still comes from clicking
  the sea. The panel shows helm status and movement stats, can switch to combat options and back,
  and restores normal combat options when leaving. Passengers cannot change the captain's speed.
- **Port tasks (courier)**: notice boards, ledger tables and the cargo hold, on Jagex's
  `port_task_slot_*` varbits. 438 tasks with wiki XP (`enum.friendscape_courier_task_xp`).
  Slots unlock at 1/7/28/56/84. Port coin bags pay out the wiki ranges. Relocating a boat
  confiscates crates and hands them back to the task.
- **Salvaging**: 185 shipwreck spawns cycle through active, depleted and respawned. The raft swaps
  its cargo hold for a hook (Modify); skiff and sloop carry both. Deploy the hook next to a wreck.
  Sort salvage at port salvaging stations using the wiki loot tables.
- **Charting**: generic "Chart" landmarks and 57 spyglass discoveries. Observation positions and
  Sailing requirements are packed in `friendscape_sailing_observations`; descriptions and first-time
  XP come from the native charting tables and `enum_5868` (tenths of XP), exposed as
  `friendscape_charting_first_xp` because this unnamed enum has no native gameval. Completion uses native
  `sailing_charting_completion_0..11` varps keyed by the task's unique ID. Existing custom landmark
  progress migrates when opening the log without awarding XP again.
- **Captain's log**: notice board > Sea charting log or spyglass > Inspect lists supported tasks by
  sea, completion counts, hints, observation coordinates and requirements. Captain's log items also
  support View-charts and View-tasks. Stop your boat, reach an observation point and Look-through
  the spyglass to record it. Island and cave observation points also work on foot.
- **Pandemonium**: the introductory quest is implemented and rewards the spyglass; Sailing stays
  available before quest completion.
- **Boat purchases**: shipwright Buy-boat or notice board > Commission a boat. Skiffs cost 15,000 coins at level 15; sloops cost 200,000 coins at level 50. Payment and ownership are rechecked atomically.
- **Boat parts**: Make-X at shipwright workbenches makes all seven hull tiers and large hull parts. Anvils make bronze through rune keel parts; use five ordinary parts on an anvil for a large part.

## Not done / deviations

- No bounty tasks, boat combat, trawling, crew, charting activities other than landmarks and
  spyglass observations, Barracuda Trials or facility upgrades; everyone owns a raft.
- Set/Un-set and reverse sailing controls work; Trim has no wind-gust mechanic yet.
- Spyglass discoveries use a three-tile square around each published observation point rather
  than Jagex's unavailable server observation zones. The view is described in chat rather than
  displayed through the native camera lens. Repeat discovery XP and sea/ocean bonuses are not
  awarded. The log lists only supported tasks, not full native sea completion.
- Task choice uses chat menus rather than Jagex's port_task_board interface.
- Salvage success uses a flat 64/192 curve. Wreck duration is the wiki's average.
- Dock quest requirements are ignored.

## Part recipe references

Observation positions and requirements: https://oldschool.runescape.wiki/w/Sea_charting

- https://oldschool.runescape.wiki/w/Construction_training (Hull parts)
- https://oldschool.runescape.wiki/w/Wooden_hull_parts
- https://oldschool.runescape.wiki/w/Rune_keel_parts
- https://oldschool.runescape.wiki/w/Skiff
- https://oldschool.runescape.wiki/w/Sloop

The pre-Pandemonium anvil menu exposes keel crafting because Friendscape permits Sailing before the quest; it does not mark the quest complete. Dragon keel recipes already exist at the Ancient Forge and have not been changed or verified in this milestone.
