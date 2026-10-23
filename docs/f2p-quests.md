# F2P quest continuation

Continues the `f2p-quests` branch after `17e945cdb`.

## Content

- Demon Slayer: Aris, the three keys, Traiborn's bone collection, Silverlight,
  an owned Delrith encounter, and the incantation. Delrith only takes damage
  from his owner's equipped Silverlight. Lost swords and sewer keys can be
  recovered without restarting the quest.
- Shield of Arrav: Reldo's offer and library book, both gang recruitment paths,
  the weapon store, shield halves, museum certificates, partner trading, and
  King Roald's reward. The original Phoenix/Black Arm progress values are used
  alongside the cache's separate quest completion varbit.
- Dragon Slayer I: the Guildmaster and Oziach, maze doors and cellar access,
  the Oracle's four offerings, Wormbrain's purchase option and existing combat
  drop, map assembly, ship purchase and repairs, Ned, Crandor access, the
  secret wall, a private Elvarg fight, and the final proof/reward.
- Below Ice Mountain: crew recruitment, Atlas training and Flex, the sandwich
  recipe, Burntof's game, the guardian, all four mineable pillars, and the
  Camdozaal entrance. Pillar locations come from the actual cache map.
- The Corsair Curse: ferry travel, the three investigations, Ithoi's confession
  and exposure, his private fight with elemental strike spells, and bank access.

Shared quest arenas return players to a persistent exit on logout/restart,
release their NPC/region when abandoned, and put death drops outside the room.
Item exchanges use strict inventory transactions so failed exchanges roll back.

## Validation

Checked locally on revision 241 with Java 21:

- `:or-cache:freshCache`, `:or-cache:mergePluginGamevals`, and the subsequent
  `:or-cache:buildCache` completed successfully.
- `:content:quest:test`: 287 tests, zero failures/errors. This includes 22 new
  tests covering item consumption, missing inputs, repeat actions, full packs,
  and coin overflow.
- `:server:app:shadowJar` completed successfully.
- The packaged server reached `Server ready`, bound port 43594, and loaded the
  quest scripts without duplicate registrations or missing gamevals.
- The temporary verification server was stopped after boot verification.

Generated `.data/gamevals/*.rscm` changes are deliberately excluded from the
source commit. Plugin `gamevals.toml` and pack configurations are authoritative.

## Remaining verification and fidelity

No live client was connected during implementation. Full player walkthroughs,
two-player Shield of Arrav trading, arena deaths/re-entry, and reconnects still
need client verification. This branch has not been deployed.

These implementations use concise dialogue and narrated scene transitions rather
than reproducing the full original dialogue trees and camera cutscenes. Elvarg
uses the existing dragon combat/protection framework. The existing maze monster
drop tables remain in use, including their pre-existing permissive key drops.
