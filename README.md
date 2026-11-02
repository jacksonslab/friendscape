# Friendscape

A private Old School RuneScape server for friends, built on
[OpenRune Server](https://github.com/OpenRune/OpenRune-Server) (itself a fork of
[RSMod](https://github.com/rsmod/rsmod)). It speaks the **revision 241** protocol, runs on Kotlin
and Java 21, and keeps gameplay as auto-loaded plugins under `content/`.

Development happens on the `friendscape-release` branch. `main` tracks upstream OpenRune and is
merged in from time to time.

## What Friendscape adds

These come on top of the upstream OpenRune content:

| Area | What's there | Docs |
|---|---|---|
| New accounts | Tutorial Island restored for rev 241 (tutors, appearance editor, skill lessons, Lumbridge exit). Accounts that already existed skip it | [friendscape-expansion.md](docs/friendscape-expansion.md) |
| Account modes | Normal, Ironman, Ultimate, Hardcore and **Group Ironman** chosen on the island. `::group` creates, invites, joins and leaves; group storage uses the native shared bank | [ironman.md](docs/ironman.md) |
| Perks | Every character is a member; `::changename` renames with no cooldown | — |
| Bank Key | Untradeable key that summons a personal banker; every character gets one | [bank-key.md](docs/bank-key.md) |
| Admin | In-game `::admin` panel: players, teleports, item spawner, god mode, broadcasts, global NPC renames | [admin-panel.md](docs/admin-panel.md) |
| Realm settings | Global XP rate and an infinite run energy toggle, read from the `realms` table | — |
| Skills | Agility, Construction, Farming, Fletching, Hunter, Prayer training, Thieving parity, Cooking burn curves, Camdozaal mining | [agility-verification.md](docs/agility-verification.md), [construction.md](docs/construction.md) |
| Sailing | Stage 1: boats, helm, sails, cargo, salvage hooks, spyglass charting and the Pandemonium quest. Stages 2–4 are still to come | [sailing-development.md](docs/sailing-development.md) |
| Quests | Remaining F2P quests, Pandemonium, Twilight's Promise | [f2p-quests.md](docs/f2p-quests.md) |
| Bosses | Theatre of Blood, a second Gemstone Crab in Lumbridge Swamp (custom DeVito model), boss HP bar fixes | [devito-boss-model.md](docs/devito-boss-model.md), [boss-hp-bar.md](docs/boss-hp-bar.md) |
| World | Player-to-player trading, Quetzal transport across Varlamore, Varrock stalls and doors, 1600-slot bank, 30-minute idle logout | — |

What still needs doing (client playthroughs, the remaining Sailing stages, release checks) is
tracked at the bottom of [friendscape-expansion.md](docs/friendscape-expansion.md).

<!-- content-progress:start -->

## 📊 Content progress

Skills **23/23** · Bosses **34/169** · Raids **1/4** · Minigames **1/52**

Full breakdown in **[PROGRESS.md](PROGRESS.md)**, including every content module and
the bosses that already have drop tables and only need the encounter writing.

<!-- content-progress:end -->

## Running it locally

You need **Java 21** (the `tools` modules build on 17 through Gradle's toolchain). Accounts,
characters, saves and Ironman groups live in PostgreSQL. With `central.postgres.jdbc-url` left
blank (the default), the server starts an embedded instance with its data in `.data/pgdata`. Set
`jdbc-url`, `user` and `password` to use your own server instead.

```bash
git clone https://github.com/jacksonslab/friendscape.git
cd friendscape
cp game.example.yml game.yml   # set name, world and port
./gradlew install              # generates the RSA key (.data/client.key) and logging config
./gradlew run
```

Copy `game.example.yml` before the first run. Without a `game.yml`, the server writes an old
built-in default (revision 233, no central config) that won't boot this branch.

The server is up when it prints `OpenRune Server Successfully initialized`. If you only see
`OpenRune Server Loaded up in x ms.`, the install step didn't finish.

After changing anything under a `-pack` module or a `gamevals.toml`, rebuild the cache before
running:

```bash
./gradlew :or-cache:buildCache
```

Generated `.data/gamevals` output is build output. Don't commit it.

IntelliJ users can install `OpenRune IntelliJ Tools-1.1.zip` from the project root (Settings →
Plugins → Install Plugin from Disk) and point it at `openRune-intelliJ-tools.toml` for entity
reference tooling.

### Config worth knowing

`game.yml` (see [game.example.yml](game.example.yml) for every key):

- `gameplay.drop-rates.multiplier`: server-wide multiplier on boosted drops
- `gameplay.idle-logout-minutes`: defaults to 30
- `gameplay.home-teleport-cooldown`: minutes, `0` turns it off
- `gameplay.quest-requirements.mode`: `assume-completed`, `respect-progress` or `virtual-completions`
- `world-types`: `main`, a league, or several ([world-types.md](docs/world-types.md))

The XP rate and infinite run energy live on the world's row in the `realms` table, not in
`game.yml`.

## Connecting a client

Use [RSProx](https://github.com/blurite/rsprox/releases). Add this to
`~/.rsprox/proxy-targets.yaml` (`%USERPROFILE%\.rsprox` on Windows; launch RSProx once if the
folder doesn't exist):

```yaml
config:
  - id: 1
    name: Friendscape
    jav_config_url: https://client.blurite.io/jav_local_241.ws
    varp_count: 15000
    revision: 241
    modulus: YOUR_MODULUS_KEY_HERE
```

Replace `YOUR_MODULUS_KEY_HERE` with the contents of `.data/client.key` from the server you're
connecting to. RSProx's private-server mode currently works on Windows and Linux only, not macOS.

## Testing

```bash
./gradlew test                                # everything, in parallel
./gradlew :content:other:bank-key:test        # a single module
```

For in-client checks, the
[OpenRune-Developer-Tools](https://github.com/OpenRune/OpenRune-Developer-Tools) client plugin
runs an MCP server on `http://127.0.0.1:7780/mcp`. An AI agent can use it to walk dialogue, click
NPCs and objects, and screenshot interfaces. [AGENTS.md](AGENTS.md) covers setup and the
verification flows, and is also the guide for writing content plugins.

## Releases

The **Release Server** workflow builds a self-contained `openrune-server-release.zip` (`server.jar`,
`game.yml` and the compiled `.data/`). Run it by hand from **Actions → Release Server** against
`friendscape-release`. See [docs/RELEASE_CI.md](docs/RELEASE_CI.md).

Before deploying to the live world, back up the database and keep the previous jar and cache so
you can roll back. Ship the cache and server together: a jar built against a different cache
won't load.

## Documentation

- [AGENTS.md](AGENTS.md): build commands, module layout, plugin conventions
- [docs/](docs): one page per system (doors, gates, drops, instances, world types, …)
- [openwiki/](openwiki): generated upstream architecture wiki, starting at
  [openwiki/quickstart.md](openwiki/quickstart.md)

## Credits

Friendscape builds on [OpenRune Server](https://github.com/OpenRune/OpenRune-Server) and its
contributors, [RSMod](https://github.com/rsmod/rsmod), and
[OpenRune-FileStore](https://github.com/OpenRune/OpenRune-FileStore) for cache handling. See
[LICENSE.md](LICENSE.md).

Old School RuneScape is a trademark of Jagex Ltd. This project isn't affiliated with or endorsed
by Jagex.
