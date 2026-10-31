# Prayer gameplay

Prayer training and the existing combat prayer system share the server's normal personal
and realm XP multipliers. Training scripts submit base XP once.

## Training

- The Prayer table stores XP in tenths, preserving values such as 4.5 XP for bones,
  5.3 for bat bones, and 22.5 for zogre bones. Superior dragon bones require 70 Prayer.
- All seven player-owned-house altar tiers, including all four god variants, support
  offerings and restoring Prayer points. Altar multipliers live in a server cache table.
  A gilded altar gives 250%, 300%, or 350% XP with zero, one, or two lit incense burners.
- Incense burners require 30 Firemaking, a tinderbox, and one clean marrentill. They
  remain lit for `200 + Firemaking + random(0 until Firemaking)` game ticks. Re-lighting
  refreshes the timer. Only incense burners in the altar's room count toward its bonus.
- Chaos altar offerings retain their saving chance and award XP only for a valid offering.
- The Ectofuntus derives four-times bone XP from the same table. Its ingredient and empty
  container exchange is atomic, and stale grinder callbacks cannot collect a second reward.
- Libation bowls process up to 100 shards per action for two Prayer points. Blessed bone
  breaking and sunfire wine mixing use atomic inventory transactions.

## Combat prayers

Prayer collisions use prayer bit indexes consistently, so incompatible boosts and
protection prayers replace one another. Quick prayers recheck requirements and resolve
conflicting saved selections on activation. Draining the final point disables prayers in
the same tick and restores affected regeneration timers.

## Build and validation

This change updates server cache schemas. Run `:or-cache:buildCache` before compiling
and ship its server cache with the corresponding application build. Generated table
accessors must be regenerated, never edited by hand. Existing player XP needs no migration.

Run `:content:skills:prayer:test :content:skills:prayer:integration
:content:interfaces:prayer-tab:integration` for duration, training, inventory safety, and
prayer interface regression tests. These run the real cache and server script startup;
they do not replace a live client check after deployment.

Mechanics references: [Prayer](https://oldschool.runescape.wiki/w/Prayer),
[Gilded altar](https://oldschool.runescape.wiki/w/Gilded_altar),
[Incense burners](https://oldschool.runescape.wiki/w/Incense_burners),
[Libation bowl](https://oldschool.runescape.wiki/w/Libation_bowl), and
[Ectofuntus](https://oldschool.runescape.wiki/w/Ectofuntus).
