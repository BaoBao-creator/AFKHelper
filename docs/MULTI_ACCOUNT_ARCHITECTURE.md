# AFKHelper account/session architecture

AFKHelper targets Minecraft/Fabric 1.18 and Java 17. Minecraft 1.18 does not expose a supported in-place account switch API for an active play connection. AFKHelper therefore only swaps the visible client identity at a vanilla disconnect boundary, then sends the player back to the title screen before the next server join.

## Crash cause fixed by this design

The old implementation tried to make `/switch` and `/bot join` work by:

* writing directly to `MinecraftClient.session` through `MinecraftClientSessionAccessor`; and
* clearing core client fields such as `player`, `interactionManager`, `cameraEntity`, `targetedEntity`, `crosshairTarget`, and `world` without a vanilla disconnect/reconnect lifecycle.

That left the client with partially replaced identity state and partially torn-down world/network/render state. Later vanilla ticks and renders still expect those fields to be consistent, so the client can crash after the command or on the next transition.

## Current safe model

`SessionTransitionManager` validates a `SessionIdentity` created with the vanilla offline UUID algorithm (`OfflinePlayer:<name>`) and `LEGACY` account type. For `/switch`, it applies that identity directly to `MinecraftClient.session` and immediately runs the vanilla disconnect flow to return to the title screen, so the next server join uses the selected offline/cracked name without mutating an active play connection in-place or creating a stored background connection.

`/bot join <username>` creates an internal AFKHelper bot profile record. It does not steal the active `ClientConnection`, does not tick vanilla networking from another thread, and does not clear the visible client's world/player references. This preserves stability on Minecraft 1.18. The record is useful for command/UI bookkeeping and for future safe bot implementations that create their own connection stack instead of reusing the active client's stack.

`/switch <username>` only assigns an offline/cracked identity to the visible client session and disconnects to the main menu. Unlike `/bot join`, it does not create or keep any AFKHelper background connection; the selected name is used when the player joins a server again.

## AFK/network-only mode

The `/afk` command remains the supported low-resource mode for the active client. It reduces FPS, view/simulation distance, particles, graphics options, sound volume, HUD visibility, and displays an empty non-pausing screen. This happens at the UI/options/render level rather than by corrupting core client state.

Render/audio mixins are intentionally limited to optional visual/audio work while AFK is enabled. They do not cancel broad client lifecycle methods such as screen ticking, game renderer ticking, world entity ticking, resource reloads, or terrain update scheduling.

## Commands

* `/afk`, `/afk on`, `/afk off`, `/afk toggle` toggle low-resource AFK mode for the active client.
* `/bot join <username>` stores an internal offline bot profile without changing the running client session.
* `/bot list` reports the active visible client, tracked internal profiles, and the currently stored offline profile.
* `/bot leave <username>` removes a tracked internal profile.
* `/bot leave all` removes all tracked internal profiles.
* `/switch <username>` switches the visible client to an offline/cracked profile, disconnects to the main menu, and does not store a background connection.
