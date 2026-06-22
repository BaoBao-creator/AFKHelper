# AFKHelper multi-account background sessions

AFKHelper targets Minecraft/Fabric 1.18 and Java 17. The multi-account path is intentionally limited to offline-mode/cracked servers: it creates `Session` instances with the vanilla `OfflinePlayer:<name>` UUID algorithm and `LEGACY` account type. It does not use `FakePlayerEntity`, local entity simulation, or a client restart.

## Minecraft client internals that must be intercepted

A normal `MinecraftClient.disconnect(Screen)` tears down the active `ClientWorld`, `ClientPlayerEntity`, `ClientPlayerInteractionManager`, screen state, and ultimately closes the `ClientConnection` held by `ClientPlayNetworkHandler`. For `/bot join <username>`, that teardown cannot be used because it would destroy the old account's server-visible socket.

The implementation therefore preserves these references before the UI transition:

* `ClientPlayNetworkHandler` and its `ClientConnection` are moved into `BotConnection`.
* The current `Session` is copied into `SessionIdentity` for the background record.
* The current `ServerInfo`/socket address is copied for diagnostics and future switch logic.
* The visible client's `player`, `interactionManager`, `cameraEntity`, `targetedEntity`, `crosshairTarget`, and `world` references are cleared without calling vanilla disconnect.
* `MinecraftClient.session` is changed through `MinecraftClientSessionAccessor`, because the field is private/final in normal client code paths.
* `ClientPlayNetworkHandlerMixin` observes play packets on preserved handlers so the background model sees keepalives, disconnects, join, position, chat, spawn, player-list, and respawn events.

## Session transition

`/bot join Steve` is handled by `BotCommands`, then `BotManager`, then `SessionTransitionManager`:

1. Validate `Steve` as an offline-mode username.
2. Capture the active handler/connection for Alex.
3. Store Alex as a `BotConnection` in `BackgroundConnectionStore`.
4. Start a dedicated background ticker for Alex's connection.
5. Clear active render/control/world references from `MinecraftClient`.
6. Replace the next active `Session` with Steve's offline-mode profile.
7. Return the visible UI to `TitleScreen`.

The result is an explicit model with an active client slot, background connection store, menu state, transition state, and preserved network state.

## Packet flow

The background connection keeps using the original Netty `ClientConnection`. Its scheduled task calls `ClientConnection.tick()` every 50 ms and checks `handleDisconnection()` when the socket closes. Vanilla's packet listener remains installed, while `ClientPlayNetworkHandlerMixin` mirrors important packets into `PacketHandler` for background bookkeeping.

`PacketHandler` handles:

* `KeepAliveS2CPacket` by immediately sending `KeepAliveC2SPacket`.
* `DisconnectS2CPacket` by closing only that background connection.
* `GameJoinS2CPacket`, `PlayerPositionLookS2CPacket`, `GameMessageS2CPacket`, `EntitySpawnS2CPacket`, `PlayerListS2CPacket`, and `PlayerRespawnS2CPacket` by refreshing liveness metadata.

## Thread safety and error handling

`BackgroundConnectionStore` uses a `ConcurrentHashMap`. `BotConnection` uses atomics for state, close coordination, background/active flags, and keepalive timestamps. Each background session has a single daemon scheduler so network ticks are serialized per connection and never block the main render thread. Join/leave operations remove sessions before closing them, which avoids double-close races.

If capture fails, `/bot join` reports the reason to chat and does not mutate session state. If a background tick throws or the socket closes, the connection moves to `ERROR`/`DISCONNECTED` and shuts its scheduler down.

## Commands

* `/bot join <username>` preserves the current connection in the background, returns to the menu, and stores `<username>` as the next active offline profile.
* `/bot list` reports active/background sessions, connection status, ping, uptime, and next active profile.
* `/bot leave <username>` closes only the named background session.
* `/bot leave all` closes all background sessions.
