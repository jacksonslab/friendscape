# Swamp boss model replacement

The swamp NPC (65518) and its remains (65519) now use the supplied DeVito-style
character. Existing display names are retained. The Varlamore gemstone crab keeps
its original appearance and animations.

## Format and assets

Friendscape uses the revision 241 OSRS JS5 cache, including the native client.
GLB, FBX and Blender files cannot be loaded directly. This integration uses legacy
`.dat` geometry, a transform-group skeleton in index 1, legacy animation frames
in index 0, and sequence definitions in config archive 12. The converter preserves
2,509 vertices, 4,597 triangles, flat shading, and the 13-color material palette
(quantized to RuneScape HSL). No client modifications or texture files are needed.

The editable source is `tools/assets/devito_boss.blend`. The converter is
`tools/scripts/convert_devito_boss.py`; generated assets belong to the gemstone-crab
pack. IDs are reserved in that pack's `gamevals.toml`:

| Asset | IDs |
|---|---|
| Living model / fallen remains | 63471 / 63472 |
| Skeleton / frame archive | 63070 / 30000 |
| Idle / walk / attack / death | 63070 / 63071 / 63072 / 63073 |
| Remains fade / remains idle | 63074 / 63075 |
| Kick | 63076 |

124 distinct skin-weight combinations become disjoint legacy transform groups.
Each receives a fitted rotation and translation; the skeleton has 250 transforms,
including pivot and face-alpha fade. This approximates smooth skinning. At scale
256, RMS vertex error is about 0.9–1.2 game units; the largest error is 23.31 units
in the punch (about 3.2% of model height). The other clips peak below 3.6 units.
The rendered native-geometry preview is an offline check, not an in-game capture.

## Behaviour

Idle and walk are registered on the NPC. The boss remains stationary under the
existing combat rules. Attacks randomly choose a punch or front kick with equal
weights, with the existing damage, target selection and seven-tick attack interval.
Spawn starts in idle because
the archive has no spawn clip. Death is retimed from 2.5 to 2.4 seconds to match
the existing four-tick removal delay. Remains use the final death pose and fade
over the existing four-tick disintegration window. Crab-specific visual effects
are disabled only at the swamp site. The death facing is held and carried over
to the remains so the fallen body keeps its orientation.

The exported model and animations face the client's forward axis. Chest hair and
the navel are integrated into the torso topology as material regions and a small
indentation, so they no longer hover as separately weighted surface pieces.
`tools/scripts/refine_devito_boss.py` performs that repair and authors the Kick
action in Blender; the editable `.blend` already includes both changes.

Stats, five-tile footprint, immunities, timer, rewards, contribution ranking,
mining eligibility, mining items, HP bar, spawn coordinates and respawn lifecycle
are unchanged. The supplied hit-event suggestion does not change combat timing.

## Rebuild

Run from the repository root with Blender 5.2:

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' `
  -b tools/assets/devito_boss.blend -P tools/scripts/convert_devito_boss.py -- `
  --output content/bosses/gemstone-crab/pack/src/main/resources/pack `
  --report work/devito-conversion-report.json
```

Then use Java 21 and run:

```powershell
.\gradlew.bat :or-cache:mergePluginGamevals :or-cache:buildCache
.\gradlew.bat :content:bosses:gemstone-crab:compileKotlin `
  :content:bosses:gemstone-crab:gemstone-crab-pack:compileKotlin
```

`PackSkeleton` and the existing `PackAnims` run on the client cache pass; neither
is copied into the compact server cache. Configs and sequences are packed on both
passes normally.

## Verification and outstanding checks

The affected Kotlin modules compile. A scoped build against an isolated copy of
the revision 241 client cache succeeds. Native decoders verify both model files,
all 237 frame payloads, matching skeleton, seven sequences, durations, and NPC model
and animation references. Existing server data confirms the inherited HP and
five-tile footprint. Four decoded native poses have been rendered and inspected.

The missing `dbrow.firemaking_camphor_logs` symbol is now registered as 55558.
All 34 firemaking row references in the shared working checkout resolve. The full
client/server cache and server package builds passed in an isolated release
containing Twilight's Promise and the quetzal network; all 13 focused quest and
quetzal item-safety tests passed. Other unfinished skill changes remain outside
that release.

The server animation pack tracks the client sequence archive checksum, so adding
or changing a sequence refreshes its server timing record. Boss startup validates
the required animations. Two regression tests cover all seven timing records and
24 repeated default-priority punch/kick calls. The runtime mappings and records
were also checked directly against the installed production package and cache.

The combined release was deployed to production on November 1, 2026, after a
successful local boot and database backup. Production reached ready and its
installed package/cache checksums match the release manifest. Rollback snapshot:
`/srv/osrs/releases/20261101-071641-devito`. Player saves, RSA keys and production
configuration were preserved.

Gameplay verification in the live client remains pending. Restart the client,
then verify idle, facing, punch/hit timing, death-to-remains continuity, lifecycle
windows, mining rewards and contribution eligibility. Confirm Varlamore still
uses its original crab assets and test quest progression and quetzal travel.
