# crossserver/

Makes one database safe to share between several servers, by extending the storage layer's residency
invariant with a distributed leg: **resident ⇒ this server holds the owner's Redis lock**. Off by
default (`cross-server.enabled`).

| File | Responsibility |
|---|---|
| `CrossServerCoordinator` | The contract the storage layer sees, plus `NOOP` for single-server installs |
| `RedisCoordinator` | The Jedis implementation: locks, TTL/heartbeat, pub-sub handover |
| `CrossServerLockException` | A timed-out acquire — surfaces like a failed SQL read |

## Why it exists

`CachedStorage` is authoritative for every resident owner and delays the quit write-back, so on a
shared database two servers can overwrite each other when a player switches fast. The coordinator
closes exactly that hole and nothing else.

## The protocol

- **Acquire** — `loadOwner` blocks on `acquireOwner` **before** the backend read (`SET NX PX`, 30s TTL,
  heartbeat every 10s), then re-checks `isHeld` under the cache lock before flipping residency; a raced
  eviction release forces a re-acquire and re-read.
- **Release** — only the two eviction paths release, and only for owners already flushed **clean**, so
  the next acquirer always reads current SQL. The split is load-bearing: `beginRelease` inside the same
  lock hold that drops the rows, `finishRelease` (the network `DEL` + `rel` publish) outside it. Keep
  that split.
- **Handover** — pub/sub. A waiting server publishes `req` every poll round (500ms); the holder's
  handler (wired in `EnhancedEchestPlugin`) flushes + evicts + releases, but **skips while the player is
  still pinned there or `ChestSessionManager.hasActivity(owner)` is true** — the requester simply
  re-asks, so deferring is always safe.
- **Locks are never stolen.** A timed-out acquire (10s) throws `CrossServerLockException`, which fails
  like a bad SQL read. A crashed holder's locks expire through the TTL. A held key that turns up owned
  by someone else logs SEVERE (split brain).
- `findExpired` skips non-resident candidates locked by another server — that server's own sweep covers
  them.

## Operational rules

- Requires mysql/mariadb/postgres **and** a reachable Redis. Either precondition failing **disables the
  plugin at startup** rather than running unsynchronized on a shared database. Do not soften that into a
  warning.
- Single-server mode is `CrossServerCoordinator.NOOP`, which collapses the whole protocol to no-ops.
  Call sites never null-check — same pattern as `Telemetry`.
- `cross-server.server-id` identifies the holder in logs and in the release protocol; a blank value gets
  a random `srv-xxxxxxxx`.
- Every `cross-server` key is bound at startup (`needsRestart()` in the schema); a live reload only warns.
- Jedis is shaded and relocated (`libs.jedis`, plus `libs.commonspool2` / `libs.json`), and its gson
  dependency comes from the server classpath un-relocated (see the root `CLAUDE.md`).
