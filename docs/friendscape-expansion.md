# Friendscape expansion

Worktree: `openrune-tutorial`, branch `tutorial-island`, based on live Friendscape
`bfac821a4` (revision 241). Nothing in this worktree has been deployed yet.

## Requested scope

- Restore Tutorial Island from the older `combined` checkout.
- Add working Ironman and Group Ironman selection.
- Integrate other missing skills, including the local Hunter and Farming work.
- Implement full OSRS-style Sailing in stages (explicit user preference).

## Current work

Tutorial Island has been ported to the revision 241 APIs. The original tutors,
appearance editor, skill lessons, combat, gates, guidance and Lumbridge exit are
present. A permanent enrollment varp keeps pre-existing mainland accounts out of
the tutorial. Login-ready events run tutorial UI after the normal login scripts.
Starter-kit replacement and ingredient exchanges use inventory transactions.
The eight unit tests and five character-creation/login integration tests pass.
Full server boot passed on 2026-10-23 (revision 241, game port 43594). A client
playthrough remains open; the developer-tools listener on port 7780 is unavailable.

The original tutorial NPC spawn resource was removed because the revision 241 raw
cache already contains the island's NPC spawns. Leaving the redundant resource in
the runtime jar also causes the drop-table loader to misclassify its TOML.

Solo Ironman restrictions existed already, but `ironman_setup` lacked selection
handlers. New handlers select Normal, Ironman, Ultimate Ironman, Hardcore Ironman
or Group Ironman. Group setup uses `::group` for creation, invitations, joining,
membership and voluntary departure. New group members must still be on Tutorial
Island; group size is limited to five. Group membership is authoritative in
PostgreSQL and scoped to the character's world type.

Group storage uses the cache's native `shared_bank` and `shared_bank_side`
interfaces and `inv.inv_group_temp`. Transfers save the personal inventory and
shared items in the same PostgreSQL transaction. Inventory rollback, storage
persistence and mode restrictions are covered by eleven passing tests, including
real PostgreSQL rollback and save/reload. Group storage becomes available after
leaving the tutorial. Item metadata that cannot safely transfer is rejected.

## Sailing stages

1. Genuine boat entities and navigation: persistent ownership, boarding, helm,
   movement, collision, docking, logout/reconnect recovery and passenger safety.
2. Starter quest and training: Pandemonium, basic seamanship, port tasks, salvage,
   charting and Barracuda Trials.
3. Progression: vessels, construction and facilities, cargo, crew, upgrades,
   sea encounters and combat, unlocks and skillcape.
4. Coverage and release: cache-driven content catalog, Ironman/group behavior,
   exploit checks and complete client playthroughs.

Current Sailing status, including the native port task board and bounty task plumbing, is kept
in `docs/sailing-development.md`.

Sailing is being developed separately in `openrune-sailing`, branch
`sailing-stage-1`, from the same live baseline. OpenRune PR 182 supplied the
initial world-entity protocol and helm implementation. It has been adapted to
the current engine. Four movement tests pass; cache build and compile pass.
Owner checks and shore recovery on logout/restart are implemented. Public
boarding is disabled while collision, docking, persistent vessels, and native
cache-driven boat configuration are completed. No Sailing XP/training is shipped.

## Validation and integration still required

- Re-run the combined checks after integration with the other feature branches.
- Verify character creation and a full tutorial with a fresh character.
- Perform the full tutorial in every supported account mode in a live client.
- Integrate the completed F2P quest commits, Hunter `9266377f9`, and Farming work
  without losing their existing tests or colliding custom IDs.
- Implement and validate each Sailing stage before describing it as complete.
- Produce a tested release with database backup and rollback before deployment.

Generated `.data/gamevals` changes are build output and should not be committed.
All local builds use Java 21.
