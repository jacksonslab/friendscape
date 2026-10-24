# Sailing implementation

Requested scope: full OSRS-style Sailing, delivered in stages. This branch is
`sailing-stage-1`, based on Friendscape `bfac821a4`, revision 241. It is not deployed.

The initial protocol and helm work comes from OpenRune PR 182:
https://github.com/OpenRune/OpenRune-Server/pull/182

## Stage 1: navigation and recovery (in progress)

- World-entity registry, client synchronization, moving deck scene, helm headings.
- Raft, skiff and sloop development templates with cache-driven furniture/docks.
- Acceleration, braking, bounded turning and quantized movement; four maths tests.
- Boat ownership checks, shore fallback saved before boarding, logout evacuation,
  pre-login recovery from an interrupted session, and owner-boat cleanup.
- Stopped boats can moor near a known dock; disembarking at sea is rejected.
- Forced map rebuild invalidates both the physical deck and root view zone caches.
- Generated readers distinguish named-item cache columns from ordinary item
  columns, needed for native Sailing upgrade materials.

Public boarding stays disabled until the remaining navigation work is verified:

- Authoritative navigable-water and hull collision, swept movement and rotation.
- Verified gangplank placements, dock geometry, level/quest access and mooring.
- Persistent vessel records, retrieval costs, shipyard construction and upgrades.
- Passenger synchronization, world switching, disconnects and server restarts.
- Full client testing of deck movement, scenery, inventory and ship controls.

The inherited template speeds and dock positions are development data awaiting
verification. Shared prototype boats and destination-only travel are insufficient
for the requested scope. Current recovery returns a disconnected sailor to shore;
it does not yet persist a vessel at sea.

## Stage 2: introduction and training

Pandemonium and introductory quest; seamanship, sail trimming and repair; port
tasks/courier cargo, salvage, charting, sea discovery and Barracuda Trials.
XP, unlocks and rewards must follow the cache/content data and be awarded only
after successful actions. Every item/cargo exchange needs atomic rollback tests.

## Stage 3: progression and sea content

Boat construction, keel/hull/sails/steering tiers, facilities, cargo holds, crew,
upgrades, sea hazards, encounters and combat, trawling, islands, quests, unlocks,
level 99 rewards and skillcape. Native cache rows supply the content catalog.

## Stage 4: release coverage

Verify levels 1–99, each vessel and activity, solo and group Ironman restrictions,
cargo conservation, concurrent passengers, reconnect/restart behavior and a full
client playthrough. Integrate with the quest/tutorial/Hunter/Farming release,
produce backup/rollback artifacts, then deploy the tested release.
