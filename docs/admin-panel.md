# Friendscape in-game administration

Log in with an Administrator account and enter `::admin` or `::adminpanel`.
The panel fits the 512 x 334 fixed viewport and uses the standard steel frame,
close button and stone controls.

- **Online players:** paginated list; select a name to see rank and coordinates in chat.
- **Find player:** exact online display name or username, ignoring case.
- **Go to player / Bring player:** teleport to a player or confirm bringing them to you.
  Bringing respects the target's protected actions; a busy target is left in place.
- **Teleport coordinates:** enter `x z plane`, or comma-separated values. Coordinates
  must be in 0–16383 and the plane in 0–3.
- **Item spawner / Open bank:** opens the existing item grid or bank interface.
- **Restore and cure:** restores your hitpoints and prayer to their base levels and
  clears poison, venom and disease. It does not change experience.
- **Toggle god mode / Toggle max hit:** switches the existing admin combat tools;
  current values appear at the bottom of the panel.
- **Toggle infinite stamina:** fills run energy and keeps it full while enabled.
  Requires Administrator rights and resets on logout; the status appears at the bottom.
- **Broadcast:** preview and confirm a plain-text message of up to 160 characters.
- **Refresh:** updates the online count, position and toggle status.
- **Global NPC names:** search by name or numeric ID, choose a nearby NPC type, or
  review saved overrides. Rename every copy of that type or restore its default
  name. Changes apply to existing NPCs and future spawns and survive a restart.
  This editor is also available with `::npcnames`. Names accept 1–40 printable
  English characters without client markup. Changes are confirmed before saving.

NPC name settings are stored in `config/npc-names.properties` on the server. Normal
deployments preserve this directory. NPC type IDs, interactions and stats are unchanged.

Player lookup and input use the game's standard menu and chatbox prompts. Enter
`::admin` to return after closing a tool. Rank changes, bans and account settings
remain available in the web admin panel.

Every button checks current Administrator rights, including after input and
confirmation prompts. State-changing actions write an `Admin panel:` entry to the
server log. These tools act in the current world; lookup requires an online player.

## Build and verify

The commands module now has a dedicated `commands-pack` submodule. Build the cache
before launching or packaging the server so the interface and clientscript are present:

```powershell
.\gradlew.bat :or-cache:freshCache :or-cache:mergePluginGamevals
.\gradlew.bat :content:other:commands:compileKotlin :server:app:shadowJar
```

In a matching local client, verify `::admin` at fixed and resizable sizes, close
with the frame button and Escape, and open each tool. With a second test account,
verify lookup, go-to, bring confirmation/cancellation, target logout and a busy
target. Verify broadcasts cancel cleanly and send only after confirmation. As a
Player or Moderator, `::admin` must not open; forged button events must not execute
actions. Revoke Administrator rights during a prompt and verify that submitting it
closes the panel without acting. Review `Admin panel:` log entries after actions.

This source change does not install the new release on the live server.
