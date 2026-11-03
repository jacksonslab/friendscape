# Sailing implementation

Lives on `friendscape-release` (revision 241). Built on the world-entity boat engine from OpenRune
PR 182 (https://github.com/OpenRune/OpenRune-Server/pull/182). Not yet tested in a live client,
including the native port task board.

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
- **Port tasks (courier)**: notice boards open Jagex's native `port_task_board`. It shows up
  to eight notices: your own tasks from that board, then the highest tasks you can take, then the
  nearest locked ones. Selecting a notice opens `port_task_info` with Accept or Cancel task. Ledger
  tables and the cargo hold handle cargo. Progress lives in Jagex's `port_task_slot_*` varbits. There
  are 438 tasks with wiki XP (`enum.friendscape_courier_task_xp`), and slots unlock at
  1/7/28/56/84. Port coin bags pay out the wiki ranges. Relocating a boat confiscates crates and
  hands them back to the task.
- **Bounty tasks (plumbing only)**: the 163 native bounty rows load with wiki XP per creature
  (`enum.friendscape_bounty_task_xp`, keyed by bounty item). Each kill of the target rolls 1/rarity
  for the first held bounty that still needs parts, so one kill never rolls twice. Port masters (new
  `port_master` column on `friendscape_sailing_ports`) take parts with Claim-rewards. A claim pays coins
  equal to the XP, plus a 1/36 shark paint; it is atomic, so a coin overflow rolls the whole claim
  back. Cancel-task works on any port task. Boards hide bounties you don't hold
  (`PortTaskBoard.BOUNTIES_ON_BOARD`) because no sea creatures spawn yet. Admins can take one with
  `::bountytask <taskId>`. The 20 creature drop tables no longer drop bounty parts unconditionally;
  the bounty hook handles them.
- **Salvaging**: 185 shipwreck spawns cycle through active, depleted and respawned. The raft swaps
  its cargo hold for a hook (Modify); skiff and sloop carry both. Deploy the hook next to a wreck.
  Sort salvage at port salvaging stations using the wiki loot tables.
- **Charting**: generic "Chart" landmarks and 57 spyglass discoveries. Observation positions and
  Sailing requirements are packed in `friendscape_sailing_observations`; descriptions and first-time
  XP come from the native charting tables and `enum_5868` (tenths of XP), exposed as
  `friendscape_charting_first_xp` because this unnamed enum has no native gameval. Completion uses native
  `sailing_charting_completion_0..11` varps keyed by the task's unique ID. Existing custom landmark
  progress migrates when opening the log without awarding XP again.
- **Captain's log**: spyglass > Inspect lists supported tasks by
  sea, completion counts, hints, observation coordinates and requirements. Captain's log items also
  support View-charts and View-tasks. Stop your boat, reach an observation point and Look-through
  the spyglass to record it. Island and cave observation points also work on foot.
- **Pandemonium**: the introductory quest is implemented and rewards the spyglass; Sailing stays
  available before quest completion.
- **Boat purchases**: shipwright Buy-boat. Skiffs cost 15,000 coins at level 15; sloops cost 200,000 coins at level 50. Payment and ownership are rechecked atomically.
- **Boat parts**: Make-X at shipwright workbenches makes all seven hull tiers and large hull parts. Anvils make bronze through rune keel parts; use five ordinary parts on an anvil for a large part.

## Not done / deviations

- No sea creature spawns or boat combat (so bounties can't be earned in play), trawling, crew,
  charting activities other than landmarks and spyglass observations, Barracuda Trials or facility upgrades; everyone owns a raft.
- Set/Un-set and reverse sailing controls work; Trim has no wind-gust mechanic yet.
- Spyglass discoveries use a three-tile square around each published observation point rather
  than Jagex's unavailable server observation zones. The view is described in chat rather than
  displayed through the native camera lens. Repeat discovery XP and sea/ocean bonuses are not
  awarded. The log lists only supported tasks, not full native sea completion.
- Boards don't rotate or refresh offers over time and have no guaranteed bounty; the choice is
  fixed by level. Cancelling from the board skips the live cancel warning.
- Bounty parts go to the inventory, or the ground if it's full. Unique parts don't get the doubled
  drop rate before the count is reached. Port masters are wired at the 30 ports with a ledger,
  but only Port Sarim's spawns, so it's the only place to claim for now.
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
