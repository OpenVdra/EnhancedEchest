package com.enhancedechest.crossserver;

import com.enhancedechest.scheduler.Scheduler;
import com.enhancedechest.telemetry.Telemetry;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.slf4j.Logger;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

import javax.net.ssl.SSLParameters;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Redis implementation of {@link CrossServerCoordinator}: one lock key per owner
 * ({@code <prefix>lock:<uuid>} holding this server's id, TTL-protected and heartbeat-extended) plus
 * one pub/sub channel ({@code <prefix>events}) used to hand owners over quickly.
 *
 * <p><b>Lock lifecycle.</b> {@link #acquireOwner} loops on {@code SET NX PX}; while another server
 * holds the key it publishes a {@code req} message each round and waits for the matching {@code rel}
 * (with a poll fallback), giving up with {@link CrossServerLockException} after the acquire timeout.
 * The lock is never stolen from a live holder — a holder that crashed simply stops heartbeating and
 * its keys expire after {@link #LOCK_TTL_MS}. The heartbeat re-extends (or restores, after a Redis
 * outage) every held key and logs loudly if a key turns out to be owned by someone else (split-brain
 * signal, should never happen).
 *
 * <p><b>Handover.</b> An incoming {@code req} for an owner this server holds is passed to the
 * release-request handler (wired in the plugin bootstrap: flush + evict + release, provided the
 * player is not online here and no chest session of theirs is still open or saving). Requesters
 * re-publish every poll round, so a request that arrives too early — the player is still
 * mid-disconnect here — is simply honored a round later; no state is kept for it.
 *
 * <p>All blocking work (acquire loops, Redis I/O) runs on the async storage executor or this class's
 * own daemon threads — never on a tick thread.
 */
public final class RedisCoordinator implements CrossServerCoordinator {

    /** Lock key TTL: how long after a holder stops heartbeating (crash) its owners free up. */
    private static final long LOCK_TTL_MS = 30_000;
    /** Heartbeat period; comfortably under the TTL so a busy-but-alive holder never expires. */
    private static final long HEARTBEAT_MS = 10_000;
    /** How long {@link #acquireOwner} keeps trying before failing the storage operation. */
    private static final long ACQUIRE_TIMEOUT_MS = 10_000;
    /** One wait-then-republish round while another server holds the lock. */
    private static final long POLL_MS = 500;

    /** Extend the key if it is ours; restore it if it vanished (Redis outage); report a foreign owner. */
    private static final String HEARTBEAT_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              redis.call('pexpire', KEYS[1], ARGV[2]); return 1
            elseif redis.call('exists', KEYS[1]) == 0 then
              redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2]); return 2
            else
              return 0
            end""";

    /** Compare-and-delete: only ever remove a key that still carries our id. */
    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end""";

    private final String serverId;
    private final String keyPrefix;
    private final Logger logger;
    private final Telemetry telemetry;
    private final Scheduler scheduler;

    private final HostAndPort address;
    private final JedisClientConfig clientConfig;
    private final JedisPool pool;

    /** Owners whose lock this server holds. Local bookkeeping only, always mutated with the key ops. */
    private final Set<UUID> held = ConcurrentHashMap.newKeySet();
    /** Owners between {@link #beginRelease} and {@link #finishRelease}: no longer held, key not yet deleted. */
    private final Set<UUID> releasing = ConcurrentHashMap.newKeySet();
    /** Per-owner futures completed by an incoming {@code rel} message, waking the acquire loop early. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> waiters = new ConcurrentHashMap<>();

    /** Flush-evict-release callback for owners another server asks for. Wired after the storage layer. */
    private volatile Consumer<UUID> releaseRequestHandler;

    private volatile boolean closed;
    private Thread subscriberThread;
    private ScheduledTask heartbeatTask;

    /** The live pub/sub handler; {@link #close()} unsubscribes it to unblock the subscriber thread. */
    private final JedisPubSub pubSub = new JedisPubSub() {
        @Override
        public void onMessage(String channel, String message) {
            try {
                handleMessage(message);
            } catch (Exception e) {
                logger.warn("Failed to handle cross-server message '{}'", message, e);
                telemetry.error(e, "crossserver.message");
            }
        }
    };

    public RedisCoordinator(String host, int port, String password, boolean ssl, int database,
                            String keyPrefix, String serverId, Scheduler scheduler,
                            Logger logger, Telemetry telemetry) {
        this.serverId  = serverId;
        this.keyPrefix = keyPrefix;
        this.scheduler = scheduler;
        this.logger    = logger;
        this.telemetry = telemetry;
        this.address   = new HostAndPort(host, port);
        DefaultJedisClientConfig.Builder cfg = DefaultJedisClientConfig.builder()
                .database(database)
                .ssl(ssl)
                .timeoutMillis(5_000);
        if (ssl) {
            // Jedis validates the certificate chain against the JVM truststore when ssl is on, but does
            // NOT verify the hostname by default. Turn on endpoint identification so a valid certificate
            // issued for a different host is rejected — i.e. full verify-full behaviour, not just verify-ca.
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            cfg.sslParameters(sslParameters);
        }
        if (password != null && !password.isEmpty()) {
            cfg.password(password);
        }
        this.clientConfig = cfg.build();
        this.pool = new JedisPool(address, clientConfig);
    }

    /**
     * Verifies the connection (PING), then starts the pub/sub subscriber thread and the heartbeat.
     * Throws on an unreachable/misconfigured Redis so the plugin can refuse to start in cross-server
     * mode rather than run unsynchronized against a shared database.
     */
    public void init() {
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }
        subscriberThread = new Thread(this::runSubscriber, "EnhancedEchest-redis-sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        heartbeatTask = scheduler.runTimerAsync(this::heartbeat, HEARTBEAT_MS, HEARTBEAT_MS,
                TimeUnit.MILLISECONDS);
        logger.info("Cross-server coordination ready — Redis {} as server '{}'", address, serverId);
    }

    /** Wires the handler invoked when another server asks for an owner this server holds. */
    public void setReleaseRequestHandler(Consumer<UUID> handler) {
        this.releaseRequestHandler = handler;
    }

    public String serverId() {
        return serverId;
    }

    private String lockKey(UUID owner) {
        return keyPrefix + "lock:" + owner;
    }

    private String channel() {
        return keyPrefix + "events";
    }

    // ---- CrossServerCoordinator ----

    @Override
    public void acquireOwner(UUID owner) {
        if (isHeld(owner)) {
            return;
        }
        String key = lockKey(owner);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ACQUIRE_TIMEOUT_MS);
        while (true) {
            if (closed) {
                throw new CrossServerLockException("Coordinator is shut down");
            }
            String holder;
            try (Jedis jedis = pool.getResource()) {
                String ok = jedis.set(key, serverId, SetParams.setParams().nx().px(LOCK_TTL_MS));
                if ("OK".equals(ok)) {
                    held.add(owner);
                    return;
                }
                holder = jedis.get(key);
            }
            if (holder == null) {
                continue;                          // freed between SET and GET — retry immediately
            }
            if (holder.equals(serverId) && !releasing.contains(owner)) {
                // Our own id but not mid-release: a stale key from a previous run with the same
                // configured server-id (crash before the TTL expired). Safe to adopt — the data it
                // guarded was this server's own.
                try (Jedis jedis = pool.getResource()) {
                    jedis.pexpire(key, LOCK_TTL_MS);
                }
                held.add(owner);
                return;
            }
            if (System.nanoTime() >= deadline) {
                throw new CrossServerLockException("Player data for " + owner + " is locked by server '"
                        + holder + "' (still online there, or their data is still being written back)");
            }
            // Ask the holder to hand the owner over, then wait one round for the release
            // notification (poll fallback covers a lost message). Re-published every round, so a
            // request that raced the holder's own quit processing is honored on the next one.
            CompletableFuture<Void> waiter = new CompletableFuture<>();
            waiters.put(owner, waiter);
            publish("req|" + owner + '|' + serverId);
            try {
                waiter.get(POLL_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | java.util.concurrent.ExecutionException ignored) {
                // fall through to the next SET NX attempt
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CrossServerLockException("Interrupted while waiting for " + owner + "'s lock");
            } finally {
                waiters.remove(owner, waiter);
            }
        }
    }

    @Override
    public boolean isHeld(UUID owner) {
        return held.contains(owner);
    }

    @Override
    public boolean isHeldElsewhere(UUID owner) {
        if (held.contains(owner)) {
            return false;
        }
        try (Jedis jedis = pool.getResource()) {
            String holder = jedis.get(lockKey(owner));
            return holder != null && !holder.equals(serverId);
        } catch (Exception e) {
            logger.warn("Redis read failed while checking {}'s lock — assuming held elsewhere", owner, e);
            telemetry.error(e, "crossserver.is-held-elsewhere");
            return true;                           // fail safe: don't touch data we can't verify
        }
    }

    @Override
    public void beginRelease(UUID owner) {
        if (held.remove(owner)) {
            releasing.add(owner);
        }
    }

    @Override
    public void finishRelease(UUID owner) {
        if (!releasing.remove(owner)) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.eval(RELEASE_SCRIPT, List.of(lockKey(owner)), List.of(serverId));
        } catch (Exception e) {
            logger.warn("Redis release failed for {} — the lock will expire on its own within {}s",
                    owner, LOCK_TTL_MS / 1000, e);
            telemetry.error(e, "crossserver.release");
            return;
        }
        publish("rel|" + owner + '|' + serverId);
    }

    // ---- messaging ----

    private void publish(String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel(), message);
        } catch (Exception e) {
            logger.warn("Redis publish failed ({}) — relying on polling/TTL instead", message, e);
            telemetry.error(e, "crossserver.publish");
        }
    }

    /** Dedicated subscriber connection; reconnects with backoff until {@link #close()}. */
    private void runSubscriber() {
        while (!closed) {
            try (Jedis jedis = new Jedis(address, clientConfig)) {
                jedis.subscribe(pubSub, channel());        // blocks until unsubscribed / disconnected
            } catch (Exception e) {
                if (closed) {
                    return;
                }
                logger.warn("Redis subscriber disconnected — retrying in 2s ({})", e.getMessage());
            }
            if (!closed) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleMessage(String message) {
        String[] parts = message.split("\\|", 3);
        if (parts.length != 3) {
            return;
        }
        UUID owner = UUID.fromString(parts[1]);
        String from = parts[2];
        switch (parts[0]) {
            case "req" -> {
                if (from.equals(serverId) || !held.contains(owner)) {
                    return;                        // our own request, or nothing to hand over
                }
                Consumer<UUID> handler = releaseRequestHandler;
                if (handler != null) {
                    handler.accept(owner);
                }
            }
            case "rel" -> {
                CompletableFuture<Void> waiter = waiters.get(owner);
                if (waiter != null) {
                    waiter.complete(null);
                }
            }
            default -> { }
        }
    }

    // ---- heartbeat ----

    private void heartbeat() {
        for (UUID owner : held) {
            try (Jedis jedis = pool.getResource()) {
                Object result = jedis.eval(HEARTBEAT_SCRIPT, List.of(lockKey(owner)),
                        List.of(serverId, String.valueOf(LOCK_TTL_MS)));
                if (result instanceof Long r && r == 0L) {
                    // Another server owns a key we believe is ours — only possible after this server
                    // was unresponsive past the TTL while someone acquired. Both copies may now
                    // flush; data loss is possible. Make it impossible to miss.
                    logger.error("SPLIT-BRAIN: owner {} is locked by another server while still "
                            + "resident here. Check for extreme lag/pauses or duplicate server-ids.",
                            owner);
                    telemetry.error(new IllegalStateException("cross-server lock lost for " + owner),
                            "crossserver.lock-lost");
                }
            } catch (Exception e) {
                logger.warn("Redis heartbeat failed — held locks may expire if this persists", e);
                telemetry.error(e, "crossserver.heartbeat");
                return;                            // one failure covers the whole round; retry next tick
            }
        }
    }

    // ---- shutdown ----

    /**
     * Releases every held lock (after the caller has flushed the cache — see the {@code onDisable}
     * ordering) and stops the subscriber, heartbeat and pool.
     */
    public void close() {
        closed = true;
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }
        for (UUID owner : held) {
            beginRelease(owner);
            finishRelease(owner);
        }
        try {
            if (pubSub.isSubscribed()) {
                pubSub.unsubscribe();              // unblocks the subscriber thread's socket read
            }
        } catch (Exception ignored) {
            // best-effort; the daemon thread exits on the next disconnect either way
        }
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        try {
            pool.close();
        } catch (Exception ignored) {
            // pool teardown on shutdown — nothing actionable
        }
    }
}
