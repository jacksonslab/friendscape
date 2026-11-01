# Core Fletching additions

All quantities below are one of each input and one output. XP is Fletching XP;
the production table stores tenths. Object symbols were resolved against
`.data/gamevals-binary/gamevals.dat`, without rebuilding it.

| Recipe | Level | XP | Table |
| --- | ---: | ---: | --- |
| Light frame + ballista limbs -> incomplete light ballista | 47 | 15 | attaching |
| Incomplete light ballista + ballista spring -> unstrung light ballista | 47 | 15 | attaching |
| Unstrung light ballista + monkey tail -> light ballista | 47 | 300 | stringing |
| Heavy frame + ballista limbs -> incomplete heavy ballista | 72 | 30 | attaching |
| Incomplete heavy ballista + ballista spring -> unstrung heavy ballista | 72 | 30 | attaching |
| Unstrung heavy ballista + monkey tail -> heavy ballista | 72 | 600 | stringing |
| Mithril bolt + mith grapple tip -> mith grapple (unf) | 59 | 11 | attaching |
| Mith grapple (unf) + rope -> mith grapple | 59 | 0 | stringing |
| Unstrung lyre + golden wool -> lyre | 25 | 1 | stringing |
| Craw's bow (u) + fangs of Venenatis -> Webweaver bow (u) | 85 | 0 | attaching |
| Yew shortbow + Scurrius' spine -> bone shortbow (knife held) | 35 | 300 | assembly |

## Primary sources

- https://oldschool.runescape.wiki/w/Fletching/Experience_table
- https://oldschool.runescape.wiki/w/Incomplete_light_ballista
- https://oldschool.runescape.wiki/w/Unstrung_heavy_ballista
- https://oldschool.runescape.wiki/w/Heavy_ballista
- https://oldschool.runescape.wiki/w/Mithril_bolts
- https://oldschool.runescape.wiki/w/Mith_grapple
- https://oldschool.runescape.wiki/w/Webweaver_bow
- https://oldschool.runescape.wiki/w/Bone_shortbow
- https://oldschool.runescape.wiki/w/Greenman_mask

The wiki Creation table and level-up table specify level 59 for the finished
grapple; its summary Fletching table inconsistently says level 1. These rows use 59.
Webweaver and finished grapple have documented one-tick recipes; bone shortbow
has documented zero ticks. Ballista timings are unknown on the wiki. Ballista,
lyre and unfinished grapple use a provisional one-tick cadence, pending live
verification; this is an implementation choice, not a measured OSRS timing.

## Deliberately excluded

- All six Greenman styles: persistent per-player unlock flags, accepting any mask
  style, confirmation, and free change-style UI cannot be represented by ordinary
  consuming production rows. A plain row would repeatedly charge leaves/award XP.
- Scorching bow: already registered by `TormentedDemonCraftingScript` for
  tormented synapse + unstrung magic longbow. No duplicate row added.
- 32 Vale-totem offering rows: require the Vale Totems facility and state flow.
- Repair-bench recipe: requires its facility handler.
- Historian Aldo's paid bone-shortbow service and Derse Venator's paid Webweaver
  service: require NPC interactions and coin fees; only direct skill routes added.
- Charged Craw's bow conversion is not added: retaining/refunding stored ether
  needs a dedicated charge-aware path.

Bone shortbow uses an assembly item pair with a held knife; putting that same
pair in attaching as well would collide across event families. Ballista final
stages belong only to stringing. No runtime, quest, NPC, unlock or charge logic
is implemented by this pack change. Parent work owns atomic transactions and
runtime verification. No cache build, Gradle run or commit was performed.
